package com.starfungames.paperboats.android

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.starfungames.paperboats.protocol.BuzzKind
import com.starfungames.paperboats.ui.ControllerApp
import com.starfungames.paperboats.ui.Palette
import com.starfungames.paperboats.ui.TypedHostController
import com.starfungames.paperboats.ui.vibrationPattern

/** The Android controller: the shared phone UI, plus a typed host address, saved prefs and the vibrator. */
class ControllerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("controller", Context.MODE_PRIVATE)
        val vibrator = getSystemService(Vibrator::class.java)
        setContent {
            Box(Modifier.fillMaxSize().background(Palette.night).windowInsetsPadding(WindowInsets.safeDrawing)) {
                TypedHostController(
                    savedAddress = prefs.getString("address", "").orEmpty(),
                    saveAddress = { prefs.edit { putString("address", it) } },
                ) { hostUrl, changeHost ->
                    ControllerApp(
                        hostUrl = hostUrl,
                        buzz = { vibrator?.vibrate(VibrationEffect.createWaveform(vibrationTimings(it), -1)) },
                        savedName = prefs.getString("name", "").orEmpty(),
                        saveSeat = { name, id -> prefs.edit { putString("name", name).putInt("seat", id) } },
                        savedSeatId = prefs.getInt("seat", -1).takeIf { it >= 0 },
                        changeHost = changeHost,
                    )
                }
            }
        }
    }
}

/** Android waveforms start with an off gap, so the shared on/off pattern gets a zero in front. */
fun vibrationTimings(kind: BuzzKind): LongArray = longArrayOf(0) + vibrationPattern(kind).map { it.toLong() }
