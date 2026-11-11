package com.starfungames.paperboats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.protocol.BuzzKind
import com.starfungames.paperboats.ui.fx.Ease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** One buzz from the host; a new instance each time, so the pad replays its feedback even for the same kind. */
class BuzzEvent(val kind: BuzzKind)

internal val OutBack = Easing { Ease.outBack(it) }
private val RockRed = Color(0xFFE5484D)

/** The two text sizes the phone uses: one huge, one readable. */
internal val Huge = TextStyle(fontSize = 120.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp)
internal val Body = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

/** Scale that pops from small past 1 and settles whenever [key] changes. */
@Composable
internal fun rememberPop(key: Any?): Animatable<Float, *> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(key) {
        scale.snapTo(0.6f)
        scale.animateTo(1f, tween(350, easing = OutBack))
    }
    return scale
}

/**
 * The phone controller, shared by the web and Android apps. The top is the player's colour field with
 * [place] set huge and [status] under it; the bottom left steers (a knob follows the thumb and springs
 * back), the bottom right paddles (hold for a burst). Each half reads its own pointers, so two thumbs work at once.
 */
@Composable
fun ControllerPad(
    colour: Int,
    status: String,
    onInput: (PlayerInput) -> Unit,
    modifier: Modifier = Modifier,
    place: String = "",
    buzz: BuzzEvent? = null,
) {
    var steer by remember { mutableFloatStateOf(0f) }
    var paddle by remember { mutableStateOf(false) }
    fun send() = onInput(PlayerInput(steer, paddle))

    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    var flash by remember { mutableStateOf(false) }
    val rock = remember { Animatable(1f) }
    val placePop = rememberPop(place)

    LaunchedEffect(buzz) {
        when (buzz?.kind) {
            BuzzKind.Light -> {
                flash = true
                delay(80)
                flash = false
            }

            BuzzKind.Rock -> {
                rock.snapTo(0f)
                rock.animateTo(1f, tween(450))
            }

            else -> Unit
        }
    }

    Column(modifier.background(Palette.night)) {
        // Rock bump: the field shakes sideways and blushes red, both fading back to rest.
        val hurt = 1f - rock.value
        Box(
            Modifier.weight(0.45f).fillMaxSize()
                .graphicsLayer { translationX = sin(rock.value * PI.toFloat() * 6f) * hurt * 18.dp.toPx() }
                .background(Palette.player(colour)),
        ) {
            if (hurt > 0f) Box(Modifier.fillMaxSize().background(RockRed.copy(alpha = 0.45f * hurt)))
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                if (place.isNotEmpty()) {
                    BasicText(
                        place,
                        Modifier.graphicsLayer {
                            scaleX = placePop.value
                            scaleY = placePop.value
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                        style = Huge.copy(color = Palette.night),
                        maxLines = 1,
                    )
                }
                BasicText(status, style = Body.copy(color = Palette.night))
            }
        }
        Row(Modifier.weight(0.55f)) {
            Box(
                Modifier.weight(1f).fillMaxSize().background(Palette.water).pointerInput(Unit) {
                    val reach = size.width * 0.3f
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val origin = down.position.x
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = (change.position.x - origin).coerceIn(-reach, reach)
                            scope.launch { knob.snapTo(dx) }
                            steer = dx / reach
                            send()
                        } while (change.pressed)
                        steer = 0f
                        send()
                        // snapTo on the next touch cancels this spring.
                        scope.launch { knob.animateTo(0f, tween(320, easing = OutBack)) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.offset { IntOffset(knob.value.roundToInt(), 0) }.size(84.dp).background(Palette.paper, CircleShape))
                BasicText(
                    "Steer",
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    style = Body.copy(color = Palette.inkSoft),
                )
            }
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .graphicsLayer {
                        scaleX = press.value
                        scaleY = press.value
                    }
                    .background(if (flash) Palette.paper else Palette.light)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            paddle = true
                            send()
                            scope.launch { press.snapTo(0.94f) }
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            paddle = false
                            send()
                            scope.launch { press.animateTo(1f, tween(260, easing = OutBack)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { BasicText("Paddle", style = Body.copy(color = Palette.night)) }
        }
    }
}
