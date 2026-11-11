package com.starfungames.paperboats.ui

import com.starfungames.paperboats.protocol.BuzzKind
import kotlinx.coroutines.delay

/** Vibration in milliseconds (on, off, on…), scaled like the juice: a light is a tick, a rock a thud, a win a double pulse. */
fun vibrationPattern(kind: BuzzKind): List<Int> = when (kind) {
    BuzzKind.Light -> listOf(15)
    BuzzKind.Rock -> listOf(60)
    BuzzKind.Win -> listOf(80, 40, 80)
}

/**
 * Keeps the phone on the host: [attempt] connects and returns once the link ends, saying whether the host welcomed it.
 * After a failed connect or a drop it tries again [retries] times, [gapMs] apart, then returns so the phone can say so.
 */
suspend fun stayConnected(retries: Int = 3, gapMs: Long = 1_000, attempt: suspend () -> Boolean) {
    var misses = 0
    while (true) {
        // A drop after a welcome counts as the first miss, so it gets the same retries as a failed first connect.
        misses = if (attempt()) 1 else misses + 1
        if (misses > retries) return
        delay(gapMs)
    }
}
