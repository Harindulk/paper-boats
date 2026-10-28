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
    data class Join(val name: String) : ControllerMessage

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

    @Serializable
    @SerialName("status")
    data class Status(val phase: Phase, val score: Int, val place: Int, val secondsLeft: Int) : HostMessage

    enum class Phase { Lobby, Racing, Results }
}

val ProtocolJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}
