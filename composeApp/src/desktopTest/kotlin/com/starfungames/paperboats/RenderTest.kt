package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RiverView
import com.starfungames.paperboats.ui.Scoreboard
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the shared screens off-screen to PNGs in build/renders, so they can be checked without a window. */
class RenderTest {
    private val out = File("build/renders").apply { mkdirs() }

    private fun save(name: String, width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width, height, content = content).use { scene ->
            val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, name).writeBytes(bytes)
            assertTrue(bytes.size > 1_000)
        }
    }

    @Test
    fun bigScreenMidRace() {
        val players = listOf("Ana", "Ben", "Chen", "Dilan").mapIndexed { i, n -> Player(i + 1, n, i) }
        val race = Race(players, seed = 2026)
        var t = 0f
        while (t < 12f) {
            race.step(1f / 60f, players.associate { it.id to PlayerInput(steer = kotlin.math.sin(t * (it.id + 1) * 0.7f), paddle = ((t * it.id).toInt() % 3 == 0)) })
            t += 1f / 60f
        }
        val snapshot = race.snapshot()
        save("host.png", 1280, 800) {
            Row(Modifier.fillMaxSize()) {
                RiverView(snapshot, Modifier.weight(1f).fillMaxHeight())
                Scoreboard(snapshot, Modifier.width(280.dp).fillMaxHeight())
            }
        }
    }

    @Test
    fun phoneController() {
        save("controller.png", 390, 760) { ControllerPad(colour = 2, status = "Ben: 2nd, 6 lights", onInput = {}, modifier = Modifier.fillMaxSize()) }
    }

    @Test
    fun lobbyWithQrCode() {
        val players = listOf("Ana", "Ben", "Chen").mapIndexed { i, n -> Player(i + 1, n, i) }
        save("lobby.png", 1280, 800) {
            Box(Modifier.fillMaxSize().background(Palette.night)) { Lobby("http://192.168.1.24:8642/", players) }
        }
    }

    @Test
    fun resultsScreen() {
        val players = listOf("Ana", "Ben", "Chen").mapIndexed { i, n -> Player(i + 1, n, i) }
        val race = Race(players, seed = 4, length = 20f)
        var t = 0f
        while (!race.finished) {
            race.step(1f / 60f, players.associate { it.id to PlayerInput(steer = kotlin.math.sin(t * it.id), paddle = it.id == 2) })
            t += 1f / 60f
        }
        save("results.png", 1280, 800) {
            Box(Modifier.fillMaxSize().background(Palette.night)) { Results(race.snapshot()) }
        }
    }
}

