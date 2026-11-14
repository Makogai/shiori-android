package mihon.feature.friends.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.feature.friends.domain.RecapPlayer
import mihon.feature.friends.domain.WeeklyRecap
import mihon.feature.friends.domain.WeeklyRecaps
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RecapAccent = Color(0xFF6AA8FF)

/**
 * The "Week W recap" card (README v6, section 2): winner banner, both totals, challenge result and highlights.
 * The card itself is recorded into a graphics layer so **Share** can send it as an image.
 */
@Composable
fun RecapCard(
    recap: WeeklyRecap,
    model: ArenaUiModel,
    modifier: Modifier = Modifier,
    onAllRecaps: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArenaSectionTitle(stringResource(MR.strings.friends_recap_title, recap.number), "🗓️")
        Box(
            modifier = Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            RecapContent(recap, model)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { scope.launch { shareRecap(context, layer, recap) } }) {
                Text("📤 " + stringResource(MR.strings.friends_recap_share))
            }
            if (onAllRecaps != null) {
                TextButton(onClick = onAllRecaps) {
                    Text(stringResource(MR.strings.friends_recap_all))
                }
            }
        }
    }
}

private val RecapYou = Color(0xFF8B5CF6)
private val RecapRival = Color(0xFFFF6B6B)

/** The recap itself, in fixed colours so the shared image looks the same everywhere (iOS WeekRecapContent). */
@Composable
private fun RecapContent(recap: WeeklyRecap, model: ArenaUiModel) {
    val you = stringResource(MR.strings.friends_you)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1B4D), Color(0xFF14102A))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(MR.strings.friends_recap_title, recap.number).uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "${recap.week.emoji} ${recap.week.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = dateRange(recap),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(text = "📊", fontSize = 28.sp)
        }
        Text(
            text = when (recap.winner) {
                null -> stringResource(MR.strings.friends_recap_tied)
                model.myId -> stringResource(MR.strings.friends_recap_you_won)
                else -> stringResource(MR.strings.friends_recap_winner, model.name(recap.winner))
            },
            color = if (recap.winner == null) Color.White else GoldColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            recap.players.take(3).forEach { player ->
                RecapTotals(player, model, winner = player.userId == recap.winner, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "🏆 " + stringResource(MR.strings.friends_recap_challenge) + ": " +
                    if (recap.challengeWinners.isEmpty()) {
                        stringResource(MR.strings.friends_leader_nobody_won)
                    } else {
                        stringResource(
                            MR.strings.friends_leader_won,
                            recap.challengeWinners.sorted().joinToString(" & ") { nameOf(it, model, you) },
                        )
                    },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = recap.players.joinToString(" · ") {
                    "${nameOf(it.userId, model, you)} ${formatScore(it.score)}"
                },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val highlights = highlightLines(recap, model)
        if (highlights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RecapHeading(stringResource(MR.strings.friends_recap_highlights))
                highlights.forEach { line ->
                    Text(text = line, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RecapTotals(player: RecapPlayer, model: ArenaUiModel, winner: Boolean, modifier: Modifier = Modifier) {
    val name = nameOf(player.userId, model, stringResource(MR.strings.friends_you))
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (player.userId == model.myId) RecapYou else RecapRival),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            if (winner) {
                Text(
                    text = "👑",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-8).dp),
                )
            }
        }
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RecapStat(player.chapters.toString(), stringResource(MR.strings.friends_recap_unit_chapters))
        RecapStat(player.minutes.toString(), stringResource(MR.strings.friends_recap_unit_min))
        RecapStat(formatNumber(player.xp), "XP")
        player.bet?.let { bet ->
            Text(
                text = "🎲 " + (if (bet >= 0) "+$bet" else "−${-bet}"),
                color = if (bet >= 0) WinColor else ArenaColors.Rival,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecapStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(text = label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RecapHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun highlightLines(recap: WeeklyRecap, model: ArenaUiModel): List<String> {
    val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val bestDay = stringResource(MR.strings.friends_recap_best_day)
    val topSeries = stringResource(MR.strings.friends_recap_top_series)
    val lines = mutableListOf<String>()
    recap.players.forEach { player ->
        val name = nameOf(player.userId, model, stringResource(MR.strings.friends_you))
        player.bestDay?.let { (day, chapters) ->
            val count = pluralStringResource(MR.plurals.friends_chapters, chapters.toInt(), chapters)
            lines += "📅 $bestDay · $name: ${day.format(dayFormat)} · $count"
        }
        player.topSeries?.takeIf { it.title.isNotBlank() }?.let { top ->
            val count = pluralStringResource(MR.plurals.friends_chapters, top.chapters.toInt(), top.chapters)
            lines += "📚 $topSeries · $name: ${top.title} · $count"
        }
    }
    recap.genreChanges.forEach { change ->
        lines += "🎭 " + WeeklyRecaps.changeText(change, model.myId) { model.name(it) }
    }
    recap.leadChanges?.let { lines += stringResource(MR.strings.friends_recap_lead_changes, it) }
    return lines
}

private fun nameOf(userId: String, model: ArenaUiModel, you: String): String =
    if (userId == model.myId) you else model.name(userId)

private fun dateRange(recap: WeeklyRecap): String {
    val format = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return "${recap.firstDay.format(format)} – ${recap.lastDay.format(format)}"
}

/** Renders the recorded card to a PNG in the shared image cache and opens the share sheet. */
private suspend fun shareRecap(context: Context, layer: GraphicsLayer, recap: WeeklyRecap) {
    try {
        val image = layer.toImageBitmap().asAndroidBitmap()
        // Hardware bitmaps can't be compressed
        val bitmap = image.copy(Bitmap.Config.ARGB_8888, false)
        val file = withContext(Dispatchers.IO) {
            val dir = context.cacheImageDir.apply { mkdirs() }
            File(dir, "arena_week_${recap.number}_${recap.week.challengeId}.png").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        context.startActivity(file.getUriCompat(context).toShareIntent(context, type = "image/png"))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        context.logcat(LogPriority.ERROR, e) { "Sharing the recap failed" }
        context.toast(context.stringResource(MR.strings.friends_recap_share_failed))
    }
}

/** A past week in the "All recaps" list. */
@Composable
fun RecapListRow(recap: WeeklyRecap, model: ArenaUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = recap.week.emoji, fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(MR.strings.friends_recap_title, recap.number),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${recap.week.title} · ${dateRange(recap)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = when (recap.winner) {
                null -> "🤝"
                model.myId -> "🏆"
                else -> "👑 " + model.name(recap.winner)
            },
            color = if (recap.winner == model.myId) GoldColor else RecapAccent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
