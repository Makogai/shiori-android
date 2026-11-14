package mihon.feature.friends.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Accent for a player: you in the theme colour, your rival in coral, others from the member palette. */
@Composable
fun playerColor(userId: String?, arena: ArenaUiModel): Color = when (userId) {
    null -> PurpleColor
    arena.myId -> ArenaColors.Me
    arena.rivalId -> ArenaColors.Rival
    else -> memberColor(userId, arena.players.keys.toList())
}

@Composable
fun GenreWarsSection(arena: ArenaUiModel, wars: GenreWarsModel, onPeriodChange: (GenrePeriod) -> Unit) {
    var selected by rememberSaveable(wars.period) { mutableStateOf<String?>(null) }
    ArenaSection(stringResource(MR.strings.friends_genre_wars), "⚔️") {
        PeriodPicker(
            options = GenrePeriod.entries,
            selected = wars.period,
            label = {
                stringResource(
                    when (it) {
                        GenrePeriod.WEEK -> MR.strings.friends_period_week
                        GenrePeriod.SEASON -> MR.strings.friends_period_season
                        GenrePeriod.ALL -> MR.strings.friends_period_all
                    },
                )
            },
            onSelect = onPeriodChange,
            track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        wars.challenges.forEach { GenreChallengeBanner(it, arena) }
        if (wars.territories.isEmpty()) {
            Text(
                text = stringResource(MR.strings.friends_no_genres),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ArenaSection
        }
        if (wars.crowns.isNotEmpty()) {
            SubHeading(stringResource(MR.strings.friends_genre_crowns))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                wars.crowns.forEach { (id, count) ->
                    val player = arena.players[id]
                    val color = playerColor(id, arena)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.14f))
                            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
                            .openPlayer(id)
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FriendAvatar(name = player?.name.orEmpty(), url = player?.avatarUrl, size = 28.dp)
                        Text(text = "👑 $count", fontWeight = FontWeight.Black, color = color)
                    }
                }
            }
        }
        SubHeading(stringResource(MR.strings.friends_genre_territories))
        wars.territories.take(MAX_TERRITORIES).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { territory ->
                    TerritoryTile(
                        territory = territory,
                        arena = arena,
                        selected = territory.genre == selected,
                        onClick = { selected = territory.genre.takeIf { it != selected } },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            val open = row.firstOrNull { it.genre == selected }
            AnimatedVisibility(
                visible = open != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                open?.let { GenreBoard(it, arena) }
            }
        }
        if (wars.duels.isNotEmpty()) {
            SubHeading(stringResource(MR.strings.friends_genre_duels, arena.rival?.name.orEmpty()))
            wars.duels.forEach { duel ->
                GenreDuelRow(duel)
            }
        }
        if (wars.myTop.isNotEmpty() || wars.rivalTop.isNotEmpty()) {
            SubHeading(stringResource(MR.strings.friends_top_genres))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopGenresColumn(
                    title = stringResource(MR.strings.friends_you),
                    genres = wars.myTop,
                    color = ArenaColors.Me,
                    modifier = Modifier.weight(1f),
                )
                if (arena.rival != null) {
                    TopGenresColumn(
                        title = arena.rival!!.name,
                        genres = wars.rivalTop,
                        color = ArenaColors.Rival,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val MAX_TERRITORIES = 12

@Composable
fun SubHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun GenreChallengeBanner(card: ChallengeCard, arena: ArenaUiModel) {
    GlowCard(
        brush = Brush.linearGradient(listOf(Color(0xFF3A0F1A), Color(0xFF1E0B2E))),
        glow = ArenaColors.Rival,
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = card.challenge.emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        MR.strings.friends_genre_challenge,
                        card.challenge.genre.orEmpty(),
                    ).uppercase(),
                    color = ArenaColors.Rival,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = card.challenge.title,
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = leaderText(card, arena) + " · " +
                        stringResource(MR.strings.friends_time_left, countdownText(card.challenge.end)),
                    color = ArenaColors.OnHeroMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LiveBadge()
        }
    }
}

@Composable
private fun TerritoryTile(
    territory: GenreTerritory,
    arena: ArenaUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = territory.owner?.let { arena.players[it] }
    val color = when {
        owner != null -> playerColor(owner.userId, arena)
        territory.contested -> PurpleColor
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = RoundedCornerShape(16.dp)
    val status = when {
        owner != null -> stringResource(
            MR.strings.friends_genre_owned_by,
            if (owner.userId == arena.myId) stringResource(MR.strings.friends_you) else owner.name,
            territory.genre,
        )
        territory.contested -> stringResource(MR.strings.friends_genre_contested)
        else -> stringResource(MR.strings.friends_genre_unclaimed)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.08f))))
            .border(if (selected) 2.dp else 1.dp, color.copy(alpha = if (selected) 1f else 0.4f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${territory.genre}: $status, ${territory.total}"
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (owner != null) {
                FriendAvatar(name = owner.name, url = owner.avatarUrl, size = 36.dp, ring = color)
                Text(
                    text = "👑",
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 30.dp),
                )
            } else {
                Text(text = if (territory.contested) "⚔️" else "🏳️", fontSize = 24.sp)
            }
        }
        Text(
            text = territory.genre,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (territory.challenge != null) "🔥 ${territory.total}" else territory.total.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GenreBoard(territory: GenreTerritory, arena: ArenaUiModel) {
    val max = territory.scores.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = territory.genre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        territory.scores.forEachIndexed { index, (id, chapters) ->
            val player = arena.players[id]
            val color = playerColor(id, arena)
            val fraction by animateFloatAsState(chapters.toFloat() / max, motion(tween(700)), label = "genreBoard")
            Row(
                modifier = Modifier.openPlayer(id),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (id == territory.owner) "👑" else "#${index + 1}",
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                FriendAvatar(name = player?.name.orEmpty(), url = player?.avatarUrl, size = 28.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (id == arena.myId) stringResource(MR.strings.friends_you) else player?.name.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color, lerp(color, Color.White, 0.4f)))),
                    )
                }
                Text(text = chapters.toString(), fontWeight = FontWeight.Black, color = color)
            }
        }
    }
}

@Composable
private fun GenreDuelRow(duel: GenreDuel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = duel.mine.toString(),
                fontWeight = FontWeight.Black,
                color = ArenaColors.Me,
                modifier = Modifier.width(36.dp),
            )
            Text(
                text = duel.genre,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = duel.theirs.toString(),
                fontWeight = FontWeight.Black,
                color = ArenaColors.Rival,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp),
            )
        }
        TugOfWarBar(
            share = if (duel.mine + duel.theirs == 0L) 0.5f else duel.mine.toFloat() / (duel.mine + duel.theirs),
            height = 16.dp,
            leftLeads = when {
                duel.mine > duel.theirs -> true
                duel.mine < duel.theirs -> false
                else -> null
            },
        )
    }
}

@Composable
private fun TopGenresColumn(
    title: String,
    genres: List<Pair<String, Long>>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = genres.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        genres.forEach { (genre, chapters) ->
            Column {
                Row {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = chapters.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(chapters.toFloat() / max)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}
