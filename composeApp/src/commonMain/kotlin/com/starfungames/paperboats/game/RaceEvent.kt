package com.starfungames.paperboats.game

import kotlinx.serialization.Serializable

/** Something that just happened on the river, for the host to draw as juice and forward to phones. */
@Serializable
sealed interface RaceEvent {
    val playerId: Int
    val x: Float
    val y: Float
}

/** A boat picked up the lantern at ([x], [y]). */
@Serializable
data class LightCollected(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A boat bumped a rock and is stunned. */
@Serializable
data class RockHit(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A player pressed paddle and the boat surged. */
@Serializable
data class Paddled(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent
