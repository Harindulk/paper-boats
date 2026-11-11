package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceEvent
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
    private val away = mutableSetOf<Int>()
    private var nextId = 1
    private var race: Race? = null

    var phase = Phase.Lobby
        private set

    var countdownLeft = 0f
        private set

    val players: List<Player> get() = seated.values.toList()

    /**
     * Seats a new player and gives them the first free colour, or returns null when all seats are taken.
     * A phone that comes back with the [rejoinId] of an away player gets that same player and boat back.
     */
    fun join(name: String, rejoinId: Int? = null): Player? {
        if (rejoinId != null && away.remove(rejoinId)) return seated.getValue(rejoinId)
        if (seated.size >= MAX_PLAYERS) return null
        val colour = (0 until MAX_PLAYERS).first { c -> seated.values.none { it.colour == c } }
        val cleanName = name.trim().take(MAX_NAME).ifEmpty { "Player ${nextId}" }
        return Player(nextId++, cleanName, colour).also { seated[it.id] = it }
    }

    /** In the lobby the seat is freed; later the player is only marked away, so their boat drifts and they can rejoin. */
    fun leave(id: Int) {
        inputs.remove(id)
        if (phase == Phase.Lobby) seated.remove(id) else if (id in seated) away += id
        if (phase == Phase.Countdown && seated.keys.all { it in away }) {
            phase = Phase.Lobby
            dropAway()
        }
    }

    fun input(id: Int, input: PlayerInput) {
        if (id in seated && id !in away) inputs[id] = input
    }

    fun canStart(): Boolean = (phase == Phase.Lobby || phase == Phase.Results) && seated.keys.any { it !in away }

    /** Starts the countdown; players who left during the last round give up their seats now. */
    fun start() {
        check(canStart()) { "Cannot start: phase=$phase, players=${seated.size}" }
        dropAway()
        race = null
        inputs.clear()
        countdownLeft = COUNTDOWN_SECONDS
        phase = Phase.Countdown
    }

    fun tick(dt: Float) {
        if (phase == Phase.Countdown) {
            countdownLeft = (countdownLeft - dt).coerceAtLeast(0f)
            if (countdownLeft == 0f) {
                race = Race(players, seed = seeds(), length = roundSeconds)
                phase = Phase.Racing
            }
            return
        }
        val current = race ?: return
        if (phase != Phase.Racing) return
        current.step(dt, inputs)
        if (current.finished) phase = Phase.Results
    }

    fun drainEvents(): List<RaceEvent> = race?.drainEvents().orEmpty()

    fun snapshot(): RaceSnapshot? = race?.snapshot()

    fun statusFor(id: Int): HostMessage.Status {
        val snapshot = race?.snapshot()
        val standings = snapshot?.standings.orEmpty()
        val place = standings.indexOfFirst { it.player.id == id }.let { if (it < 0) 0 else it + 1 }
        val score = standings.firstOrNull { it.player.id == id }?.score ?: 0
        val secondsLeft = if (phase == Phase.Racing && snapshot != null) ceil(snapshot.timeLeft).toInt() else 0
        val countdown = if (phase == Phase.Countdown) ceil(countdownLeft).toInt() else 0
        return HostMessage.Status(phase, score, place, secondsLeft, countdown)
    }

    private fun dropAway() {
        away.forEach(seated::remove)
        away.clear()
    }

    companion object {
        const val MAX_PLAYERS = 8
        const val MAX_NAME = 16
        const val COUNTDOWN_SECONDS = 3f
    }
}
