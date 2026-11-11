package com.starfungames.paperboats.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.random.Random

/** What one player is doing right now, as sent by their controller. */
data class PlayerInput(val steer: Float = 0f, val paddle: Boolean = false)

@Serializable
data class Player(val id: Int, val name: String, val colour: Int)

/**
 * A river race. The river runs upward: x is across (0..1), y is distance travelled.
 * Pure game rules with no UI and no clock, so the host, tests and any platform run the same race.
 */
class Race(players: List<Player>, seed: Long, val length: Float = DEFAULT_LENGTH_SECONDS, val river: River = River.LanternCreek) {
    private val random = Random(seed)
    private val boats = players.mapIndexed { i, p -> Boat(p, x = laneX(i, players.size)) }
    private val lights = mutableListOf<Light>()
    private val rocks = mutableListOf<Rock>()
    private val logs = mutableListOf<Log>()
    private val whirlpools = mutableListOf<Whirlpool>()
    private val events = mutableListOf<RaceEvent>()
    private var nextLightId = 0
    private var generatedTo = 0f

    var elapsed = 0f
        private set

    val finished: Boolean get() = elapsed >= length

    init {
        require(players.isNotEmpty()) { "A race needs at least one player" }
        generateUpTo(COURSE_AHEAD)
    }

    /** Advances the race by [dt] seconds. Inputs for unknown players are ignored. */
    fun step(dt: Float, inputs: Map<Int, PlayerInput>) {
        if (finished) return
        val step = dt.coerceIn(0f, MAX_STEP)
        elapsed = (elapsed + step).coerceAtMost(length)
        val leader = boats.maxOf { it.y }
        driftLogs(step)
        boats.forEach { boat -> moveBoat(boat, inputs[boat.player.id] ?: PlayerInput(), leader, step) }
        collide()
        generateUpTo(boats.maxOf { it.y } + COURSE_AHEAD)
        val tail = boats.minOf { it.y } - COURSE_BEHIND
        lights.removeAll { it.y < tail }
        rocks.removeAll { it.y < tail }
        logs.removeAll { it.y < tail }
        whirlpools.removeAll { it.y < tail }
    }

    /** Events since the last call, in the order they happened. */
    fun drainEvents(): List<RaceEvent> = events.toList().also { events.clear() }

    fun snapshot() = RaceSnapshot(
        elapsed = elapsed,
        length = length,
        boats = boats.map { BoatView(it.player, it.x, it.y, it.score, it.stun > 0f, it.boost > 0f, (it.vx / STEER_SPEED).coerceIn(-1f, 1f)) },
        lights = lights.toList(),
        rocks = rocks.toList(),
        river = river,
        logs = logs.toList(),
        whirlpools = whirlpools.toList(),
    )

    private fun moveBoat(boat: Boat, input: PlayerInput, leader: Float, dt: Float) {
        if (boat.stun > 0f) {
            boat.stun -= dt
            boat.vx *= 0.9f
        } else {
            val steer = input.steer.coerceIn(-1f, 1f)
            boat.vx += (steer * STEER_SPEED - boat.vx) * (1f - DRAG_PER_SECOND.pow(dt))
            if (input.paddle && !boat.paddleHeld) {
                boat.boost = BOOST_SECONDS
                events += Paddled(boat.player.id, boat.x, boat.y)
            }
        }
        boat.paddleHeld = input.paddle
        boat.boost = (boat.boost - dt).coerceAtLeast(0f)
        // A gentle current pulls stragglers forward so nobody falls off the big screen.
        val catchUp = ((leader - boat.y) * CATCH_UP).coerceAtMost(MAX_CATCH_UP)
        val forward = CURRENT_SPEED + (if (boat.boost > 0f) BOOST_SPEED else 0f) + catchUp
        val pool = whirlpools.firstOrNull { hypot(it.x - boat.x, it.y - boat.y) < it.radius }
        if (pool != null && !boat.inWhirlpool) events += WhirlpoolEntered(boat.player.id, boat.x, boat.y)
        boat.inWhirlpool = pool != null
        boat.y += forward * dt * (if (boat.stun > 0f) STUN_SLOWDOWN else 1f) * (if (pool != null) WHIRLPOOL_SLOWDOWN else 1f)
        val pull = if (pool != null) (pool.x - boat.x) * WHIRLPOOL_PULL else 0f
        boat.x = (boat.x + (boat.vx + pull) * dt).coerceIn(BANK, 1f - BANK)
    }

    private fun collide() {
        for (boat in boats) {
            val value = if (length - elapsed <= GOLDEN_HOUR_SECONDS) 2 else 1
            lights.removeAll { light ->
                val hit = hypot(light.x - boat.x, light.y - boat.y) < BOAT_RADIUS + LIGHT_RADIUS
                if (hit) {
                    boat.score += value
                    events += LightCollected(boat.player.id, light.x, light.y, value)
                }
                hit
            }
            if (boat.stun <= 0f) {
                rocks.firstOrNull { hypot(it.x - boat.x, it.y - boat.y) < BOAT_RADIUS + it.radius }?.let { rock ->
                    boat.stun = STUN_SECONDS
                    events += RockHit(boat.player.id, boat.x, boat.y)
                    boat.boost = 0f
                    boat.vx = if (boat.x < rock.x) -STEER_SPEED else STEER_SPEED
                }
            }
            if (boat.stun <= 0f) {
                logs.firstOrNull { abs(it.y - boat.y) < BOAT_RADIUS + LOG_HALF_THICKNESS && abs(it.x - boat.x) < it.halfWidth + BOAT_RADIUS }?.let { log ->
                    boat.stun = STUN_SECONDS
                    events += LogHit(boat.player.id, boat.x, boat.y)
                    boat.boost = 0f
                    boat.vx = if (boat.x < log.x) -STEER_SPEED else STEER_SPEED
                }
            }
        }
        // A paddling boat that runs into a cruising one spins it out and takes one of its lanterns.
        for (rammer in boats) {
            if (rammer.boost <= 0f || rammer.stun > 0f) continue
            val victim = boats.firstOrNull {
                it !== rammer && it.stun <= 0f && it.boost <= 0f && hypot(it.x - rammer.x, it.y - rammer.y) < RAM_REACH
            } ?: continue
            val stole = victim.score > 0
            if (stole) {
                victim.score -= 1
                rammer.score += 1
            }
            victim.stun = STUN_SECONDS
            victim.vx = if (victim.x < rammer.x) -STEER_SPEED else STEER_SPEED
            events += Rammed(victim.player.id, victim.x, victim.y, rammer.player.id, stole)
        }
    }

    private fun generateUpTo(y: Float) {
        while (generatedTo < y) {
            val start = generatedTo
            generatedTo += SEGMENT
            repeat(random.nextInt(2, 5)) {
                lights += Light(nextLightId++, random.between(BANK + 0.04f, 1f - BANK - 0.04f), start + random.nextFloat() * SEGMENT)
            }
            if (start > START_CLEAR && random.nextFloat() < river.rockChance) {
                val r = random.between(0.035f, 0.07f)
                val x = random.between(BANK + r, 1f - BANK - r)
                val rockY = start + random.nextFloat() * SEGMENT
                val blocksLight = lights.any { abs(it.y - rockY) < r + LIGHT_RADIUS && abs(it.x - x) < r + LIGHT_RADIUS }
                if (!blocksLight) rocks += Rock(x, rockY, r)
            }
            // Only rivers with logs or whirlpools draw from the random stream here, so Lantern Creek keeps its course.
            if (river.logChance > 0f && start > START_CLEAR && random.nextFloat() < river.logChance) {
                val half = random.between(0.08f, 0.13f)
                val speed = random.between(0.07f, 0.15f) * (if (random.nextBoolean()) 1f else -1f)
                logs += Log(random.between(BANK + half, 1f - BANK - half), start + random.nextFloat() * SEGMENT, half, speed)
            }
            if (river.whirlpoolChance > 0f && start > START_CLEAR && random.nextFloat() < river.whirlpoolChance) {
                val r = random.between(0.08f, 0.12f)
                val y = start + random.nextFloat() * SEGMENT
                val x = random.between(BANK + r * 0.6f, 1f - BANK - r * 0.6f)
                if (rocks.none { hypot(it.x - x, it.y - y) < r + it.radius }) whirlpools += Whirlpool(x, y, r)
            }
        }
    }

    private fun driftLogs(dt: Float) {
        for (i in logs.indices) {
            val log = logs[i]
            val min = BANK + log.halfWidth
            val max = 1f - BANK - log.halfWidth
            val x = log.x + log.vx * dt
            logs[i] = when {
                x < min -> log.copy(x = min + (min - x), vx = -log.vx)
                x > max -> log.copy(x = max - (x - max), vx = -log.vx)
                else -> log.copy(x = x)
            }
        }
    }

    private class Boat(val player: Player, var x: Float) {
        var y = 0f
        var vx = 0f
        var boost = 0f
        var stun = 0f
        var score = 0
        var paddleHeld = false
        var inWhirlpool = false
    }

    companion object {
        const val DEFAULT_LENGTH_SECONDS = 60f
        const val BOAT_RADIUS = 0.028f
        const val LIGHT_RADIUS = 0.018f
        const val BANK = 0.06f
        /** The last seconds of a race, when every lantern scores 2. */
        const val GOLDEN_HOUR_SECONDS = 10f
        const val LOG_HALF_THICKNESS = 0.014f
        /** Boats are drawn larger than their hit circle, so a ram counts when the drawn hulls touch. */
        private const val RAM_REACH = 2.5f * BOAT_RADIUS
        private const val MAX_STEP = 0.1f
        private const val CURRENT_SPEED = 0.18f
        private const val BOOST_SPEED = 0.22f
        private const val BOOST_SECONDS = 0.45f
        private const val STEER_SPEED = 0.55f
        private const val DRAG_PER_SECOND = 0.002f
        private const val STUN_SECONDS = 0.8f
        private const val STUN_SLOWDOWN = 0.35f
        private const val CATCH_UP = 0.6f
        private const val MAX_CATCH_UP = 0.25f
        private const val SEGMENT = 0.35f
        private const val COURSE_AHEAD = 2.5f
        private const val COURSE_BEHIND = 1.5f
        private const val START_CLEAR = 0.8f
        private const val WHIRLPOOL_PULL = 2.4f
        private const val WHIRLPOOL_SLOWDOWN = 0.55f

        fun laneX(index: Int, count: Int): Float = BANK + (1f - 2 * BANK) * (index + 1) / (count + 1)
    }
}

private fun Float.pow(exponent: Float): Float = exp(exponent * ln(this))

private fun Random.between(from: Float, until: Float): Float = from + nextFloat() * (until - from)
