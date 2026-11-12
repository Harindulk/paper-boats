package com.starfungames.paperboats

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Sail drawings for the renders and the README, as a finger would draw them: an initial, a wave, a star and a smiley. */
object SampleSails {
    private fun line(vararg xy: Float) = xy.toList()

    private fun curve(points: Int, at: (Float) -> Pair<Float, Float>) = (0..points).flatMap { at(it / points.toFloat()).toList() }

    private fun ring(cx: Float, cy: Float, r: Float, from: Float = 0f, to: Float = 1f) = curve(32) {
        val a = 2 * PI.toFloat() * (from + (to - from) * it)
        cx + r * cos(a) to cy + r * sin(a)
    }

    val letterA = listOf(line(0.34f, 0.92f, 0.5f, 0.38f, 0.66f, 0.92f), line(0.41f, 0.72f, 0.59f, 0.72f))

    val wave = listOf(0.58f, 0.74f, 0.9f).map { y -> curve(24) { t -> 0.1f + 0.8f * t to y + 0.05f * sin(t * 4 * PI.toFloat()) } }

    val star = listOf(
        curve(10) { t ->
            val i = (t * 10).toInt()
            val r = if (i % 2 == 0) 0.25f else 0.1f
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            0.5f + r * cos(a) to 0.68f + r * 1.05f * sin(a)
        },
    )

    val smiley = listOf(
        ring(0.5f, 0.69f, 0.22f),
        line(0.43f, 0.63f),
        line(0.57f, 0.63f),
        ring(0.5f, 0.68f, 0.13f, from = 0.1f, to = 0.4f),
    )

    val all = listOf(letterA, wave, star, smiley)
}
