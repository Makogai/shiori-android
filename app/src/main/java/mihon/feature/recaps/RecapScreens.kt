package mihon.feature.recaps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.feature.habits.ReadingHabits
import mihon.feature.habits.shareCard
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CalendarMonth
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.SegmentedPicker
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.theme.IosColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class RecapsViewModel(habits: ReadingHabits) : ViewModel() {

    val state: StateFlow<Pair<RecapLibrary, LocalDate>?> =
        combine(habits.subscribeReads(), ReadingHabits.currentDay()) { reads, today ->
            RecapLibrary(reads, ReadingHabits.zone()) to today
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}

class RecapsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<RecapsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var kindIndex by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.recap_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val (library, today) = state ?: return@Scaffold
            val kind = RecapKind.entries[kindIndex]
            val periods = remember(library, kind) { library.periods(kind) }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item {
                    SegmentedPicker(
                        options = listOf(stringResource(MR.strings.recap_week), stringResource(MR.strings.recap_month)),
                        selectedIndex = kindIndex,
                        onSelect = { kindIndex = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (periods.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(MR.strings.recap_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        )
                    }
                } else {
                    item {
                        GroupedSection {
                            periods.forEachIndexed { index, (period, totals) ->
                                if (index > 0) GroupedDivider()
                                GroupedRow(
                                    title = periodTitle(period, today),
                                    icon = MaterialSymbols.Rounded.CalendarMonth,
                                    iconColor = if (kind == RecapKind.Week) IosColors.Indigo else IosColors.Orange,
                                    value = pluralStringResource(
                                        MR.plurals.recap_chapters_count,
                                        totals.chapters,
                                        totals.chapters,
                                    ),
                                    onClick = { navigator.push(RecapDetailScreen(period.key)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class RecapDetailScreen(private val periodKey: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val layer = rememberGraphicsLayer()
        val viewModel = metroViewModel<RecapsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val period = remember(periodKey) { RecapPeriod.parse(periodKey) } ?: return
        val today = state?.second ?: LocalDate.now()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = periodTitle(period, today),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val library = state?.first ?: return@Scaffold
            val recap = remember(library, period, today) { library.recap(period, today) }
            val title = periodTitle(period, today)
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                    ) {
                        RecapCard(recap, title)
                    }
                }
                item {
                    Button(
                        onClick = { scope.launch { shareCard(context, layer, "shiori_recap_${period.key}.png") } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(MR.strings.action_share))
                    }
                }
                recap.busiestDay?.let { (day, chapters) ->
                    item {
                        GroupedSection {
                            GroupedRow(
                                title = stringResource(MR.strings.recap_busiest_day),
                                value = day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) +
                                    " · $chapters",
                                chevron = false,
                            )
                        }
                    }
                }
                if (recap.newSeries.isNotEmpty()) {
                    item {
                        GroupedSection(header = stringResource(MR.strings.recap_new_series)) {
                            recap.newSeries.forEachIndexed { index, series ->
                                if (index > 0) GroupedDivider(startInset = 16.dp)
                                GroupedRow(
                                    title = series.title,
                                    value = series.chapters.toString(),
                                    chevron = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CardText = Color.White
private val CardMuted = Color.White.copy(alpha = 0.7f)

/** The shareable card, in fixed colours so the image looks the same in every theme. */
@Composable
private fun RecapCard(recap: Recap, title: String) {
    val context = LocalContext.current
    val colors = when (recap.period.kind) {
        RecapKind.Week -> listOf(IosColors.Blue, IosColors.Indigo, IosColors.Purple)
        RecapKind.Month -> listOf(IosColors.Orange, IosColors.Pink, IosColors.Purple)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(text = "Shiori", style = MaterialTheme.typography.labelLarge, color = CardMuted)
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = CardText)
        }
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = recap.totals.chapters.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = CardText,
                )
                ChangeBadge(recap.chaptersChange, Modifier.padding(bottom = 14.dp))
            }
            Text(
                text = stringResource(
                    if (recap.inProgress) MR.strings.recap_chapters_so_far else MR.strings.recap_chapters,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = CardMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RecapStat(
                recap.totals.readMillis.milliseconds.toDurationString(context, "0m"),
                stringResource(MR.strings.recap_time),
                recap.timeChange,
                Modifier.weight(1f),
            )
            RecapStat(
                recap.totals.series.toString(),
                stringResource(MR.strings.recap_series),
                recap.seriesChange,
                Modifier.weight(1f),
            )
            RecapStat(
                "🔥 ${recap.longestStreak}",
                stringResource(MR.strings.recap_streak),
                null,
                Modifier.weight(1f),
            )
        }
        DayBars(recap)
        if (recap.topSeries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(MR.strings.recap_top).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CardMuted,
                )
                recap.topSeries.forEachIndexed { index, series ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}. ${series.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = CardText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = series.chapters.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = CardMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBars(recap: Recap) {
    val max = recap.days.maxOf { it.second }.coerceAtLeast(1)
    val week = recap.period.kind == RecapKind.Week
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(if (week) 8.dp else 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            recap.days.forEach { (_, chapters) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(if (chapters > 0) 0.08f + 0.92f * chapters / max else 0.04f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (chapters > 0) CardText else CardMuted.copy(alpha = 0.3f)),
                )
            }
        }
        if (week) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recap.days.forEach { (day, _) ->
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapStat(value: String, label: String, change: Double?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CardText)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = CardMuted)
        ChangeBadge(change)
    }
}

@Composable
private fun ChangeBadge(change: Double?, modifier: Modifier = Modifier) {
    change ?: return
    val percent = (abs(change) * 100).roundToInt()
    Text(
        text = (if (change >= 0) "▲ " else "▼ ") + "$percent%",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = CardText,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "This week", "Last month", "3 Nov – 9 Nov" or "November 2026". */
@Composable
private fun periodTitle(period: RecapPeriod, today: LocalDate): String {
    val current = RecapPeriod.containing(period.kind, today)
    return when {
        period == current -> stringResource(
            if (period.kind == RecapKind.Week) MR.strings.recap_this_week else MR.strings.recap_this_month,
        )
        period == current.previous() -> stringResource(
            if (period.kind == RecapKind.Week) MR.strings.recap_last_week else MR.strings.recap_last_month,
        )
        period.kind == RecapKind.Month ->
            period.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
        else -> {
            val format = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            val last = period.end.minusDays(1)
            val year = if (last.year != today.year) " ${last.year}" else ""
            "${period.start.format(format)} – ${last.format(format)}$year"
        }
    }
}
