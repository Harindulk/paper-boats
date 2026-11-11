package com.starfungames.paperboats.game

/** A fun title on the results screen for something a player did during the race. */
data class Award(val playerId: Int, val title: String, val line: String)

/**
 * Up to three awards from a race's [events]: Pirate (lanterns stolen by ramming), Shipwreck (bumps taken from
 * rocks, logs and rams) and Golden hour (points from golden-hour lanterns). Each player wins at most one,
 * only a stat above zero counts, and ties go to the better place in [standings].
 */
fun awards(events: List<RaceEvent>, standings: List<BoatView>): List<Award> {
    val steals = events.filterIsInstance<Rammed>().filter { it.stole }.groupingBy { it.by }.eachCount()
    val bumps = events.filter { it is RockHit || it is LogHit || it is Rammed }.groupingBy { it.playerId }.eachCount()
    val golden = events.filterIsInstance<LightCollected>().filter { it.value > 1 }.groupBy { it.playerId }
        .mapValues { (_, lights) -> lights.sumOf { it.value } }
    val stats = listOf(
        Triple("Pirate", steals) { n: Int -> "Stole ${if (n == 1) "1 lantern" else "$n lanterns"} by ramming" },
        Triple("Shipwreck", bumps) { n: Int -> if (n == 1) "Bumped once" else "Bumped $n times" },
        Triple("Golden hour", golden) { n: Int -> "Scored $n in the last ${Race.GOLDEN_HOUR_SECONDS.toInt()} seconds" },
    )
    val awarded = mutableSetOf<Int>()
    return stats.mapNotNull { (title, counts, line) ->
        // maxBy keeps the first of equal values, and standings are in place order, so ties go to the better place.
        standings.map { it.player.id }.filter { it !in awarded && (counts[it] ?: 0) > 0 }.maxByOrNull { counts.getValue(it) }
            ?.let { id ->
                awarded += id
                Award(id, title, line(counts.getValue(id)))
            }
    }
}
