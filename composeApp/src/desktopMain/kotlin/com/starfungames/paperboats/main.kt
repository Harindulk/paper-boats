package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RiverView
import com.starfungames.paperboats.ui.Scoreboard

fun main() = application {
    val host = remember { PartyHost() }
    val server = remember { runCatching { startHostServer(host) }.getOrNull() }
    val joinUrl = remember { lanAddress()?.let { "http://$it:$DEFAULT_PORT/" } }
    DisposableEffect(Unit) { onDispose { server?.stop(500, 1000) } }

    Window(onCloseRequest = ::exitApplication, title = "Paper Boats", state = rememberWindowState(width = 1280.dp, height = 800.dp)) {
        PartyScreen(host, if (server != null) joinUrl else null)
    }
}

/** Keyboard seats for playing without phones: A / D / W, and the arrow keys. */
private val keyboardKeys = listOf(Triple(Key.A, Key.D, Key.W), Triple(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp))

@Composable
fun PartyScreen(host: PartyHost, joinUrl: String?) {
    var phase by remember { mutableStateOf(host.phase) }
    var snapshot by remember { mutableStateOf<RaceSnapshot?>(null) }
    var players by remember { mutableStateOf(emptyList<Player>()) }
    val held = remember { mutableSetOf<Key>() }
    val keyboardPlayers = remember { mutableListOf<Int>() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            keyboardPlayers.forEachIndexed { seat, id ->
                val (left, right, paddle) = keyboardKeys[seat]
                host.input(id, PlayerInput((if (right in held) 1f else 0f) - (if (left in held) 1f else 0f), paddle in held))
            }
            host.tick((now - last) / 1e9f)
            phase = host.phase
            snapshot = host.snapshot()
            players = host.players
            last = now
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
        val current = snapshot
        when {
            phase == Phase.Racing && current != null -> Row(Modifier.fillMaxSize()) {
                RiverView(current, Modifier.weight(1f).fillMaxHeight())
                Scoreboard(current, Modifier.width(280.dp).fillMaxHeight())
            }
            phase == Phase.Results && current != null -> Results(current)
            else -> Lobby(joinUrl, players)
        }
    }
}

private val title = TextStyle(color = Palette.ink, fontSize = 64.sp, fontWeight = FontWeight.SemiBold)
private val body = TextStyle(color = Palette.ink, fontSize = 22.sp)
private val soft = TextStyle(color = Palette.inkSoft, fontSize = 20.sp)

@Composable
internal fun Lobby(joinUrl: String?, players: List<Player>) {
    Row(Modifier.fillMaxSize().padding(64.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BasicText("Paper Boats", style = title)
            BasicText("A river race for up to eight phones. Scan to grab a boat, then press Space to race.", style = body)
            if (players.isEmpty()) BasicText("Nobody on the water yet.", style = soft)
            players.forEach { PlayerRow(it) }
            BasicText("No phones? Press K to add a keyboard player.", style = soft)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (joinUrl != null) {
                QrCode(joinUrl, Modifier.size(300.dp))
                BasicText(joinUrl, style = body)
                BasicText("Phones must be on the same Wi-Fi.", style = soft)
            } else {
                BasicText("Connect this computer to Wi-Fi so phones can join.", style = body)
            }
        }
    }
}

@Composable
private fun PlayerRow(player: Player) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(18.dp).background(Palette.player(player.colour), CircleShape))
        BasicText(player.name, style = body)
    }
}

@Composable
internal fun Results(snapshot: RaceSnapshot) {
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val winner = snapshot.standings.first()
        BasicText("${winner.player.name} wins with ${winner.score} lights", style = title)
        snapshot.standings.forEachIndexed { place, boat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BasicText("${place + 1}", style = soft)
                Box(Modifier.size(18.dp).background(Palette.player(boat.player.colour), CircleShape))
                BasicText("${boat.player.name}  ${boat.score}", style = body)
            }
        }
        BasicText("Press Space to race again.", style = soft)
    }
}
