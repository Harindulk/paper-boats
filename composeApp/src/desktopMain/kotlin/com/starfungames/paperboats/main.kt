package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RiverView
import com.starfungames.paperboats.ui.Scoreboard

// shortcut: two players share the keyboard until phones join over the network (next milestone).
private val keyboardSeats = mapOf(
    1 to Triple(Key.A, Key.D, Key.W),
    2 to Triple(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp),
)

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Paper Boats", state = rememberWindowState(width = 1280.dp, height = 800.dp)) {
        val players = remember { listOf(Player(1, "Left hand", 0), Player(2, "Right hand", 1)) }
        val race = remember { Race(players, seed = 2026) }
        var snapshot by remember { mutableStateOf(race.snapshot()) }
        val held = remember { mutableSetOf<Key>() }
        val focus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focus.requestFocus()
            var last = withFrameNanos { it }
            while (!race.finished) {
                val now = withFrameNanos { it }
                val inputs = keyboardSeats.mapValues { (_, keys) ->
                    val (left, right, paddle) = keys
                    PlayerInput(steer = (if (right in held) 1f else 0f) - (if (left in held) 1f else 0f), paddle = paddle in held)
                }
                race.step((now - last) / 1e9f, inputs)
                snapshot = race.snapshot()
                last = now
            }
        }

        Row(
            Modifier.fillMaxSize().background(Palette.night).focusRequester(focus).focusable().onKeyEvent {
                when (it.type) {
                    KeyEventType.KeyDown -> held += it.key
                    KeyEventType.KeyUp -> held -= it.key
                }
                false
            },
        ) {
            RiverView(snapshot, Modifier.weight(1f).fillMaxHeight())
            Scoreboard(snapshot, Modifier.width(280.dp).fillMaxHeight())
        }
    }
}
