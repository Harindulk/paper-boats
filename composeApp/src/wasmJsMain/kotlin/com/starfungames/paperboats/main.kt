package com.starfungames.paperboats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.starfungames.paperboats.ui.ControllerPad

// The phone's browser controller. The WebSocket link to the host comes in the next milestone.
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ControllerPad(colour = 0, status = "Waiting for the host", onInput = {}, modifier = Modifier.fillMaxSize())
    }
}
