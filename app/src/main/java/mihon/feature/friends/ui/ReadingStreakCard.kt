package mihon.feature.friends.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.util.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import mihon.feature.friends.data.FriendsPreferences
import mihon.feature.friends.data.LocalReads
import mihon.feature.friends.domain.ReadingStreakStats
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.LocalFireDepartment
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ReadingStreakViewModel(
    localReads: LocalReads,
    preferences: FriendsPreferences,
) : ViewModel() {

    /** Null when the card is turned off. */
    val stats: StateFlow<ReadingStreakStats?> = combine(
        localReads.subscribeReadTimes(),
        preferences.showStreakCard.changes(),
    ) { reads, show ->
        if (show) ReadingStreakStats.compute(reads, Instant.now(), ZoneId.systemDefault()) else null
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}

@Composable
fun ReadingStreakCard(
    stats: ReadingStreakStats,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val count = rememberCountUp(stats.currentStreak.toLong(), durationMillis = 900)
    val readToday = stats.lastSevenDays.last() > 0
    val alive = stats.currentStreak > 0
    val pulse by rememberLoop(1_200, reverse = true, constant = 0.5f)
    GlowCard(
        brush = Brush.linearGradient(listOf(Color(0xFF4A1405), Color(0xFF2A0D24), Color(0xFF140A2A))),
        glow = if (alive) FlameColor else MaterialTheme.colorScheme.outline,
        elevation = 10.dp,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    if (alive) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(FlameColor.copy(alpha = 0.45f + 0.3f * pulse), Color.Transparent),
                                ),
                                radius = size.minDimension / 2 * (0.85f + 0.15f * pulse),
                            )
                        }
                    }
                    Icon(
                        imageVector = MaterialSymbols.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (alive) Color(0xFFFFB13D) else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    text = count.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    text = stringResource(MR.strings.friends_streak_current).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color(0xFFFFB13D),
                )
                Text(
                    text = stringResource(MR.strings.friends_streak_best, stats.bestStreak),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArenaColors.OnHeroMuted,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WeekLine(
                    label = stringResource(MR.strings.friends_streak_this_week),
                    chapters = stats.thisWeekChapters,
                    time = stats.thisWeekSeconds.seconds.toDurationString(context, "0m"),
                    emphasized = true,
                )
                WeekLine(
                    label = stringResource(MR.strings.friends_streak_last_week),
                    chapters = stats.lastWeekChapters,
                    time = stats.lastWeekSeconds.seconds.toDurationString(context, "0m"),
                    emphasized = false,
                )
                MiniBars(stats.lastSevenDays)
                if (!readToday) {
                    Text(
                        text = stringResource(
                            if (alive) MR.strings.friends_streak_keep else MR.strings.friends_streak_start,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB13D),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekLine(label: String, chapters: Int, time: String, emphasized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ArenaColors.OnHeroMuted,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = "${pluralStringResource(MR.plurals.friends_chapters, chapters, chapters)} · $time",
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.Normal,
            color = if (emphasized) Color.White else ArenaColors.OnHeroMuted,
        )
    }
}

@Composable
private fun MiniBars(days: List<Int>) {
    val max = days.maxOrNull()?.coerceAtLeast(1) ?: 1
    val grow = rememberCountUp(1_000, durationMillis = 700) / 1_000f
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val slot = size.width / days.size
        val barWidth = slot * 0.58f
        days.forEachIndexed { index, value ->
            val today = index == days.lastIndex
            val height = if (value == 0) 3.dp.toPx() else size.height * value / max * grow
            val topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height)
            if (value == 0) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.18f),
                    topLeft,
                    Size(barWidth, height),
                    CornerRadius(3.dp.toPx()),
                )
                return@forEachIndexed
            }
            if (today) {
                drawRoundRect(
                    FlameColor.copy(alpha = 0.35f),
                    topLeft.copy(x = topLeft.x - 3f, y = topLeft.y - 3f),
                    Size(barWidth + 6f, height + 3f),
                    CornerRadius(5.dp.toPx()),
                )
            }
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFD34D), FlameColor, Color(0xFFFF3D6E)),
                    startY = topLeft.y,
                    endY = size.height,
                ),
                topLeft,
                Size(barWidth, height),
                CornerRadius(3.dp.toPx()),
                alpha = if (today) 1f else 0.75f,
            )
        }
    }
}
