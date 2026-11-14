package mihon.feature.friends.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mihon.feature.friends.domain.Achievement
import mihon.feature.friends.domain.AchievementProgress
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.Levels
import mihon.feature.friends.domain.Medal
import mihon.feature.friends.domain.MedalCabinet
import mihon.feature.friends.domain.MedalTally
import mihon.feature.friends.domain.QuestProgress
import mihon.feature.friends.domain.Tier
import mihon.feature.friends.domain.Xp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter
import java.util.Locale

val PurpleColor = Color(0xFF9B6CFF)

/** The season banner, as on iOS: tier badge in a burst of light, season XP toward the next tier, and the level row. */
@Composable
fun SeasonHero(model: ArenaUiModel, modifier: Modifier = Modifier) {
    val me = model.me ?: return
    val tier = me.tier
    val tierColor = tier.composeColor
    val next = me.tier.next
    val seasonEnd = remember(model.season, model.zone) {
        model.season.plusMonths(1).atDay(1).atStartOfDay(model.zone).toInstant()
    }
    val shape = RoundedCornerShape(28.dp)
    val base = lerp(PanelBase, MaterialTheme.colorScheme.primary, 0.1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(
                18.dp,
                shape,
                ambientColor = tierColor.copy(alpha = 0.35f),
                spotColor = tierColor.copy(alpha = 0.35f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(lerp(tierColor, base, 0.45f), base, lerp(base, Color.Black, 0.3f))),
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(tierColor.copy(alpha = 0.45f), Color.Transparent),
                        Offset(size.width * 0.18f, size.height * 0.45f),
                        220.dp.toPx(),
                    ),
                )
            }
            .border(
                1.2.dp,
                Brush.verticalGradient(listOf(tierColor.copy(alpha = 0.8f), Color.White.copy(alpha = 0.05f))),
                shape,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(MR.strings.friends_season_short, model.seasonNumber).uppercase(),
                color = ArenaColors.OnHero,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = model.season.month.getDisplayName(
                    java.time.format.TextStyle.FULL_STANDALONE,
                    Locale.getDefault(),
                ),
                color = ArenaColors.OnHero.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "⏳ " + countdownText(seasonEnd),
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(modifier = Modifier.size(width = 96.dp, height = 100.dp), contentAlignment = Alignment.Center) {
                LightRays(color = tierColor, modifier = Modifier.requiredSize(150.dp))
                TierBadge(tier = tier, size = 86.dp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tier.label.uppercase(),
                    style = TextStyle(
                        brush = Brush.verticalGradient(listOf(Color.White, lerp(tierColor, Color.White, 0.35f))),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        shadow = Shadow(tierColor.copy(alpha = 0.8f), blurRadius = 20f),
                    ),
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatNumber(rememberCountUp(me.seasonXp)),
                        color = ArenaColors.OnHero,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(MR.strings.friends_season_xp_label),
                        color = ArenaColors.OnHero.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                XpBar(
                    progress = me.tierProgress,
                    brush = tierBrush(next ?: tier),
                    glow = (next ?: tier).composeColor,
                    height = 8.dp,
                )
                Text(
                    text = if (next != null) {
                        stringResource(MR.strings.friends_xp_to_tier, formatNumber(me.toNextTier ?: 0), next.label)
                    } else {
                        stringResource(MR.strings.friends_tier_max)
                    },
                    color = lerp((next ?: tier).composeColor, Color.White, 0.35f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        LevelRow(me)
    }
}

/** Level ring, level name and XP toward the next level, on a dark inset. */
@Composable
private fun LevelRow(me: ArenaPlayer) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressRing(
            fraction = me.level.progress,
            colors = listOf(ArenaColors.Xp, lerp(ArenaColors.Xp, Color.White, 0.4f)),
            size = 50.dp,
            stroke = 4.dp,
        ) {
            Text(
                text = me.level.level.toString(),
                color = ArenaColors.OnHero,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.friends_level_named, me.level.level, me.level.name),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(MR.strings.friends_xp, formatNumber(rememberCountUp(me.xp))),
                    color = ArenaColors.OnHero.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            XpBar(
                progress = me.level.progress,
                brush = Brush.horizontalGradient(listOf(lerp(ArenaColors.Xp, Color.White, 0.3f), ArenaColors.Xp)),
                glow = ArenaColors.Xp,
                height = 6.dp,
            )
            Text(
                text = stringResource(
                    MR.strings.friends_xp_to_level_named,
                    formatNumber(me.level.toNext),
                    me.level.level + 1,
                    Levels.name(me.level.level + 1),
                ),
                color = ArenaColors.OnHero.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Today's duel: the face-off, the tug-of-war and the rivalry's record (iOS ArenaDuelPanel). */
@Composable
fun RivalryHero(model: ArenaUiModel, modifier: Modifier = Modifier) {
    val me = model.me
    val rivalColor = model.rivalId?.let { playerColor(it, model) } ?: Color(0xFFFF9500)
    val todayPlace = me?.takeIf { model.players.size > 1 }?.let { mine ->
        1 + model.players.values.count { it.todayChapters > mine.todayChapters }
    }
    ArenaPanel(
        left = ArenaColors.Me,
        right = rivalColor,
        cornerRadius = 28.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(MR.strings.friends_rivalry).uppercase(),
                    color = ArenaColors.OnHero,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.weight(1f),
                )
                if (model.players.size > 2) {
                    Text(
                        text = stringResource(MR.strings.friends_vs_next),
                        color = ArenaColors.OnHero.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                todayPlace?.let { PlacePill(it) }
            }
            FaceOff(model = model, avatarSize = 76.dp)
            TugOfWar(model = model, barHeight = 16.dp)
            if (model.rival != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        icon = "⚔️",
                        label = stringResource(MR.strings.friends_days_wlt),
                        value = "${model.record.won}–${model.record.lost}–${model.record.tied}",
                        description = stringResource(
                            MR.strings.friends_record_value,
                            model.record.won,
                            model.record.lost,
                            model.record.tied,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = "⇄",
                        label = stringResource(MR.strings.friends_lead_changes),
                        value = model.leadChanges.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = "🔥",
                        label = if (model.winStreak > 0) {
                            stringResource(MR.strings.friends_won_in_a_row, stringResource(MR.strings.friends_you))
                        } else {
                            stringResource(MR.strings.friends_win_streak)
                        },
                        value = if (model.winStreak > 0) model.winStreak.toString() else "–",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "#2 TODAY", gold when first. */
@Composable
fun PlacePill(place: Int, modifier: Modifier = Modifier) {
    val first = place == 1
    Text(
        text = stringResource(MR.strings.friends_place_today, place),
        color = if (first) Color.Black else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(if (first) tierBrush(Tier.GOLD) else SolidColor(Color.White.copy(alpha = 0.15f)))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatTile(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 9.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) {
                if (description != null) contentDescription = "$label: $description"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = icon, color = ArenaColors.OnHero.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(
                text = value,
                color = ArenaColors.OnHero,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
        Text(
            text = label,
            color = ArenaColors.OnHero.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChallengesSection(model: ArenaUiModel, onPlaceBet: (ChallengeCard) -> Unit = {}) {
    val trophies = model.me?.wins?.size ?: 0
    ArenaSection(
        title = stringResource(MR.strings.friends_challenges),
        emoji = "🏁",
        card = false,
        trailing = if (trophies > 0) {
            { SectionTrailing("🏆 × $trophies") }
        } else {
            null
        },
    ) {
        model.activeChallenges.forEach {
            LiveChallengeCard(it, model)
            BetPanel(it, model, onPlaceBet)
        }
        model.upcoming?.let { UpcomingChallengeCard(it) }
        if (model.pastChallenges.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(model.pastChallenges.take(12), key = { it.challenge.id }) { PastChallengeTile(it, model) }
            }
        }
        if (model.activeChallenges.isEmpty() && model.upcoming == null && model.pastChallenges.isEmpty()) {
            Text(
                text = stringResource(MR.strings.friends_no_challenge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.arenaCard(),
            )
        }
    }
}

/** A running challenge: big emoji, LIVE, countdown, and everyone's score as racing bars (iOS ActiveChallengeCard). */
@Composable
private fun LiveChallengeCard(card: ChallengeCard, model: ArenaUiModel) {
    val max = card.standings.maxOfOrNull { it.score }?.takeIf { it > 0 } ?: 1.0
    val leaderColor = card.leaders.firstOrNull()?.let { playerColor(it, model) } ?: Color(0xFFFF9500)
    ArenaPanel(
        left = leaderColor,
        right = playerColor(model.rivalId ?: model.myId, model),
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(leaderColor.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = card.challenge.emoji, fontSize = 30.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiveBadge()
                        Text(
                            text = metricLabel(card.challenge),
                            color = ArenaColors.OnHero.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = card.challenge.title,
                        color = ArenaColors.OnHero,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = card.challenge.description,
                        color = ArenaColors.OnHero.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⏱ " + stringResource(MR.strings.friends_time_left, countdownText(card.challenge.end)),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = leaderText(card, model, finished = false),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.standings.forEachIndexed { index, standing ->
                    ChallengeRow(card, standing, index, max, model)
                }
            }
        }
    }
}

@Composable
private fun ChallengeRow(
    card: ChallengeCard,
    standing: ChallengeStanding,
    index: Int,
    max: Double,
    model: ArenaUiModel,
) {
    val isMe = standing.userId == model.myId
    val player = model.players[standing.userId]
    val leads = standing.userId in card.leaders
    val color = playerColor(standing.userId, model)
    val fraction by animateFloatAsState((standing.score / max).toFloat(), motion(tween(900)), label = "challengeBar")
    Row(
        modifier = Modifier.openPlayer(standing.userId),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (leads) "👑" else "${index + 1}",
            color = ArenaColors.OnHero.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        FriendAvatar(name = player?.name.orEmpty(), url = player?.avatarUrl, size = 28.dp, color = color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row {
                Text(
                    text = if (isMe) stringResource(MR.strings.friends_you) else player?.name.orEmpty(),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatScore(standing.score),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
                    )
                }
            }
        }
    }
}

/** "You lead", "Marko won", "Tied at the top"... as on iOS. */
@Composable
fun leaderText(card: ChallengeCard, model: ArenaUiModel, finished: Boolean): String {
    val leaders = card.leaders.sorted()
    val only = leaders.singleOrNull()
    return when {
        leaders.isEmpty() -> stringResource(
            if (finished) MR.strings.friends_leader_nobody_won else MR.strings.friends_leader_none,
        )
        only == model.myId -> stringResource(
            if (finished) MR.strings.friends_leader_you_won else MR.strings.friends_leader_you_lead,
        )
        only != null -> stringResource(
            if (finished) MR.strings.friends_leader_won else MR.strings.friends_leader_leads,
            model.name(only),
        )
        else -> stringResource(if (finished) MR.strings.friends_leader_shared else MR.strings.friends_leader_tied)
    }
}

@Composable
private fun metricLabel(challenge: mihon.feature.friends.data.Challenge): String = when (challenge.metric) {
    "genre" -> challenge.genre ?: stringResource(MR.strings.friends_metric_genre)
    "minutes" -> stringResource(MR.strings.friends_metric_minutes)
    "series" -> stringResource(MR.strings.friends_metric_series)
    "night" -> stringResource(MR.strings.friends_metric_night)
    "early" -> stringResource(MR.strings.friends_metric_early)
    "weekend" -> stringResource(MR.strings.friends_metric_weekend)
    "best_day" -> stringResource(MR.strings.friends_metric_best_day)
    "streak" -> stringResource(MR.strings.friends_metric_streak)
    else -> stringResource(MR.strings.friends_metric_chapters)
}

/** The next challenge, locked until it starts, on a dashed outline (iOS UpcomingChallengeCard). */
@Composable
private fun UpcomingChallengeCard(challenge: mihon.feature.friends.data.Challenge) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = primary.copy(alpha = 0.35f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = challenge.emoji, fontSize = 26.sp)
            }
            Text(
                text = "🔒",
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(primary)
                    .padding(3.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(MR.strings.friends_up_next).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = primary,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = challenge.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(MR.strings.friends_starts_in_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = countdownText(challenge.start),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** A finished challenge as a trophy tile, gold when you won it (iOS PastChallengesRow). */
@Composable
private fun PastChallengeTile(card: ChallengeCard, model: ArenaUiModel) {
    val won = model.myId in card.leaders
    val shape = RoundedCornerShape(18.dp)
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val winner = card.leaders.singleOrNull()?.let { model.players[it] }
    val best = card.standings.maxOfOrNull { it.score } ?: 0.0
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(164.dp)
            .then(if (won) Modifier.shadow(8.dp, shape, ambientColor = GoldColor, spotColor = GoldColor) else Modifier)
            .clip(shape)
            .background(
                if (won) {
                    Brush.linearGradient(listOf(lerp(cardColor, GoldColor, 0.28f), cardColor))
                } else {
                    SolidColor(cardColor)
                },
            )
            .border(1.5.dp, if (won) GoldColor.copy(alpha = 0.8f) else Color.Transparent, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = card.challenge.emoji, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Text(text = if (won) "🏆" else "🏁", fontSize = 16.sp, modifier = Modifier.alpha(if (won) 1f else 0.6f))
        }
        Text(
            text = card.challenge.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (winner != null) {
                FriendAvatar(
                    name = winner.name,
                    url = winner.avatarUrl,
                    size = 18.dp,
                    color = playerColor(winner.userId, model),
                )
            }
            Text(
                text = leaderText(card, model, finished = true),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (best > 0) formatScore(best) else stringResource(MR.strings.friends_no_score),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BetResult(model.me?.settledBets?.firstOrNull { it.challengeId == card.challenge.id })
    }
}

fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) formatNumber(score.toLong()) else String.format(Locale.getDefault(), "%.1f", score)

private val QuestGreen = Color(0xFF34D399)

/** Today's three quests with rings, bars and their XP, plus the perfect-day row (iOS ArenaQuestsCard). */
@Composable
fun QuestsSection(model: ArenaUiModel) {
    val done = model.quests.count { it.done }
    val tomorrow = remember(model.today, model.zone) { model.today.plusDays(1).atStartOfDay(model.zone).toInstant() }
    ArenaSection(
        title = stringResource(MR.strings.friends_quests_today),
        emoji = "📜",
        card = false,
        trailing = { SectionTrailing(stringResource(MR.strings.friends_quests_done_count, done, model.quests.size)) },
    ) {
        ArenaPanel(
            left = QuestGreen,
            right = MaterialTheme.colorScheme.primary,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                model.quests.forEach { QuestRow(it) }
                PerfectDayRow(model, tomorrow)
            }
        }
    }
}

@Composable
private fun PerfectDayRow(model: ArenaUiModel, tomorrow: java.time.Instant) {
    val perfect = model.perfectToday
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (perfect) {
                    Brush.horizontalGradient(
                        listOf(GoldColor.copy(alpha = 0.55f), Color(0xFFFF8A3D).copy(alpha = 0.35f)),
                    )
                } else {
                    SolidColor(Color.White.copy(alpha = 0.07f))
                },
            )
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🌟", fontSize = 20.sp, modifier = Modifier.alpha(if (perfect) 1f else 0.5f))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(
                    if (perfect) MR.strings.friends_perfect_day_done else MR.strings.friends_perfect_day_hint,
                ),
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            model.me?.let { me ->
                Text(
                    text = stringResource(MR.strings.friends_quest_totals, me.questsCompleted, me.perfectDays),
                    color = ArenaColors.OnHero.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(MR.strings.friends_new_quests_in),
                color = ArenaColors.OnHero.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = countdownText(tomorrow),
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun QuestRow(progress: QuestProgress) {
    val done = progress.done
    val checkScale by animateFloatAsState(if (done) 1f else 0f, motion(bouncy()), label = "check")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressRing(
            fraction = progress.fraction,
            colors = listOf(QuestGreen, QuestGreen),
            size = 46.dp,
            stroke = 4.dp,
        ) {
            if (done) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Check,
                    contentDescription = null,
                    tint = QuestGreen,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                )
            } else {
                Text(text = progress.quest.emoji, fontSize = 18.sp)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = progress.quest.label,
                color = ArenaColors.OnHero.copy(alpha = if (done) 0.75f else 1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            XpBar(
                progress = progress.fraction,
                brush = Brush.horizontalGradient(listOf(QuestGreen.copy(alpha = 0.7f), QuestGreen)),
                glow = QuestGreen,
                height = 6.dp,
            )
            Text(
                text = if (done) {
                    stringResource(MR.strings.friends_quest_completed)
                } else {
                    "${progress.value.coerceAtMost(progress.quest.target)}/${progress.quest.target}"
                },
                color = ArenaColors.OnHero.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(MR.strings.friends_quest_reward, Xp.PER_QUEST.toInt()),
            color = Color.White.copy(alpha = if (done) 1f else 0.75f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .then(if (done) Modifier.shadow(6.dp, CircleShape, spotColor = ArenaColors.Xp) else Modifier)
                .clip(CircleShape)
                .background(
                    if (done) {
                        Brush.verticalGradient(listOf(lerp(ArenaColors.Xp, Color.White, 0.15f), ArenaColors.Xp))
                    } else {
                        SolidColor(Color.White.copy(alpha = 0.1f))
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun BoardsSection(model: ArenaUiModel, boards: LeagueUiModel?, tab: BoardTab, onTabChange: (BoardTab) -> Unit) {
    ArenaSection(stringResource(MR.strings.friends_boards), "🔢", card = false) {
        PeriodPicker(
            options = BoardTab.entries,
            selected = tab,
            label = { boardTabTitle(it) },
            onSelect = onTabChange,
        )
        if (tab == BoardTab.SEASON) {
            SeasonBoard(model)
        } else if (boards != null) {
            StatusBanner(boards)
            Podium(boards.standings, boards.myId, colorOf = { playerColor(it, model) })
            BoardList {
                boards.standings.forEachIndexed { index, standing ->
                    if (index > 0) BoardDivider()
                    StandingRow(
                        standing = standing,
                        isMe = standing.row.userId == boards.myId,
                        color = playerColor(standing.row.userId, model),
                    )
                }
            }
        }
    }
}

@Composable
fun boardTabTitle(tab: BoardTab): String = stringResource(
    when (tab) {
        BoardTab.TODAY -> MR.strings.friends_period_today
        BoardTab.WEEK -> MR.strings.friends_period_week
        BoardTab.MONTH -> MR.strings.friends_period_month
        BoardTab.SEASON -> MR.strings.friends_period_season
        BoardTab.ALL -> MR.strings.friends_period_all
    },
)

@Composable
fun periodTitle(period: LeaguePeriod): String = boardTabTitle(BoardTab.entries.first { it.period == period })

/** Periods as a capsule with a highlighted choice, as on iOS. */
@Composable
fun <T> PeriodPicker(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            Brush.verticalGradient(listOf(lerp(primary, Color.White, 0.12f), primary))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    )
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** The leaderboard's rows on one card, separated by hairlines. */
@Composable
private fun BoardList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

@Composable
private fun BoardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
}

@Composable
private fun SeasonBoard(model: ArenaUiModel) {
    BoardList {
        model.seasonRanking.forEachIndexed { index, id ->
            if (index > 0) BoardDivider()
            SeasonRow(model, id)
        }
    }
}

@Composable
private fun SeasonRow(model: ArenaUiModel, id: String) {
    val player = model.players.getValue(id)
    val isMe = id == model.myId
    val place = model.seasonPlaces[id] ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .openPlayer(id)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = place.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (place == 1) Color(0xFFFF9500) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center,
        )
        FriendAvatar(name = player.name, url = player.avatarUrl, size = 40.dp, color = playerColor(id, model))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TierBadge(tier = player.tier, size = 16.dp, glow = false)
                Text(
                    text = if (isMe) "${player.name} (${stringResource(MR.strings.friends_you)})" else player.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(MR.strings.friends_tier, player.tier.label) + " · " +
                    stringResource(MR.strings.friends_level_short, player.level.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            RollingNumber(
                value = player.seasonXp,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Medals, challenge trophies and finished seasons, per player (iOS ArenaCabinetCard). */
@Composable
fun CabinetSection(model: ArenaUiModel, boards: LeagueUiModel) {
    ArenaSection(stringResource(MR.strings.friends_trophy_cabinet), "🏆") {
        model.seasonRanking.mapNotNull { model.players[it] }.forEachIndexed { index, player ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            CabinetShelf(model, player, boards.cabinets[player.userId] ?: MedalCabinet())
        }
        Text(
            text = stringResource(MR.strings.friends_cabinet_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CabinetShelf(model: ArenaUiModel, player: ArenaPlayer, cabinet: MedalCabinet) {
    val isMe = player.userId == model.myId
    val total = cabinet.total
    Column(modifier = Modifier.openPlayer(player.userId), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FriendAvatar(
                name = player.name,
                url = player.avatarUrl,
                size = 30.dp,
                color = playerColor(player.userId, model),
            )
            Text(
                text = if (isMe) stringResource(MR.strings.friends_you) else player.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            listOf(Medal.GOLD to total.gold, Medal.SILVER to total.silver, Medal.BRONZE to total.bronze)
                .forEach { (medal, count) ->
                    Row(
                        modifier = Modifier.alpha(if (count > 0) 1f else 0.35f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(text = medal.emoji)
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
        }
        Column(modifier = Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    MR.strings.friends_medal_line,
                    cabinet.day.dots(),
                    cabinet.week.dots(),
                    cabinet.month.dots(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (player.wins.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    player.wins.forEach { challenge ->
                        Text(
                            text = "🏆 ${challenge.emoji} ${challenge.title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GoldColor.copy(alpha = 0.2f))
                                .border(1.dp, GoldColor.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            val seasons = model.seasonHistories[player.userId].orEmpty()
            if (isMe && seasons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    seasons.take(6).forEach { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TierBadge(result.tier, 18.dp, glow = false)
                            Text(
                                text = "S${result.number} · #${result.place}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MedalTally.dots(): String = "$gold·$silver·$bronze"

@Composable
fun AchievementsSection(model: ArenaUiModel) {
    val me = model.me ?: return
    AchievementsGrid(me.achievements)
}

/** Lifetime achievements: unlocked ones glow in their colour, locked ones show their progress (iOS). */
@Composable
fun AchievementsGrid(achievements: List<AchievementProgress>, modifier: Modifier = Modifier) {
    val unlocked = achievements.count { it.unlocked }
    val sorted = achievements.sortedWith(
        compareBy<AchievementProgress> {
            !it.unlocked
        }.thenByDescending { it.fraction },
    )
    ArenaSection(
        title = stringResource(MR.strings.friends_achievements),
        emoji = "🏵️",
        modifier = modifier,
        trailing = { SectionTrailing("$unlocked/${achievements.size}") },
    ) {
        sorted.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { AchievementBadge(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AchievementBadge(progress: AchievementProgress, modifier: Modifier = Modifier) {
    val unlocked = progress.unlocked
    val achievement = progress.achievement
    val color = Color(achievement.color)
    val target = achievementTargetText(achievement)
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${achievement.label}, $target, " +
                "${progress.value.coerceAtMost(achievement.target)}/${achievement.target}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (unlocked) {
                        Modifier.shadow(
                            8.dp,
                            CircleShape,
                            spotColor = color,
                            ambientColor = color,
                        )
                    } else {
                        Modifier
                    },
                )
                .drawBehind {
                    val radius = size.minDimension / 2
                    if (unlocked) {
                        drawCircle(
                            Brush.linearGradient(
                                listOf(lerp(color, Color.White, 0.3f), color, lerp(color, Color.Black, 0.3f)),
                            ),
                        )
                        drawCircle(
                            Color.White.copy(alpha = 0.5f),
                            radius = radius * 0.93f - 0.75.dp.toPx(),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    } else {
                        drawCircle(track)
                        val stroke = 3.dp.toPx()
                        drawArc(
                            color.copy(alpha = 0.8f),
                            startAngle = -90f,
                            sweepAngle = 360f * progress.fraction,
                            useCenter = false,
                            topLeft = Offset(stroke, stroke),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = achievement.emoji, fontSize = 22.sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.55f))
        }
        Text(
            text = achievement.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (unlocked) {
                stringResource(MR.strings.friends_achievement_unlocked)
            } else {
                "${formatNumber(progress.value.coerceAtMost(achievement.target))}/${formatNumber(achievement.target)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What an achievement asks for, e.g. "100 chapters"; units match [mihon.feature.friends.domain.Achievements]. */
private fun achievementTargetText(achievement: Achievement): String = when (achievement) {
    Achievement.FIRST_CHAPTER -> "1 chapter"
    Achievement.BOOKWORM, Achievement.LIBRARY_CARD, Achievement.ARCHIVIST -> "${achievement.target} chapters"
    Achievement.WEEK_WARRIOR, Achievement.UNSTOPPABLE -> "${achievement.target}-day streak"
    Achievement.NIGHT_OWL -> "${achievement.target} chapters 00–05"
    Achievement.EARLY_BIRD -> "${achievement.target} chapters 05–09"
    Achievement.GENRE_HOPPER -> "${achievement.target} genres"
    Achievement.COLLECTOR -> "${achievement.target} series"
    Achievement.MARATHONER -> "${achievement.target} h reading"
    Achievement.CHAMPION -> "Win a challenge"
    Achievement.DYNASTY -> "Win 3 in a row"
    Achievement.PERFECTIONIST -> "${achievement.target} perfect days"
    Achievement.TASTE_MAKER -> "${achievement.target} suggestions added"
}
