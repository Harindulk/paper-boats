package com.starfungames.paperboats

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders [content] off-screen to PNG bytes. With [settle], a second of frames runs first, since
 * animations start on the first frame after launch and the pops need time to land.
 */
fun renderPng(width: Int, height: Int, settle: Boolean = false, content: @Composable () -> Unit): ByteArray =
    ImageComposeScene(width, height, content = content).use { scene ->
        if (settle) for (ms in 0L..1_000L step 100L) scene.render(ms * 1_000_000L)
        scene.render(if (settle) 2_000_000_000L else 0L).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
