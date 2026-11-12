package com.starfungames.paperboats

import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.RiverFx
import java.io.File
import kotlin.test.Test

/**
 * Keeps a host running for a browser check: SERVE_SECONDS=60 ./gradlew :composeApp:desktopTest --tests '*ServeForCheck*'
 * With START_AT=2 it starts a race as soon as two phones are in, like pressing Space; ROUND_SECONDS shortens the race.
 * SAIL_SECONDS=20 holds that start until every phone has drawn a sail, or for 20 seconds.
 */
class ServeForCheck {
    @Test
    fun serve() {
        val seconds = System.getenv("SERVE_SECONDS")?.toLongOrNull() ?: return
        val startAt = System.getenv("START_AT")?.toIntOrNull()
        val host = PartyHost(roundSeconds = System.getenv("ROUND_SECONDS")?.toFloatOrNull() ?: 60f)
        val server = startHostServer(host, port = DEFAULT_PORT)
        var started = false
        var readyAt: Long? = null
        val sailWait = (System.getenv("SAIL_SECONDS")?.toLongOrNull() ?: 0L) * 1000
        val end = System.currentTimeMillis() + seconds * 1000
        while (System.currentTimeMillis() < end) {
            // Wait for every phone to draw its sail (or skip, which sends none) for up to SAIL_SECONDS, default 0.
            if (startAt != null && host.players.size < startAt) readyAt = null
            if (!started && startAt != null && host.players.size >= startAt && host.canStart()) {
                val since = readyAt ?: System.currentTimeMillis().also { readyAt = it }
                if (host.players.all { it.sail.isNotEmpty() } || System.currentTimeMillis() - since >= sailWait) {
                    tv("check-tv-start.png", host)
                    host.start()
                    started = true
                }
            }
            host.tick(1f / 60f)
            Thread.sleep(16)
        }
        tv("check-tv-end.png", host)
        server.stop(100, 500)
    }

    /** Prints each player's sail and renders the lobby with their boats, to show the drawings reached the host. */
    private fun tv(name: String, host: PartyHost) {
        host.players.forEach { println("check ${it.name}: ${it.sail.size} strokes, ${it.sail.sumOf { s -> s.size / 2 }} points") }
        val fx = RiverFx()
        File("build/renders", name).writeBytes(
            renderPng(1280, 800) { Lobby("http://check/", host.players, fx, fx.time) { _, _ -> } },
        )
    }
}
