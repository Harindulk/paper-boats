package com.starfungames.paperboats

import com.starfungames.paperboats.game.Award
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.LogHit
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.game.Rammed
import com.starfungames.paperboats.game.River
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.BuzzKind
import com.starfungames.paperboats.protocol.ControllerMessage
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.protocol.ProtocolJson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * The party as the desktop sees it: the shared session behind a lock, plus every phone's outbox.
 * Each phone has its own queue drained by its socket coroutine, so the UI thread never waits on the network.
 */
class PartyHost(roundSeconds: Float = 60f) {
    private val party = PartySession(roundSeconds) { Random.nextLong() }
    private val outboxes = ConcurrentHashMap<Int, Channel<HostMessage>>()
    private val screenEvents = mutableListOf<RaceEvent>()
    private var sentPhase = Phase.Lobby

    val phase: Phase get() = synchronized(party) { party.phase }
    val countdownLeft: Float get() = synchronized(party) { party.countdownLeft }
    val players: List<Player> get() = synchronized(party) { party.players }
    val river: River get() = synchronized(party) { party.river }
    val raceNumber: Int get() = synchronized(party) { party.raceNumber }
    fun awards(): List<Award> = synchronized(party) { party.awards() }
    fun snapshot(): RaceSnapshot? = synchronized(party) { party.snapshot() }
    fun canStart(): Boolean = synchronized(party) { party.canStart() }
    fun start() = synchronized(party) { party.start() }
    fun join(name: String): Player? = synchronized(party) { party.join(name) }
    fun input(id: Int, input: PlayerInput) = synchronized(party) { party.input(id, input) }
    fun leave(id: Int) = synchronized(party) { party.leave(id) }

    /** Race events since the last call, for the big screen; each event is returned once. */
    fun drainEvents(): List<RaceEvent> = synchronized(party) { screenEvents.toList().also { screenEvents.clear() } }

    /** Advances the party, buzzes the phones that just got a light or a rock, and tells everyone when the phase changes. */
    fun tick(dt: Float) = synchronized(party) {
        party.tick(dt)
        val events = party.drainEvents()
        screenEvents += events
        for (event in events) {
            when (event) {
                is LightCollected -> outboxes[event.playerId]?.trySend(HostMessage.Buzz(BuzzKind.Light))

                // A whirlpool only drags, so it gets no buzz: the phone buzzes for lanterns and for bumps that stun.
                is RockHit, is LogHit, is Rammed -> outboxes[event.playerId]?.trySend(HostMessage.Buzz(BuzzKind.Rock))

                else -> Unit
            }
        }
        // start() and leave() can change the phase too, so compare with what the phones last heard.
        if (party.phase != sentPhase) {
            sentPhase = party.phase
            if (sentPhase == Phase.Results) {
                party.snapshot()?.standings?.firstOrNull()?.let { outboxes[it.player.id]?.trySend(HostMessage.Buzz(BuzzKind.Win)) }
            }
            sendStatuses()
        }
    }

    /** Handles one phone from its first message to its last. */
    suspend fun serve(socket: DefaultWebSocketServerSession) {
        val first = (socket.incoming.receiveCatching().getOrNull() as? Frame.Text)?.let(::decode)
        if (first !is ControllerMessage.Join) return socket.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Say hello first"))
        // shortcut: a full phone stops hearing the oldest messages, fine for statuses and buzzes.
        val outbox = Channel<HostMessage>(64, BufferOverflow.DROP_OLDEST)
        val player = seat(first.name, first.rejoinId, outbox)
            ?: return socket.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "All eight boats are taken"))
        try {
            coroutineScope {
                launch {
                    for (message in outbox) socket.sendMessage(message)
                    socket.close(CloseReason(CloseReason.Codes.NORMAL, "Rejoined on another connection"))
                }
                for (frame in socket.incoming) {
                    val message = (frame as? Frame.Text)?.let(::decode)
                    if (outboxes[player.id] !== outbox) continue
                    when (message) {
                        is ControllerMessage.Input -> input(player.id, message.toPlayerInput())
                        is ControllerMessage.Sail -> synchronized(party) { party.sail(player.id, message.strokes) }
                        else -> Unit
                    }
                }
                outbox.close()
            }
        } finally {
            outbox.close()
            // Only the phone's current connection may give up its seat: an old socket closing after a rejoin must not.
            synchronized(party) { if (outboxes.remove(player.id, outbox)) party.leave(player.id) }
        }
    }

    private fun seat(name: String, rejoinId: Int?, outbox: Channel<HostMessage>): Player? = synchronized(party) {
        // The same phone came back before its old socket timed out: retire that socket so the seat is free to restore.
        if (rejoinId != null) {
            outboxes.remove(rejoinId)?.let {
                it.close()
                party.leave(rejoinId)
            }
        }
        party.join(name, rejoinId)?.also {
            outboxes[it.id] = outbox
            outbox.trySend(HostMessage.Welcome(it.id, it.colour))
        }
    }

    /** Tells every phone its place, score and time left, a few times a second. */
    suspend fun broadcastForever() {
        while (true) {
            delay(200)
            synchronized(party) { sendStatuses() }
        }
    }

    private fun sendStatuses() = outboxes.forEach { (id, outbox) -> outbox.trySend(party.statusFor(id)) }

    private fun decode(frame: Frame.Text): ControllerMessage? =
        runCatching { ProtocolJson.decodeFromString(ControllerMessage.serializer(), frame.readText()) }.getOrNull()

    private suspend fun DefaultWebSocketServerSession.sendMessage(message: HostMessage) =
        send(Frame.Text(ProtocolJson.encodeToString(HostMessage.serializer(), message)))
}

/** Serves the phone controller at / and the game link at /ws. Port 0 picks any free port. */
fun startHostServer(host: PartyHost, port: Int = DEFAULT_PORT): EmbeddedServer<*, *> = embeddedServer(CIO, port = port, host = "0.0.0.0") {
    install(Compression) { gzip() }
    install(WebSockets) {
        pingPeriod = 10.seconds
        maxFrameSize = 64 * 1024L // a full sail is about 25 KB; anything bigger is not a phone
    }
    routing {
        staticResources("/", "controller")
        webSocket("/ws") { host.serve(this) }
    }
    launch { host.broadcastForever() }
}.start(wait = false)

const val DEFAULT_PORT = 8642
