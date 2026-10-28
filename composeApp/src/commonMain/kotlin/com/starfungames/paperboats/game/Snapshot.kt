package com.starfungames.paperboats.game

import kotlinx.serialization.Serializable

@Serializable
data class Light(val id: Int, val x: Float, val y: Float)

@Serializable
data class Rock(val x: Float, val y: Float, val radius: Float)

@Serializable
data class BoatView(val player: Player, val x: Float, val y: Float, val score: Int, val stunned: Boolean, val boosting: Boolean)

/** Everything the big screen needs to draw one frame. */
@Serializable
data class RaceSnapshot(
    val elapsed: Float,
    val length: Float,
    val boats: List<BoatView>,
    val lights: List<Light>,
    val rocks: List<Rock>,
) {
    val timeLeft: Float get() = (length - elapsed).coerceAtLeast(0f)

    /** Players ordered by lights collected, then by distance travelled. */
    val standings: List<BoatView> get() = boats.sortedWith(compareByDescending<BoatView> { it.score }.thenByDescending { it.y })
}
