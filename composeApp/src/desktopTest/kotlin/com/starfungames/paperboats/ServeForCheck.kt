package com.starfungames.paperboats

import kotlin.test.Test

/**
 * Keeps a host running for a browser check: SERVE_SECONDS=60 ./gradlew :composeApp:desktopTest --tests '*ServeForCheck*'
 * With START_AT=2 it starts a race as soon as two phones are in, like pressing Space; ROUND_SECONDS shortens the race.
 */
class ServeForCheck {
    @Test
    fun serve() {
        val seconds = System.getenv("SERVE_SECONDS")?.toLongOrNull() ?: return
        val startAt = System.getenv("START_AT")?.toIntOrNull()
        val host = PartyHost(roundSeconds = System.getenv("ROUND_SECONDS")?.toFloatOrNull() ?: 60f)
        val server = startHostServer(host, port = DEFAULT_PORT)
        var started = false
        val end = System.currentTimeMillis() + seconds * 1000
        while (System.currentTimeMillis() < end) {
            if (!started && startAt != null && host.players.size >= startAt && host.canStart()) { host.start(); started = true }
            host.tick(1f / 60f)
            Thread.sleep(16)
        }
        server.stop(100, 500)
    }
}
