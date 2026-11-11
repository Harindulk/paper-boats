package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.protocol.BuzzKind
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
        assertFailsWith<IllegalStateException> { party.start() }
        party.tick(PartySession.COUNTDOWN_SECONDS)
        assertEquals(Phase.Racing, party.phase)
        assertFailsWith<IllegalStateException> { party.start() }
        party.input(ana.id, PlayerInput(steer = 1f, paddle = true))
        repeat(150) { party.tick(1f / 60f) }
        assertEquals(Phase.Results, party.phase)
        assertEquals(1, party.statusFor(ana.id).place)
        party.start()
        assertEquals(Phase.Countdown, party.phase)
    }

    @Test
    fun inputsFromStrangersAreIgnored() {
        val party = PartySession()
        party.join("Ana")
        party.start()
        party.tick(PartySession.COUNTDOWN_SECONDS)
        party.input(99, PlayerInput(steer = 1f))
        party.tick(0.1f)
        assertEquals(listOf(1), party.snapshot()!!.boats.map { it.player.id })
    }

    @Test
    fun startGoesThroughCountdown() {
        val party = PartySession()
        party.join("Ana")
        party.start()
        assertEquals(Phase.Countdown, party.phase)
        assertNull(party.snapshot())
        repeat(3) { party.tick(1f) }
        assertEquals(Phase.Racing, party.phase)
        assertTrue(party.snapshot() != null)
    }

    @Test
    fun statusDuringCountdownCarriesWholeSeconds() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        assertEquals(0, party.statusFor(ana.id).countdown)
        party.start()
        party.tick(0.6f)
        assertEquals(2.4f, party.countdownLeft, 1e-4f)
        assertEquals(3, party.statusFor(ana.id).countdown)
    }

    @Test
    fun rejoinMidRaceGetsSameBoat() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        party.join("Ben")
        party.start()
        party.tick(PartySession.COUNTDOWN_SECONDS)
        party.leave(ana.id)
        assertEquals(2, party.snapshot()!!.boats.size, "the boat stays on the river")
        val back = party.join("x", rejoinId = ana.id)
        assertEquals(ana, back)
        assertEquals(2, party.players.size)
    }

    @Test
    fun rejoinWithUnknownIdSeatsNewPlayer() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        val stranger = party.join("x", rejoinId = 99)!!
        assertTrue(stranger.id != ana.id && stranger.id != 99)
        assertTrue(party.join("y", rejoinId = ana.id)!!.id != ana.id, "a seat that is not away cannot be taken over")
    }

    @Test
    fun everyoneLeavingDuringCountdownReturnsToLobby() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        party.start()
        party.leave(ana.id)
        assertEquals(Phase.Lobby, party.phase)
        assertEquals(emptyList(), party.players)
        party.tick(PartySession.COUNTDOWN_SECONDS)
        assertEquals(Phase.Lobby, party.phase)
    }

    @Test
    fun partyPassesRaceEventsThrough() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        party.start()
        party.tick(PartySession.COUNTDOWN_SECONDS)
        party.input(ana.id, PlayerInput(paddle = true))
        party.tick(1f / 60f)
        assertEquals(1, party.drainEvents().size)
        assertEquals(emptyList(), party.drainEvents())
    }

    @Test
    fun oldJoinDecodesAndBuzzEncodesWithItsType() {
        assertEquals(ControllerMessage.Join("a"), ProtocolJson.decodeFromString(ControllerMessage.serializer(), """{"type":"join","name":"a"}"""))
        val rejoin: ControllerMessage = ControllerMessage.Join("a", rejoinId = 4)
        assertEquals(rejoin, ProtocolJson.decodeFromString(ControllerMessage.serializer(), ProtocolJson.encodeToString(ControllerMessage.serializer(), rejoin)))
        val buzz: HostMessage = HostMessage.Buzz(BuzzKind.Rock)
        assertEquals("""{"type":"buzz","kind":"Rock"}""", ProtocolJson.encodeToString(HostMessage.serializer(), buzz))
    }

    @Test
    fun statusLinesReadNaturally() {
        assertEquals("2nd, 5 lanterns, 12 s", describe(HostMessage.Status(Phase.Racing, score = 5, place = 2, secondsLeft = 12)))
        assertEquals("Race starts in 3", describe(HostMessage.Status(Phase.Countdown, score = 0, place = 0, secondsLeft = 0, countdown = 3)))
        assertEquals("Finished 1st with 1 lantern", describe(HostMessage.Status(Phase.Results, score = 1, place = 1, secondsLeft = 0)))
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
