package com.starfungames.paperboats.audio

import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.LogHit
import com.starfungames.paperboats.game.Paddled
import com.starfungames.paperboats.game.Rammed
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.game.WhirlpoolEntered
import com.starfungames.paperboats.protocol.BuzzKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundTest {
    @Test
    fun everyCueIsShortLoudEnoughAndNeverClips() {
        for (cue in Cue.entries) {
            val pcm = samples(cue)
            assertTrue(pcm.size in SAMPLE_RATE / 20..SAMPLE_RATE * 2, "$cue is ${pcm.size} samples")
            assertTrue(pcm.all { it in -1f..1f }, "$cue clips")
            assertTrue(pcm.maxOf { abs(it) } > 0.1f, "$cue is silent")
        }
    }

    @Test
    fun cuesStartAndEndAtRestSoTheyDoNotClick() {
        for (cue in Cue.entries) {
            val pcm = samples(cue)
            assertTrue(abs(pcm.first()) < 0.05f && abs(pcm.last()) < 0.05f, "$cue clicks")
        }
    }

    @Test
    fun raceEventsMapToCues() {
        assertEquals(Cue.Lantern, cueFor(LightCollected(1, 0f, 0f)))
        assertEquals(Cue.GoldLantern, cueFor(LightCollected(1, 0f, 0f, value = 2)))
        assertEquals(Cue.Bump, cueFor(RockHit(1, 0f, 0f)))
        assertEquals(Cue.Bump, cueFor(LogHit(1, 0f, 0f)))
        assertEquals(Cue.Ram, cueFor(Rammed(1, 0f, 0f, by = 2, stole = true)))
        assertEquals(Cue.Whirl, cueFor(WhirlpoolEntered(1, 0f, 0f)))
        assertEquals(Cue.Paddle, cueFor(Paddled(1, 0f, 0f)))
    }

    @Test
    fun phoneBuzzesMapToCues() {
        assertEquals(Cue.Lantern, cueFor(BuzzKind.Light))
        assertEquals(Cue.Bump, cueFor(BuzzKind.Rock))
        assertEquals(Cue.Finish, cueFor(BuzzKind.Win))
    }

    @Test
    fun samplesAreMadeOncePerCue() {
        assertTrue(samples(Cue.Tick) === samples(Cue.Tick))
    }
}
