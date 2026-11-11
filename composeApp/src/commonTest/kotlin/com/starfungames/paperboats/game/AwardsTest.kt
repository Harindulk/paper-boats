package com.starfungames.paperboats.game

import kotlin.test.Test
import kotlin.test.assertEquals

class AwardsTest {
    private fun view(id: Int) = BoatView(Player(id, "p$id", id), 0.5f, 0f, 0, stunned = false, boosting = false)
    private val standings = listOf(view(1), view(2), view(3), view(4))

    private fun ram(victim: Int, by: Int, stole: Boolean = true) = Rammed(victim, 0f, 0f, by, stole)
    private fun gold(id: Int) = LightCollected(id, 0f, 0f, value = 2)

    @Test
    fun eachAwardGoesToTheBiggestStat() {
        val events = listOf(
            ram(1, by = 3), ram(2, by = 3), ram(1, by = 2, stole = false),
            RockHit(4, 0f, 0f), LogHit(4, 0f, 0f),
            gold(2), gold(2), LightCollected(1, 0f, 0f), gold(1),
        )
        assertEquals(
            listOf(
                Award(3, "Pirate", "Stole 2 lanterns by ramming"),
                Award(1, "Shipwreck", "Bumped 2 times"),
                Award(2, "Golden hour", "Scored 4 in the last 10 seconds"),
            ),
            awards(events, standings),
        )
    }

    @Test
    fun shipwreckCountsRocksLogsAndRams() {
        val events = listOf(RockHit(2, 0f, 0f), LogHit(2, 0f, 0f), ram(2, by = 1, stole = false), RockHit(3, 0f, 0f), RockHit(3, 0f, 0f))
        assertEquals(listOf(Award(2, "Shipwreck", "Bumped 3 times")), awards(events, standings))
    }

    @Test
    fun aPlayerWinsOnlyOneAwardAndTheNextBestGetsIt() {
        // Ana steals twice and has the most bumps too, so Shipwreck passes to Chen, the next most bumped.
        val events =
            listOf(
                ram(4, by = 1),
                ram(2, by = 1),
                RockHit(1, 0f, 0f),
                RockHit(1, 0f, 0f),
                RockHit(1, 0f, 0f),
                RockHit(3, 0f, 0f),
                RockHit(3, 0f, 0f),
            )
        assertEquals(listOf(1, 3), awards(events, standings).map { it.playerId })
    }

    @Test
    fun nothingHappenedMeansNoAwards() {
        assertEquals(emptyList(), awards(listOf(Paddled(1, 0f, 0f), LightCollected(1, 0f, 0f)), standings))
        assertEquals(emptyList(), awards(emptyList(), standings))
    }

    @Test
    fun tiesGoToTheBetterPlace() {
        val events = listOf(gold(4), gold(3))
        assertEquals(listOf(Award(3, "Golden hour", "Scored 2 in the last 10 seconds")), awards(events, listOf(view(3), view(4))))
        assertEquals(4, awards(events, listOf(view(4), view(3))).single().playerId)
    }

    @Test
    fun singularLines() {
        val lines = awards(listOf(ram(2, by = 1), RockHit(3, 0f, 0f)), standings).map { it.line }
        assertEquals(listOf("Stole 1 lantern by ramming", "Bumped once"), lines)
    }
}
