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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.party.ControllerClient
import com.starfungames.paperboats.party.PartySession
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase

/** The whole phone experience: pick a name, join the host at [hostUrl], then steer and paddle. */
@Composable
fun ControllerApp(hostUrl: String, client: ControllerClient = remember { ControllerClient() }) {
    var name by remember { mutableStateOf("") }
    var joined by remember { mutableStateOf(false) }
    var colour by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Joining…") }

    if (!joined) {
        NameEntry(name, onName = { name = it.take(PartySession.MAX_NAME) }, onJoin = { joined = true })
        return
    }
    LaunchedEffect(hostUrl) {
        val result = runCatching {
            client.run(hostUrl, name) { message ->
                when (message) {
                    is HostMessage.Welcome -> { colour = message.colour; status = "You're on the water. Wait for the race." }
                    is HostMessage.Status -> status = describe(message)
                    is HostMessage.Buzz -> Unit
                }
            }
        }
        status = result.exceptionOrNull()?.let { "Lost the host. Reload to join again." } ?: "The host closed the party."
    }
    ControllerPad(colour, status, onInput = client::send, modifier = Modifier.fillMaxSize())
}

/** One line for the top of the phone, from the host's status. */
fun describe(status: HostMessage.Status): String = when (status.phase) {
    Phase.Lobby -> "You're on the water. Wait for the race."
    Phase.Countdown -> "Race starts in ${status.countdown}"
    Phase.Racing -> "${ordinal(status.place)}, ${lights(status.score)}, ${status.secondsLeft} s"
    Phase.Results -> "Finished ${ordinal(status.place)} with ${lights(status.score)}"
}

private fun lights(n: Int) = if (n == 1) "1 light" else "$n lights"

fun ordinal(n: Int): String = when {
    n <= 0 -> "-"
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

@Composable
private fun NameEntry(name: String, onName: (String) -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.night).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        BasicText("Paper Boats", style = TextStyle(color = Palette.ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold))
        BasicText("Your name on the big screen", style = TextStyle(color = Palette.inkSoft, fontSize = 18.sp))
        BasicTextField(
            name,
            onName,
            singleLine = true,
            textStyle = TextStyle(color = Palette.ink, fontSize = 24.sp),
            cursorBrush = SolidColor(Palette.light),
            modifier = Modifier.fillMaxWidth().background(Palette.water, RoundedCornerShape(12.dp)).padding(16.dp),
        )
        Box(
            Modifier.fillMaxWidth().background(Palette.light, RoundedCornerShape(28.dp)).clickable(onClick = onJoin).padding(18.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("Grab a boat", style = TextStyle(color = Palette.night, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)) }
    }
}
