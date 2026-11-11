package com.starfungames.paperboats.ui

import com.starfungames.paperboats.game.BoatView
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.Paddled
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.ui.fx.Particles.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiverFxTest {
    private val ana = Player(1, "Ana", 0)
    private val ben = Player(2, "Ben", 1)

    private fun snapshot(leaderY: Float = 0f, boosting: Boolean = false) = RaceSnapshot(
        elapsed = 5f, length = 60f,
        boats = listOf(BoatView(ana, 0.3f, leaderY, 2, false, boosting), BoatView(ben, 0.7f, leaderY - 0.1f, 1, false, false)),
        lights = emptyList(), rocks = emptyList(),
    )

    private fun RiverFx.run(seconds: Float, snapshot: RaceSnapshot?) {
        repeat((seconds * 60).toInt()) { step(1f / 60f, snapshot) }
    }

    @Test
    fun rockHitShakesTheCameraAndSplashes() {
        val fx = RiverFx()
        fx.onEvents(listOf(RockHit(1, 0.5f, 2f)))
        assertTrue(fx.camera.trauma >= 0.35f)
        assertTrue(fx.particles.live.any { it.kind == Kind.Splash })
    }

    @Test
    fun shakeReturnsToRest() {
        val fx = RiverFx()
        fx.onEvents(listOf(RockHit(1, 0.5f, 2f)))
        fx.run(1f, snapshot())
        assertEquals(0f, fx.camera.trauma)
    }

    @Test
    fun lightPickupSparksInTheLightColourAndPopsAPlusOne() {
        val fx = RiverFx()
        fx.onEvents(listOf(LightCollected(1, 0.4f, 3f)))
        val sparks = fx.particles.live.filter { it.kind == Kind.Spark }
        assertTrue(sparks.isNotEmpty())
        assertTrue(sparks.all { it.colour == RiverFx.LIGHT_COLOUR })
        assertEquals(1, fx.pops.size)
        fx.run(1f, snapshot())
        assertTrue(fx.pops.isEmpty(), "the +1 fades out")
    }

    @Test
    fun paddleSquashIsBrief() {
        val fx = RiverFx()
        fx.onEvents(listOf(Paddled(1, 0.3f, 0f)))
        assertTrue(fx.squash(1) > 0.9f)
        assertEquals(0f, fx.squash(2))
        fx.run(0.2f, snapshot())
        assertEquals(0f, fx.squash(1))
    }

    @Test
    fun boatsLeaveAWakeAndMoreWhileBoosting() {
        val calm = RiverFx().apply { run(0.5f, snapshot()) }
        val boost = RiverFx().apply { run(0.5f, snapshot(boosting = true)) }
        val calmWake = calm.particles.live.count { it.kind == Kind.Wake }
        assertTrue(calmWake in 16..24, "two boats, one puff per 0.05 s: $calmWake")
        assertTrue(boost.particles.live.count { it.kind == Kind.Wake } > calmWake)
        assertTrue(RiverFx().apply { run(0.5f, null) }.particles.live.isEmpty(), "no wake without a race")
    }

    @Test
    fun cameraGlidesToTheLeader() {
        val fx = RiverFx()
        fx.step(1f / 60f, snapshot(leaderY = 1f))
        assertTrue(fx.camera.y > 0f && fx.camera.y < 0.5f, "no snapping: ${fx.camera.y}")
        fx.run(2f, snapshot(leaderY = 1f))
        assertEquals(1f, fx.camera.y, 0.01f)
    }

    @Test
    fun finishReleasesOneLanternPerLightStaggered() {
        val fx = RiverFx()
        fx.onEvents(listOf(RockHit(1, 0.5f, 2f)))
        fx.finish(BoatView(ana, 0.3f, 9f, score = 6, stunned = false, boosting = false))
        assertTrue(fx.particles.live.none { it.kind == Kind.Splash }, "race juice is cleared")
        fx.step(1f / 60f, null)
        assertTrue(fx.particles.live.count { it.kind == Kind.Lantern } < 6, "staggered")
        fx.run(2.2f, null)
        val lanterns = fx.particles.live.filter { it.kind == Kind.Lantern }
        assertEquals(6, lanterns.size)
        assertTrue(lanterns.all { it.colour == ana.colour })
        assertTrue(fx.particles.live.any { it.kind == Kind.Confetti })
        fx.run(5f, null)
        assertTrue(fx.particles.live.isEmpty(), "confetti stops after 2 s and everything settles")
    }

    @Test
    fun arrivalsAreTimedPerPlayer() {
        val fx = RiverFx()
        fx.noteArrivals(listOf(ana))
        fx.run(0.5f, null)
        fx.noteArrivals(listOf(ana, ben))
        assertEquals(0.5f, fx.arrival(ana.id), 0.02f)
        assertEquals(0f, fx.arrival(ben.id))
    }
}
