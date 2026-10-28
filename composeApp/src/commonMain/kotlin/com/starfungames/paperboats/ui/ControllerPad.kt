package com.starfungames.paperboats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.PlayerInput

/**
 * The phone controller, shared by the web and Android apps: the left half steers (slide a thumb
 * left or right), the right half paddles (hold for a burst).
 */
@Composable
fun ControllerPad(colour: Int, status: String, onInput: (PlayerInput) -> Unit, modifier: Modifier = Modifier) {
    var steer by remember { mutableFloatStateOf(0f) }
    var paddle by remember { mutableStateOf(false) }
    fun send() = onInput(PlayerInput(steer, paddle))

    Column(modifier.background(Palette.night)) {
        BasicText(status, Modifier.fillMaxWidth().padding(20.dp), style = TextStyle(color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
        Row(Modifier.weight(1f)) {
            Box(
                Modifier.weight(1f).fillMaxSize().background(Palette.water).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val origin = down.position.x
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            steer = ((change.position.x - origin) / (size.width * 0.3f)).coerceIn(-1f, 1f)
                            send()
                        } while (change.pressed)
                        steer = 0f
                        send()
                    }
                },
                contentAlignment = Alignment.Center,
            ) { BasicText("Slide to steer", style = TextStyle(color = Palette.inkSoft, fontSize = 18.sp)) }
            Box(
                Modifier.weight(1f).fillMaxSize().background(Palette.player(colour)).pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        paddle = true
                        send()
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        paddle = false
                        send()
                    }
                },
                contentAlignment = Alignment.Center,
            ) { BasicText("Paddle", style = TextStyle(color = Palette.night, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)) }
        }
    }
}
