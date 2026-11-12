package com.starfungames.paperboats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
private const val VIEW_SPAN = 1.1f
private const val VIEW_BEHIND = 0.36f

/** Pixels per race unit across the river: about 70% of a 16:9 screen's width, never wider than the space it has. */
internal fun riverUnit(width: Float, height: Float) = minOf(width, height * 1.1f)
private const val GO_SECONDS = 0.8f

/** Boats are drawn larger than their hit circle so they read on a TV across the room. */
private const val BOAT_DRAW_SCALE = 1.7f

/** Logs are drawn a little thicker than their hit band, like the boats. */
private const val LOG_DRAW_SCALE = 1.5f

/** The big screen: the river, its lights, rocks, logs and whirlpools, every boat, and the juice in [fx]. Shared by all platforms. */
@Composable
fun RiverView(snapshot: RaceSnapshot, fx: RiverFx, modifier: Modifier = Modifier, time: Float = fx.time) {
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val unit = riverUnit(size.width, size.height)
        val (shakeX, shakeY) = fx.camera.shakeOffset(time)
        val left = (size.width - unit) / 2 + shakeX * unit
        val bottom = fx.camera.y - VIEW_BEHIND + shakeY
        fun toScreen(x: Float, y: Float) = Offset(left + x * unit, size.height - (y - bottom) / VIEW_SPAN * size.height)

        drawRect(Palette.bank)
        drawRect(Palette.water, Offset(left + Race.BANK * unit, 0f), Size((1 - 2 * Race.BANK) * unit, size.height))
        drawRipples(left, unit, bottom)
        clipRect(left + Race.BANK * unit, 0f, left + (1 - Race.BANK) * unit, size.height) {
            snapshot.whirlpools.forEach { drawWhirlpool(toScreen(it.x, it.y), it.radius * unit, time) }
        }

        fx.particles.live.filter { it.kind == Kind.Wake }.forEach {
            val fade = 1f - it.life / it.maxLife
            val w = it.size * unit * (1.4f + 1.4f * (1f - fade))
            drawOval(Palette.paper.copy(alpha = 0.26f * fade), toScreen(it.x, it.y) - Offset(w, w * 0.55f), Size(w * 2, w * 1.1f))
        }
        // Rocks are darker than the water with a ring of foam, so they never read like a lantern's halo.
        snapshot.rocks.forEach {
            val at = toScreen(it.x, it.y)
            drawCircle(Palette.paper.copy(alpha = 0.4f), it.radius * unit * 1.12f, at, style = Stroke(width = it.radius * unit * 0.16f))
            drawCircle(Palette.rock, it.radius * unit, at)
        }
        snapshot.logs.forEach {
            val at = toScreen(it.x, it.y)
            val half = Size(it.halfWidth * unit, Race.LOG_HALF_THICKNESS * unit * LOG_DRAW_SCALE)
            val corner = CornerRadius(half.height)
            drawRoundRect(Palette.logEdge, at - Offset(half.width, half.height), half * 2f, corner)
            drawRoundRect(Palette.log, at - Offset(half.width, half.height), Size(half.width * 2f, half.height * 1.4f), corner)
        }
        // In the golden hour every lantern is worth 2, so they grow and take the richer amber.
        val lantern = if (snapshot.goldenHour) Palette.gold else Palette.light
        val lanternSize = if (snapshot.goldenHour) 1.35f else 1f
        snapshot.lights.forEach {
            val at = toScreen(it.x, it.y + sin(time * 2.6f + it.id * 1.9f) * 0.004f)
            val r = Race.LIGHT_RADIUS * unit * lanternSize * (1f + 0.06f * sin(time * 3.1f + it.id))
            drawCircle(lantern.copy(alpha = 0.16f), r * 2.6f, at)
            drawCircle(lantern, r, at)
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
                toScreen(boat.x, boat.y + bob),
                Race.BOAT_RADIUS * unit * BOAT_DRAW_SCALE,
                Palette.player(boat.player.colour),
                degrees = roll + boat.lean * 14f + wobble,
                scaleX = 1f + 0.22f * squash,
                scaleY = 1f - 0.18f * squash,
                sail = boat.player.sail,
            )
        }
        // Names after every hull, under each boat on a dark plate, so everyone on the sofa finds their own boat over water, lanterns and other boats.
        snapshot.boats.forEach { boat ->
            drawWord(
                text,
                boat.player.name,
                toScreen(boat.x, boat.y) + Offset(0f, Race.BOAT_RADIUS * unit * BOAT_DRAW_SCALE * 1.6f),
                24.sp,
                Palette.player(boat.player.colour),
                1f,
                1f,
                plate = Palette.night,
            )
        }
        fx.particles.live.filter { it.kind == Kind.Spark }.forEach {
            drawCircle(fxColour(it.colour).copy(alpha = 1f - it.life / it.maxLife), it.size * unit * 1.8f, toScreen(it.x, it.y))
        }
        fx.pops.forEach {
            val t = (time - it.born) / RiverFx.POP_SECONDS
            val alpha = 1f - ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val gold = it.value > 1
            drawWord(
                text,
                "+${it.value}",
                toScreen(it.x, it.y + 0.045f + 0.07f * Ease.outCubic(t)),
                if (gold) 44.sp else 34.sp,
                if (gold) Palette.gold else Palette.light,
                1f,
                alpha,
            )
        }
        fx.goldenBanner().takeIf { it > 0f }?.let { t ->
            drawWord(
                text,
                "Golden hour",
                Offset(center.x, size.height * 0.2f),
                96.sp,
                Palette.gold,
                Ease.outBack(t / 0.2f),
                1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f),
                maxWidth = (1 - 2 * Race.BANK) * unit * 0.92f,
            )
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

/** Draws [word] centred on [at], shrunk to fit [maxWidth] when it is wider. */
internal fun DrawScope.drawWord(
    text: TextMeasurer,
    word: String,
    at: Offset,
    fontSize: TextUnit,
    colour: Color,
    scale: Float,
    alpha: Float,
    maxWidth: Float = Float.MAX_VALUE,
    plate: Color? = null,
) {
    if (alpha <= 0f || scale <= 0f) return
    val layout = text.measure(word, TextStyle(color = colour, fontSize = fontSize, fontWeight = FontWeight.Bold))
    val fit = scale * minOf(1f, maxWidth / layout.size.width)
    withTransform({ scale(fit, fit, at) }) {
        if (plate != null) {
            val pad = Offset(layout.size.height * 0.35f, layout.size.height * 0.05f)
            val half = Offset(layout.size.width / 2f, layout.size.height / 2f) + pad
            drawRoundRect(plate.copy(alpha = alpha.coerceIn(0f, 1f)), at - half, Size(half.x * 2, half.y * 2), CornerRadius(half.y))
        }
        drawText(layout, alpha = alpha.coerceIn(0f, 1f), topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** Two spiral arms in the light water colour, turning slowly so a whirlpool reads as moving water, not a hole. */
private fun DrawScope.drawWhirlpool(at: Offset, radius: Float, time: Float) {
    val turn = -time * 1.6f
    for (arm in 0 until 2) {
        val spiral = Path()
        for (step in 0..40) {
            val f = step / 40f
            val angle = turn + arm * PI.toFloat() + f * 2.6f * PI.toFloat()
            val point = at + Offset(cos(angle), sin(angle)) * (radius * (0.12f + 0.88f * f))
            if (step == 0) spiral.moveTo(point.x, point.y) else spiral.lineTo(point.x, point.y)
        }
        drawPath(spiral, Palette.waterLight, style = Stroke(width = radius * 0.17f, cap = StrokeCap.Round))
    }
    drawCircle(Palette.night, radius * 0.16f, at)
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
internal fun DrawScope.drawBoat(
    at: Offset,
    r: Float,
    colour: Color,
    degrees: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    sail: List<List<Float>> = emptyList(),
) {
    val hull = Path().apply {
        moveTo(at.x - r * 1.1f, at.y)
        lineTo(at.x + r * 1.1f, at.y)
        lineTo(at.x + r * 0.7f, at.y + r * 0.7f)
        lineTo(at.x - r * 0.7f, at.y + r * 0.7f)
        close()
    }
    val pivot = at.copy(y = at.y + r * 0.7f)
    withTransform({
        rotate(degrees, at)
        scale(scaleX, scaleY, pivot)
    }) {
        drawPath(hull, colour)
        drawSail(Rect(at.x - r * 0.9f, at.y - r * 1.5f, at.x + r * 0.9f, at.y - r * 0.05f), sail)
    }
}

/** A paper sail filling the triangle in [box] (apex top middle), with the player's [strokes] inked on it and clipped to it. */
internal fun DrawScope.drawSail(box: Rect, strokes: List<List<Float>>) {
    val sail = Path().apply {
        moveTo(box.center.x, box.top)
        lineTo(box.right, box.bottom)
        lineTo(box.left, box.bottom)
        close()
    }
    drawPath(sail, Palette.paper)
    if (strokes.isEmpty()) return
    val width = box.width * SAIL_INK_WIDTH
    fun at(stroke: List<Float>, i: Int) = Offset(box.left + stroke[2 * i] * box.width, box.top + stroke[2 * i + 1] * box.height)
    clipPath(sail) {
        strokes.forEach { stroke ->
            if (stroke.size == 2) {
                drawCircle(Palette.night, width / 2, at(stroke, 0))
            } else {
                val line = Path()
                for (i in 0 until stroke.size / 2) at(stroke, i).let { if (i == 0) line.moveTo(it.x, it.y) else line.lineTo(it.x, it.y) }
                drawPath(line, Palette.night, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Ink width as a share of the sail's width, so a drawing reads the same on a lobby boat and a race boat. */
private const val SAIL_INK_WIDTH = 0.07f

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
