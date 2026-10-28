package com.starfungames.paperboats

import kotlin.test.Test

/** Keeps a host running for a browser check: SERVE_SECONDS=60 ./gradlew :composeApp:desktopTest --tests '*ServeForCheck*' */
class ServeForCheck {
    @Test
    fun serve() {
        val seconds = System.getenv("SERVE_SECONDS")?.toLongOrNull() ?: return
        val server = startHostServer(PartyHost(), port = DEFAULT_PORT)
        Thread.sleep(seconds * 1000)
        server.stop(100, 500)
    }
}
