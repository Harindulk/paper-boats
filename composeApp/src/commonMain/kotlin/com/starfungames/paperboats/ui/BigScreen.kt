package com.starfungames.paperboats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.game.Award
import com.starfungames.paperboats.game.Player
import com.starfungames.paperboats.game.Race
import com.starfungames.paperboats.game.RaceSnapshot
import com.starfungames.paperboats.game.River
import com.starfungames.paperboats.ui.fx.Ease
import kotlin.math.ceil
import kotlin.math.sin

// Two sizes do the work on every big screen: a title and the body. Soft is the body size in a quieter ink.
private val title = TextStyle(color = Palette.ink, fontSize = 72.sp, fontWeight = FontWeight.SemiBold, lineHeight = 76.sp)
private val body = TextStyle(color = Palette.ink, fontSize = 26.sp, lineHeight = 34.sp)
private val soft = body.copy(color = Palette.inkSoft)
private val awardTitle = TextStyle(color = Palette.ink, fontSize = 48.sp, fontWeight = FontWeight.SemiBold, lineHeight = 54.sp)

/**
 * The race: the river with its juice, and the scoreboard beside it.
 * [countdown] overlays the numbers before the start, under the [heading] that names the race.
 */
@Composable
fun RaceScreen(snapshot: RaceSnapshot, fx: RiverFx, time: Float, countdown: Float? = null, heading: String? = null) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            RiverView(snapshot, fx, Modifier.fillMaxSize(), time)
            if (countdown != null) {
                val text = rememberTextMeasurer()
                val number = ceil(countdown).toInt().coerceAtLeast(1)
                val since = number - countdown
                Canvas(Modifier.fillMaxSize()) {
                    val riverWidth = minOf(size.width, size.height * 0.8f) * (1 - 2 * Race.BANK)
                    if (heading != null) drawWord(text, heading, Offset(center.x, size.height * 0.2f), 56.sp, Palette.paper, 1f, 1f, riverWidth * 0.9f)
                    drawCallout(text, "$number", Ease.outBack(since / 0.4f), 1f, Palette.paper)
                }
            }
        }
        Scoreboard(snapshot, Modifier.width(300.dp).fillMaxHeight())
    }
}

/** The countdown on the river: every boat on its starting lane, the course still dark, the race's name and a big number per second. */
@Composable
fun CountdownScreen(players: List<Player>, countdown: Float, fx: RiverFx, time: Float, raceNumber: Int = 1, river: River = River.LanternCreek) {
    if (players.isEmpty()) return
    val preview = remember(players, river) {
        Race(players, seed = 0, river = river).snapshot().copy(lights = emptyList(), rocks = emptyList(), logs = emptyList(), whirlpools = emptyList())
    }
    RaceScreen(preview, fx, time, countdown, "Race $raceNumber: ${river.title}")
}

/** The lobby: the QR large on the right, and a strip of river where each joining boat drops in with a pop. */
@Composable
fun Lobby(joinUrl: String?, players: List<Player>, fx: RiverFx, time: Float, offline: String = NO_WIFI, qr: @Composable (String, Modifier) -> Unit) {
    val text = rememberTextMeasurer()
    Row(Modifier.fillMaxSize().padding(64.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText("Paper Boats", style = title)
            BasicText("A river race for up to eight phones. Scan the code to grab a boat, then press Space to race.", style = body)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                drawRect(Palette.water, Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.45f))
                val r = size.height * 0.2f
                players.forEachIndexed { i, player ->
                    val age = fx.arrival(player.id)
                    val drop = Ease.outCubic(age / 0.35f)
                    val pop = if (age < 0.35f) 1f else Ease.outBack((age - 0.35f) / 0.3f).let { 1.25f - 0.25f * it }
                    val x = size.width * (i + 0.5f) / maxOf(players.size, 4)
                    val waterline = size.height * 0.6f + sin(time * 4f + player.id * 2.1f) * r * 0.06f
                    drawBoat(
                        Offset(x, waterline - (1f - drop) * size.height * 0.6f), r, Palette.player(player.colour),
                        degrees = sin(time * 2.4f + player.id) * 4f, scaleX = pop, scaleY = 2f - pop,
                    )
                    drawWord(text, player.name, Offset(x, size.height * 0.88f), 22.sp, Palette.ink, 1f, drop)
                }
            }
            BasicText(if (players.isEmpty()) "Nobody on the water yet. No phones? Press K to add a keyboard player." else "No phones? Press K to add a keyboard player.", style = soft)
        }
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            if (joinUrl != null) {
                qr(joinUrl, Modifier.size(380.dp))
                BasicText(joinUrl, style = body)
                BasicText("Phones join on the same Wi-Fi.", style = soft)
            } else {
                BasicText(offline, Modifier.width(380.dp), style = body)
            }
        }
    }
}

/** The finish: the winner's boat large, its lanterns lifting into the sky, the standings sliding in, then the [awards]. */
@Composable
fun Results(snapshot: RaceSnapshot, fx: RiverFx, time: Float, awards: List<Award> = emptyList()) {
    val standings = snapshot.standings
    val winner = standings.first()
    val tied = standings.filter { it.score == winner.score }
    val age = time - fx.finishedAt
    Box(Modifier.fillMaxSize().background(Palette.night)) {
        Canvas(Modifier.fillMaxSize()) {
            val waterTop = size.height * (1f - RiverFx.FINISH_Y)
            drawRect(Palette.water, Offset(0f, waterTop), Size(size.width, size.height - waterTop))
            val r = size.height * 0.075f
            val land = Ease.outBack(age / 0.6f)
            drawBoat(
                Offset(size.width * RiverFx.FINISH_X, waterTop + sin(time * 3.4f) * r * 0.08f), r * land, Palette.player(winner.player.colour),
                degrees = sin(time * 2.1f) * 5f,
            )
            drawSkyParticles(fx)
        }
        // Awards sit in the sky above the winner and arrive after the standings, one by one.
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.46f).padding(start = 64.dp, top = 72.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            awards.forEachIndexed { i, award ->
                val boat = standings.firstOrNull { it.player.id == award.playerId } ?: return@forEachIndexed
                val t = Ease.outBack((age - 1.1f - i * 0.25f) / 0.45f)
                Column(Modifier.graphicsLayer { alpha = t.coerceIn(0f, 1f); scaleX = t; scaleY = t; transformOrigin = TransformOrigin(0f, 0.5f) }) {
                    BasicText(award.title, style = awardTitle)
                    BasicText(
                        "${boat.player.name} ${award.line.replaceFirstChar { it.lowercase() }}",
                        style = body.copy(color = Palette.player(boat.player.colour)), maxLines = 1,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(0.48f).align(Alignment.TopEnd).padding(top = 64.dp, end = 64.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BasicText(if (tied.size > 1) tied.joinToString(" and ") { it.player.name } + " tie" else "${winner.player.name} wins", style = title)
            BasicText(lightsLine(winner.score, tied.size > 1, winner.player.name), style = body)
            Spacer(Modifier.height(12.dp))
            standings.forEachIndexed { place, boat ->
                val t = Ease.outCubic((age - 0.4f - place * 0.07f) / 0.4f)
                Row(
                    Modifier.graphicsLayer { alpha = t; translationX = (1f - t) * 80f },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BasicText("${place + 1}", Modifier.width(26.dp), style = soft)
                    Box(Modifier.size(18.dp).background(Palette.player(boat.player.colour), CircleShape))
                    BasicText(boat.player.name, Modifier.weight(1f), style = body, maxLines = 1)
                    BasicText("${boat.score}", style = body.copy(color = Palette.light, fontWeight = FontWeight.SemiBold))
                }
            }
            Spacer(Modifier.weight(1f))
            BasicText("Press Space to race again.", Modifier.padding(bottom = 0.dp), style = soft)
        }
    }
}

private fun lightsLine(score: Int, tied: Boolean, leader: String): String {
    val lights = if (score == 1) "1 lantern" else "$score lanterns"
    return if (tied) "$lights each. $leader went furthest." else "$lights carried home."
}

const val NO_WIFI = "Connect this computer to Wi-Fi so phones can join."
