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
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The phone's side of the link, shared by the web controller and the Android app:
 * joins the host, streams inputs, and reports every message the host sends back.
 */
class ControllerClient(private val http: HttpClient = HttpClient { install(WebSockets) }) {
    private val outbox = Channel<ControllerMessage>(Channel.CONFLATED)
    private var last: PlayerInput? = null

    /** Queues the latest input; older unsent inputs are dropped, only the newest matters. */
    fun send(input: PlayerInput) {
        if (input == last) return
        last = input
        outbox.trySend(ControllerMessage.Input(input.steer, input.paddle))
    }

    /**
     * Connects and stays connected until the socket closes; [onMessage] runs for every host message.
     * Pass the player id from an earlier Welcome as [rejoinId] to get the same boat back after a reload or a drop.
     */
    suspend fun run(url: String, name: String, rejoinId: Int?, onMessage: (HostMessage) -> Unit) {
        http.webSocket(url) {
            send(Frame.Text(ProtocolJson.encodeToString(ControllerMessage.serializer(), ControllerMessage.Join(name, rejoinId))))
            coroutineScope {
                val writer = launch {
                    outbox.consumeEach { send(Frame.Text(ProtocolJson.encodeToString(ControllerMessage.serializer(), it))) }
                }
                for (frame in incoming) {
                    if (frame is Frame.Text) onMessage(ProtocolJson.decodeFromString(HostMessage.serializer(), frame.readText()))
                }
                writer.cancel()
            }
        }
    }

    fun close() = http.close()
}
