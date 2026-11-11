package com.starfungames.paperboats.ui

import com.starfungames.paperboats.protocol.BuzzKind
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerLogicTest {
    @Test
    fun buzzesScaleWithTheEvent() {
        assertEquals(listOf(15), vibrationPattern(BuzzKind.Light))
        assertEquals(listOf(60), vibrationPattern(BuzzKind.Rock))
        assertEquals(listOf(80, 40, 80), vibrationPattern(BuzzKind.Win))
    }

    @Test
    fun givesUpAfterThreeRetriesOneSecondApart() = runTest {
        var attempts = 0
        stayConnected {
            attempts++
            false
        }
        assertEquals(4, attempts)
        assertEquals(3_000, currentTime)
    }

    @Test
    fun aDropAfterAWelcomeGetsThreeFreshRetries() = runTest {
        val welcomed = ArrayDeque(listOf(false, true, false, false, false, true))
        var attempts = 0
        stayConnected {
            attempts++
            welcomed.removeFirst()
        }
        assertEquals(5, attempts)
    }
}
