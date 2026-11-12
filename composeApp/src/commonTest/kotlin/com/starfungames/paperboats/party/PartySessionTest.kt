package com.starfungames.paperboats.party

import com.starfungames.paperboats.game.Paddled
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.River
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
        assertEquals(
            ControllerMessage.Join("a"),
            ProtocolJson.decodeFromString(ControllerMessage.serializer(), """{"type":"join","name":"a"}"""),
        )
        val rejoin: ControllerMessage = ControllerMessage.Join("a", rejoinId = 4)
        assertEquals(
            rejoin,
            ProtocolJson.decodeFromString(
                ControllerMessage.serializer(),
                ProtocolJson.encodeToString(ControllerMessage.serializer(), rejoin),
            ),
        )
        val buzz: HostMessage = HostMessage.Buzz(BuzzKind.Rock)
        assertEquals("""{"type":"buzz","kind":"Rock"}""", ProtocolJson.encodeToString(HostMessage.serializer(), buzz))
    }

    @Test
    fun statusLinesReadNaturally() {
        assertEquals("2nd, 5 lanterns, 12 s", describe(HostMessage.Status(Phase.Racing, score = 5, place = 2, secondsLeft = 12)))
        assertEquals(
            "Race starts in 3",
            describe(HostMessage.Status(Phase.Countdown, score = 0, place = 0, secondsLeft = 0, countdown = 3)),
        )
        assertEquals("Finished 1st with 1 lantern", describe(HostMessage.Status(Phase.Results, score = 1, place = 1, secondsLeft = 0)))
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal),
        )
    }

    @Test
    fun messagesRoundTripAsCompactJson() {
        val input: ControllerMessage = ControllerMessage.Input(steer = -0.5f, paddle = true)
        val json = ProtocolJson.encodeToString(ControllerMessage.serializer(), input)
        assertEquals("""{"type":"input","steer":-0.5,"paddle":true}""", json)
        assertEquals(input, ProtocolJson.decodeFromString(ControllerMessage.serializer(), json))
        val welcome: HostMessage = HostMessage.Welcome(playerId = 3, colour = 2)
        assertEquals(
            welcome,
            ProtocolJson.decodeFromString(HostMessage.serializer(), ProtocolJson.encodeToString(HostMessage.serializer(), welcome)),
        )
    }

    @Test
    fun steeringFromAControllerIsClamped() {
        assertEquals(PlayerInput(1f, false), ControllerMessage.Input(steer = 7f, paddle = false).toPlayerInput())
    }

    @Test
    fun riversRotateEveryRace() {
        val party = PartySession(roundSeconds = 0.5f)
        party.join("Ana")
        val seen = List(4) {
            party.start()
            val announced = party.river to party.raceNumber
            party.tick(PartySession.COUNTDOWN_SECONDS)
            assertEquals(announced.first, party.snapshot()!!.river, "the countdown announces the river that is raced")
            repeat(40) { party.tick(1f / 60f) }
            announced
        }
        assertEquals(listOf(River.LanternCreek to 1, River.LogjamBend to 2, River.WhirlpoolNarrows to 3, River.LanternCreek to 4), seen)
    }

    /** Ana sweeps the creek on seed 9, the route that hits rocks and picks up lanterns. */
    private fun sweptRace(drainEachTick: Boolean): Pair<PartySession, Int> {
        val party = PartySession(roundSeconds = 20f) { 9L }
        val ana = party.join("Ana")!!
        party.start()
        party.tick(PartySession.COUNTDOWN_SECONDS)
        var t = 0f
        while (party.phase == Phase.Racing) {
            party.input(ana.id, PlayerInput(steer = if ((t / 1.3f).toInt() % 2 == 0) 1f else -1f))
            party.tick(1f / 60f)
            if (drainEachTick) party.drainEvents()
            t += 1f / 60f
        }
        return party to ana.id
    }

    @Test
    fun awardsSurviveTheHostDrainingEvents() {
        val (drained, id) = sweptRace(drainEachTick = true)
        val (kept, _) = sweptRace(drainEachTick = false)
        assertEquals(Phase.Results, drained.phase)
        assertTrue(drained.awards().isNotEmpty(), "Ana hit rocks, so she should be the shipwreck")
        assertEquals(kept.awards(), drained.awards())
        val award = drained.awards().single()
        assertEquals("${award.title}. ${award.line}.", drained.statusFor(id).award)
    }

    @Test
    fun awardsAreOnlyForTheResultsAndStartFreshEachRace() {
        val (party, id) = sweptRace(drainEachTick = true)
        party.start()
        assertTrue(party.awards().isEmpty())
        assertNull(party.statusFor(id).award)
        party.tick(PartySession.COUNTDOWN_SECONDS)
        assertTrue(party.awards().isEmpty())
    }

    @Test
    fun hostStillGetsEveryEventExactlyOnce() {
        val party = PartySession()
        val ana = party.join("Ana")!!
        party.start()
        party.tick(PartySession.COUNTDOWN_SECONDS)
        var paddles = 0
        repeat(30) {
            party.input(ana.id, PlayerInput(paddle = it % 2 == 0))
            party.tick(1f / 60f)
            if (it % 3 == 0) paddles += party.drainEvents().count { e -> e is Paddled }
        }
        paddles += party.drainEvents().count { it is Paddled }
        assertEquals(15, paddles)
    }

    @Test
    fun oldStatusJsonStillDecodes() {
        val old = """{"type":"status","phase":"Results","score":3,"place":1,"secondsLeft":0}"""
        assertEquals(HostMessage.Status(Phase.Results, 3, 1, 0), ProtocolJson.decodeFromString(HostMessage.serializer(), old))
        val withAward: HostMessage = HostMessage.Status(Phase.Results, 3, 1, 0, award = "Pirate. Stole 2 lanterns by ramming.")
        assertEquals(
            withAward,
            ProtocolJson.decodeFromString(HostMessage.serializer(), ProtocolJson.encodeToString(HostMessage.serializer(), withAward)),
        )
    }

    private fun lobbyWithAna(): Pair<PartySession, Int> = PartySession().let { it to it.join("Ana")!!.id }

    @Test
    fun aSailIsKeptAndClampedIntoTheSail() {
        val (party, ana) = lobbyWithAna()
        party.sail(ana, listOf(listOf(0.1f, 0.2f, 0.5f, 0.5f), listOf(-3f, 0.5f, 1.4f, 2f)))
        assertEquals(listOf(listOf(0.1f, 0.2f, 0.5f, 0.5f), listOf(0f, 0.5f, 1f, 1f)), party.players.single().sail)
    }

    @Test
    fun brokenStrokesAreDropped() {
        val (party, ana) = lobbyWithAna()
        val good = listOf(0.3f, 0.3f)
        party.sail(ana, listOf(listOf(0.1f, Float.NaN), listOf(0.1f, 0.2f, 0.3f), emptyList(), listOf(Float.POSITIVE_INFINITY, 0f), good))
        assertEquals(listOf(good), party.players.single().sail)
    }

    @Test
    fun aHugeSailIsCutToTheCap() {
        val (party, ana) = lobbyWithAna()
        val long = List(PartySession.MAX_SAIL_POINTS * 2 + 10) { 0.5f }
        party.sail(ana, List(PartySession.MAX_SAIL_STROKES + 5) { long })
        val sail = party.players.single().sail
        assertEquals(PartySession.MAX_SAIL_STROKES, sail.size)
        assertTrue(sail.all { it.size == PartySession.MAX_SAIL_POINTS * 2 })
    }

    @Test
    fun sailsFromStrangersAreIgnored() {
        val (party, _) = lobbyWithAna()
        party.sail(99, listOf(listOf(0.5f, 0.5f)))
        assertEquals(emptyList(), party.players.single().sail)
    }

    @Test
    fun aSailCanChangeBetweenRacesButNotDuringOne() {
        val party = PartySession(roundSeconds = 1f)
        val ana = party.join("Ana")!!.id
        val star = listOf(listOf(0.5f, 0.1f, 0.6f, 0.9f))
        party.sail(ana, star)
        party.start()
        party.sail(ana, listOf(listOf(0.9f, 0.9f)))
        party.tick(PartySession.COUNTDOWN_SECONDS)
        party.sail(ana, listOf(listOf(0.9f, 0.9f)))
        assertEquals(star, party.players.single().sail, "no redraw in the countdown or the race")
        assertEquals(star, party.snapshot()!!.boats.single().player.sail, "the race carries the sail to the big screen")
        repeat(80) { party.tick(1f / 60f) }
        assertEquals(Phase.Results, party.phase)
        party.sail(ana, emptyList())
        assertEquals(emptyList(), party.players.single().sail, "the results let a player redraw, or wipe it")
    }

    @Test
    fun sailMessagesRoundTripAndOldJoinsAndPlayersStillDecode() {
        val sail: ControllerMessage = ControllerMessage.Sail(listOf(listOf(0.25f, 0.5f, 0.75f, 1f)))
        val json = ProtocolJson.encodeToString(ControllerMessage.serializer(), sail)
        assertEquals(sail, ProtocolJson.decodeFromString(ControllerMessage.serializer(), json))
        assertEquals(
            ControllerMessage.Join("Ana"),
            ProtocolJson.decodeFromString(ControllerMessage.serializer(), """{"type":"join","name":"Ana"}"""),
        )
        assertEquals(Player(1, "Ana", 0), ProtocolJson.decodeFromString(Player.serializer(), """{"id":1,"name":"Ana","colour":0}"""))
    }
}
