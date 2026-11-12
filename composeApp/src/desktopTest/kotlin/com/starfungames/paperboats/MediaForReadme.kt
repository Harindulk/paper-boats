package com.starfungames.paperboats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.PlayerInput
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.game.Rammed
import com.starfungames.paperboats.game.River
import com.starfungames.paperboats.game.awards
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.CountdownScreen
import com.starfungames.paperboats.ui.HostEntry
import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RaceResults
import com.starfungames.paperboats.ui.RaceScreen
import com.starfungames.paperboats.ui.Results
import com.starfungames.paperboats.ui.RiverFx
import com.starfungames.paperboats.ui.SailPad
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the README's race GIF and screenshots into docs/ from a seeded, scripted race.
 * Does nothing unless MEDIA=1: MEDIA=1 ./gradlew :composeApp:desktopTest --tests '*MediaForReadme*'
 */
class MediaForReadme {
    private val docs = File("../docs")
    private val frames = File("build/media-frames")
    private val dt = 1f / 60f
    private val players = listOf("Ana", "Ben", "Chen", "Dilan").mapIndexed { i, n -> Player(i + 1, n, i, SampleSails.all[i]) }

    @Test
    fun media() {
        if (System.getenv("MEDIA") != "1") return
        docs.mkdirs()
        race()
        stills()
    }

    private fun png(
        name: String,
        width: Int = 1280,
        height: Int = 800,
        settle: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val bytes = renderPng(width, height, settle, content)
        File(docs, name).writeBytes(bytes)
        assertTrue(bytes.size > 1_000)
    }

    /** Steers each boat at the nearest lantern ahead (leaning to its own lane so the boats spread out) and paddles on a per-player beat; Ben turns pirate in [RAM_FROM, RAM_TO]. */
    private fun inputs(snapshot: RaceSnapshot, step: Int): Map<Int, PlayerInput> = snapshot.boats.associate { boat ->
        val id = boat.player.id
        val lane = Race.laneX(id - 1, snapshot.boats.size)
        val pirate = id == 2 && snapshot.elapsed in RAM_FROM..RAM_TO
        val targetX = if (pirate) {
            snapshot.boats.filter { it !== boat }.minBy { abs(it.x - boat.x) + abs(it.y - boat.y) }.x
        } else {
            snapshot.lights.filter { it.y in boat.y + 0.03f..boat.y + 0.6f }.minByOrNull {
                abs(it.x - boat.x) * 2 + abs(it.x - lane) * 2 +
                    (it.y - boat.y)
            }?.x
                ?: lane
        }
        val paddle = if (pirate) step % 20 < 10 else step / (14 + id * 5) % 4 == 0
        id to PlayerInput(steer = ((targetX - boat.x) * 10f).coerceIn(-1f, 1f), paddle = paddle)
    }

    private fun race() {
        frames.deleteRecursively()
        frames.mkdirs()
        val race = Race(players, seed = SEED, length = ROUND_SECONDS, river = River.LogjamBend)
        val fx = RiverFx()
        val events = mutableListOf<RaceEvent>()
        var step = 0
        var frame = 0
        var busiest = -1f
        var goldenShot = false
        while (!race.finished) {
            race.step(dt, inputs(race.snapshot(), step))
            val now = race.drainEvents()
            events += now
            fx.onEvents(now)
            val snapshot = race.snapshot()
            fx.step(dt, snapshot)
            step++
            val t = snapshot.elapsed
            if (!goldenShot && t >= GOLDEN_STILL_AT) {
                goldenShot = true
                png("golden-hour.png") { RaceScreen(snapshot, fx, fx.time) }
            }
            // The busiest moment before the clip starts becomes the mid-race still.
            val busy = fx.pops.size * 3f + fx.camera.trauma * 6f + fx.particles.live.size / 40f
            if (t in 1.5f..GOLDEN_FROM - 0.2f && step % 4 == 0 && busy > busiest) {
                busiest = busy
                png("race.png") { RaceScreen(snapshot, fx, fx.time) }
            }
            if (t >= CLIP_FROM && step % 4 == 0) frame(frame++) { RaceScreen(snapshot, fx, fx.time) }
        }
        val snapshot = race.snapshot()
        val prizes = awards(events, snapshot.standings)
        println(
            "media rams=${events.count {
                it is Rammed
            }} awards=$prizes scores=${snapshot.standings.map { it.player.name to it.score }}",
        )
        fx.finish(snapshot.standings.first())
        repeat((RESULTS_SECONDS * 15).toInt()) {
            repeat(4) { fx.step(dt, null) }
            frame(frame++) { Results(snapshot, fx, fx.time, prizes) }
        }
        repeat(60) { fx.step(dt, null) } // the awards have all landed by now
        png("results.png") { Results(snapshot, fx, fx.time, prizes) }
        gif()
        frames.deleteRecursively()
    }

    private fun frame(index: Int, content: @androidx.compose.runtime.Composable () -> Unit) =
        File(frames, "f%04d.png".format(index)).writeBytes(renderPng(1280, 800, content = content))

    private fun gif() {
        val ffmpeg = if (File("/opt/homebrew/bin/ffmpeg").exists()) "/opt/homebrew/bin/ffmpeg" else "ffmpeg"
        val filter = "fps=15,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];" +
            "[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle"
        val process = ProcessBuilder(
            ffmpeg, "-y", "-loglevel", "error", "-framerate", "15", "-i", File(frames, "f%04d.png").path,
            "-filter_complex", filter, File(docs, "race.gif").path,
        ).inheritIO().start()
        check(process.waitFor() == 0) { "ffmpeg failed" }
    }

    private fun stills() {
        val lobbyFx = RiverFx()
        lobbyFx.noteArrivals(players.take(3))
        repeat(60) { lobbyFx.step(dt, null) }
        lobbyFx.noteArrivals(players)
        repeat(12) { lobbyFx.step(dt, null) } // Dilan is still dropping in
        png("lobby.png") {
            Box(Modifier.fillMaxSize().background(Palette.night)) {
                Lobby("http://192.168.1.24:8642/", players, lobbyFx, lobbyFx.time) { url, modifier -> QrCode(url, modifier) }
            }
        }
        val countdownFx = RiverFx().apply { step(0.5f, null) }
        png("countdown.png") { CountdownScreen(players, 2.85f, countdownFx, countdownFx.time, raceNumber = 2, river = River.LogjamBend) }
        png("phone-sail.png", 390, 844, settle = true) { SailPad(colour = 1, initial = SampleSails.wave, onDone = {}) }
        png("phone-pad.png", 390, 844, settle = true) {
            ControllerPad(colour = 1, status = "7 lanterns · 9 s left", onInput = {}, modifier = Modifier.fillMaxSize(), place = "1st")
        }
        png("phone-results.png", 390, 844, settle = true) {
            RaceResults(
                1,
                HostMessage.Status(Phase.Results, score = 9, place = 1, secondsLeft = 0, award = "Pirate. Stole 2 lanterns by ramming."),
            )
        }
        png("android-address.png", 390, 844, settle = true) { HostEntry("192.168.1.24:8642", onAddress = {}, onConnect = {}) }
    }

    private companion object {
        const val SEED = 2026L
        const val ROUND_SECONDS = 14f

        /** The clip runs from here to the finish, all of it golden hour, then [RESULTS_SECONDS] of the finish. */
        const val CLIP_FROM = 8f
        const val RESULTS_SECONDS = 2.2f
        const val GOLDEN_FROM = ROUND_SECONDS - Race.GOLDEN_HOUR_SECONDS
        const val GOLDEN_STILL_AT = GOLDEN_FROM + 0.4f
        const val RAM_FROM = 9f
        const val RAM_TO = 11f
    }
}
