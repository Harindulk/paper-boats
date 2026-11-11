package com.starfungames.paperboats.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun tiesBreakByDistanceThenId() {
        val view = { id: Int, y: Float -> BoatView(Player(id, "p$id", id), 0.5f, y, 3, stunned = false, boosting = false) }
        val snapshot = RaceSnapshot(0f, 60f, listOf(view(3, 1f), view(1, 1f), view(2, 2f)), emptyList(), emptyList())
        assertEquals(listOf(2, 1, 3), snapshot.standings.map { it.player.id })
    }

    @Test
    fun collectingALightEmitsOneEventAtTheLight() {
        val race = Race(listOf(players.first()), seed = 9)
        var t = 0f
        while (race.snapshot().boats.single().score == 0) {
            val lightsBefore = race.snapshot().lights
            race.drainEvents()
            race.step(1f / 60f, mapOf(1 to PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f)))
            t += 1f / 60f
            assertTrue(t < 30f, "never collected a light")
            if (race.snapshot().boats.single().score == 1) {
                val events = race.drainEvents().filterIsInstance<LightCollected>()
                val eaten = lightsBefore - race.snapshot().lights.toSet()
                assertEquals(1, events.size)
                assertEquals(1, events.single().playerId)
                assertTrue(eaten.any { it.x == events.single().x && it.y == events.single().y }, "the event sits where the light was")
                assertEquals(emptyList(), race.drainEvents())
            }
        }
    }

    @Test
    fun hittingARockEmitsRockHitOnce() {
        val race = Race(listOf(players.first()), seed = 9)
        var hits = 0
        var rockEvents = 0
        var wasStunned = false
        play(race, 30f) { t ->
            val stunned = race.snapshot().boats.single().stunned
            if (stunned && !wasStunned) hits++
            wasStunned = stunned
            rockEvents += race.drainEvents().count { it is RockHit }
            mapOf(1 to PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f))
        }
        rockEvents += race.drainEvents().count { it is RockHit }
        if (race.snapshot().boats.single().stunned && !wasStunned) hits++
        assertTrue(hits > 0, "the sweep should hit a rock")
        assertEquals(hits, rockEvents, "one RockHit per bump, not per frame touching the rock")
    }

    /** Ben sweeps for lanterns while Ana hangs back; then Ana chases Ben and paddles once close. Returns the first ram. */
    private fun chase(race: Race, sweepSeconds: Float): Pair<Rammed, Pair<RaceSnapshot, RaceSnapshot>>? {
        var t = 0f
        while (t < 40f) {
            val before = race.snapshot()
            val ana = before.boats.first { it.player.id == 1 }
            val ben = before.boats.first { it.player.id == 2 }
            val chasing = t > sweepSeconds
            race.step(1f / 60f, mapOf(
                1 to if (chasing) PlayerInput(((ben.x - ana.x) * 12f).coerceIn(-1f, 1f), paddle = hypot(ben.x - ana.x, ben.y - ana.y) < 0.12f && (t * 3f).toInt() % 2 == 0) else PlayerInput(),
                2 to PlayerInput(steer = if (chasing) 0f else if ((t / 1.1f).toInt() % 2 == 0) 1f else -1f),
            ))
            race.drainEvents().filterIsInstance<Rammed>().firstOrNull()?.let { return it to (before to race.snapshot()) }
            t += 1f / 60f
        }
        return null
    }

    @Test
    fun aPaddlingBoatRamsAnotherAndStealsALantern() {
        val race = Race(players, seed = 4)
        val (ram, states) = assertNotNull(chase(race, sweepSeconds = 12f), "Ana should catch Ben")
        val (before, after) = states
        fun score(s: RaceSnapshot, id: Int) = s.boats.first { it.player.id == id }.score
        assertEquals(2, ram.playerId, "Ben is the one rammed")
        assertEquals(1, ram.by)
        assertTrue(after.boats.first { it.player.id == 2 }.stunned, "the rammed boat spins out")
        assertTrue(score(before, 2) > 0, "test setup: Ben had a lantern to lose")
        assertTrue(ram.stole)
        assertEquals(score(before, 2) - 1, score(after, 2), "Ben loses a lantern")
        assertEquals(score(before, 1) + 1, score(after, 1), "Ana takes it")
    }

    @Test
    fun ramWithNothingToStealOnlyStuns() {
        val race = Race(players, seed = 4)
        val (ram, states) = assertNotNull(chase(race, sweepSeconds = 0f))
        val (before, after) = states
        if (before.boats.first { it.player.id == 2 }.score == 0) {
            assertTrue(!ram.stole)
            assertEquals(0, after.boats.first { it.player.id == 2 }.score, "scores never go negative")
        }
    }

    @Test
    fun aStunnedBoatCannotBeRammedAgainStraightAway() {
        val race = Race(players, seed = 4)
        assertNotNull(chase(race, sweepSeconds = 0f))
        var again = 0
        repeat(30) { // 0.5 s, inside the stun
            race.step(1f / 60f, mapOf(1 to PlayerInput(paddle = it % 2 == 0)))
            again += race.drainEvents().count { it is Rammed }
        }
        assertEquals(0, again)
    }

    @Test
    fun paddlePressEmitsPaddledOnRisingEdgeOnly() {
        val race = Race(listOf(players.first()), seed = 5)
        var frame = 0
        play(race, 0.5f) { mapOf(1 to PlayerInput(paddle = frame++ % 15 < 10)) }
        assertEquals(2, race.drainEvents().count { it is Paddled && it.playerId == 1 })
    }

    @Test
    fun leanFollowsSteer() {
        val race = Race(listOf(players.first()), seed = 5)
        play(race, 1f) { mapOf(1 to PlayerInput(steer = 1f)) }
        assertTrue(race.snapshot().boats.single().lean > 0.8f)
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
    @Test
    fun lanternCreekIsTheDefaultRiverAndPlaysExactlyAsBefore() {
        val race = Race(players, seed = 9)
        var t = 0f
        while (t < 20f) {
            race.step(1f / 60f, mapOf(1 to PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f, paddle = (t * 2).toInt() % 3 == 0)))
            t += 1f / 60f
        }
        val s = race.snapshot()
        assertEquals(River.LanternCreek, s.river)
        assertTrue(s.logs.isEmpty() && s.whirlpools.isEmpty())
        // Pinned from the river before rivers existed: same seed, same inputs, same course and scores.
        assertEquals(
            "[0.7166398,4.7585945,9, 0.72917503,4.771361,3] 33 [Rock(x=0.59854704, y=4.095461, radius=0.060536392), " +
                "Rock(x=0.8743268, y=4.707791, radius=0.052653186), Rock(x=0.25122386, y=5.0425787, radius=0.043933935), " +
                "Rock(x=0.7361244, y=6.967539, radius=0.045838855)]",
            "${s.boats.map { "${it.x},${it.y},${it.score}" }} ${s.lights.size} ${s.rocks}",
        )
    }

    @Test
    fun logjamBendHasLogsAndFewerRocks() {
        val seeds = 1L..20L
        val creekRocks = seeds.sumOf { Race(players, it).snapshot().rocks.size }
        val logjamRocks = seeds.sumOf { Race(players, it, river = River.LogjamBend).snapshot().rocks.size }
        assertTrue(logjamRocks < creekRocks, "logjam $logjamRocks rocks vs creek $creekRocks")
        val logs = seeds.sumOf { Race(players, it, river = River.LogjamBend).snapshot().logs.size }
        assertTrue(logs > seeds.count(), "about one log per stretch of river: $logs logs in 20 courses")
        assertEquals(0, seeds.sumOf { Race(players, it).snapshot().logs.size }, "no logs on the creek")
    }

    @Test
    fun logsDriftAcrossAndStayBetweenTheBanks() {
        val race = Race(players, seed = 3, river = River.LogjamBend)
        val before = race.snapshot().logs
        play(race, 0.5f) { emptyMap() }
        before.zip(race.snapshot().logs).forEach { (a, b) ->
            assertEquals(a.y, b.y, "logs drift across, not along")
            assertTrue(a.x != b.x, "log at y=${a.y} did not move")
        }
        repeat(20) {
            play(race, 1f) { emptyMap() }
            race.snapshot().logs.forEach { assertTrue(it.x - it.halfWidth >= Race.BANK - 1e-4f && it.x + it.halfWidth <= 1f - Race.BANK + 1e-4f, "log off the river: $it") }
        }
    }

    /** Steers one boat at the nearest thing ahead returned by [target] until an event of type [E] arrives. */
    private inline fun <reified E : RaceEvent> steerInto(race: Race, target: (RaceSnapshot, BoatView) -> Float?): Pair<E, RaceSnapshot>? {
        var t = 0f
        while (t < 40f) {
            val s = race.snapshot()
            val boat = s.boats.single()
            val x = target(s, boat)
            race.step(1f / 60f, mapOf(boat.player.id to PlayerInput(steer = if (x == null) 0f else ((x - boat.x) * 20f).coerceIn(-1f, 1f))))
            race.drainEvents().filterIsInstance<E>().firstOrNull()?.let { return it to race.snapshot() }
            t += 1f / 60f
        }
        return null
    }

    @Test
    fun touchingALogStunsLikeARock() {
        val race = Race(listOf(players.first()), seed = 3, river = River.LogjamBend)
        val (hit, after) = assertNotNull(steerInto<LogHit>(race) { s, b -> s.logs.filter { it.y > b.y }.minByOrNull { it.y }?.x }, "never reached a log")
        assertEquals(1, hit.playerId)
        assertTrue(after.boats.single().stunned)
        var again = 0
        repeat(30) {
            race.step(1f / 60f, emptyMap())
            again += race.drainEvents().count { it is LogHit }
        }
        assertEquals(0, again, "one bump, one LogHit")
    }

    @Test
    fun whirlpoolsPullTowardTheirCentreAndSlowYouWithoutStunning() {
        val race = Race(listOf(players.first()), seed = 3, river = River.WhirlpoolNarrows)
        assertTrue(race.snapshot().whirlpools.isNotEmpty())
        // Aim a little to one side of the centre so the pull has somewhere to go.
        val (entered, inside) = assertNotNull(
            steerInto<WhirlpoolEntered>(race) { s, b -> s.whirlpools.filter { it.y > b.y }.minByOrNull { it.y }?.let { it.x + it.radius * 0.6f } },
            "never reached a whirlpool",
        )
        assertEquals(1, entered.playerId)
        val boat = inside.boats.single()
        val pool = inside.whirlpools.first { hypot(it.x - boat.x, it.y - boat.y) < it.radius }
        assertTrue(!boat.stunned)
        play(race, 0.25f) { emptyMap() }
        val later = race.snapshot().boats.single()
        assertTrue(abs(later.x - pool.x) < abs(boat.x - pool.x), "pulled toward the centre: ${boat.x} -> ${later.x}, centre ${pool.x}")
        assertTrue(later.y - boat.y < 0.18f * 0.25f * 0.8f, "slowed: moved ${later.y - boat.y}")
        assertEquals(0, race.drainEvents().count { it is WhirlpoolEntered }, "one event per visit")
    }

    @Test
    fun lanternsInTheLastTenSecondsAreWorthTwo() {
        val race = Race(listOf(players.first()), seed = 9, length = 16f)
        val values = mutableListOf<Pair<Float, Int>>()
        var t = 0f
        while (!race.finished) {
            race.step(1f / 60f, mapOf(1 to PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f)))
            race.drainEvents().filterIsInstance<LightCollected>().forEach { values += race.snapshot().timeLeft to it.value }
            t += 1f / 60f
        }
        assertTrue(values.any { it.second == 1 } && values.any { it.second == 2 }, "test setup: lanterns on both sides of the golden hour: $values")
        values.forEach { (left, value) -> assertEquals(if (left <= Race.GOLDEN_HOUR_SECONDS) 2 else 1, value, "at $left s left") }
        assertEquals(values.sumOf { it.second }, race.snapshot().boats.single().score)
    }

    @Test
    fun snapshotKnowsWhenGoldenHourBegins() {
        assertTrue(!RaceSnapshot(49.9f, 60f, emptyList(), emptyList(), emptyList()).goldenHour)
        assertTrue(RaceSnapshot(50f, 60f, emptyList(), emptyList(), emptyList()).goldenHour)
    }
}
