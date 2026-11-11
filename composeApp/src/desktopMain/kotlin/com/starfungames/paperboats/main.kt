package com.starfungames.paperboats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.starfungames.paperboats.game.Award
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.CountdownScreen
import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.NO_WIFI
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RaceScreen
import com.starfungames.paperboats.ui.Results
import com.starfungames.paperboats.ui.RiverFx

fun main() = application {
    val host = remember { PartyHost() }
    // Ktor binds in the background, so probe the port first to catch a second copy of the host.
    val server = remember { runCatching { java.net.ServerSocket(DEFAULT_PORT).close(); startHostServer(host) }.getOrNull() }
    val joinUrl = remember { lanAddress()?.let { "http://$it:$DEFAULT_PORT/" } }
    DisposableEffect(Unit) { onDispose { server?.stop(500, 1000) } }

    Window(onCloseRequest = ::exitApplication, title = "Paper Boats", state = rememberWindowState(width = 1280.dp, height = 800.dp)) {
        PartyScreen(host, if (server != null) joinUrl else null, if (server == null) "Port $DEFAULT_PORT is busy. Close the other Paper Boats window and start again." else NO_WIFI)
    }
}

/** Keyboard seats for playing without phones: A / D / W, and the arrow keys. */
private val keyboardKeys = listOf(Triple(Key.A, Key.D, Key.W), Triple(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp))

@Composable
fun PartyScreen(host: PartyHost, joinUrl: String?, offline: String = NO_WIFI) {
    var phase by remember { mutableStateOf(host.phase) }
    var snapshot by remember { mutableStateOf<RaceSnapshot?>(null) }
    var players by remember { mutableStateOf(emptyList<Player>()) }
    var countdown by remember { mutableStateOf(0f) }
    var awards by remember { mutableStateOf(emptyList<Award>()) }
    var time by remember { mutableStateOf(0f) }
    val fx = remember { RiverFx() }
    val held = remember { mutableSetOf<Key>() }
    val keyboardPlayers = remember { mutableListOf<Int>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            keyboardPlayers.forEachIndexed { seat, id ->
                val (left, right, paddle) = keyboardKeys[seat]
                host.input(id, PlayerInput((if (right in held) 1f else 0f) - (if (left in held) 1f else 0f), paddle in held))
            }
            host.tick(dt)
            val next = host.phase
            val live = host.snapshot()
            if (next == Phase.Countdown && phase != Phase.Countdown) fx.newRace()
            fx.onEvents(host.drainEvents()) // drained every frame so the host's buffer never grows
            if (next == Phase.Results && phase != Phase.Results) {
                live?.standings?.firstOrNull()?.let(fx::finish)
                awards = host.awards()
            }
            fx.noteArrivals(host.players)
            fx.step(dt, live.takeIf { next == Phase.Racing })
            phase = next
            snapshot = live ?: snapshot // the last race stays drawable while the next countdown fades in
            players = host.players
            countdown = host.countdownLeft
            time = fx.time
        }
    }

    Box(
        Modifier.fillMaxSize().background(Palette.night).focusRequester(focus).focusable().onKeyEvent { event ->
            when (event.type) {
                KeyEventType.KeyDown -> {
                    held += event.key
                    when {
                        event.key == Key.Spacebar && host.canStart() -> host.start()
                        event.key == Key.K && host.phase == Phase.Lobby && keyboardPlayers.size < keyboardKeys.size ->
                            host.join(if (keyboardPlayers.isEmpty()) "Keys A D W" else "Arrow keys")?.let { keyboardPlayers += it.id }
                    }
                }
                KeyEventType.KeyUp -> held -= event.key
            }
            true
        },
    ) {
        Crossfade(phase, animationSpec = tween(400)) { shown ->
            val current = snapshot
            when {
                shown == Phase.Countdown -> CountdownScreen(players, countdown, fx, time, host.raceNumber, host.river)
                shown == Phase.Racing && current != null -> RaceScreen(current, fx, time)
                shown == Phase.Results && current != null -> Results(current, fx, time, awards)
                else -> Lobby(joinUrl, players, fx, time, offline) { url, modifier -> QrCode(url, modifier) }
            }
        }
    }
}
