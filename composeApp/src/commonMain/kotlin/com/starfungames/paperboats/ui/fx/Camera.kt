package com.starfungames.paperboats.ui.fx

import kotlin.math.sin

/** The big-screen view: follows a target y smoothly and shakes by trauma. Never touches race state. */
class Camera {
    var y = 0f
        private set
    var trauma = 0f
        private set
    private var velocity = 0f

    /** Critically damped follow towards [targetY] (Unity-style SmoothDamp), with no overshoot. */
    fun follow(targetY: Float, dt: Float) {
        if (dt <= 0f) return
        val omega = 2f / SMOOTH_TIME
        val x = omega * dt
        val decay = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x)
        val change = y - targetY
        val temp = (velocity + omega * change) * dt
        velocity = (velocity - omega * temp) * decay
        var next = targetY + (change + temp) * decay
        if ((targetY > y) == (next > targetY)) { next = targetY; velocity = 0f }
        y = next
    }

    /** Adds shake; hits stack and clamp at 1. */
    fun addTrauma(amount: Float) { trauma = (trauma + amount).coerceIn(0f, 1f) }

    /** Lets trauma decay over [dt] seconds. */
    fun step(dt: Float) { trauma = (trauma - TRAUMA_DECAY * dt).coerceAtLeast(0f) }

    /** Shake offset in race units at [time] seconds: trauma² so small hits barely move, smooth sines not jitter. */
    fun shakeOffset(time: Float): Pair<Float, Float> {
        if (trauma <= 0f) return 0f to 0f
        val shake = trauma * trauma
        return MAX_SHAKE * shake * sin(time * 47f) to MAX_SHAKE * shake * sin(time * 61f + 1.3f)
    }

    private companion object {
        const val SMOOTH_TIME = 0.25f
        const val TRAUMA_DECAY = 1.6f
        const val MAX_SHAKE = 0.025f
    }
}
