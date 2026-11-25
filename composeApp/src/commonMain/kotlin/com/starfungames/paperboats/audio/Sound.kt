package com.starfungames.paperboats.audio

import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.LogHit
import com.starfungames.paperboats.game.Paddled
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.Rammed
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.game.WhirlpoolEntered
import com.starfungames.paperboats.protocol.BuzzKind
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Every sound in the game. They are synthesised here, in shared code, so no target ships audio files. */
enum class Cue { Paddle, Lantern, GoldLantern, Bump, Ram, Whirl, Tick, Go, Finish }

const val SAMPLE_RATE = 22_050

/** Plays [cue] once, mixed over anything already playing. Each target has its own speaker; a missing one stays silent. */
expect fun play(cue: Cue)

fun cueFor(event: RaceEvent): Cue = when (event) {
    is LightCollected -> if (event.value > 1) Cue.GoldLantern else Cue.Lantern
    is RockHit, is LogHit -> Cue.Bump
    is Rammed -> Cue.Ram
    is WhirlpoolEntered -> Cue.Whirl
    is Paddled -> Cue.Paddle
}

fun cueFor(kind: BuzzKind): Cue = when (kind) {
    BuzzKind.Light -> Cue.Lantern
    BuzzKind.Rock -> Cue.Bump
    BuzzKind.Win -> Cue.Finish
}

private val made = mutableMapOf<Cue, FloatArray>()

/** Mono PCM in -1..1 at [SAMPLE_RATE], made on first use and kept. */
fun samples(cue: Cue): FloatArray = made.getOrPut(cue) { synth(cue) }

private fun synth(cue: Cue): FloatArray = when (cue) {
    Cue.Paddle -> noise(0.14f, amp = 0.35f, smooth = 0.25f)

    Cue.Lantern -> bell(1320f, 0.35f)

    Cue.GoldLantern -> mix(bell(1320f, 0.3f), at(0.08f, bell(1760f, 0.4f)))

    Cue.Bump -> mix(tone(0.25f, amp = 0.7f, decay = 14f) { 95f - 45f * it }, noise(0.12f, amp = 0.3f, smooth = 0.08f))

    Cue.Ram -> mix(
        tone(0.2f, amp = 0.6f, decay = 16f) {
            110f - 50f * it
        },
        at(0.04f, tone(0.25f, amp = 0.3f, decay = 6f) { 700f - 400f * it }),
    )

    Cue.Whirl -> tone(0.55f, amp = 0.3f, decay = 3f) { 320f + 180f * sin(it * PI.toFloat()) + 25f * sin(it * 40f) }

    Cue.Tick -> bell(880f, 0.12f)

    Cue.Go -> mix(bell(1320f, 0.4f), bell(2640f, 0.25f, amp = 0.15f))

    Cue.Finish -> mix(
        *listOf(523f, 659f, 784f).mapIndexed { i, f ->
            at(i * 0.11f, bell(f, 0.3f))
        }.toTypedArray(),
        at(0.33f, bell(1047f, 0.7f)),
    )
}

/** A sine whose pitch follows [hz] over the note's progress 0..1, with a 5 ms attack and a decay that lands on zero. */
private fun tone(seconds: Float, amp: Float, decay: Float, hz: (Float) -> Float): FloatArray {
    val n = (seconds * SAMPLE_RATE).toInt()
    var phase = 0.0
    return FloatArray(n) { i ->
        val t = i.toFloat() / n
        phase += 2 * PI * hz(t) / SAMPLE_RATE
        (sin(phase) * amp * envelope(i, n, decay)).toFloat()
    }
}

private fun bell(hz: Float, seconds: Float, amp: Float = 0.4f) = mix(
    tone(seconds, amp, decay = 6f) {
        hz
    },
    tone(seconds, amp * 0.3f, decay = 9f) {
        hz *
            2.01f
    },
)

/** Low-passed white noise: [smooth] near 0 is a dull thump, near 1 a bright hiss. Seeded so every splash sounds alike. */
private fun noise(seconds: Float, amp: Float, smooth: Float): FloatArray {
    val n = (seconds * SAMPLE_RATE).toInt()
    val random = Random(7)
    var low = 0f
    return FloatArray(n) { i ->
        low += smooth * (random.nextFloat() * 2f - 1f - low)
        low * amp * 3f * envelope(i, n, decay = 5f)
    }
}

private fun envelope(i: Int, n: Int, decay: Float): Float {
    val attack = (i / (0.005f * SAMPLE_RATE)).coerceAtMost(1f)
    val t = i.toFloat() / n
    return attack * exp(-decay * t) * (1f - t)
}

/** [pcm] delayed by [seconds] of silence. */
private fun at(seconds: Float, pcm: FloatArray): FloatArray = FloatArray((seconds * SAMPLE_RATE).toInt()) + pcm

/** Layers clips from time zero, then limits the sum so stacked notes never clip. */
private fun mix(vararg parts: FloatArray): FloatArray {
    val out = FloatArray(parts.maxOf { it.size })
    for (part in parts) part.forEachIndexed { i, v -> out[i] += v }
    return FloatArray(out.size) { out[it].coerceIn(-1f, 1f) }
}
