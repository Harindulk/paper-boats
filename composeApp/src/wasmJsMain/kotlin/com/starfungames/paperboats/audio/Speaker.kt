package com.starfungames.paperboats.audio

// Browsers start audio suspended until a tap; the Join tap comes before any sound, and resume() covers late starts.
private fun audioContext(): JsAny? = js(
    "{ try { if (!window.pbAudio) window.pbAudio = new (window.AudioContext || window.webkitAudioContext)(); return window.pbAudio; } catch (e) { return null; } }",
)

private fun newBuffer(context: JsAny, length: Int, rate: Int): JsAny = js("context.createBuffer(1, length, rate)")

private fun setSample(buffer: JsAny, i: Int, value: Float): Unit = js("buffer.getChannelData(0)[i] = value")

private fun start(context: JsAny, buffer: JsAny): Unit = js(
    "{ try { if (context.state === 'suspended') context.resume(); var s = context.createBufferSource(); s.buffer = buffer; s.connect(context.destination); s.start(); } catch (e) {} }",
)

private val buffers = mutableMapOf<Cue, JsAny>()

actual fun play(cue: Cue) {
    val context = audioContext() ?: return
    val buffer = buffers.getOrPut(cue) {
        val pcm = samples(cue)
        newBuffer(context, pcm.size, SAMPLE_RATE).also { b -> pcm.forEachIndexed { i, v -> setSample(b, i, v) } }
    }
    start(context, buffer)
}
