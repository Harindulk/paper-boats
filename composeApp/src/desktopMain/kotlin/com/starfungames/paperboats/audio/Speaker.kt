package com.starfungames.paperboats.audio

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

private val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)

// Opening a line can take a few milliseconds, so it happens off the frame loop.
private val speaker = Executors.newSingleThreadExecutor { Thread(it, "speaker").apply { isDaemon = true } }

private val pcm16 = mutableMapOf<Cue, ByteArray>()

actual fun play(cue: Cue) {
    speaker.execute {
        val bytes = pcm16.getOrPut(cue) {
            val pcm = samples(cue)
            ByteArray(pcm.size * 2).also { out ->
                pcm.forEachIndexed { i, v ->
                    val s = (v * Short.MAX_VALUE).toInt()
                    out[2 * i] = s.toByte()
                    out[2 * i + 1] = (s shr 8).toByte()
                }
            }
        }
        // A machine with no sound device (a CI runner, a muted VM) just stays quiet.
        runCatching {
            AudioSystem.getClip().apply {
                addLineListener { if (it.type == LineEvent.Type.STOP) close() }
                open(format, bytes, 0, bytes.size)
                start()
            }
        }
    }
}
