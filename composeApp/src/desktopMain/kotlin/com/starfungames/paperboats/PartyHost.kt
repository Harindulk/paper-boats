package com.starfungames.paperboats

import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.ControllerMessage
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.protocol.ProtocolJson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** The party as the desktop sees it: the shared session behind a lock, plus every phone's socket. */
class PartyHost(roundSeconds: Float = 60f) {
    private val party = PartySession(roundSeconds) { Random.nextLong() }
    private val sockets = ConcurrentHashMap<Int, DefaultWebSocketServerSession>()

    val phase: Phase get() = synchronized(party) { party.phase }
    val players: List<Player> get() = synchronized(party) { party.players }
    fun snapshot(): RaceSnapshot? = synchronized(party) { party.snapshot() }
    fun canStart(): Boolean = synchronized(party) { party.canStart() }
    fun start() = synchronized(party) { party.start() }
    fun tick(dt: Float) = synchronized(party) { party.tick(dt) }
    fun join(name: String): Player? = synchronized(party) { party.join(name) }
    fun input(id: Int, input: PlayerInput) = synchronized(party) { party.input(id, input) }
    fun leave(id: Int) = synchronized(party) { party.leave(id) }

    /** Handles one phone from its first message to its last. */
    suspend fun serve(socket: DefaultWebSocketServerSession) {
        val first = (socket.incoming.receiveCatching().getOrNull() as? Frame.Text)?.let(::decode)
        if (first !is ControllerMessage.Join) return socket.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Say hello first"))
        val player = join(first.name) ?: return socket.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "All eight boats are taken"))
        sockets[player.id] = socket
        try {
            socket.sendMessage(HostMessage.Welcome(player.id, player.colour))
            for (frame in socket.incoming) {
                val message = (frame as? Frame.Text)?.let(::decode)
                if (message is ControllerMessage.Input) input(player.id, message.toPlayerInput())
            }
        } finally {
            sockets.remove(player.id)
            leave(player.id)
        }
    }

    /** Tells every phone its place, score and time left, a few times a second. */
    suspend fun broadcastForever() {
        while (true) {
            delay(200)
            sockets.forEach { (id, socket) ->
                val status = synchronized(party) { party.statusFor(id) }
                runCatching { socket.sendMessage(status) }
            }
        }
    }

    private fun decode(frame: Frame.Text): ControllerMessage? =
        runCatching { ProtocolJson.decodeFromString(ControllerMessage.serializer(), frame.readText()) }.getOrNull()

    private suspend fun DefaultWebSocketServerSession.sendMessage(message: HostMessage) =
        send(Frame.Text(ProtocolJson.encodeToString(HostMessage.serializer(), message)))
}

/** Serves the phone controller at / and the game link at /ws. Port 0 picks any free port. */
fun startHostServer(host: PartyHost, port: Int = DEFAULT_PORT): EmbeddedServer<*, *> =
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        install(WebSockets) { pingPeriod = 10.seconds }
        routing {
            staticResources("/", "controller")
            webSocket("/ws") { host.serve(this) }
        }
        launch { host.broadcastForever() }
    }.start(wait = false)

const val DEFAULT_PORT = 8642
