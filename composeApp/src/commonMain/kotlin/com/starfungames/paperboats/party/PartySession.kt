package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import kotlin.math.ceil

/**
 * One party on the big screen: players join in the lobby, race, see the results, race again.
 * Pure state with no networking or clock, so the desktop host, the tests and any future host share it.
 */
class PartySession(private val roundSeconds: Float = Race.DEFAULT_LENGTH_SECONDS, private val seeds: () -> Long = { 1L }) {
    private val seated = linkedMapOf<Int, Player>()
    private val inputs = mutableMapOf<Int, PlayerInput>()
    private var nextId = 1
    private var race: Race? = null

    var phase = Phase.Lobby
        private set

    val players: List<Player> get() = seated.values.toList()

    /** Seats a new player and gives them the first free colour, or returns null when all seats are taken. */
    fun join(name: String): Player? {
        if (seated.size >= MAX_PLAYERS) return null
        val colour = (0 until MAX_PLAYERS).first { c -> seated.values.none { it.colour == c } }
        val cleanName = name.trim().take(MAX_NAME).ifEmpty { "Player ${nextId}" }
        return Player(nextId++, cleanName, colour).also { seated[it.id] = it }
    }

    /** A player who leaves mid-race keeps their boat, which drifts until the round ends. */
    fun leave(id: Int) {
        seated.remove(id)
        inputs.remove(id)
    }

    fun input(id: Int, input: PlayerInput) {
        if (id in seated) inputs[id] = input
    }

    fun canStart(): Boolean = phase != Phase.Racing && seated.isNotEmpty()

    fun start() {
        check(canStart()) { "Cannot start: phase=$phase, players=${seated.size}" }
        race = Race(players, seed = seeds(), length = roundSeconds)
        inputs.clear()
        phase = Phase.Racing
    }

    fun tick(dt: Float) {
        val current = race ?: return
        if (phase != Phase.Racing) return
        current.step(dt, inputs)
        if (current.finished) phase = Phase.Results
    }

    fun snapshot(): RaceSnapshot? = race?.snapshot()

    fun statusFor(id: Int): HostMessage.Status {
        val snapshot = race?.snapshot()
        val standings = snapshot?.standings.orEmpty()
        val place = standings.indexOfFirst { it.player.id == id }.let { if (it < 0) 0 else it + 1 }
        val score = standings.firstOrNull { it.player.id == id }?.score ?: 0
        val secondsLeft = if (phase == Phase.Racing && snapshot != null) ceil(snapshot.timeLeft).toInt() else 0
        return HostMessage.Status(phase, score, place, secondsLeft)
    }

    companion object {
        const val MAX_PLAYERS = 8
        const val MAX_NAME = 16
    }
}
