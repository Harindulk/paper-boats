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

    @Test
    fun aLongScribbleIsThinnedToTheCapKeepingItsEnds() {
        val points = (0..499).flatMap { listOf(it / 499f, 0.5f) }
        val thin = thinStroke(points, 64)
        assertEquals(128, thin.size)
        assertEquals(listOf(0f, 0.5f), thin.take(2))
        assertEquals(listOf(1f, 0.5f), thin.takeLast(2))
    }

    @Test
    fun aShortStrokeIsOnlyRounded() {
        assertEquals(listOf(0.123f, 0.988f, 0.5f, 0.5f), thinStroke(listOf(0.12345f, 0.98765f, 0.5f, 0.5f), 64))
    }
}
