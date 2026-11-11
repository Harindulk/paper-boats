package com.starfungames.paperboats.ui.fx

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FxTest {
    @Test
    fun easesStartAtZeroEndAtOneAndClamp() {
        listOf(Ease::outCubic, Ease::outBack, Ease::inOutSine).forEach { ease ->
            assertEquals(0f, ease(0f), 1e-5f)
            assertEquals(1f, ease(1f), 1e-5f)
            assertEquals(0f, ease(-2f), 1e-5f)
            assertEquals(1f, ease(3f), 1e-5f)
        }
    }

    @Test
    fun outBackOvershootsBeforeSettling() {
        assertTrue((50..95).any { Ease.outBack(it / 100f) > 1f })
    }

    @Test
    fun particlesDieAfterTheirMaxLife() {
        val fx = Particles()
        fx.emit(Particles.Kind.Spark, 0.5f, 1f, colour = 2, count = 10, random = Random(1))
        assertEquals(10, fx.live.size)
        val longest = fx.live.maxOf { it.maxLife }
        repeat(((longest + 0.1f) * 60).toInt()) { fx.step(1f / 60) }
        assertTrue(fx.live.isEmpty())
    }

    @Test
    fun capHoldsWhenManyEventsLandAtOnce() {
        val fx = Particles()
        repeat(20) { fx.emit(Particles.Kind.Splash, 0.5f, it.toFloat(), colour = 0, count = 100, random = Random(it)) }
        assertEquals(600, fx.live.size)
        assertEquals(14f, fx.live.first().y, 0.5f, "the oldest bursts are dropped first")
        assertEquals(19f, fx.live.last().y, 0.5f, "the newest burst survives")
    }

    @Test
    fun lanternsRiseAndConfettiFalls() {
        val fx = Particles()
        fx.emit(Particles.Kind.Lantern, 0.5f, 0f, colour = 1, count = 20, random = Random(3))
        fx.emit(Particles.Kind.Confetti, 0.5f, 0f, colour = 1, count = 20, random = Random(4))
        repeat(30) { fx.step(1f / 60) }
        val (lanterns, confetti) = fx.live.partition { it.kind == Particles.Kind.Lantern }
        assertEquals(20, lanterns.size)
        assertEquals(20, confetti.size)
        assertTrue(lanterns.all { it.y > 0f })
        assertTrue(confetti.all { it.y < 0f })
    }

    @Test
    fun cameraFollowConvergesWithoutOvershoot() {
        val camera = Camera()
        var maxY = 0f
        repeat(90) {
            camera.follow(10f, 1f / 60)
            maxY = maxOf(maxY, camera.y)
        }
        assertTrue(abs(camera.y - 10f) < 0.1f, "within 1% after 1.5 s, was ${camera.y}")
        assertTrue(maxY <= 10f, "overshot to $maxY")
    }

    @Test
    fun traumaDecaysAndShakeIsZeroAtRest() {
        val camera = Camera()
        assertEquals(0f to 0f, camera.shakeOffset(1.3f))
        camera.addTrauma(1f)
        assertTrue(camera.shakeOffset(0.37f).let { it.first != 0f || it.second != 0f })
        repeat(60) { camera.step(1f / 60) }
        assertEquals(0f, camera.trauma)
        assertEquals(0f to 0f, camera.shakeOffset(0.37f))
    }
}
