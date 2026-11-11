package com.starfungames.paperboats

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.starfungames.paperboats.ui.ControllerApp
import com.starfungames.paperboats.ui.vibrationPattern

/** The page is served by the desktop host, so the game link lives on the same address. */
private fun pageHost(): String = js("window.location.host")

// iOS Safari has no navigator.vibrate, and private modes can refuse storage: none of these may throw.
private fun vibrate(pattern: String): Unit =
    js("{ try { if (navigator.vibrate) navigator.vibrate(pattern.split(',').map(Number)); } catch (e) {} }")

private fun load(key: String): String? = js("{ try { return localStorage.getItem(key); } catch (e) { return null; } }")

private fun store(key: String, value: String): Unit = js("{ try { localStorage.setItem(key, value); } catch (e) {} }")

private fun hideLoading(): Unit = js("{ var el = document.getElementById('loading'); if (el) el.remove(); }")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ControllerApp(
            hostUrl = "ws://${pageHost()}/ws",
            buzz = { vibrate(vibrationPattern(it).joinToString(",")) },
            savedName = load("name").orEmpty(),
            saveSeat = { name, id ->
                store("name", name)
                store("seat", id.toString())
            },
            savedSeatId = load("seat")?.toIntOrNull(),
        )
    }
    hideLoading()
}
