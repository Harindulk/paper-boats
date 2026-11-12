package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.protocol.ControllerMessage
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The phone's side of the link, shared by the web controller and the Android app:
 * joins the host, streams inputs, and reports every message the host sends back.
 */
class ControllerClient(private val http: HttpClient = HttpClient { install(WebSockets) }) {
    private val outbox = Channel<ControllerMessage>(Channel.CONFLATED)
    private var last: PlayerInput? = null

    // The sail has its own queue so a stream of inputs never replaces it, and is kept to resend on every (re)join.
    private val sails = Channel<ControllerMessage.Sail>(Channel.CONFLATED)
    private var sail: ControllerMessage.Sail? = null

    /** Queues the latest input; older unsent inputs are dropped, only the newest matters. */
    fun send(input: PlayerInput) {
        if (input == last) return
        last = input
        outbox.trySend(ControllerMessage.Input(input.steer, input.paddle))
    }

    /** Sends the player's sail drawing now (or as soon as connected) and again after any reconnect. */
    fun sendSail(strokes: List<List<Float>>) {
        sail = ControllerMessage.Sail(strokes).also { sails.trySend(it) }
    }

    /**
     * Connects and stays connected until the socket closes; [onMessage] runs for every host message.
     * Pass the player id from an earlier Welcome as [rejoinId] to get the same boat back after a reload or a drop.
     */
    suspend fun run(url: String, name: String, rejoinId: Int?, onMessage: (HostMessage) -> Unit) {
        last = null // a new host session has no inputs yet, so resend even an unchanged one
        http.webSocket(url) {
            suspend fun write(message: ControllerMessage) =
                send(Frame.Text(ProtocolJson.encodeToString(ControllerMessage.serializer(), message)))
            write(ControllerMessage.Join(name, rejoinId))
            sail?.let { sails.trySend(it) }
            coroutineScope {
                // Plain loops, not consumeEach: cancelling these writers must leave the queues open for the next run.
                val writers = listOf(launch { for (message in outbox) write(message) }, launch { for (message in sails) write(message) })
                for (frame in incoming) {
                    if (frame is Frame.Text) onMessage(ProtocolJson.decodeFromString(HostMessage.serializer(), frame.readText()))
                }
                writers.forEach { it.cancel() }
            }
        }
    }

    fun close() = http.close()
}
