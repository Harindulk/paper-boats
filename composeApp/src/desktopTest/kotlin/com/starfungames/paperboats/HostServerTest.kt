package com.starfungames.paperboats

import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.party.ControllerClient
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Two phones join a real host over WebSockets, using the same client code the web and Android controllers use. */
class HostServerTest {
    @Test
    fun twoPhonesJoinRaceAndHearTheirPlaces() = runBlocking {
        val host = PartyHost(roundSeconds = 3f)
        val server = startHostServer(host, port = 0)
        val port = server.engine.resolvedConnectors().first().port
        val url = "ws://127.0.0.1:$port/ws"
        val heard = List(2) { CopyOnWriteArrayList<HostMessage>() }
        val clients = List(2) { ControllerClient(HttpClient(CIO) { install(WebSockets) }) }
        try {
            val jobs = clients.mapIndexed { i, client -> launch { client.run(url, "Phone ${i + 1}") { heard[i] += it } } }
            withTimeout(10_000) { while (host.players.size < 2 || heard.any { it.none { m -> m is HostMessage.Welcome } }) delay(20) }
            assertEquals(setOf("Phone 1", "Phone 2"), host.players.map { it.name }.toSet())
            assertEquals(setOf(0, 1), heard.map { list -> list.filterIsInstance<HostMessage.Welcome>().single().colour }.toSet())

            host.start()
            host.tick(PartySession.COUNTDOWN_SECONDS)
            clients[0].send(PlayerInput(steer = 1f, paddle = true))
            val startX = host.snapshot()!!.boats.first { it.player.name == "Phone 1" }.x
            withTimeout(10_000) {
                while (host.phase == Phase.Racing) {
                    host.tick(1f / 30f)
                    delay(10)
                }
            }
            val end = host.snapshot()!!
            assertTrue(end.boats.first { it.player.name == "Phone 1" }.x > startX, "the steering input reached the host")
            withTimeout(5_000) {
                while (heard.any { list -> list.none { it is HostMessage.Status && it.phase == Phase.Results } }) delay(50)
            }
            val places = heard.map { list -> list.filterIsInstance<HostMessage.Status>().last().place }
            assertEquals(setOf(1, 2), places.toSet())
            jobs.forEach { it.cancel() }
        } finally {
            clients.forEach { it.close() }
            server.stop(100, 500)
        }
    }

    @Test
    fun theHostServesTheControllerPage() = runBlocking {
        val server = startHostServer(PartyHost(), port = 0)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val page = java.net.URI("http://127.0.0.1:$port/index.html").toURL().readText()
            assertTrue("controller.js" in page)
        } finally {
            server.stop(100, 500)
        }
    }
}
