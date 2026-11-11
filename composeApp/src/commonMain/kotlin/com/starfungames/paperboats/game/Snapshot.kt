package com.starfungames.paperboats.game

import kotlinx.serialization.Serializable

@Serializable
data class Light(val id: Int, val x: Float, val y: Float)

@Serializable
data class Rock(val x: Float, val y: Float, val radius: Float)

/** A plank drifting across the river at [vx], bouncing off the banks; it spans x ± [halfWidth]. */
@Serializable
data class Log(val x: Float, val y: Float, val halfWidth: Float, val vx: Float)

@Serializable
data class Whirlpool(val x: Float, val y: Float, val radius: Float)

@Serializable
data class BoatView(val player: Player, val x: Float, val y: Float, val score: Int, val stunned: Boolean, val boosting: Boolean, val lean: Float = 0f)

/** Everything the big screen needs to draw one frame. */
@Serializable
data class RaceSnapshot(
    val elapsed: Float,
    val length: Float,
    val boats: List<BoatView>,
    val lights: List<Light>,
    val rocks: List<Rock>,
    val river: River = River.LanternCreek,
    val logs: List<Log> = emptyList(),
    val whirlpools: List<Whirlpool> = emptyList(),
) {
    val timeLeft: Float get() = (length - elapsed).coerceAtLeast(0f)

    /** The last [Race.GOLDEN_HOUR_SECONDS] of the race, when every lantern is worth 2. */
    val goldenHour: Boolean get() = timeLeft <= Race.GOLDEN_HOUR_SECONDS

    /** Players ordered by lights collected, then by distance travelled, then by id so ties stay stable. */
    val standings: List<BoatView> get() = boats.sortedWith(compareByDescending<BoatView> { it.score }.thenByDescending { it.y }.thenBy { it.player.id })
}
