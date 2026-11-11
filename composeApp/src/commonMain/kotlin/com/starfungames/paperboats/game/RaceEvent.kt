package com.starfungames.paperboats.game

import kotlinx.serialization.Serializable

/** Something that just happened on the river, for the host to draw as juice and forward to phones. */
@Serializable
sealed interface RaceEvent {
    val playerId: Int
    val x: Float
    val y: Float
}

/** A boat picked up the lantern at ([x], [y]), scoring [value]: 2 in the golden hour, 1 before it. */
@Serializable
data class LightCollected(override val playerId: Int, override val x: Float, override val y: Float, val value: Int = 1) : RaceEvent

/** A boat bumped a rock and is stunned. */
@Serializable
data class RockHit(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A boat bumped a drifting log and is stunned, just like a rock. */
@Serializable
data class LogHit(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A boat drifted into a whirlpool, which pulls it toward the centre and slows it while it stays inside. */
@Serializable
data class WhirlpoolEntered(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A player pressed paddle and the boat surged. */
@Serializable
data class Paddled(override val playerId: Int, override val x: Float, override val y: Float) : RaceEvent

/** A paddling boat rammed this one ([playerId]) and spun it out; [stole] says a lantern went to [by]. */
@Serializable
data class Rammed(override val playerId: Int, override val x: Float, override val y: Float, val by: Int, val stole: Boolean) : RaceEvent
