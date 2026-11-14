package mihon.feature.friends.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.presentation.util.toDurationString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.friends.data.ActivityDay
import mihon.feature.friends.data.ActivityDays
import mihon.feature.friends.data.FeedRow
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.LibraryStats
import mihon.feature.friends.data.NamedCount
import mihon.feature.friends.domain.LeaguePeriods
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** v5 profile data: published library stats and each player's activity, paged. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ProfileExtrasViewModel(val repository: FriendsRepository) : ViewModel() {

    data class Feed(
        val rows: List<FeedRow> = emptyList(),
        val loading: Boolean = false,
        /** The server has nothing older. */
        val exhausted: Boolean = false,
        val failed: Boolean = false,
    )

    val feeds: StateFlow<Map<String, Feed>>
        field = MutableStateFlow(emptyMap())

    /** The Arena time zone: from the loaded snapshot, else the league row. */
    val zone: ZoneId
        get() = repository.snapshot.value?.zone ?: LeaguePeriods.zone(repository.account.value.arena?.timezone)

    fun loadStats(userId: String) {
        viewModelScope.launch {
            try {
                repository.loadProfileStats(userId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Loading library stats failed" }
            }
        }
    }

    /** Loads the newest page ([reset]) or the next older one. */
    fun loadFeed(userId: String, reset: Boolean) {
        val current = feeds.value[userId] ?: Feed()
        if (current.loading || (!reset && current.exhausted)) return
        val before = if (reset) null else current.rows.lastOrNull()?.readInstant
        update(userId) { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val page = repository.playerFeed(userId, before)
                update(userId) {
                    Feed(
                        rows = ActivityDays.merge(if (reset) emptyList() else it.rows, page),
                        exhausted = page.size < ActivityDays.PAGE_SIZE,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Loading activity failed" }
                update(userId) { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun update(userId: String, change: (Feed) -> Feed) =
        feeds.update { it + (userId to change(it[userId] ?: Feed())) }
}

/** "Library stats" (v5): the player's Statistics screen numbers, top genres and sources, and when they were sent. */
@Composable
fun LibraryStatsSection(userId: String, isMe: Boolean) {
    val viewModel = metroViewModel<ProfileExtrasViewModel>()
    val all by viewModel.repository.profileStats.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.loadStats(userId) }
    val row = all[userId]
    ArenaSection(
        title = stringResource(MR.strings.friends_library_stats),
        emoji = "📚",
        trailing = row?.updatedInstant?.let { updated ->
            {
                val ago = if (System.currentTimeMillis() - updated.toEpochMilli() < 60_000) {
                    stringResource(MR.strings.friends_just_now)
                } else {
                    relativeTimeSpanString(updated.toEpochMilli())
                }
                Text(
                    text = stringResource(MR.strings.friends_library_stats_updated, ago),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (row == null) {
            Text(
                text = stringResource(
                    if (isMe) MR.strings.friends_library_stats_none_me else MR.strings.friends_library_stats_none,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LibraryStatsContent(row.stats)
        }
    }
}

@Composable
private fun ColumnScope.LibraryStatsContent(stats: LibraryStats) {
    val context = LocalContext.current
    val tiles = listOf(
        Triple("📚", formatNumber(stats.libraryCount.toLong()), stringResource(MR.strings.friends_lib_in_library)),
        Triple("✅", formatNumber(stats.completedCount.toLong()), stringResource(MR.strings.friends_lib_completed)),
        Triple("📖", formatNumber(stats.startedCount.toLong()), stringResource(MR.strings.friends_lib_started)),
        Triple("📁", formatNumber(stats.localCount.toLong()), stringResource(MR.strings.friends_lib_local)),
        Triple(
            "📑",
            "${formatNumber(stats.chaptersRead.toLong())} / ${formatNumber(stats.chaptersTotal.toLong())}",
            stringResource(MR.strings.friends_lib_chapters_read),
        ),
        Triple(
            "⬇️",
            formatNumber(stats.chaptersDownloaded.toLong()),
            stringResource(MR.strings.friends_lib_downloaded),
        ),
        Triple(
            "⏳",
            stats.readSeconds.seconds.toDurationString(context, "0m"),
            stringResource(MR.strings.friends_lib_time_read),
        ),
        Triple("🔗", formatNumber(stats.trackedCount.toLong()), stringResource(MR.strings.friends_lib_tracked)),
        Triple(
            "⭐",
            stats.meanScore?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–",
            stringResource(MR.strings.friends_lib_mean_score),
        ),
        Triple("🛰️", stats.trackersUsed.toString(), stringResource(MR.strings.friends_lib_trackers)),
    )
    tiles.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (emoji, value, label) -> StatTile(emoji, value, label, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (stats.topGenres.isNotEmpty()) {
        Chips(stringResource(MR.strings.friends_lib_top_genres), stats.topGenres, ArenaColors.Xp)
    }
    if (stats.topSources.isNotEmpty()) {
        Chips(stringResource(MR.strings.friends_lib_top_sources), stats.topSources, ArenaColors.Me)
    }
    if (stats.app.isNotEmpty()) {
        val app = when (stats.app) {
            "mihon" -> "Mihon"
            "tachiyomi-ios" -> "Tachiyomi"
            "shiori-ios" -> "Shiori"
            else -> stats.app
        }
        Text(
            text = stringResource(MR.strings.friends_lib_from, "$app ${stats.appVersion}".trim()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun Chips(title: String, items: List<NamedCount>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Text(
                    text = "${item.name}  ${item.count}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** "Activity" (v5): the player's counted reads by day in the Arena time zone, collapsed except Today, paged. */
@Composable
fun ActivitySection(userId: String) {
    val viewModel = metroViewModel<ProfileExtrasViewModel>()
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.loadFeed(userId, reset = true) }
    val feed = feeds[userId] ?: ProfileExtrasViewModel.Feed()
    val zone = viewModel.zone
    val days = remember(feed.rows, zone) { ActivityDays.group(feed.rows, zone) }
    val today = LocalDate.now(zone)
    val toggled = remember(userId) { mutableStateMapOf<LocalDate, Boolean>() }
    LoadFeedSocial(feed.rows)
    ArenaSection(stringResource(MR.strings.friends_activity), "🗓️") {
        if (days.isEmpty()) {
            Text(
                text = stringResource(
                    when {
                        feed.loading -> MR.strings.friends_loading
                        feed.failed -> MR.strings.friends_activity_failed
                        else -> MR.strings.friends_activity_empty
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        days.forEach { day ->
            val open = toggled[day.date] ?: (day.date == today)
            DayGroup(day, today, zone, open) { toggled[day.date] = !open }
        }
        if (days.isNotEmpty() && !feed.exhausted) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (feed.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { viewModel.loadFeed(userId, reset = false) }) {
                        Text(stringResource(MR.strings.friends_load_more))
                    }
                }
            }
        }
    }
}

/** "Today", "Yesterday", else "Tuesday, 10 Nov". */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(MR.strings.friends_activity_today)
    today.minusDays(1) -> stringResource(MR.strings.friends_activity_yesterday)
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
}

@Composable
private fun DayGroup(day: ActivityDay, today: LocalDate, zone: ZoneId, open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    val header = listOf(
        dayLabel(day.date, today),
        pluralStringResource(MR.plurals.friends_chapters, day.chapters, day.chapters),
        ActivityDays.minutes(day.seconds),
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(text = "▸", modifier = Modifier.rotate(rotation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                day.rows.forEach { row ->
                    ActivityRow(row, zone)
                    FeedReactions(row, modifier = Modifier.padding(start = 40.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(row: FeedRow, zone: ZoneId) {
    val time = row.readInstant?.atZone(zone)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SharedCover(
            coverUrl = row.coverUrl,
            seriesKey = row.seriesKey,
            title = row.seriesTitle,
            modifier = Modifier
                .width(30.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.seriesTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapterText(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Ch. 40" from the number, else the chapter's name. */
private fun chapterText(row: FeedRow): String {
    val number = row.chapterNumber
    return if (number != null && number >= 0) {
        "Ch. " + if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
    } else {
        row.chapterName ?: "–"
    }
}
