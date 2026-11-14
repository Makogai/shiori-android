package mihon.feature.friends.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.withContext
import mihon.feature.friends.cards.ProfileCardsSection
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.Levels
import mihon.feature.friends.domain.Medal
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Another player's profile: the same stats as your own, read-only. */
class PlayerProfileScreen(private val userId: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<PlayerProfileViewModel>()
        val account by viewModel.repository.account.collectAsStateWithLifecycle()
        val snapshot by viewModel.repository.snapshot.collectAsStateWithLifecycle()
        val myId = account.userId
        val profile by produceState<PlayerProfileModel?>(null, snapshot, myId, userId) {
            val current = snapshot
            value = if (current != null && myId != null) {
                withContext(Dispatchers.Default) { PlayerProfileModel.build(current, myId, userId) }
            } else {
                null
            }
        }
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = profile?.player?.name.orEmpty(),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val current = profile
            if (current == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            CompositionLocalProvider(
                LocalOpenPlayer provides { id ->
                    when (id) {
                        userId -> Unit
                        myId -> navigator.push(FriendsProfileScreen())
                        else -> navigator.push(PlayerProfileScreen(id))
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    ProfileHeader(current)
                    PlayerStatsSections(current)
                }
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class PlayerProfileViewModel(val repository: FriendsRepository) : ViewModel()

/** Avatar, name, chosen title, level ring + level name and tier badge. */
@Composable
fun ProfileHeader(
    profile: PlayerProfileModel,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
) {
    val player = profile.player
    val tierColor = player.tier.composeColor
    GlowCard(
        brush = heroBrush(tierColor),
        glow = tierColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (avatar != null) {
                avatar()
            } else {
                LevelAvatar(
                    name = player.name,
                    url = player.avatarUrl,
                    level = player.level.level,
                    levelProgress = player.level.progress,
                    tier = player.tier,
                    size = 104.dp,
                    team = if (profile.isMe) ArenaColors.Me else ArenaColors.Rival,
                )
            }
            Text(
                text = player.name,
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            player.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = GoldColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            // Wraps instead of clipping with long level names, narrow screens or large fonts
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(MR.strings.friends_level, player.level.level) + " · " + player.level.name,
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                TierPill(player.tier)
                profile.seasonPlace?.let {
                    Text(
                        text = "#$it",
                        color = ArenaColors.OnHero,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                XpBar(
                    progress = player.level.progress,
                    brush = Brush.horizontalGradient(listOf(ArenaColors.Xp, Color(0xFFC4B5FD))),
                    glow = ArenaColors.Xp,
                )
                Text(
                    text = stringResource(MR.strings.friends_xp, formatNumber(player.xp)) + " · " +
                        stringResource(MR.strings.friends_xp_to_level, formatNumber(player.level.toNext)),
                    color = ArenaColors.OnHeroMuted,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Every stats section of a profile, in the order shared with iOS. */
@Composable
fun ColumnScope.PlayerStatsSections(profile: PlayerProfileModel) {
    val context = LocalContext.current
    fun duration(seconds: Long) = seconds.seconds.toDurationString(context, "0m")

    ArenaSection(stringResource(MR.strings.friends_reading_time), "⏱️") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                MR.strings.friends_period_all to profile.allTime,
                MR.strings.friends_period_season to profile.season,
                MR.strings.friends_streak_this_week to profile.week,
            ).forEach { (label, totals) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = duration(totals.seconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = pluralStringResource(
                            MR.plurals.friends_chapters,
                            totals.chapters.toInt(),
                            totals.chapters,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    ArenaSection(stringResource(MR.strings.friends_key_stats), "📊") {
        val best = profile.bestDay
        val stats = listOf(
            Triple("📖", formatNumber(profile.allTime.chapters), stringResource(MR.strings.friends_metric_chapters)),
            Triple("🗂️", formatNumber(profile.series), stringResource(MR.strings.friends_metric_series)),
            Triple(
                "⏳",
                profile.minutesPerChapter?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–",
                stringResource(MR.strings.friends_stat_avg_minutes),
            ),
            Triple("🔥", profile.currentStreak.toString(), stringResource(MR.strings.friends_stat_current_streak)),
            Triple("☄️", profile.longestStreak.toString(), stringResource(MR.strings.friends_stat_longest_streak)),
            Triple(
                "📅",
                best?.chapters?.toString() ?: "–",
                stringResource(MR.strings.friends_metric_best_day) +
                    (best?.day?.let { " · " + it.format(dateFormat) } ?: ""),
            ),
            Triple("🌟", profile.player.perfectDays.toString(), stringResource(MR.strings.friends_stat_perfect_days)),
            Triple(
                "🎁",
                profile.player.suggestionsAccepted.toString(),
                stringResource(MR.strings.friends_stat_suggestions_accepted),
            ),
        )
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (emoji, value, label) -> KeyStat(emoji, value, label, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    LibraryStatsSection(userId = profile.player.userId, isMe = profile.isMe)
    ProfileCardsSection(userId = profile.player.userId)

    ArenaSection(stringResource(MR.strings.friends_favourite_hours), "🕰️") {
        HourStrip(profile.hours)
    }

    ArenaSection(stringResource(MR.strings.friends_genres), "🎭") {
        if (profile.genres.isEmpty()) {
            EmptyNote(stringResource(MR.strings.friends_no_genres))
        } else {
            GenreDonut(profile.genres)
        }
    }

    if (profile.topSeries.isNotEmpty()) {
        ArenaSection(stringResource(MR.strings.friends_top_series), "📚") {
            TopSeriesRow(series = profile.topSeries)
        }
    }

    val tierAccent = profile.player.tier.composeColor
    ArenaSection(stringResource(MR.strings.friends_season_history), "🛡️") {
        if (profile.xpHistory.size > 1) {
            SubHeading(stringResource(MR.strings.friends_xp_history))
            XpHistoryChart(profile.xpHistory)
        }
        SettledBetsList(profile.settledBets)
        SeasonRow(
            tierLabel = profile.player.tier,
            title = stringResource(MR.strings.friends_period_season) + " · " + stringResource(MR.strings.friends_live),
            xp = profile.player.seasonXp,
        )
        if (profile.seasonHistory.isEmpty()) {
            EmptyNote(stringResource(MR.strings.friends_no_seasons_yet))
        }
        profile.seasonHistory.forEach { result ->
            SeasonRow(
                tierLabel = result.tier,
                title = stringResource(MR.strings.friends_season_place, result.number, result.place),
                xp = result.xp,
            )
        }
    }

    ArenaSection(stringResource(MR.strings.friends_medals), "🏅") {
        val total = profile.medals.total
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MedalDisc(Medal.GOLD, total.gold, GoldColor)
            MedalDisc(Medal.SILVER, total.silver, SilverColor)
            MedalDisc(Medal.BRONZE, total.bronze, BronzeColor)
        }
        CabinetBreakdown(profile.medals)
    }

    ArenaSection(stringResource(MR.strings.friends_challenge_trophies), "🏆") {
        if (profile.player.wins.isEmpty()) {
            EmptyNote(stringResource(MR.strings.friends_no_trophies))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = chipSpacing) {
                profile.player.wins.forEach { challenge ->
                    Text(
                        text = "🏆 ${challenge.emoji} ${challenge.title}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1300),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tierBrush(mihon.feature.friends.domain.Tier.GOLD))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    AchievementsGrid(profile.player.achievements)

    ArenaSection(stringResource(MR.strings.friends_titles_held), "👑") {
        if (profile.titles.isEmpty()) {
            EmptyNote(stringResource(MR.strings.friends_no_titles_held))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = chipSpacing) {
                profile.titles.forEach { award ->
                    Text(
                        text = award.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GoldColor.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (profile.records.isNotEmpty()) {
        ArenaSection(stringResource(MR.strings.friends_record_vs_rivals), "🥊") {
            profile.records.forEach { (other, record) -> RecordRow(other, record) }
        }
    }

    ActivitySection(userId = profile.player.userId)
}

@Composable
private fun EmptyNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun KeyStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics(mergeDescendants = true) {}
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 24 bars, one per hour, coloured from cool to hot by how much was read then. */
@Composable
private fun HourStrip(hours: List<Long>) {
    val max = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val top = hours.indices.sortedByDescending { hours[it] }.filter { hours[it] > 0 }.take(3)
    val description = top.joinToString { "%02d:00 (%d)".format(it, hours[it]) }
    val grow = rememberCountUp(1_000, durationMillis = 800) / 1_000f
    val empty = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = description },
        ) {
            val slot = size.width / 24
            val barWidth = slot * 0.7f
            hours.forEachIndexed { hour, value ->
                val heat = value.toFloat() / max
                val height = if (value == 0L) 3.dp.toPx() else size.height * heat * grow
                drawRoundRect(
                    color = if (value == 0L) empty else lerp(Color(0xFF5B8DEF), Color(0xFFFF4D4D), heat),
                    topLeft = Offset(hour * slot + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18", "23").forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (peak != null) {
            Text(
                text = stringResource(MR.strings.friends_peak_hour, "%02d:00".format(peak)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val chipSpacing = Arrangement.spacedBy(6.dp)

private val GenreColors = listOf(0xFFFF6B6B, 0xFF8B5CF6, 0xFF3D8BFF, 0xFFF5C542, 0xFF2EC4B6, 0xFFFF8A3D, 0xFF9AA5B1)
    .map { Color(it) }

@Composable
private fun GenreDonut(genres: List<Pair<String, Long>>) {
    val slices = genres.take(GenreColors.size - 1).let { top ->
        val rest = genres.drop(top.size).sumOf { it.second }
        if (rest > 0) top + ("…" to rest) else top
    }
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    val grow = rememberCountUp(1_000, durationMillis = 900) / 1_000f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val stroke = 18.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                slices.forEachIndexed { index, (_, value) ->
                    val sweep = 360f * value / total * grow
                    drawArc(
                        GenreColors[index % GenreColors.size],
                        start,
                        (sweep - 1.5f).coerceAtLeast(0.5f),
                        false,
                        Offset(stroke / 2, stroke / 2),
                        arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Text(text = formatNumber(genres.sumOf { it.second }), fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { index, (genre, value) ->
                val color = GenreColors[index % GenreColors.size]
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                        )
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.toFloat() / slices.first().second * grow)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}

/** Running lifetime XP with dashed lines where levels were reached. */
@Composable
private fun XpHistoryChart(points: List<Pair<java.time.LocalDate, Long>>) {
    val max = points.maxOf { it.second }.coerceAtLeast(1)
    val grow = rememberCountUp(1_000, durationMillis = 900) / 1_000f
    val line = ArenaColors.Xp
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = "${points.last().second} XP"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .semantics { contentDescription = description },
    ) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        var level = 1
        while (Levels.threshold(level) <= max) {
            val y = size.height * (1f - Levels.threshold(level).toFloat() / max)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            level++
        }
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, (_, xp) ->
            val x = index * step
            val y = size.height * (1f - xp.toFloat() / max * grow)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SeasonRow(tierLabel: mihon.feature.friends.domain.Tier, title: String, xp: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TierBadge(tierLabel, 30.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(MR.strings.friends_tier, tierLabel.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(MR.strings.friends_xp, formatNumber(xp)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun MedalDisc(medal: Medal, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(color, Color.White, 0.4f), color, lerp(color, Color.Black, 0.25f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = medal.emoji, fontSize = 26.sp)
        }
        Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RecordRow(other: ArenaPlayer, record: mihon.feature.friends.domain.HeadToHeadRecord) {
    val total = (record.won + record.lost + record.tied).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .openPlayer(other.userId),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FriendAvatar(name = other.name, url = other.avatarUrl, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = other.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            ) {
                listOf(
                    record.won to ArenaColors.Quest,
                    record.tied to MaterialTheme.colorScheme.outline,
                    record.lost to ArenaColors.Rival,
                ).filter { it.first > 0 }.forEach { (days, color) ->
                    Box(
                        Modifier
                            .weight(days.toFloat() / total)
                            .height(8.dp)
                            .background(color),
                    )
                }
                if (record.won + record.lost + record.tied == 0) {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        Text(
            text = stringResource(MR.strings.friends_record_value, record.won, record.lost, record.tied),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
        )
    }
}
