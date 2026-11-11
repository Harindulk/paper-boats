package com.starfungames.paperboats.ui.fx

import kotlin.math.PI
import kotlin.math.cos

/** Easing curves: each clamps t to 0..1 and maps 0 to 0 and 1 to 1. */
object Ease {
    private const val BACK = 1.70158f

    /** Fast start, gentle settle: for things coming to rest. */
    fun outCubic(t: Float): Float = 1f - cube(1f - t.coerceIn(0f, 1f))

    /** Overshoots past 1 then settles: for pops. */
    fun outBack(t: Float): Float {
        val u = t.coerceIn(0f, 1f) - 1f
        return 1f + (BACK + 1f) * u * u * u + BACK * u * u
    }

    /** Slow at both ends: for loops and drifts. */
    fun inOutSine(t: Float): Float = (1f - cos(PI.toFloat() * t.coerceIn(0f, 1f))) / 2f

    private fun cube(x: Float) = x * x * x
}
