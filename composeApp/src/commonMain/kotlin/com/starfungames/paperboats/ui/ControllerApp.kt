package com.starfungames.paperboats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.party.ControllerClient
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.BuzzKind
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * The whole phone experience: pick a name, join the host at [hostUrl], then steer and paddle.
 * Platform bits come in as lambdas so this stays common: [buzz] vibrates, [saveSeat] remembers the
 * name and boat so a reload ([savedName], [savedSeatId]) rejoins the same boat.
 */
@Composable
fun ControllerApp(
    hostUrl: String,
    buzz: (BuzzKind) -> Unit = {},
    savedName: String = "",
    saveSeat: (name: String, id: Int) -> Unit = { _, _ -> },
    savedSeatId: Int? = null,
    client: ControllerClient = remember { ControllerClient() },
) {
    var name by remember { mutableStateOf(savedName) }
    // Each join (the first, or a Rejoin tap) restarts the link; a reload with a saved boat joins straight away.
    var joins by remember { mutableStateOf(if (savedSeatId != null && savedName.isNotBlank()) 1 else 0) }
    var seat by remember { mutableStateOf(savedSeatId) }
    var colour by remember { mutableStateOf(0) }
    var welcomed by remember { mutableStateOf(false) }
    var lost by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<HostMessage.Status?>(null) }
    var lastBuzz by remember { mutableStateOf<BuzzEvent?>(null) }
    var sawCountdown by remember { mutableStateOf(false) }
    var showGo by remember { mutableStateOf(false) }

    if (joins == 0) {
        NameEntry(name, onName = { name = it.take(PartySession.MAX_NAME) }, onJoin = { joins++ })
        return
    }
    LaunchedEffect(joins) {
        lost = false
        stayConnected {
            welcomed = false
            try {
                client.run(hostUrl, name, rejoinId = seat) { message ->
                    when (message) {
                        is HostMessage.Welcome -> {
                            welcomed = true; seat = message.playerId; colour = message.colour
                            saveSeat(name, message.playerId)
                        }
                        is HostMessage.Status -> status = message
                        is HostMessage.Buzz -> { buzz(message.kind); lastBuzz = BuzzEvent(message.kind) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A refused or dropped socket: stayConnected decides whether to try again.
            }
            welcomed.also { welcomed = false }
        }
        lost = true
    }
    val phase = status?.phase
    LaunchedEffect(phase) {
        if (phase == Phase.Countdown) sawCountdown = true
        if (phase == Phase.Racing && sawCountdown) {
            sawCountdown = false
            showGo = true; delay(700); showGo = false
        }
    }

    val current = status
    when {
        lost -> LostHost(onRejoin = { joins++ })
        current?.phase == Phase.Countdown -> Countdown(colour, current.countdown.toString())
        current?.phase == Phase.Results -> RaceResults(colour, current)
        else -> Box(Modifier.fillMaxSize()) {
            val race = current?.takeIf { welcomed && it.phase == Phase.Racing }
            ControllerPad(
                colour,
                status = when {
                    !welcomed -> "Finding the big screen…"
                    race != null -> "${lanterns(race.score)} · ${race.secondsLeft} s left"
                    else -> "Wait for the race to start"
                },
                onInput = client::send,
                modifier = Modifier.fillMaxSize(),
                place = if (race != null) ordinal(race.place) else name.trim().ifEmpty { "Paper Boats" },
                buzz = lastBuzz,
            )
            if (showGo) Countdown(colour, "Go")
        }
    }
}

/** One line for the top of the phone, from the host's status. */
fun describe(status: HostMessage.Status): String = when (status.phase) {
    Phase.Lobby -> "You're on the water. Wait for the race."
    Phase.Countdown -> "Race starts in ${status.countdown}"
    Phase.Racing -> "${ordinal(status.place)}, ${lanterns(status.score)}, ${status.secondsLeft} s"
    Phase.Results -> "Finished ${ordinal(status.place)} with ${lanterns(status.score)}"
}

private fun lanterns(n: Int) = if (n == 1) "1 lantern" else "$n lanterns"

fun ordinal(n: Int): String = when {
    n <= 0 -> "-"
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

/** "3", "2", "1", "Go": one huge number on the player's colour, popping in each time it changes. */
@Composable
internal fun Countdown(colour: Int, text: String) {
    val pop = rememberPop(text)
    Box(Modifier.fillMaxSize().background(Palette.player(colour)), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            style = Huge.copy(color = Palette.night, fontSize = 220.sp),
        )
    }
}

@Composable
internal fun RaceResults(colour: Int, status: HostMessage.Status) {
    val pop = rememberPop(status.place)
    Column(
        Modifier.fillMaxSize().background(Palette.player(colour)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        BasicText("You finished", style = Body.copy(color = Palette.night))
        BasicText(
            ordinal(status.place),
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0f, 0.5f) },
            style = Huge.copy(color = Palette.night),
        )
        BasicText("with ${lanterns(status.score)}", style = Body.copy(color = Palette.night))
        status.award?.let { award ->
            // The host sends "Title. Line."; the title gets the big type.
            BasicText(award.substringBefore(". "), Modifier.padding(top = 32.dp), style = Huge.copy(color = Palette.night, fontSize = 48.sp, letterSpacing = 0.sp))
            BasicText(award.substringAfter(". ", ""), style = Body.copy(color = Palette.night))
        }
        BasicText("Wait for the next race", Modifier.padding(top = 48.dp), style = Body.copy(color = Palette.night.copy(alpha = 0.7f)))
    }
}

@Composable
internal fun LostHost(onRejoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.night).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        BasicText("Lost the host", style = Huge.copy(color = Palette.ink, fontSize = 64.sp, letterSpacing = (-1).sp))
        BasicText("Check you're on the same Wi-Fi as the big screen.", style = Body.copy(color = Palette.inkSoft, fontWeight = FontWeight.Normal))
        BigButton("Rejoin", onRejoin)
    }
}

@Composable
internal fun NameEntry(name: String, onName: (String) -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.night).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        BasicText("Paper Boats", style = Huge.copy(color = Palette.ink, fontSize = 64.sp, letterSpacing = (-1).sp))
        BasicText("Your name on the big screen", style = Body.copy(color = Palette.inkSoft, fontWeight = FontWeight.Normal))
        BasicTextField(
            name,
            onName,
            singleLine = true,
            textStyle = Body.copy(color = Palette.ink, fontSize = 28.sp),
            cursorBrush = SolidColor(Palette.light),
            modifier = Modifier.fillMaxWidth().background(Palette.water, RoundedCornerShape(12.dp)).padding(18.dp),
        )
        BigButton("Grab a boat", onJoin)
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Palette.light, RoundedCornerShape(50)).clickable(onClick = onClick).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Body.copy(color = Palette.night)) }
}
