package mihon.feature.habits

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
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
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.habits.FinishedRead
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Immutable
data class YearSummary(
    val year: Int,
    val chapters: Int,
    val readMillis: Long,
    val daysRead: Int,
    val longestStreak: Int,
    /** Series titles with their finished chapters, most first. */
    val topSeries: List<Pair<String, Int>>,
) {
    companion object {
        fun of(reads: List<FinishedRead>, year: Int, zone: ZoneId): YearSummary {
            val inYear = reads.filter { it.localDate(zone).year == year }
            val days = inYear.mapTo(mutableSetOf()) { it.localDate(zone) }
            return YearSummary(
                year = year,
                chapters = inYear.size,
                readMillis = inYear.sumOf { it.readDurationMs },
                daysRead = days.size,
                longestStreak = ReadingDays.longestStreak(days),
                topSeries = inYear.groupBy { it.mangaId }.values
                    .sortedByDescending { it.size }
                    .take(5)
                    .map { it.first().mangaTitle to it.size },
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class YearInReviewViewModel(habits: ReadingHabits) : ViewModel() {

    val state: StateFlow<YearSummary?> = combine(habits.subscribeReads(), ReadingHabits.currentDay()) { reads, today ->
        YearSummary.of(reads, today.year, ReadingHabits.zone())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}

class YearInReviewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val layer = rememberGraphicsLayer()
        val viewModel = metroViewModel<YearInReviewViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.habits_year_review, LocalDate.now().year),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val summary = state ?: return@Scaffold
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                    ) {
                        YearCard(summary)
                    }
                }
                item {
                    Button(
                        onClick = { scope.launch { shareCard(context, layer, "shiori_year_${summary.year}.png") } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(MR.strings.action_share))
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
private fun YearCard(summary: YearSummary) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(text = "Shiori", style = MaterialTheme.typography.labelLarge, color = CardMuted)
            Text(
                text = stringResource(MR.strings.habits_year_review, summary.year),
                style = MaterialTheme.typography.headlineMedium,
                color = CardText,
            )
        }
        Column {
            Text(text = summary.chapters.toString(), fontSize = 56.sp, fontWeight = FontWeight.Black, color = CardText)
            Text(
                text = stringResource(MR.strings.habits_year_chapters),
                style = MaterialTheme.typography.titleMedium,
                color = CardMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            YearStat(
                summary.readMillis.milliseconds.toDurationString(context, "0m"),
                stringResource(MR.strings.habits_year_time),
                Modifier.weight(1f),
            )
            YearStat(
                "🔥 ${summary.longestStreak}",
                stringResource(MR.strings.habits_year_streak),
                Modifier.weight(1f),
            )
            YearStat(
                summary.daysRead.toString(),
                stringResource(MR.strings.habits_year_days),
                Modifier.weight(1f),
            )
        }
        if (summary.topSeries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(MR.strings.habits_year_top).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CardMuted,
                )
                summary.topSeries.forEachIndexed { index, (title, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}. $title",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = CardText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = count.toString(), style = MaterialTheme.typography.labelLarge, color = CardMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CardText)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = CardMuted)
    }
}

/** Renders the recorded card to a PNG in the shared image cache and opens the share sheet. */
internal suspend fun shareCard(context: Context, layer: GraphicsLayer, fileName: String) {
    try {
        // Hardware bitmaps can't be compressed
        val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val file = withContext(Dispatchers.IO) {
            val dir = context.cacheImageDir.apply { mkdirs() }
            File(dir, fileName).also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        context.startActivity(file.getUriCompat(context).toShareIntent(context, type = "image/png"))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        context.logcat(LogPriority.ERROR, e) { "Sharing a reading card failed" }
        context.toast(context.stringResource(MR.strings.friends_recap_share_failed))
    }
}
