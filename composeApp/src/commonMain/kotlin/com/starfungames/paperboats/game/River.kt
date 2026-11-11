package com.starfungames.paperboats.game

/** The rivers a party rotates through, one per race. Each sets how often a stretch of river gets each hazard. */
enum class River(val title: String, val rockChance: Float, val logChance: Float = 0f, val whirlpoolChance: Float = 0f) {
    LanternCreek("Lantern Creek", rockChance = 0.75f),
    LogjamBend("Logjam Bend", rockChance = 0.3f, logChance = 0.6f),
    WhirlpoolNarrows("Whirlpool Narrows", rockChance = 0.35f, whirlpoolChance = 0.5f),
}
