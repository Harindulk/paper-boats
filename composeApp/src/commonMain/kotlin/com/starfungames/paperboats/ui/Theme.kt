package com.starfungames.paperboats.ui

import androidx.compose.ui.graphics.Color

object Palette {
    val night = Color(0xFF0B1A24)
    val water = Color(0xFF123247)
    val waterLight = Color(0xFF1B4560)
    val bank = Color(0xFF0E2419)
    val rock = Color(0xFF3A4650)
    val light = Color(0xFFFFC46B)
    val paper = Color(0xFFF4F1EA)
    val ink = Color(0xFFF2F5F4)
    val inkSoft = Color(0xFFA9BAC3)

    /** One colour per player seat, far apart on the wheel. */
    val players = listOf(
        Color(0xFFFF8A65), Color(0xFF64B5F6), Color(0xFFAED581), Color(0xFFBA68C8),
        Color(0xFFFFD54F), Color(0xFF4DD0E1), Color(0xFFF06292), Color(0xFFE0E0E0),
    )

    fun player(colour: Int): Color = players[colour.mod(players.size)]
}
