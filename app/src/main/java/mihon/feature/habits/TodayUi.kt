package mihon.feature.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import tachiyomi.data.habits.FinishedRead
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.theme.GroupedListPadding
import tachiyomi.presentation.core.theme.GroupedSectionHeader
import tachiyomi.presentation.core.theme.IosColors
import tachiyomi.presentation.core.theme.groupedRow
import tachiyomi.presentation.core.theme.iosGlassCapsule
import tachiyomi.presentation.core.theme.separator
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

@Immutable
data class TodayState(
    val count: Int,
    val goal: Int?,
    /** Days in a row that met the goal. */
    val goalStreak: Int,
    /** Today's finished chapters, newest first. */
    val reads: List<FinishedRead>,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class TodayViewModel(habits: ReadingHabits) : ViewModel() {

    val state: StateFlow<TodayState?> = combine(
        habits.subscribeReads(),
        habits.subscribeGoal(),
        ReadingHabits.currentDay(),
    ) { reads, goal, today ->
        val zone = ReadingHabits.zone()
        val todays = reads.filter { it.localDate(zone) == today }.sortedByDescending { it.readAt }
        val counts = ReadingDays.countByDay(reads.map { it.readAt.toJavaInstant() }, zone)
        TodayState(
            count = todays.size,
            goal = goal,
            goalStreak = goal?.let { ReadingDays.goalStreak(counts, it, today) } ?: 0,
            reads = todays,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}

/**
 * The "📖 N today" pill at the top of the Library and Recents, with the goal ring and 🔥 streak when a daily
 * goal is set. It opens today's chapters in a sheet, and hides while nothing is read and no goal is set.
 */
@Composable
fun TodayPill(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<TodayViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state ?: return
    if (today.count == 0 && today.goal == null) return
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .iosGlassCapsule()
            .clip(CircleShape)
            .clickable(role = Role.Button) { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (today.goal == null) {
            Text(
                text = "📖 " + stringResource(MR.strings.habits_today_pill, today.count),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            GoalRing(progress = today.count.toFloat() / today.goal, modifier = Modifier.size(18.dp))
            Text(text = "${today.count}/${today.goal}", style = MaterialTheme.typography.labelLarge)
            if (today.goalStreak > 0) {
                Text(
                    text = "🔥 ${today.goalStreak}",
                    style = MaterialTheme.typography.labelLarge,
                    color = IosColors.Orange,
                )
            }
        }
    }

    if (showSheet) {
        DayReadsSheet(
            title = stringResource(MR.strings.habits_today_title),
            reads = today.reads,
            onDismissRequest = { showSheet = false },
        )
    }
}

@Composable
fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.separator
    val fill = if (progress >= 1f) IosColors.Green else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            fill,
            -90f,
            360f * progress.coerceIn(0f, 1f),
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** A day's finished chapters in a sheet, grouped by series. */
@Composable
fun DayReadsSheet(title: String, reads: List<FinishedRead>, onDismissRequest: () -> Unit) {
    val navigator = LocalNavigator.current
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    text = pluralStringResource(MR.plurals.friends_chapters, reads.size, reads.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (reads.isEmpty()) {
                item {
                    Text(
                        text = stringResource(MR.strings.habits_today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            dayReads(
                reads = reads,
                onOpenManga = navigator?.let {
                    { mangaId ->
                        onDismissRequest()
                        it.push(MangaScreen(mangaId))
                    }
                },
            )
        }
    }
}

/** Grouped sections, one per series (most recent first), each listing its chapters with the time read. */
fun LazyListScope.dayReads(reads: List<FinishedRead>, onOpenManga: ((Long) -> Unit)?) {
    val zone = ReadingHabits.zone()
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    reads.groupBy { it.mangaId }.values.forEach { series ->
        val first = series.first()
        item(key = "series-${first.mangaId}") {
            GroupedSectionHeader(title = first.mangaTitle)
        }
        series.forEachIndexed { index, read ->
            item(key = "chapter-${read.chapterId}") {
                Column(modifier = Modifier.groupedRow(index, series.size)) {
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.separator,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onOpenManga != null) {
                                    Modifier.clickable { onOpenManga(read.mangaId) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaCover.Book(data = read.cover, modifier = Modifier.width(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = read.chapterName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = read.readAt.toJavaInstant().atZone(zone).toLocalTime().format(timeFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Today's chapters as a screen, for the "Today" launcher shortcut. */
class TodayScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<TodayViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.habits_today_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val today = state ?: return@Scaffold
            if (today.reads.isEmpty()) {
                EmptyScreen(stringResource(MR.strings.habits_today_empty), modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + GroupedListPadding.calculateBottomPadding(),
                ),
            ) {
                dayReads(today.reads, onOpenManga = { navigator.push(MangaScreen(it)) })
            }
        }
    }
}
