package com.starfungames.paperboats.ui

import com.starfungames.paperboats.game.BoatView
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.Paddled
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.game.Rammed
import com.starfungames.paperboats.game.RockHit
import com.starfungames.paperboats.ui.fx.Camera
import com.starfungames.paperboats.ui.fx.Particles
import com.starfungames.paperboats.ui.fx.Particles.Kind
import kotlin.random.Random

/**
 * The big screen's juice, kept apart from the race: particles, camera, "+1" pops, paddle squash,
 * lobby arrivals and the lantern finish. Pure and seeded, so it is tested without a window.
 * Tiers: a light is small (sparks, no shake), a rock is medium (splash and shake), the win is large.
 */
class RiverFx(seed: Long = 7) {
    /** A "+1" rising from a collected light, in race units. */
    data class Pop(val x: Float, val y: Float, val born: Float)

    private val random = Random(seed)
    private val paddledAt = mutableMapOf<Int, Float>()
    private val arrivedAt = mutableMapOf<Int, Float>()
    private val wakeDue = mutableMapOf<Int, Float>()
    private val pending = mutableListOf<Pop>()
    private var lanternsLeft = 0
    private var lanternColour = 0
    private var nextLantern = 0f
    private var lanternGap = 0.15f
    private var confettiUntil = 0f

    var time = 0f
        private set
    var particles = Particles()
        private set
    var camera = Camera()
        private set
    /** When the finish began, for the results screen's staggered entrances. */
    var finishedAt = 0f
        private set
    val pops: List<Pop> get() = pending

    /** A fresh river for a new race: no leftover particles, the camera back at the start. */
    fun newRace() {
        particles = Particles()
        camera = Camera()
        pending.clear()
        paddledAt.clear()
        wakeDue.clear()
        lanternsLeft = 0
        confettiUntil = 0f
    }

    fun onEvents(events: List<RaceEvent>) = events.forEach { event ->
        when (event) {
            is LightCollected -> {
                particles.emit(Kind.Spark, event.x, event.y, LIGHT_COLOUR, 14, random)
                pending += Pop(event.x, event.y, time)
            }
            is RockHit -> {
                particles.emit(Kind.Splash, event.x, event.y, PAPER_COLOUR, 18, random)
                camera.addTrauma(ROCK_TRAUMA)
            }
            is Rammed -> {
                particles.emit(Kind.Splash, event.x, event.y, PAPER_COLOUR, 24, random)
                camera.addTrauma(ROCK_TRAUMA)
                if (event.stole) pending += Pop(event.x, event.y, time)
            }
            is Paddled -> {
                paddledAt[event.playerId] = time
                particles.emit(Kind.Wake, event.x, event.y - STERN, PAPER_COLOUR, 3, random)
            }
        }
    }

    /** Advances every effect by [dt]; [snapshot] is the live race, or null when nothing is racing. */
    fun step(dt: Float, snapshot: RaceSnapshot?) {
        time += dt
        snapshot?.let { race ->
            race.boats.maxOfOrNull { it.y }?.let { camera.follow(it, dt) }
            race.boats.forEach { boat ->
                val due = wakeDue.getOrPut(boat.player.id) { time }
                if (time >= due) {
                    particles.emit(Kind.Wake, boat.x + (random.nextFloat() - 0.5f) * 0.02f, boat.y - STERN, PAPER_COLOUR, if (boat.boosting) 2 else 1, random)
                    wakeDue[boat.player.id] = due + WAKE_EVERY
                }
            }
        }
        while (lanternsLeft > 0 && time >= nextLantern) {
            particles.emit(Kind.Lantern, FINISH_X, FINISH_Y + 0.1f, lanternColour, 1, random) // from the sail top
            lanternsLeft--
            nextLantern += lanternGap
        }
        if (time < confettiUntil) {
            particles.emit(Kind.Confetti, random.nextFloat(), 1.02f, CONFETTI_COLOURS[random.nextInt(CONFETTI_COLOURS.size)], 3, random)
        }
        camera.step(dt)
        particles.step(dt)
        pending.removeAll { time - it.born >= POP_SECONDS }
    }

    /** Clears the river and lets the winner's lanterns lift off, one per light, in results-screen units. */
    fun finish(winner: BoatView) {
        // The camera stays put so the race can cross-fade out where it ended.
        particles = Particles()
        pending.clear()
        finishedAt = time
        lanternColour = winner.player.colour
        lanternsLeft = winner.score
        lanternGap = minOf(0.15f, 2f / maxOf(1, winner.score))
        nextLantern = time + 0.3f
        confettiUntil = time + CONFETTI_SECONDS
    }

    /** Records when each player first appeared, so their boat can pop onto the lobby river. */
    fun noteArrivals(players: List<Player>) {
        arrivedAt.keys.retainAll(players.map { it.id }.toSet())
        players.forEach { arrivedAt.getOrPut(it.id) { time } }
    }

    /** Seconds since [playerId] joined; large when unknown so it draws at rest. */
    fun arrival(playerId: Int): Float = arrivedAt[playerId]?.let { time - it } ?: 60f

    /** Paddle squash for a boat: 1 the moment it paddles, back to 0 after [SQUASH_SECONDS]. */
    fun squash(playerId: Int): Float = paddledAt[playerId]?.let { (1f - (time - it) / SQUASH_SECONDS).coerceIn(0f, 1f) } ?: 0f

    companion object {
        /** Particle colour codes beyond the player seats. */
        const val LIGHT_COLOUR = 100
        const val PAPER_COLOUR = 101
        /** Where the winner's boat sits on the results screen: x across, y up from the bottom, both 0..1. */
        const val FINISH_X = 0.3f
        const val FINISH_Y = 0.24f
        const val POP_SECONDS = 0.9f
        const val SQUASH_SECONDS = 0.12f
        private const val ROCK_TRAUMA = 0.6f
        private const val WAKE_EVERY = 0.05f
        private const val STERN = 0.03f
        private const val CONFETTI_SECONDS = 2f
        private val CONFETTI_COLOURS = listOf(PAPER_COLOUR, LIGHT_COLOUR, 0, 1, 2, 3)
    }
}
