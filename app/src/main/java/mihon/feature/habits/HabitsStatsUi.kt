package mihon.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.util.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import mihon.feature.recaps.RecapsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CalendarMonth
import tachiyomi.data.habits.FinishedRead
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.theme.IosColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Immutable
data class HabitsStats(
    val today: LocalDate,
    val reads: List<FinishedRead>,
    val countByDay: Map<LocalDate, Int>,
    val secondsByDay: Map<LocalDate, Long>,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HabitsStatsViewModel(habits: ReadingHabits) : ViewModel() {

    val state: StateFlow<HabitsStats?> = combine(
        habits.subscribeReads(),
        habits.subscribeReadingSeconds(),
        ReadingHabits.currentDay(),
    ) { reads, seconds, today ->
        val zone = ReadingHabits.zone()
        HabitsStats(
            today = today,
            reads = reads,
            countByDay = reads.groupingBy { it.localDate(zone) }.eachCount(),
            secondsByDay = seconds,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}

private const val HEATMAP_WEEKS = 18

/** Statistics' habits part: the reading calendar, reading time per day, Year in Review and the recaps. */
@Composable
fun HabitsStatsSection() {
    val viewModel = metroViewModel<HabitsStatsViewModel>()
    val stats by viewModel.state.collectAsStateWithLifecycle()
    val data = stats ?: return
    val navigator = LocalNavigator.current
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedSection(header = stringResource(MR.strings.habits_calendar)) {
            Heatmap(
                today = data.today,
                counts = data.countByDay,
                onDayClick = { selectedDay = it.toString() },
                modifier = Modifier.padding(14.dp),
            )
        }
        GroupedSection(header = stringResource(MR.strings.habits_reading_time)) {
            ReadingTimeBars(data.today, data.secondsByDay, modifier = Modifier.padding(14.dp))
        }
        if (navigator != null) {
            GroupedSection {
                GroupedRow(
                    title = stringResource(MR.strings.habits_year_review, data.today.year),
                    icon = MaterialSymbols.Rounded.CalendarMonth,
                    iconColor = IosColors.Pink,
                    onClick = { navigator.push(YearInReviewScreen()) },
                )
                GroupedDivider()
                GroupedRow(
                    title = stringResource(MR.strings.recap_title),
                    icon = MaterialSymbols.Rounded.CalendarMonth,
                    iconColor = IosColors.Indigo,
                    onClick = { navigator.push(RecapsScreen()) },
                )
            }
        }
    }

    selectedDay?.let { LocalDate.parse(it) }?.let { day ->
        val zone = ReadingHabits.zone()
        DayReadsSheet(
            title = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            reads = remember(day, data.reads) {
                data.reads.filter { it.localDate(zone) == day }.sortedByDescending { it.readAt }
            },
            onDismissRequest = { selectedDay = null },
        )
    }
}

/** GitHub-style calendar: a column per week (Monday first), the last [HEATMAP_WEEKS] weeks, today last. */
@Composable
private fun Heatmap(
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .minusWeeks((HEATMAP_WEEKS - 1).toLong())
    val max = counts.filterKeys { it >= firstWeek }.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        DayOfWeek.entries.forEach { weekday ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekday.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(12.dp),
                )
                repeat(HEATMAP_WEEKS) { week ->
                    val day = firstWeek.plusWeeks(week.toLong()).plusDays((weekday.value - 1).toLong())
                    val count = counts[day] ?: 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    day > today -> empty.copy(alpha = 0.02f)
                                    count == 0 -> empty
                                    else -> primary.copy(alpha = 0.25f + 0.75f * count / max)
                                },
                            )
                            .then(if (day <= today) Modifier.clickable { onDayClick(day) } else Modifier),
                    )
                }
            }
        }
    }
}

/** Active reader time for the last seven days as bars, with today's and the week's totals. */
@Composable
private fun ReadingTimeBars(today: LocalDate, secondsByDay: Map<LocalDate, Long>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { secondsByDay[it] ?: 0L }
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TimeTotal(
                label = stringResource(MR.strings.habits_today_title),
                value = values.last().seconds.toDurationString(context, "0m"),
            )
            TimeTotal(
                label = stringResource(MR.strings.stats_last_7_days),
                value = values.sum().seconds.toDurationString(context, "0m"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                val isToday = index == days.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height((6 + 64 * values[index] / max).toInt().dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    values[index] == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                },
                            ),
                    )
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeTotal(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
