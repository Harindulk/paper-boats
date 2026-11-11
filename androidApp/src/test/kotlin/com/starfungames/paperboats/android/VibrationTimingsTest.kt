package com.starfungames.paperboats.android

import com.starfungames.paperboats.protocol.BuzzKind
import kotlin.test.Test
import kotlin.test.assertContentEquals

class VibrationTimingsTest {
    @Test
    fun waveformsStartWithNoDelay() {
        assertContentEquals(longArrayOf(0, 15), vibrationTimings(BuzzKind.Light))
        assertContentEquals(longArrayOf(0, 80, 40, 80), vibrationTimings(BuzzKind.Win))
    }
}
