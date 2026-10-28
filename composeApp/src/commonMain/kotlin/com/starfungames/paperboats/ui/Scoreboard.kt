package com.starfungames.paperboats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.RaceSnapshot
import kotlin.math.ceil

@Composable
fun Scoreboard(snapshot: RaceSnapshot, modifier: Modifier = Modifier) {
    Column(modifier.background(Palette.night).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            if (snapshot.timeLeft > 0f) "${ceil(snapshot.timeLeft).toInt()} s" else "Finished",
            style = TextStyle(color = Palette.ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
        )
        snapshot.standings.forEachIndexed { place, boat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("${place + 1}", style = TextStyle(color = Palette.inkSoft, fontSize = 18.sp))
                Box(Modifier.size(14.dp).background(Palette.player(boat.player.colour), CircleShape))
                BasicText(boat.player.name, style = TextStyle(color = Palette.ink, fontSize = 20.sp))
                BasicText("${boat.score}", style = TextStyle(color = Palette.light, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
