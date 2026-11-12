package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.starfungames.paperboats.game.Award
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.River
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.CountdownScreen
import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RaceScreen
import com.starfungames.paperboats.ui.Results
import com.starfungames.paperboats.ui.RiverFx
import java.io.File
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the shared screens off-screen to PNGs in build/renders, so they can be checked without a window. */
class RenderTest {
    private val out = File("build/renders").apply { mkdirs() }
    private val dt = 1f / 60f

    private fun save(name: String, width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val bytes = renderPng(width, height, content = content)
        File(out, name).writeBytes(bytes)
        assertTrue(bytes.size > 1_000)
    }

    private fun players(vararg names: String) = names.mapIndexed { i, n -> Player(i + 1, n, i, SampleSails.all[i % SampleSails.all.size]) }

    private fun inputs(players: List<Player>, t: Float) =
        players.associate { it.id to PlayerInput(steer = sin(t * (it.id + 1) * 0.7f), paddle = ((t * 3 * it.id).toInt() % 4 == 0)) }

    @Test
    fun bigScreenMidRace() {
        val players = players("Ana", "Ben", "Chen", "Dilan")
        val race = Race(players, seed = 2026)
        val fx = RiverFx()
        var t = 0f
        var sinceLight = 99f
        // Run past the opening, then stop just after a light is picked up so the burst is on screen.
        while (t < 8f || sinceLight !in 0.1f..0.14f) {
            race.step(dt, inputs(players, t))
            val events = race.drainEvents()
            if (events.any { it is LightCollected }) sinceLight = 0f else sinceLight += dt
            fx.onEvents(events)
            fx.step(dt, race.snapshot())
            t += dt
        }
        val snapshot = race.snapshot()
        save("host.png", 1280, 800) { RaceScreen(snapshot, fx, fx.time) }
    }

    /** Runs a four-boat race on [river] for [seconds] with juice, and saves the frame. */
    private fun raceFrame(name: String, river: River, seconds: Float, length: Float = Race.DEFAULT_LENGTH_SECONDS) {
        val players = players("Ana", "Ben", "Chen", "Dilan")
        val race = Race(players, seed = 2026, length = length, river = river)
        val fx = RiverFx()
        var t = 0f
        while (t < seconds) {
            race.step(dt, inputs(players, t))
            fx.onEvents(race.drainEvents())
            fx.step(dt, race.snapshot())
            t += dt
        }
        val snapshot = race.snapshot()
        save(name, 1280, 800) { RaceScreen(snapshot, fx, fx.time) }
    }

    @Test
    fun everyRiverMidRace() = River.entries.forEach { raceFrame("river-${it.name}.png", it, 9f) }

    @Test
    fun goldenHourBannerAndGoldLanterns() = raceFrame("golden-hour.png", River.LanternCreek, 10.35f, length = 20f)

    @Test
    fun countdownPopsOnTheRiver() {
        val fx = RiverFx().apply { step(0.5f, null) }
        save("countdown.png", 1280, 800) {
            CountdownScreen(players("Ana", "Ben", "Chen"), 2.85f, fx, fx.time, raceNumber = 3, river = River.WhirlpoolNarrows)
        }
    }

    @Test
    fun phoneController() {
        save("controller.png", 390, 760) {
            ControllerPad(colour = 2, status = "Ben: 2nd, 6 lights", onInput = {}, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun lobbyWithQrCode() {
        val players = players("Ana", "Ben", "Chen")
        val fx = RiverFx()
        fx.noteArrivals(players.take(2))
        repeat(60) { fx.step(dt, null) }
        fx.noteArrivals(players)
        repeat(12) { fx.step(dt, null) } // Chen is still dropping in
        save("lobby.png", 1280, 800) {
            Box(Modifier.fillMaxSize().background(Palette.night)) {
                Lobby("http://192.168.1.24:8642/", players, fx, fx.time) { url, modifier -> QrCode(url, modifier) }
            }
        }
    }

    @Test
    fun lobbySailLandsWithASquash() {
        val players = players("Ana", "Ben", "Chen")
        val fx = RiverFx()
        fx.noteArrivals(players.map { it.copy(sail = if (it.id == 2) emptyList() else it.sail) })
        repeat(90) { fx.step(dt, null) }
        fx.noteArrivals(players) // Ben just pressed "Set sail"
        repeat(5) { fx.step(dt, null) }
        save("lobby-sail-lands.png", 1280, 800) {
            Box(Modifier.fillMaxSize().background(Palette.night)) {
                Lobby("http://192.168.1.24:8642/", players, fx, fx.time) { url, modifier -> QrCode(url, modifier) }
            }
        }
    }

    @Test
    fun resultsScreen() {
        val players = players("Ana", "Ben", "Chen")
        val race = Race(players, seed = 4, length = 20f)
        var t = 0f
        while (!race.finished) {
            race.step(dt, players.associate { it.id to PlayerInput(steer = sin(t * it.id), paddle = it.id == 2) })
            t += dt
        }
        val snapshot = race.snapshot()
        val fx = RiverFx()
        fx.finish(snapshot.standings.first())
        repeat(84) { fx.step(dt, null) }
        save("results.png", 1280, 800) { Results(snapshot, fx, fx.time) }
        // Long enough for the awards to land after the standings.
        repeat(150) { fx.step(dt, null) }
        val awards = listOf(
            Award(3, "Pirate", "Stole 3 lanterns by ramming"),
            Award(1, "Shipwreck", "Bumped 6 times"),
            Award(2, "Golden hour", "Scored 8 in the last 10 seconds"),
        )
        save("results-awards.png", 1280, 800) { Results(snapshot, fx, fx.time, awards) }
    }
}
