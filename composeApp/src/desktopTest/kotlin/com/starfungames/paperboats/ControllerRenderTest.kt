package com.starfungames.paperboats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.use
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.Countdown
import com.starfungames.paperboats.ui.LostHost
import com.starfungames.paperboats.ui.NameEntry
import com.starfungames.paperboats.ui.RaceResults
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders each phone screen at iPhone size to build/renders/phone-*.png, after the pops have settled. */
class ControllerRenderTest {
    private val out = File("build/renders").apply { mkdirs() }

    private fun save(name: String, content: @Composable () -> Unit) {
        ImageComposeScene(390, 844, content = content).use { scene ->
            // Animations start on the first frame after launch, so step a few frames for the pops to settle.
            for (ms in 0L..1_000L step 100L) scene.render(ms * 1_000_000L)
            val bytes = scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "phone-$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 1_000)
        }
    }

    @Test
    fun nameEntry() = save("name") { NameEntry("Harindu", onName = {}, onJoin = {}) }

    @Test
    fun countdown() = save("countdown") { Countdown(colour = 1, text = "3") }

    @Test
    fun padMidRace() = save("pad") {
        ControllerPad(colour = 2, status = "6 lanterns · 42 s left", onInput = {}, modifier = Modifier.fillMaxSize(), place = "2nd")
    }

    @Test
    fun results() = save("results") { RaceResults(colour = 0, HostMessage.Status(Phase.Results, score = 6, place = 2, secondsLeft = 0, award = "Pirate. Stole 3 lanterns by ramming.")) }

    @Test
    fun lostHost() = save("lost") { LostHost(onRejoin = {}) }
}
