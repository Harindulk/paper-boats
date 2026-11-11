package com.starfungames.paperboats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.ui.fx.Ease
import kotlin.math.ceil

private const val HURRY_SECONDS = 10f

/** The side panel: seconds left (growing and amber in the last ten) and the standings. */
@Composable
fun Scoreboard(snapshot: RaceSnapshot, modifier: Modifier = Modifier) {
    val left = snapshot.timeLeft
    val hurry = left in 0.001f..HURRY_SECONDS
    val whole = ceil(left).toInt()
    // Each second in the last ten lands with a small pop that settles before the next.
    val pop = if (hurry) 1f + 0.2f * (1f - Ease.outCubic((whole - left) / 0.35f)) else 1f
    Column(modifier.background(Palette.night).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.height(110.dp), contentAlignment = Alignment.BottomStart) {
            BasicText(
                if (left > 0f) "$whole" else "Done",
                Modifier.graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    transformOrigin = TransformOrigin(0f, 1f)
                },
                style = TextStyle(
                    color = if (hurry) Palette.light else Palette.ink,
                    fontSize = if (hurry) (56f + (HURRY_SECONDS - left) * 3.6f).sp else 56.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        snapshot.standings.forEachIndexed { place, boat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("${place + 1}", Modifier.width(22.dp), style = TextStyle(color = Palette.inkSoft, fontSize = 22.sp))
                Box(Modifier.size(16.dp).background(Palette.player(boat.player.colour), CircleShape))
                BasicText(boat.player.name, Modifier.weight(1f), style = TextStyle(color = Palette.ink, fontSize = 22.sp), maxLines = 1)
                BasicText("${boat.score}", style = TextStyle(color = Palette.light, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
