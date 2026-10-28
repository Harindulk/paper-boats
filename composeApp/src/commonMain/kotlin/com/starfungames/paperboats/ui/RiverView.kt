package com.starfungames.paperboats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.starfungames.paperboats.game.BoatView
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceSnapshot

/** How much river is on screen, in race units, and how far behind the leader the camera sits. */
private const val VIEW_SPAN = 1.4f
private const val VIEW_BEHIND = 0.35f

/** The big screen: the river, its lights and rocks, and every boat. Shared by all platforms. */
@Composable
fun RiverView(snapshot: RaceSnapshot, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val riverWidth = minOf(size.width, size.height * 0.8f)
        val left = (size.width - riverWidth) / 2
        val bottom = (snapshot.boats.maxOfOrNull { it.y } ?: 0f) - VIEW_BEHIND
        fun toScreen(x: Float, y: Float) = Offset(left + x * riverWidth, size.height - (y - bottom) / VIEW_SPAN * size.height)
        val unit = riverWidth

        drawRect(Palette.bank)
        drawRect(Palette.water, Offset(left + Race.BANK * unit, 0f), Size((1 - 2 * Race.BANK) * unit, size.height))
        drawRipples(left, unit, bottom)

        snapshot.rocks.forEach { drawCircle(Palette.rock, it.radius * unit, toScreen(it.x, it.y)) }
        snapshot.lights.forEach {
            val at = toScreen(it.x, it.y)
            drawCircle(Palette.light.copy(alpha = 0.18f), Race.LIGHT_RADIUS * unit * 2.6f, at)
            drawCircle(Palette.light, Race.LIGHT_RADIUS * unit, at)
        }
        snapshot.boats.forEach { drawBoat(it, toScreen(it.x, it.y), Race.BOAT_RADIUS * unit) }
    }
}

private fun DrawScope.drawRipples(left: Float, unit: Float, bottom: Float) {
    val spacing = 0.12f
    var y = bottom - bottom.mod(spacing)
    while (y < bottom + VIEW_SPAN) {
        val sy = size.height - (y - bottom) / VIEW_SPAN * size.height
        val shift = ((y * 37f).mod(1f)) * 0.5f + 0.2f
        drawRect(Palette.waterLight, Offset(left + shift * unit, sy), Size(unit * 0.12f, 2f))
        y += spacing
    }
}

private fun DrawScope.drawBoat(boat: BoatView, at: Offset, r: Float) {
    val hull = Path().apply {
        moveTo(at.x - r * 1.1f, at.y)
        lineTo(at.x + r * 1.1f, at.y)
        lineTo(at.x + r * 0.7f, at.y + r * 0.7f)
        lineTo(at.x - r * 0.7f, at.y + r * 0.7f)
        close()
    }
    val sail = Path().apply {
        moveTo(at.x, at.y - r * 1.5f)
        lineTo(at.x + r * 0.9f, at.y - r * 0.05f)
        lineTo(at.x - r * 0.9f, at.y - r * 0.05f)
        close()
    }
    rotate(if (boat.stunned) 18f else 0f, at) {
        if (boat.boosting) drawCircle(Palette.paper.copy(alpha = 0.25f), r * 1.9f, at.copy(y = at.y + r * 0.9f))
        drawPath(hull, Palette.player(boat.player.colour))
        drawPath(sail, Palette.paper)
    }
}
