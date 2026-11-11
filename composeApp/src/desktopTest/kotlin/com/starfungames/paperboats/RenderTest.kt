package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.use
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.CountdownScreen
import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RaceScreen
import com.starfungames.paperboats.ui.Results
import com.starfungames.paperboats.ui.RiverFx
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the shared screens off-screen to PNGs in build/renders, so they can be checked without a window. */
class RenderTest {
    private val out = File("build/renders").apply { mkdirs() }
    private val dt = 1f / 60f

    private fun save(name: String, width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width, height, content = content).use { scene ->
            val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, name).writeBytes(bytes)
            assertTrue(bytes.size > 1_000)
        }
    }

    private fun players(vararg names: String) = names.mapIndexed { i, n -> Player(i + 1, n, i) }

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

    @Test
    fun countdownPopsOnTheRiver() {
        val fx = RiverFx().apply { step(0.5f, null) }
        save("countdown.png", 1280, 800) { CountdownScreen(players("Ana", "Ben", "Chen"), 2.85f, fx, fx.time) }
    }

    @Test
    fun phoneController() {
        save("controller.png", 390, 760) { ControllerPad(colour = 2, status = "Ben: 2nd, 6 lights", onInput = {}, modifier = Modifier.fillMaxSize()) }
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
    }
}
