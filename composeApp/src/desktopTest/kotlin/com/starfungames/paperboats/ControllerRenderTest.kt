package com.starfungames.paperboats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.starfungames.paperboats.protocol.HostMessage
import com.starfungames.paperboats.protocol.HostMessage.Phase
import com.starfungames.paperboats.ui.ControllerPad
import com.starfungames.paperboats.ui.Countdown
import com.starfungames.paperboats.ui.HostEntry
import com.starfungames.paperboats.ui.LostHost
import com.starfungames.paperboats.ui.NameEntry
import com.starfungames.paperboats.ui.RaceResults
import com.starfungames.paperboats.ui.SailPad
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders each phone screen at iPhone size to build/renders/phone-*.png, after the pops have settled. */
class ControllerRenderTest {
    private val out = File("build/renders").apply { mkdirs() }

    private fun save(name: String, content: @Composable () -> Unit) {
        val bytes = renderPng(390, 844, settle = true, content = content)
        File(out, "phone-$name.png").writeBytes(bytes)
        assertTrue(bytes.size > 1_000)
    }

    @Test
    fun nameEntry() = save("name") { NameEntry("Harindu", onName = {}, onJoin = {}) }

    @Test
    fun drawYourSail() = save("sail") { SailPad(colour = 3, initial = SampleSails.smiley, onDone = {}) }

    @Test
    fun drawYourSailBlank() = save("sail-blank") { SailPad(colour = 0, initial = emptyList(), onDone = {}) }

    @Test
    fun resultsWithRedraw() = save("results-redraw") {
        RaceResults(colour = 2, HostMessage.Status(Phase.Results, score = 4, place = 3, secondsLeft = 0), onRedraw = {})
    }

    @Test
    fun countdown() = save("countdown") { Countdown(colour = 1, text = "3") }

    @Test
    fun padMidRace() = save("pad") {
        ControllerPad(colour = 2, status = "6 lanterns · 42 s left", onInput = {}, modifier = Modifier.fillMaxSize(), place = "2nd")
    }

    @Test
    fun results() = save("results") {
        RaceResults(
            colour = 0,
            HostMessage.Status(Phase.Results, score = 6, place = 2, secondsLeft = 0, award = "Pirate. Stole 3 lanterns by ramming."),
        )
    }

    @Test
    fun lostHost() = save("lost") { LostHost(onRejoin = {}) }

    @Test
    fun lostHostWithATypedAddress() = save("lost-typed") { LostHost(onRejoin = {}, changeHost = {}) }

    @Test
    fun hostEntry() = save("address") { HostEntry("192.168.1.24:8642", onAddress = {}, onConnect = {}) }
}
