package com.starfungames.paperboats

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** A QR code drawn straight onto a Compose canvas: dark modules on white, with a quiet zone. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    Canvas(modifier) {
        val cell = minOf(size.width, size.height) / matrix.width
        drawRect(Color.White, size = Size(cell * matrix.width, cell * matrix.height))
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color(0xFF0B1A24), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
