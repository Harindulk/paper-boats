package com.starfungames.paperboats.protocol

import com.starfungames.paperboats.game.PlayerInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Controller to host. */
@Serializable
sealed interface ControllerMessage {
    @Serializable
    @SerialName("join")
    data class Join(val name: String, val rejoinId: Int? = null) : ControllerMessage

    @Serializable
    @SerialName("input")
    data class Input(val steer: Float, val paddle: Boolean) : ControllerMessage {
        fun toPlayerInput() = PlayerInput(steer.coerceIn(-1f, 1f), paddle)
    }
}

/** Host to controller: just enough for the phone to show its own colour, place and score. */
@Serializable
sealed interface HostMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val playerId: Int, val colour: Int) : HostMessage

    /** [award] is this player's results award, such as "Pirate. Stole 2 lanterns by ramming.", or null. */
    @Serializable
    @SerialName("status")
    data class Status(
        val phase: Phase,
        val score: Int,
        val place: Int,
        val secondsLeft: Int,
        val countdown: Int = 0,
        val award: String? = null,
    ) : HostMessage

    /** A short phone vibration for something that just happened to this player. */
    @Serializable
    @SerialName("buzz")
    data class Buzz(val kind: BuzzKind) : HostMessage

    enum class Phase { Lobby, Countdown, Racing, Results }
}

enum class BuzzKind { Light, Rock, Win }

val ProtocolJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}
