package mihon.feature.friends.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.presentation.util.toDurationString
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.FeedRow
import mihon.feature.friends.data.TopSeries
import mihon.feature.friends.domain.Medal
import mihon.feature.friends.domain.MedalCabinet
import mihon.feature.friends.domain.MedalTally
import mihon.feature.friends.domain.TitleAward
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.LocalFireDepartment
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

val GoldColor = Color(0xFFF5C542)
val SilverColor = Color(0xFFC0C7D0)
val BronzeColor = Color(0xFFCD7F32)
val FlameColor = Color(0xFFFF7A1A)

private val memberPalette = listOf(
    Color(0xFF4F8EF7),
    Color(0xFFEF5DA8),
    Color(0xFF2EC4B6),
    Color(0xFFFFB020),
    Color(0xFF9B6CFF),
    Color(0xFF6CC551),
    Color(0xFFFF6B6B),
    Color(0xFF00A6ED),
)

/** Stable per-member colour for charts and bars. */
fun memberColor(userId: String, members: List<String>): Color {
    val index = members.sorted().indexOf(userId).coerceAtLeast(0)
    return memberPalette[index % memberPalette.size]
}

/** A player's picture, or their initial on a gradient of their colour (the theme colour by default), as on iOS. */
@Composable
fun FriendAvatar(
    name: String,
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        }
        if (ring != null) {
            Box(
                Modifier
                    .size(size)
                    .border((size.value / 18f).coerceAtLeast(2f).dp, ring, CircleShape),
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** The top three on pedestals, 2nd · 1st · 3rd, in their players' colours (iOS LeaguePodium). */
@Composable
fun Podium(
    standings: List<Standing>,
    myId: String,
    colorOf: @Composable (String) -> Color,
    modifier: Modifier = Modifier,
    score: (Standing) -> String = { it.row.chapters.toString() },
) {
    val top = standings.take(3)
    if (top.size < 2) return
    val order = listOf(1, 0, 2).filter { it < top.size }
    var raised by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { raised = true }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { index ->
            val standing = top[index]
            val color = colorOf(standing.row.userId)
            val target = listOf(96.dp, 70.dp, 52.dp)[index]
            val height by animateDpAsState(if (raised) target else 8.dp, motion(bouncy()), label = "podium")
            val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .openPlayer(standing.row.userId),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (index == 0 && standing.row.chapters > 0) Text(text = "👑", fontSize = 22.sp)
                Box {
                    FriendAvatar(
                        name = standing.row.displayName,
                        url = standing.row.avatarUrl,
                        size = if (index == 0) 68.dp else 54.dp,
                        color = color,
                        ring = if (index == 0) Color(0xFFFFCC00) else null,
                    )
                    Medal.entries.getOrNull(standing.rank - 1)?.let { medal ->
                        Text(
                            text = medal.emoji,
                            fontSize = if (index == 0) 24.sp else 20.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp),
                        )
                    }
                }
                Text(
                    text = if (standing.row.userId == myId) {
                        stringResource(MR.strings.friends_you)
                    } else {
                        standing.row.displayName
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = score(standing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(shape)
                        .background(
                            Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.45f))),
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (raised) {
                        Text(
                            text = standing.rank.toString(),
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Where you stand in the selected period, on a theme-colour gradient card (iOS LeagueStandingBanner). */
@Composable
fun StatusBanner(model: LeagueUiModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val me = model.me
    val gap = model.gap
    val first = me != null && me.rank == 1 && me.row.chapters > 0 && gap > 0
    val emoji = when {
        first -> "🏆"
        me?.rank == 2 && me.row.chapters > 0 -> "🥈"
        else -> "📖"
    }
    val headline = when {
        me == null || me.row.chapters == 0L -> stringResource(MR.strings.friends_not_on_board)
        gap == 0L -> stringResource(MR.strings.friends_neck_and_neck)
        me.rank == 1 -> pluralStringResource(MR.plurals.friends_leading_by, gap.toInt(), gap)
        else -> pluralStringResource(MR.plurals.friends_behind_by, gap.toInt(), gap)
    }
    val chapters = me?.row?.chapters ?: 0L
    val subtitle = stringResource(MR.strings.friends_you) + ": " + listOf(
        pluralStringResource(MR.plurals.friends_chapters, chapters.toInt(), chapters),
        (me?.row?.seconds ?: 0L).seconds.toDurationString(context, "0m"),
    ).joinToString(" · ")
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = primary.copy(alpha = 0.3f), spotColor = primary.copy(alpha = 0.3f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(primary, lerp(primary, if (first) Color(0xFFFF9500) else Color(0xFFAF52DE), 0.35f)),
                ),
            )
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = emoji, fontSize = 34.sp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = headline,
                color = onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(text = subtitle, color = onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
        if (me != null && model.standings.size > 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#${me.rank}",
                    color = onPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(MR.strings.friends_of_players, model.standings.size),
                    color = onPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Everyone in order: place, avatar, name and title, time and series, and chapters (iOS LeagueBoardList rows). */
@Composable
fun StandingRow(
    standing: Standing,
    isMe: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val row = standing.row
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .openPlayer(row.userId)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = standing.rank.toString(),
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (standing.rank == 1) Color(0xFFFF9500) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FriendAvatar(name = row.displayName, url = row.avatarUrl, size = 40.dp, color = color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isMe) {
                        "${row.displayName} (${stringResource(
                            MR.strings.friends_you,
                        )})"
                    } else {
                        row.displayName
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (standing.streak > 0) StreakFlame(standing.streak, hot = standing.streak >= 3)
            }
            if (!row.title.isNullOrBlank()) TitleChip(row.title)
            Text(
                text = listOf(
                    row.seconds.seconds.toDurationString(context, "0m"),
                    pluralStringResource(MR.plurals.friends_series, row.series.toInt(), row.series),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.chapters.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(MR.plurals.friends_chapter_word, row.chapters.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A chosen title in a small grey capsule, under a name. */
@Composable
fun TitleChip(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun StreakFlame(days: Int, modifier: Modifier = Modifier, hot: Boolean = true) {
    val tint = if (hot) FlameColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = MaterialSymbols.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = days.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

/** You against a rival: the month's days won, then a bar pair per metric growing out from the middle (iOS). */
@Composable
fun RivalryCard(rivalry: Rivalry, me: BoardRow, colors: Pair<Color, Color>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendAvatar(name = me.displayName, url = me.avatarUrl, size = 30.dp, color = colors.first)
            Text(
                text = stringResource(MR.strings.friends_you),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${rivalry.myMonthlyWins} – ${rivalry.theirMonthlyWins}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(MR.strings.friends_days_won_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = rivalry.rival.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FriendAvatar(
                name = rivalry.rival.displayName,
                url = rivalry.rival.avatarUrl,
                size = 30.dp,
                color = colors.second,
            )
        }
        rivalry.metrics.forEach { metric ->
            val label = when (metric.kind) {
                RivalMetric.Kind.CHAPTERS -> stringResource(MR.strings.friends_metric_chapters)
                RivalMetric.Kind.TIME -> stringResource(MR.strings.friends_metric_time)
                RivalMetric.Kind.SERIES -> stringResource(MR.strings.friends_metric_series)
                RivalMetric.Kind.STREAK -> stringResource(MR.strings.friends_metric_streak)
                RivalMetric.Kind.BEST_DAY -> stringResource(MR.strings.friends_metric_best_day)
            }
            fun format(value: Long) = when (metric.kind) {
                RivalMetric.Kind.TIME -> value.seconds.toDurationString(context, "0m")
                RivalMetric.Kind.STREAK -> "${value}d"
                else -> value.toString()
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(
                        text = format(metric.mine),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (metric.mine > metric.theirs) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = format(metric.theirs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (metric.theirs > metric.mine) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                VersusBar(mine = metric.mine, theirs = metric.theirs, colors = colors)
            }
        }
    }
}

/** Two bars growing out from the middle, each against the bigger value. */
@Composable
private fun VersusBar(mine: Long, theirs: Long, colors: Pair<Color, Color>) {
    val top = maxOf(mine, theirs, 1L).toFloat()
    val left by animateFloatAsState(mine / top, motion(tween(600)), label = "versusLeft")
    val right by animateFloatAsState(theirs / top, motion(tween(600)), label = "versusRight")
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            Triple(left, colors.first, true),
            Triple(right, colors.second, false),
        ).forEach { (fraction, color, end) ->
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                if (fraction > 0f) {
                    val width = (size.width * fraction).coerceAtLeast(size.height)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.15f), color)),
                        topLeft = Offset(if (end) size.width - width else 0f, 0f),
                        size = Size(width, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/** This month's titles, a tile each with who holds it (iOS LeagueTitlesCard). */
@Composable
fun TitlesList(
    titles: List<TitleAward>,
    members: Map<String, BoardRow>,
    myId: String,
    colorOf: @Composable (String) -> Color,
) {
    if (titles.isEmpty()) {
        Text(
            text = stringResource(MR.strings.friends_no_titles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    titles.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            pair.forEach { award ->
                TitleTile(
                    award,
                    members[award.holder],
                    award.holder == myId,
                    colorOf(award.holder),
                    Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TitleTile(award: TitleAward, holder: BoardRow?, mine: Boolean, color: Color, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(
                if (mine) {
                    primary.copy(
                        alpha = 0.14f,
                    )
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.07f)
                },
            )
            .border(1.5.dp, if (mine) primary.copy(alpha = 0.6f) else Color.Transparent, shape)
            .openPlayer(award.holder)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(text = award.title.emoji, fontSize = 26.sp, modifier = Modifier.weight(1f))
            if (holder !=
                null
            ) {
                FriendAvatar(name = holder.displayName, url = holder.avatarUrl, size = 26.dp, color = color)
            }
        }
        Text(
            text = award.text.removePrefix(award.title.emoji).trim(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (mine) stringResource(MR.strings.friends_you) else holder?.displayName.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MedalCounts(tally: MedalTally, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Medal.GOLD to tally.gold, Medal.SILVER to tally.silver, Medal.BRONZE to tally.bronze)
            .forEach { (medal, count) ->
                Text(text = "${medal.emoji} $count", style = MaterialTheme.typography.labelLarge)
            }
    }
}

@Composable
fun CabinetBreakdown(cabinet: MedalCabinet) {
    listOf(
        MR.strings.friends_medals_days to cabinet.day,
        MR.strings.friends_medals_weeks to cabinet.week,
        MR.strings.friends_medals_months to cabinet.month,
    ).forEach { (label, tally) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            MedalCounts(tally)
        }
    }
}

@Composable
fun ChartLegend(members: Map<String, BoardRow>, memberIds: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        memberIds.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(memberColor(id, memberIds)),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = members[id]?.displayName.orEmpty(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Covers with their chapter count on a badge and the title underneath (iOS TopSeriesCard). */
@Composable
fun TopSeriesRow(series: List<TopSeries>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(series, key = { it.key }) { item ->
            Column(modifier = Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    SharedCover(
                        coverUrl = item.cover,
                        seriesKey = item.key,
                        title = item.title.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        text = item.chapters.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = item.title.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Each player's most read series in the period: their name, then their covers. */
@Composable
fun TopSeriesByPlayer(rows: List<BoardRow>, myId: String, colorOf: @Composable (String) -> Color) {
    rows.filter { it.topSeries.isNotEmpty() }.forEach { row ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FriendAvatar(name = row.displayName, url = row.avatarUrl, size = 20.dp, color = colorOf(row.userId))
                Text(
                    text = if (row.userId == myId) stringResource(MR.strings.friends_you) else row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TopSeriesRow(row.topSeries)
        }
    }
}

/** "You read Ch. 58", the series and when, and its cover (iOS LeagueFeedCard rows). */
@Composable
fun FeedItem(row: FeedRow, member: BoardRow?, isMe: Boolean = false, color: Color = MaterialTheme.colorScheme.primary) {
    val chapter = row.chapterNumber?.takeIf { it >= 0 }?.let { "Ch. ${it.toString().removeSuffix(".0")}" }
        ?: row.chapterName?.takeIf { it.isNotBlank() }
        ?: row.seriesTitle
    val name = if (isMe) stringResource(MR.strings.friends_you) else member?.displayName.orEmpty()
    Row(
        modifier = Modifier.openPlayer(row.userId),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FriendAvatar(name = member?.displayName ?: name, url = member?.avatarUrl, size = 32.dp, color = color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
                    append(" ")
                    append(stringResource(MR.strings.friends_read_chapter, chapter))
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    row.seriesTitle,
                    row.readInstant?.let { relativeTimeSpanString(it.toEpochMilli()) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SharedCover(
            coverUrl = row.coverUrl,
            seriesKey = row.seriesKey,
            title = row.seriesTitle,
            modifier = Modifier
                .width(28.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
    FeedReactions(row, modifier = Modifier.padding(start = 42.dp))
}

/** Grouped bars: one group per day, one bar per member. */
@Composable
fun DailyChart(model: LeagueUiModel, memberIds: List<String>, modifier: Modifier = Modifier) {
    val values = memberIds.map { id -> model.chart[id].orEmpty() }
    val max = values.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    val colors = remember(memberIds) { memberIds.map { memberColor(it, memberIds) } }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val progress = remember(model.chartDays) { Animatable(0f) }
    LaunchedEffect(model.chartDays) { progress.animateTo(1f, tween(700)) }
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val days = model.chartDays.size.coerceAtLeast(1)
            val groupWidth = size.width / days
            val barWidth = (groupWidth * 0.8f / memberIds.size.coerceAtLeast(1)).coerceAtMost(14.dp.toPx())
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            for (day in 0 until days) {
                val groupStart = day * groupWidth + (groupWidth - barWidth * memberIds.size) / 2
                values.forEachIndexed { member, series ->
                    val value = series.getOrNull(day) ?: 0
                    if (value == 0L) return@forEachIndexed
                    val height = size.height * value / max * progress.value
                    drawRoundRect(
                        color = colors[member],
                        topLeft = Offset(groupStart + member * barWidth, size.height - height),
                        size = Size(barWidth * 0.85f, height),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = model.chartDays.firstOrNull()?.let { "${it.dayOfMonth}.${it.monthValue}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = model.chartDays.lastOrNull()?.let { "${it.dayOfMonth}.${it.monthValue}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
