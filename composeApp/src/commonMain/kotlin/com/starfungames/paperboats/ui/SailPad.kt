package com.starfungames.paperboats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.party.PartySession
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The sail's width over its height, the same on the phone and on every boat on the big screen. */
internal const val SAIL_ASPECT = 1.8f / 1.45f

/**
 * Rounds a stroke's x, y pairs to three decimals and, when it has more than [maxPoints] points,
 * keeps [maxPoints] of them evenly spaced from the first to the last, so a long scribble fits the host's cap.
 */
fun thinStroke(points: List<Float>, maxPoints: Int = PartySession.MAX_SAIL_POINTS): List<Float> {
    val n = points.size / 2
    val keep = if (n <= maxPoints) (0 until n).toList() else List(maxPoints) { it * (n - 1) / (maxPoints - 1) }
    return keep.flatMap { i -> listOf(points[2 * i], points[2 * i + 1]).map { (it * 1000).roundToInt() / 1000f } }
}

/** The largest sail that fits [area], centred. */
private fun sailBox(area: Size): Rect {
    val width = minOf(area.width, area.height * SAIL_ASPECT)
    val height = width / SAIL_ASPECT
    return Rect(Offset((area.width - width) / 2, (area.height - height) / 2), Size(width, height))
}

/**
 * "Draw your sail": a big paper sail on the player's colour to draw on with a finger or a mouse.
 * [onDone] gets the strokes in 0..1 sail space, already thinned for the wire; an empty sail is a skip.
 */
@Composable
fun SailPad(colour: Int, initial: List<List<Float>>, onDone: (List<List<Float>>) -> Unit) {
    var strokes by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().background(Palette.player(colour)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText("Draw your sail", style = Huge.copy(color = Palette.night, fontSize = 52.sp, letterSpacing = (-1).sp))
        BasicText("It flies on the big screen.", style = Body.copy(color = Palette.night, fontWeight = FontWeight.Normal))
        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val box = sailBox(Size(size.width.toFloat(), size.height.toFloat()))
                    if (strokes.size >= PartySession.MAX_SAIL_STROKES) return@awaitEachGesture
                    fun sailPoint(at: Offset) = listOf((at.x - box.left) / box.width, (at.y - box.top) / box.height)
                    var last = down.position
                    strokes = strokes + listOf(sailPoint(last))
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        // Skip tiny moves so a stroke stays short; the host caps it anyway.
                        if (hypot(change.position.x - last.x, change.position.y - last.y) < box.width * 0.012f) continue
                        last = change.position
                        strokes = strokes.dropLast(1) + listOf(strokes.last() + sailPoint(last))
                    }
                }
            },
        ) {
            drawSail(sailBox(size), strokes)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (strokes.isNotEmpty()) {
                BasicText(
                    "Clear",
                    Modifier.clickable { strokes = emptyList() }.padding(horizontal = 20.dp, vertical = 20.dp),
                    style = Body.copy(color = Palette.night),
                )
            }
            Box(
                Modifier.weight(1f).background(Palette.night, RoundedCornerShape(50))
                    .clickable { onDone(strokes.map { thinStroke(it) }) }.padding(20.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(if (strokes.isEmpty()) "Skip" else "Set sail", style = Body.copy(color = Palette.paper)) }
        }
    }
}
