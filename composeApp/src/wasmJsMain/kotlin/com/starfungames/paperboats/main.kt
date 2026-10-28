package com.starfungames.paperboats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.starfungames.paperboats.ui.ControllerApp

/** The page is served by the desktop host, so the game link lives on the same address. */
private fun pageHost(): String = js("window.location.host")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ControllerApp(hostUrl = "ws://${pageHost()}/ws")
    }
}
