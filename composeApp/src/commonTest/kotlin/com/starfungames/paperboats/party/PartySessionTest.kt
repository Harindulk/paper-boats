package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.protocol.ControllerMessage
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.protocol.ProtocolJson
import com.starfungames.paperboats.ui.describe
import com.starfungames.paperboats.ui.ordinal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartySessionTest {
    @Test
    fun playersGetTheFirstFreeColourAndAFallbackName() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        val ben = party.join("  ")!!
        assertEquals(0, ana.colour)
        assertEquals(1, ben.colour)
        assertEquals("Player 2", ben.name)
        party.leave(ana.id)
        assertEquals(0, party.join("Chen")!!.colour, "a freed colour is reused")
    }

    @Test
    fun theNinthPlayerIsTurnedAway() {
        val party = PartySession()
        repeat(PartySession.MAX_PLAYERS) { assertTrue(party.join("p$it") != null) }
        assertNull(party.join("one too many"))
    }

    @Test
    fun aRoundGoesFromLobbyToRaceToResultsAndAgain() {
        val party = PartySession(roundSeconds = 2f)
        assertTrue(!party.canStart(), "no race without players")
        val ana = party.join("Ana")!!
        party.start()
        assertEquals(Phase.Racing, party.phase)
        assertFailsWith<IllegalStateException> { party.start() }
        party.input(ana.id, PlayerInput(steer = 1f, paddle = true))
        repeat(150) { party.tick(1f / 60f) }
        assertEquals(Phase.Results, party.phase)
        assertEquals(1, party.statusFor(ana.id).place)
        party.start()
        assertEquals(Phase.Racing, party.phase)
    }

    @Test
    fun inputsFromStrangersAreIgnored() {
        val party = PartySession()
        party.join("Ana")
        party.start()
        party.input(99, PlayerInput(steer = 1f))
        party.tick(0.1f)
        assertEquals(listOf(1), party.snapshot()!!.boats.map { it.player.id })
    }

    @Test
    fun statusLinesReadNaturally() {
        assertEquals("2nd, 5 lights, 12 s", describe(HostMessage.Status(Phase.Racing, score = 5, place = 2, secondsLeft = 12)))
        assertEquals("Finished 1st with 1 light", describe(HostMessage.Status(Phase.Results, score = 1, place = 1, secondsLeft = 0)))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
    }

    @Test
    fun messagesRoundTripAsCompactJson() {
        val input: ControllerMessage = ControllerMessage.Input(steer = -0.5f, paddle = true)
        val json = ProtocolJson.encodeToString(ControllerMessage.serializer(), input)
        assertEquals("""{"type":"input","steer":-0.5,"paddle":true}""", json)
        assertEquals(input, ProtocolJson.decodeFromString(ControllerMessage.serializer(), json))
        val welcome: HostMessage = HostMessage.Welcome(playerId = 3, colour = 2)
        assertEquals(welcome, ProtocolJson.decodeFromString(HostMessage.serializer(), ProtocolJson.encodeToString(HostMessage.serializer(), welcome)))
    }

    @Test
    fun steeringFromAControllerIsClamped() {
        assertEquals(PlayerInput(1f, false), ControllerMessage.Input(steer = 7f, paddle = false).toPlayerInput())
    }
}
