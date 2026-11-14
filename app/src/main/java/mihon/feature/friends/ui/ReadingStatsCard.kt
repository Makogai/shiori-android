package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.toDurationString
import mihon.feature.friends.domain.ReadingStreakStats
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import mihon.icons.materialsymbols.rounded.LocalFireDepartment
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.readableAccent
import tachiyomi.presentation.core.theme.separator
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private val Flame = Color(0xFFFF9500)

/**
 * The iOS Recents reading stats card: the streak, this week's chapters and time, the last seven days as
 * bars, and a recaps row under them. The top opens Statistics.
 */
@Composable
fun ReadingStatsCard(
    stats: ReadingStreakStats,
    onOpenStats: () -> Unit,
    onOpenRecaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.readableAccent()
    val readToday = stats.lastSevenDays.lastOrNull()?.let { it > 0 } == true
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenStats)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(min = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (readToday) Flame else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stats.currentStreak.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(
                        if (stats.currentStreak == 1) MR.strings.stats_streak_day else MR.strings.stats_streak_days,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalDivider(
                modifier = Modifier.height(52.dp),
                color = MaterialTheme.colorScheme.separator,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stats.thisWeekChapters.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " " + stringResource(MR.strings.stats_chapters_unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = " " + stats.thisWeekSeconds.seconds.toDurationString(context, "0m"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(MR.strings.stats_last_7_days),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DayBars(stats.lastSevenDays)
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.separator,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenRecaps)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.QueryStats,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(MR.strings.friends_recaps),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = MaterialSymbols.Rounded.ChevronRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Seven capsule bars, today last and fully opaque, with weekday initials under them. */
@Composable
private fun DayBars(days: List<Int>) {
    val max = days.maxOrNull()?.coerceAtLeast(1) ?: 1
    val initials = remember {
        val today = LocalDate.now()
        List(days.size) { i ->
            today.minusDays((days.size - 1 - i).toLong()).dayOfWeek
                .getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { index, value ->
            val today = index == days.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(if (value == 0) 6.dp else (6 + 38 * value / max).dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                value == 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                today -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            },
                        ),
                )
                Text(
                    text = initials[index],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                    color = if (today) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
