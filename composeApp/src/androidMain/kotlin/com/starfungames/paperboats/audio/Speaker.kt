package com.starfungames.paperboats.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual fun play(cue: Cue) {
    val pcm = samples(cue)
    runCatching {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 4)
            .build()
        track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        track.notificationMarkerPosition = pcm.size
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()

                override fun onPeriodicNotification(t: AudioTrack) = Unit
            },
        )
        track.play()
    }
}
