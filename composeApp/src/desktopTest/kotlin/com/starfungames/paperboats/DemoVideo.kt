package com.starfungames.paperboats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.audio.Cue
import com.starfungames.paperboats.audio.SAMPLE_RATE
import com.starfungames.paperboats.audio.cueFor
import com.starfungames.paperboats.audio.samples
import com.starfungames.paperboats.game.LightCollected
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceEvent
import com.starfungames.paperboats.game.River
import com.starfungames.paperboats.game.awards
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.Countdown
import com.starfungames.paperboats.ui.CountdownScreen
import com.starfungames.paperboats.ui.Lobby
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.RaceResults
import com.starfungames.paperboats.ui.RaceScreen
import com.starfungames.paperboats.ui.Results
import com.starfungames.paperboats.ui.RiverFx
import com.starfungames.paperboats.ui.SailPad
import com.starfungames.paperboats.ui.ordinal
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.test.Test

/**
 * Renders docs/demo.mp4: a seeded party from title to results with the big screen and Ana's phone side by side,
 * captions, and the game's own synthesised sounds on the soundtrack. Every frame is drawn by the real game screens.
 * Does nothing unless DEMO=1: DEMO=1 ./gradlew :composeApp:desktopTest --tests '*DemoVideo*'
 */
class DemoVideo {
    private val frames = File("build/demo-frames")
    private val sounds = mutableListOf<Pair<Float, Cue>>()
    private val phones = mutableMapOf<String, ImageBitmap>()
    private val players = listOf("Ana", "Ben", "Chen", "Dilan").mapIndexed { i, n -> Player(i + 1, n, i, SampleSails.all[i]) }
    private val ana = players.first()
    private var frame = 0
    private val clock get() = frame / FPS.toFloat()

    @Test
    fun demo() {
        if (System.getenv("DEMO") != "1") return
        frames.deleteRecursively()
        frames.mkdirs()
        title()
        lobby()
        val fx = RiverFx()
        countdown(fx)
        race(fx)
        ending()
        encode(writeSoundtrack())
        frames.deleteRecursively()
    }

    private fun title() = repeat((TITLE_SECONDS * FPS).toInt()) {
        shot {
            Card(
                "Paper Boats",
                "A couch party race. The TV is the river, every phone is a paddle.",
                "Kotlin Multiplatform: desktop · web (Kotlin/Wasm) · Android",
            )
        }
    }

    private fun lobby() {
        val fx = RiverFx()
        val sail = ana.sail
        val points = sail.sumOf { it.size / 2 }
        val steps = (LOBBY_SECONDS * FPS).toInt()
        repeat(steps) { i ->
            val t = i / FPS.toFloat()
            fx.noteArrivals(players.take(1 + (t / 1.4f).toInt().coerceAtMost(3)))
            repeat(2) { fx.step(1f / 60f, null) }
            // Ana draws her sail stroke by stroke over the first two thirds of the shot.
            val drawn = ((t / (LOBBY_SECONDS * 0.66f)).coerceAtMost(1f) * points).toInt()
            val partial = prefix(sail, drawn)
            val phone = phone("sail-$drawn") { SailPad(colour = ana.colour, initial = partial, onDone = {}) }
            shot("Scan the QR code on the TV, type a name, then draw your own sail.", phone) {
                Box(Modifier.fillMaxSize().background(Palette.night)) {
                    Lobby("http://192.168.1.24:8642/", players, fx, fx.time) { url, modifier -> QrCode(url, modifier) }
                }
            }
        }
    }

    private fun countdown(fx: RiverFx) {
        fx.newRace()
        val steps = (COUNTDOWN_SECONDS * FPS).toInt()
        var shown = 0
        repeat(steps) { i ->
            val left = COUNTDOWN_SECONDS - i / FPS.toFloat()
            val number = ceil(left).toInt()
            if (number != shown) {
                shown = number
                sounds += clock to Cue.Tick
            }
            repeat(2) { fx.step(1f / 60f, null) }
            val phone = phone("count-$number") { Countdown(ana.colour, number.toString()) }
            shot("Three rivers take turns: rocks, drifting logs, whirlpools.", phone) {
                CountdownScreen(players, left, fx, fx.time, raceNumber = 2, river = River.WhirlpoolNarrows)
            }
        }
    }

    private fun race(fx: RiverFx) {
        sounds += clock to Cue.Go
        val race = Race(players, seed = SEED, length = RACE_SECONDS, river = River.WhirlpoolNarrows)
        val events = mutableListOf<RaceEvent>()
        var step = 0
        while (!race.finished) {
            race.step(1f / 60f, botInputs(race.snapshot(), step, RAM_FROM..RAM_TO))
            val now = race.drainEvents()
            events += now
            fx.onEvents(now)
            val snapshot = race.snapshot()
            fx.step(1f / 60f, snapshot)
            if (step++ % 2 == 1) continue
            now.forEach { sounds += clock to cueFor(it) }
            val boat = snapshot.boats.first { it.player.id == ana.id }
            val place = snapshot.standings.indexOf(boat) + 1
            val secs = ceil(snapshot.timeLeft).toInt()
            val status = "${boat.score} lanterns · $secs s left"
            val phone = phone("pad-$place-$status") {
                ControllerPad(colour = ana.colour, status = status, onInput = {}, modifier = Modifier.fillMaxSize(), place = ordinal(place))
            }
            val t = snapshot.elapsed
            val caption = when {
                t < 7f -> "Left thumb steers, right thumb paddles. Catch the floating lanterns."
                t in RAM_FROM - 0.5f..RAM_TO + 1.5f -> "Paddle into a boat to spin it out and steal a lantern."
                snapshot.goldenHour -> "Golden hour: in the last 10 seconds every lantern counts double."
                t in 34f..44f -> "Whirlpools pull a boat in and slow it down. Steer around them."
                else -> "The phone buzzes and chimes on every lantern and every bump."
            }
            shot(caption, phone) { RaceScreen(snapshot, fx, fx.time) }
        }
        sounds += clock to Cue.Finish
        val snapshot = race.snapshot()
        val prizes = awards(events, snapshot.standings)
        val boat = snapshot.boats.first { it.player.id == ana.id }
        val award = prizes.firstOrNull { it.playerId == ana.id }?.let { "${it.title}. ${it.line}." }
        val status = HostMessage.Status(
            Phase.Results,
            score = boat.score,
            place = snapshot.standings.indexOf(boat) + 1,
            secondsLeft = 0,
            award = award,
        )
        val phone = phone("results") { RaceResults(ana.colour, status) }
        println("demo awards=$prizes golden=${events.count { it is LightCollected && it.value > 1 }}")
        fx.finish(snapshot.standings.first())
        repeat((RESULTS_SECONDS * FPS).toInt()) {
            repeat(2) { fx.step(1f / 60f, null) }
            shot("Awards for the pirate, the shipwreck and the golden-hour hero. Then rematch.", phone) {
                Results(snapshot, fx, fx.time, prizes)
            }
        }
    }

    private fun ending() = repeat((END_SECONDS * FPS).toInt()) {
        shot {
            Card(
                "One Kotlin codebase",
                "Race rules, protocol, screens, effects and sound live in commonMain.",
                "Desktop host · Kotlin/Wasm phone page · Android app",
            )
        }
    }

    /** The first [points] points of a drawing, keeping whole strokes before the one being drawn. */
    private fun prefix(sail: List<List<Float>>, points: Int): List<List<Float>> {
        var left = points
        return sail.mapNotNull { stroke ->
            val take = minOf(left, stroke.size / 2)
            left -= take
            if (take >= 2) stroke.take(take * 2) else null
        }
    }

    /** A phone screen, rendered settled (so its pop-in animations have landed) once per distinct state. */
    private fun phone(key: String, content: @Composable () -> Unit): ImageBitmap = phones.getOrPut(key) {
        org.jetbrains.skia.Image.makeFromEncoded(renderPng(PHONE_W, PHONE_H, settle = true, content = content)).toComposeImageBitmap()
    }

    /** One 1920×1080 frame: the TV on the left, Ana's phone on the right, a caption under the TV. */
    private fun shot(caption: String? = null, phone: ImageBitmap? = null, tv: @Composable () -> Unit) {
        val png = renderPng(1920, 1080) {
            Box(Modifier.fillMaxSize().background(Backdrop)) {
                if (phone == null) {
                    tv()
                    return@Box
                }
                Box(Modifier.offset(48.dp, 56.dp).size(1360.dp, 850.dp).clip(RoundedCornerShape(18.dp))) { tv() }
                Box(
                    Modifier.offset(
                        1472.dp,
                        56.dp,
                    ).size((PHONE_W + 16).dp, (PHONE_H + 16).dp).clip(RoundedCornerShape(52.dp)).background(Bezel).padding(8.dp),
                ) {
                    Image(phone, null, Modifier.fillMaxSize().clip(RoundedCornerShape(44.dp)))
                }
                if (caption != null) BasicText(caption, Modifier.offset(48.dp, 948.dp), style = CaptionStyle)
            }
        }
        File(frames, "f%05d.png".format(frame++)).writeBytes(png)
    }

    /** Mixes every cue at its time over a quiet river murmur, as 16-bit mono WAV. */
    private fun writeSoundtrack(): File {
        val length = (clock * SAMPLE_RATE).toInt() + SAMPLE_RATE
        val mix = FloatArray(length)
        val random = Random(3)
        var low = 0f
        for (i in mix.indices) {
            low += 0.02f * (random.nextFloat() * 2f - 1f - low)
            mix[i] = low * 0.6f
        }
        for ((at, cue) in sounds) {
            val start = (at * SAMPLE_RATE).toInt()
            samples(cue).forEachIndexed { i, v -> if (start + i < length) mix[start + i] += v * 0.7f }
        }
        val pcm = ByteArrayOutputStream()
        for (v in mix) {
            val s = (v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            pcm.write(s and 0xFF)
            pcm.write(s shr 8 and 0xFF)
        }
        val file = File("build/demo.wav")
        DataOutputStream(file.outputStream()).use { out ->
            fun le32(v: Int) = out.writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            out.writeBytes("RIFF")
            le32(36 + pcm.size())
            out.writeBytes("WAVEfmt ")
            le32(16)
            le16(1)
            le16(1)
            le32(SAMPLE_RATE)
            le32(SAMPLE_RATE * 2)
            le16(2)
            le16(16)
            out.writeBytes("data")
            le32(pcm.size())
            pcm.writeTo(out)
        }
        return file
    }

    private fun encode(audio: File) {
        val ffmpeg = if (File("/opt/homebrew/bin/ffmpeg").exists()) "/opt/homebrew/bin/ffmpeg" else "ffmpeg"
        val process = ProcessBuilder(
            ffmpeg, "-y", "-loglevel", "error", "-framerate", "$FPS", "-i", File(frames, "f%05d.png").path, "-i", audio.path,
            "-c:v", "libx264", "-preset", "slow", "-crf", "24", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
            "-shortest", "-movflags", "+faststart", File("../docs/demo.mp4").path,
        ).inheritIO().start()
        check(process.waitFor() == 0) { "ffmpeg failed" }
    }

    private companion object {
        const val FPS = 30
        const val SEED = 2026L
        const val TITLE_SECONDS = 3.5f
        const val LOBBY_SECONDS = 7f
        const val COUNTDOWN_SECONDS = 3f
        const val RACE_SECONDS = Race.DEFAULT_LENGTH_SECONDS
        const val RESULTS_SECONDS = 6f
        const val END_SECONDS = 5f
        const val RAM_FROM = 22f
        const val RAM_TO = 26f
        const val PHONE_W = 390
        const val PHONE_H = 844
        val Backdrop = Color(0xFF07090F)
        val Bezel = Color(0xFF1B1E27)
        val CaptionStyle = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(title: String, line: String, footer: String) {
    Column(
        Modifier.fillMaxSize().background(Palette.night).padding(120.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        BasicText(title, style = TextStyle(color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp))
        BasicText(line, style = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.SemiBold))
        BasicText(footer, style = TextStyle(color = Color(0xFFB8BECC), fontSize = 32.sp))
    }
}
