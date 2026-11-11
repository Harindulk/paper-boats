package com.starfungames.paperboats

import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.party.ControllerClient
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.BuzzKind
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            val jobs = clients.mapIndexed { i, client -> launch { client.run(url, "Phone ${i + 1}", rejoinId = null) { heard[i] += it } } }
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
            val winner = places.indexOf(1)
            withTimeout(5_000) { while (HostMessage.Buzz(BuzzKind.Win) !in heard[winner]) delay(20) }
            assertFalse(HostMessage.Buzz(BuzzKind.Win) in heard[1 - winner], "only the winner hears the win buzz")
            jobs.forEach { it.cancel() }
        } finally {
            clients.forEach { it.close() }
            server.stop(100, 500)
        }
    }

    @Test
    fun sameClientStillSteersAfterReconnecting() = runBlocking {
        val host = PartyHost(roundSeconds = 30f)
        val server = startHostServer(host, port = 0)
        val url = "ws://127.0.0.1:${server.engine.resolvedConnectors().first().port}/ws"
        // The phone page keeps one client for every retry, so the second run must still carry inputs.
        val phone = ControllerClient(HttpClient(CIO) { install(WebSockets) })
        val heard = CopyOnWriteArrayList<HostMessage>()
        suspend fun welcomes(n: Int) = withTimeout(5_000) {
            while (heard.count { it is HostMessage.Welcome } < n) delay(20)
            heard.filterIsInstance<HostMessage.Welcome>().last()
        }
        try {
            val first = launch { phone.run(url, "Ana", rejoinId = null) { heard += it } }
            val seat = welcomes(1)
            host.start()
            host.tick(PartySession.COUNTDOWN_SECONDS)
            phone.send(PlayerInput(steer = 1f))
            delay(200)
            first.cancel()
            delay(300)
            launch { phone.run(url, "Ana", rejoinId = seat.playerId) { heard += it } }
            assertEquals(seat, welcomes(2))
            phone.send(PlayerInput(steer = 1f))
            val leaned = withTimeoutOrNull(5_000) {
                while (host.snapshot()!!.boats.single().lean < 0.5f) { host.tick(0.05f); delay(20) }
                true
            }
            assertEquals(true, leaned, "steering after a reconnect reaches the host")
            coroutineContext.cancelChildren()
        } finally {
            phone.close()
            server.stop(100, 500)
        }
    }

    @Test
    fun phoneThatReconnectsKeepsItsBoat() = runBlocking {
        val host = PartyHost(roundSeconds = 30f)
        val server = startHostServer(host, port = 0)
        val url = "ws://127.0.0.1:${server.engine.resolvedConnectors().first().port}/ws"
        val phones = List(3) { ControllerClient(HttpClient(CIO) { install(WebSockets) }) }
        val heard = List(3) { CopyOnWriteArrayList<HostMessage>() }
        suspend fun welcomeOf(i: Int) = withTimeout(5_000) {
            while (heard[i].none { it is HostMessage.Welcome }) delay(20)
            heard[i].filterIsInstance<HostMessage.Welcome>().single()
        }
        try {
            val first = launch { phones[0].run(url, "Ana", rejoinId = null) { heard[0] += it } }
            val seat = welcomeOf(0)
            host.start()
            host.tick(PartySession.COUNTDOWN_SECONDS)
            assertEquals(Phase.Racing, host.phase)

            // The page reloads: the old socket closes, then the phone comes back with its seat id.
            first.cancel()
            phones[0].close()
            val second = launch { phones[1].run(url, "Ana", rejoinId = seat.playerId) { heard[1] += it } }
            assertEquals(seat, welcomeOf(1))

            // A Wi-Fi blip: the phone rejoins while the host still holds its old socket, which closes afterwards.
            launch { phones[2].run(url, "Ana", rejoinId = seat.playerId) { heard[2] += it } }
            assertEquals(seat, welcomeOf(2))
            second.cancel()
            phones[1].close()
            delay(500)
            heard[2].clear()
            withTimeout(5_000) { while (heard[2].none { it is HostMessage.Status }) delay(20) }
            assertEquals(listOf(seat.playerId), host.players.map { it.id }, "still one boat, not a new seat")
            host.input(seat.playerId, PlayerInput(steer = 1f))
            assertTrue(host.snapshot()!!.boats.single().player.id == seat.playerId)
        } finally {
            phones.forEach { it.close() }
            server.stop(100, 500)
        }
    }

    @Test
    fun rockHitBuzzesThatPhoneOnly() = runBlocking {
        val host = PartyHost(roundSeconds = 40f)
        val server = startHostServer(host, port = 0)
        val url = "ws://127.0.0.1:${server.engine.resolvedConnectors().first().port}/ws"
        val phones = List(2) { ControllerClient(HttpClient(CIO) { install(WebSockets) }) }
        val heard = List(2) { CopyOnWriteArrayList<HostMessage>() }
        try {
            phones.forEachIndexed { i, phone -> launch { phone.run(url, "Phone $i", rejoinId = null) { heard[i] += it } } }
            withTimeout(5_000) { while (heard.any { list -> list.none { it is HostMessage.Welcome } }) delay(20) }
            val ids = heard.map { list -> list.filterIsInstance<HostMessage.Welcome>().single().playerId }
            host.start()
            host.tick(PartySession.COUNTDOWN_SECONDS)

            // Phone 0 steers straight at the next rock until it hits one.
            val events = mutableListOf<RaceEvent>()
            while (events.none { it is RockHit && it.playerId == ids[0] }) {
                assertEquals(Phase.Racing, host.phase, "phone 0 never reached a rock")
                val race = host.snapshot()!!
                val boat = race.boats.first { it.player.id == ids[0] }
                val rock = race.rocks.filter { it.y > boat.y }.minByOrNull { it.y }
                host.input(ids[0], PlayerInput(steer = if (rock == null) 0f else ((rock.x - boat.x) * 20f).coerceIn(-1f, 1f)))
                host.tick(1f / 30f)
                events += host.drainEvents()
            }
            assertTrue(host.drainEvents().isEmpty(), "the screen gets each event once")

            fun buzzes(i: Int, kind: BuzzKind) = heard[i].count { it == HostMessage.Buzz(kind) }
            fun count(i: Int, rock: Boolean) = events.count { it.playerId == ids[i] && (if (rock) it is RockHit else it is LightCollected) }
            withTimeout(5_000) { while (buzzes(0, BuzzKind.Rock) < count(0, rock = true)) delay(20) }
            delay(300)
            for (i in 0..1) {
                assertEquals(count(i, rock = true), buzzes(i, BuzzKind.Rock), "phone $i rock buzzes")
                assertEquals(count(i, rock = false), buzzes(i, BuzzKind.Light), "phone $i light buzzes")
            }
        } finally {
            phones.forEach { it.close() }
            server.stop(100, 500)
        }
    }

    @Test
    fun wasmIsServedGzipped() = runBlocking {
        val server = startHostServer(PartyHost(), port = 0)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val dir = java.io.File(javaClass.getResource("/controller/index.html")!!.toURI()).parentFile
            val wasm = dir.listFiles()!!.filter { it.extension == "wasm" }.maxBy { it.length() }
            val connection = java.net.URI("http://127.0.0.1:$port/${wasm.name}").toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Accept-Encoding", "gzip")
            val body = connection.inputStream.use { it.readBytes() }
            assertEquals(200, connection.responseCode)
            assertEquals("gzip", connection.getHeaderField("Content-Encoding"))
            assertEquals("application/wasm", connection.contentType.substringBefore(';'))
            val raw = java.util.zip.GZIPInputStream(body.inputStream()).readBytes()
            assertEquals(wasm.length(), raw.size.toLong())
            println("controller wasm ${wasm.name}: raw ${wasm.length()} B, gzip ${body.size} B")
        } finally {
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
