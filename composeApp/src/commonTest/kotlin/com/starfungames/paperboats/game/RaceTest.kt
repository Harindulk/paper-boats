package com.starfungames.paperboats.game

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RaceTest {
    private val players = listOf(Player(1, "Ana", 0), Player(2, "Ben", 1))

    private fun play(race: Race, seconds: Float, inputs: (Float) -> Map<Int, PlayerInput>) {
        var t = 0f
        while (t < seconds) {
            race.step(1f / 60f, inputs(t))
            t += 1f / 60f
        }
    }

    @Test
    fun sameSeedAndInputsGiveTheSameRace() {
        val a = Race(players, seed = 7)
        val b = Race(players, seed = 7)
        val noise = Random(1)
        val script = List(600) { mapOf(1 to PlayerInput(noise.nextFloat() * 2 - 1, noise.nextBoolean())) }
        script.forEach { a.step(1f / 60f, it); b.step(1f / 60f, it) }
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun boatsStayOnTheRiver() {
        val race = Race(players, seed = 3)
        play(race, 20f) { t -> mapOf(1 to PlayerInput(steer = 1f), 2 to PlayerInput(steer = if (t % 2 < 1) -1f else 1f)) }
        race.snapshot().boats.forEach { assertTrue(it.x in Race.BANK..(1f - Race.BANK), "boat left the river at x=${it.x}") }
    }

    @Test
    fun paddlingGetsYouAheadButTheCurrentKeepsStragglersClose() {
        val race = Race(players, seed = 11)
        var frame = 0
        play(race, 15f) { mapOf(1 to PlayerInput(paddle = (frame++ / 10) % 2 == 0)) }
        val (paddler, idle) = race.snapshot().boats.partition { it.player.id == 1 }.let { it.first.single() to it.second.single() }
        assertTrue(paddler.y > idle.y, "the paddling boat should lead")
        assertTrue(paddler.y - idle.y < 0.6f, "the idle boat should stay on screen, gap was ${paddler.y - idle.y}")
    }

    @Test
    fun steeringMovesTheBoatSideways() {
        val race = Race(listOf(players.first()), seed = 5)
        val start = race.snapshot().boats.single().x
        play(race, 0.5f) { mapOf(1 to PlayerInput(steer = -1f)) }
        assertTrue(race.snapshot().boats.single().x < start - 0.1f)
    }

    @Test
    fun theRaceEndsOnTime() {
        val race = Race(players, seed = 2, length = 5f)
        play(race, 6f) { emptyMap() }
        assertTrue(race.finished)
        assertEquals(0f, race.snapshot().timeLeft)
        val before = race.snapshot()
        race.step(1f, emptyMap())
        assertEquals(before, race.snapshot(), "nothing moves after the finish")
    }

    @Test
    fun aBoatThatSweepsTheRiverCollectsLights() {
        val race = Race(listOf(players.first()), seed = 9)
        play(race, 30f) { t -> mapOf(1 to PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f)) }
        assertTrue(race.snapshot().boats.single().score > 3)
    }

    @Test
    fun standingsRankByLightsThenDistance() {
        val view = { id: Int, score: Int, y: Float -> BoatView(Player(id, "p$id", id), 0.5f, y, score, stunned = false, boosting = false) }
        val snapshot = RaceSnapshot(0f, 60f, listOf(view(1, 2, 5f), view(2, 4, 1f), view(3, 2, 7f)), emptyList(), emptyList())
        assertEquals(listOf(2, 3, 1), snapshot.standings.map { it.player.id })
    }

    @Test
    fun aRaceNeedsPlayers() {
        assertFailsWith<IllegalArgumentException> { Race(emptyList(), seed = 1) }
    }

    @Test
    fun lanesAreSpreadEvenly() {
        val xs = (0 until 4).map { Race.laneX(it, 4) }
        val gaps = xs.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { abs(it - gaps.first()) < 1e-5f })
    }
}
