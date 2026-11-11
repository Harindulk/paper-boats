package com.starfungames.paperboats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.BoatView
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.ui.fx.Ease
import com.starfungames.paperboats.ui.fx.Particles.Kind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How much river is on screen, in race units, and how far behind the leader the camera sits. */
private const val VIEW_SPAN = 1.4f
private const val VIEW_BEHIND = 0.35f
private const val GO_SECONDS = 0.8f
/** Boats are drawn larger than their hit circle so they read on a TV across the room. */
private const val BOAT_DRAW_SCALE = 1.7f

/** The big screen: the river, its lights and rocks, every boat, and the juice in [fx]. Shared by all platforms. */
@Composable
fun RiverView(snapshot: RaceSnapshot, fx: RiverFx, modifier: Modifier = Modifier, time: Float = fx.time) {
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val unit = minOf(size.width, size.height * 0.8f)
        val (shakeX, shakeY) = fx.camera.shakeOffset(time)
        val left = (size.width - unit) / 2 + shakeX * unit
        val bottom = fx.camera.y - VIEW_BEHIND + shakeY
        fun toScreen(x: Float, y: Float) = Offset(left + x * unit, size.height - (y - bottom) / VIEW_SPAN * size.height)

        drawRect(Palette.bank)
        drawRect(Palette.water, Offset(left + Race.BANK * unit, 0f), Size((1 - 2 * Race.BANK) * unit, size.height))
        drawRipples(left, unit, bottom)

        fx.particles.live.filter { it.kind == Kind.Wake }.forEach {
            val fade = 1f - it.life / it.maxLife
            val w = it.size * unit * (1.4f + 1.4f * (1f - fade))
            drawOval(Palette.paper.copy(alpha = 0.26f * fade), toScreen(it.x, it.y) - Offset(w, w * 0.55f), Size(w * 2, w * 1.1f))
        }
        snapshot.rocks.forEach { drawCircle(Palette.rock, it.radius * unit, toScreen(it.x, it.y)) }
        snapshot.lights.forEach {
            val at = toScreen(it.x, it.y + sin(time * 2.6f + it.id * 1.9f) * 0.004f)
            val r = Race.LIGHT_RADIUS * unit * (1f + 0.06f * sin(time * 3.1f + it.id))
            drawCircle(Palette.light.copy(alpha = 0.16f), r * 2.6f, at)
            drawCircle(Palette.light, r, at)
        }
        fx.particles.live.filter { it.kind == Kind.Splash }.forEach {
            drawCircle(Palette.paper.copy(alpha = 0.85f * (1f - it.life / it.maxLife)), it.size * unit, toScreen(it.x, it.y))
        }
        snapshot.boats.forEach { boat ->
            val id = boat.player.id
            val bob = sin(time * 2f * PI.toFloat() * 1.3f + id * 2.1f) * 0.03f * Race.BOAT_RADIUS
            val roll = sin(time * 5.2f + id * 1.3f) * 4f
            val wobble = if (boat.stunned) sin(time * 22f) * 14f else 0f
            val squash = fx.squash(id)
            drawBoat(
                toScreen(boat.x, boat.y + bob), Race.BOAT_RADIUS * unit * BOAT_DRAW_SCALE, Palette.player(boat.player.colour),
                degrees = roll + boat.lean * 14f + wobble, scaleX = 1f + 0.22f * squash, scaleY = 1f - 0.18f * squash,
            )
        }
        fx.particles.live.filter { it.kind == Kind.Spark }.forEach {
            drawCircle(fxColour(it.colour).copy(alpha = 1f - it.life / it.maxLife), it.size * unit * 1.8f, toScreen(it.x, it.y))
        }
        fx.pops.forEach {
            val t = (time - it.born) / RiverFx.POP_SECONDS
            val alpha = 1f - ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
            drawWord(text, "+1", toScreen(it.x, it.y + 0.045f + 0.07f * Ease.outCubic(t)), 34.sp, Palette.light, 1f, alpha)
        }
        if (snapshot.elapsed > 0f && snapshot.elapsed < GO_SECONDS) {
            val t = snapshot.elapsed / GO_SECONDS
            drawCallout(text, "Go", Ease.outBack(t / 0.45f), 1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f), Palette.light)
        }
    }
}

/** A particle colour code from [RiverFx] as a colour. */
internal fun fxColour(code: Int): Color = when (code) {
    RiverFx.LIGHT_COLOUR -> Palette.light
    RiverFx.PAPER_COLOUR -> Palette.paper
    else -> Palette.player(code)
}

/** One huge word in the middle of the canvas, popped by [scale]: the countdown numbers and "Go". */
internal fun DrawScope.drawCallout(text: TextMeasurer, word: String, scale: Float, alpha: Float, colour: Color) =
    drawWord(text, word, center, 200.sp, colour, scale, alpha)

internal fun DrawScope.drawWord(text: TextMeasurer, word: String, at: Offset, fontSize: TextUnit, colour: Color, scale: Float, alpha: Float) {
    if (alpha <= 0f || scale <= 0f) return
    val layout = text.measure(word, TextStyle(color = colour, fontSize = fontSize, fontWeight = FontWeight.Bold))
    withTransform({ scale(scale, scale, at) }) {
        drawText(layout, alpha = alpha.coerceIn(0f, 1f), topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
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

/** A folded paper boat centred on [at] with hull half-width about [r], tilted by [degrees] and squashed by the scales. */
internal fun DrawScope.drawBoat(at: Offset, r: Float, colour: Color, degrees: Float = 0f, scaleX: Float = 1f, scaleY: Float = 1f) {
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
    val pivot = at.copy(y = at.y + r * 0.7f)
    withTransform({ rotate(degrees, at); scale(scaleX, scaleY, pivot) }) {
        drawPath(hull, colour)
        drawPath(sail, Palette.paper)
    }
}

/** Lantern and confetti particles in results-screen units: x across 0..1, y up from the bottom 0..1. */
internal fun DrawScope.drawSkyParticles(fx: RiverFx) {
    fx.particles.live.forEach {
        val at = Offset(it.x * size.width, size.height * (1f - it.y))
        val s = it.size * size.height * (if (it.kind == Kind.Lantern) 1.5f else 1f)
        val fade = ((1f - it.life / it.maxLife) / 0.3f).coerceIn(0f, 1f)
        when (it.kind) {
            Kind.Lantern -> {
                val colour = Palette.player(it.colour)
                drawCircle(colour.copy(alpha = 0.14f * fade), s * 1.8f, at)
                drawRect(colour.copy(alpha = fade), at - Offset(s * 0.7f, s * 0.9f), Size(s * 1.4f, s * 1.8f))
                drawCircle(Palette.light.copy(alpha = fade), s * 0.36f, at)
            }
            Kind.Confetti -> {
                val w = s * 1.6f * abs(cos(it.life * 7f + it.x * 30f)) + s * 0.2f
                withTransform({ rotate(it.life * 120f + it.x * 900f, at) }) {
                    drawRect(fxColour(it.colour).copy(alpha = fade), at - Offset(w / 2, s * 0.5f), Size(w, s))
                }
            }
            else -> Unit
        }
    }
}
