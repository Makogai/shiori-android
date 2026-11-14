package mihon.feature.friends.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import mihon.feature.friends.data.FriendsPreferences
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.Lead
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.EmojiEvents
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Immutable
data class ArenaSummary(
    val configured: Boolean,
    val registered: Boolean,
    val model: ArenaUiModel? = null,
    val showCard: Boolean = false,
    /** The 🃏 Shiori Cards button in the Library top bar (CARD_SPEC §16). */
    val showCardsButton: Boolean = false,
    /** The strip of my latest cards at the top of the Library. */
    val showCardsStrip: Boolean = false,
) {
    companion object {
        /** The Library cards strip: the setting on and a Friends account signed in (cards are per player). */
        fun showsCardsStrip(setting: Boolean, configured: Boolean, userId: String?): Boolean =
            setting && configured && userId != null
    }

    /** Own season place, or null before earning season XP. */
    val rank: Int? get() = model?.let { m -> m.me?.takeIf { it.seasonXp > 0 }?.let { m.seasonPlaces[it.userId] } }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ArenaSummaryViewModel(
    repository: FriendsRepository,
    preferences: FriendsPreferences,
) : ViewModel() {

    val summary: StateFlow<ArenaSummary> = combine(
        repository.account,
        repository.snapshot,
        preferences.showLibraryBanner.changes(),
        preferences.showCardsButton.changes(),
        preferences.showLibraryCards.changes(),
    ) { account, snapshot, showCard, showCards, showStrip ->
        val userId = account.userId
        ArenaSummary(
            configured = account.configured,
            registered = userId != null,
            model = if (snapshot != null && userId != null) ArenaUiModel.build(snapshot, userId) else null,
            showCard = showCard && account.configured,
            showCardsButton = showCards && account.configured && userId != null,
            showCardsStrip = ArenaSummary.showsCardsStrip(showStrip, account.configured, userId),
        )
    }
        .onEach { summary -> summary.model?.let { repository.recordQuests(ArenaUiModel.completedQuestKeys(it)) } }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            ArenaSummary(configured = false, registered = false),
        )
}

/** 🃏: opens the Shiori Cards hub (CARD_SPEC §16). */
@Composable
fun CardsToolbarAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(text = "🃏", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ArenaToolbarAction(summary: ArenaSummary, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                val rank = summary.rank
                if (rank != null) {
                    Badge(
                        containerColor = if (rank == 1) GoldColor else MaterialTheme.colorScheme.primary,
                        contentColor = if (rank == 1) Color(0xFF1A1300) else MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text("#$rank")
                    }
                }
            },
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.EmojiEvents,
                contentDescription = stringResource(MR.strings.friends_arena),
            )
        }
    }
}

/** The Arena card at the top of the Library: face-off, tug-of-war, the live challenge and today's quests. */
@Composable
fun LibraryArenaCard(
    summary: ArenaSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSuggestions: () -> Unit = onClick,
) {
    if (!summary.showCard) return
    if (!summary.registered) {
        ArenaTeaserCard(onClick = onClick, modifier = modifier)
        return
    }
    // Registered but not loaded yet: show nothing rather than a placeholder that jumps
    val model = summary.model?.takeIf { it.me != null } ?: return
    ArenaHeroCard(model = model, onClick = onClick, modifier = modifier, onOpenSuggestions = onOpenSuggestions)
}

@Composable
fun ArenaHeroCard(
    model: ArenaUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSuggestions: () -> Unit = onClick,
) {
    val me = model.me ?: return
    val description = arenaDescription(model)
    val glow = when (model.lead) {
        Lead.ME -> ArenaColors.Me
        Lead.RIVAL -> ArenaColors.Rival
        Lead.TIED -> me.tier.composeColor
    }
    GlowCard(
        brush = heroBrush(me.tier.composeColor),
        glow = glow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 360.dp
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚔️ " + stringResource(MR.strings.friends_arena).uppercase(),
                        color = ArenaColors.OnHeroMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(MR.strings.friends_season_short, model.seasonNumber),
                        color = ArenaColors.OnHeroMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = MaterialSymbols.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ArenaColors.OnHeroMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                FaceOff(model = model, avatarSize = if (compact) 40.dp else 48.dp, showNames = false)
                TugOfWar(model = model, barHeight = 20.dp)
                model.activeChallenges.firstOrNull()?.let { challenge ->
                    ChallengeChip(card = challenge, model = model)
                }
                if (model.quests.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        model.quests.forEach { QuestPill(it) }
                        if (model.perfectToday) Text(text = "🌟", fontSize = 20.sp)
                    }
                }
                if (model.newSuggestions > 0) {
                    SuggestionsChip(
                        count = model.newSuggestions,
                        onClick = onOpenSuggestions,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/** Shown before registering: an invitation into the Arena. */
@Composable
private fun ArenaTeaserCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlowCard(
        brush = heroBrush(ArenaColors.Rival),
        glow = ArenaColors.Rival,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "⚔️", fontSize = 34.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(MR.strings.friends_register_title),
                    color = ArenaColors.OnHero,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(MR.strings.friends_register_summary),
                    color = ArenaColors.OnHeroMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MaterialSymbols.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ArenaColors.OnHero,
            )
        }
    }
}

/** Both players facing each other with their level rings and tier badges, today's chapters under each. */
@Composable
fun FaceOff(model: ArenaUiModel, avatarSize: Dp, modifier: Modifier = Modifier, showNames: Boolean = true) {
    val me = model.me ?: return
    val rival = model.rival
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FighterColumn(
            player = me,
            displayName = stringResource(MR.strings.friends_you),
            team = ArenaColors.Me,
            avatarSize = avatarSize,
            showName = showNames,
            leading = model.lead == Lead.ME,
            modifier = Modifier.weight(1f),
        )
        VsEmblem(size = avatarSize * 0.8f)
        if (rival != null) {
            FighterColumn(
                player = rival,
                displayName = rival.name,
                team = ArenaColors.Rival,
                avatarSize = avatarSize,
                showName = showNames,
                mirrored = true,
                leading = model.lead == Lead.RIVAL,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "?", color = ArenaColors.OnHeroMuted, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun FighterColumn(
    player: ArenaPlayer,
    displayName: String,
    team: Color,
    avatarSize: Dp,
    leading: Boolean,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    mirrored: Boolean = false,
) {
    val scale by animateFloatAsState(if (leading) 1.06f else 0.96f, motion(bouncy()), label = "fighter")
    val avatar = @Composable {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .openPlayer(player.userId),
        ) {
            LevelAvatar(
                name = player.name,
                url = player.avatarUrl,
                level = player.level.level,
                levelProgress = player.level.progress,
                tier = player.tier,
                size = avatarSize,
                team = team,
            )
            if (leading) {
                Text(
                    text = "👑",
                    fontSize = (avatarSize.value / 4).coerceAtLeast(12f).sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 2.dp),
                )
            }
        }
    }
    val count = @Composable {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RollingNumber(
                value = player.todayChapters,
                style = TextStyle(
                    fontSize = (avatarSize.value * 0.42f).coerceIn(20f, 28f).sp,
                    fontWeight = FontWeight.Black,
                    color = team,
                ),
            )
            Text(
                text = "ch",
                color = ArenaColors.OnHeroMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
    if (!showName) {
        // Compact: count beside the avatar, facing the middle
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (mirrored) Alignment.End else Alignment.Start),
        ) {
            if (mirrored) count()
            avatar()
            if (!mirrored) count()
        }
        return
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        avatar()
        Text(
            text = displayName,
            color = ArenaColors.OnHero,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 10.dp)
                .openPlayer(player.userId),
        )
        count()
    }
}

@Composable
fun VsEmblem(size: Dp, modifier: Modifier = Modifier) {
    val pulse by rememberLoop(1_400, reverse = true, constant = 0.5f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB13D).copy(alpha = 0.35f + 0.25f * pulse), Color.Transparent),
                    center = center,
                    radius = this.size.minDimension / 2,
                ),
            )
        }
        Text(
            text = stringResource(MR.strings.friends_vs),
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFF3B0), Color(0xFFFFB13D), Color(0xFFFF5A3D)),
                    start = Offset.Zero,
                    end = Offset(0f, 60f),
                ),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            ),
        )
    }
}

/** Tug-of-war bar with the status line underneath. */
@Composable
fun TugOfWar(model: ArenaUiModel, modifier: Modifier = Modifier, barHeight: Dp = 26.dp) {
    val rival = model.rival
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TugOfWarBar(
            share = model.tugShare,
            height = barHeight,
            leftLeads = when (model.lead) {
                Lead.ME -> true
                Lead.RIVAL -> false
                Lead.TIED -> null
            },
        )
        val gap = model.todayGap.toInt()
        Text(
            text = when {
                rival == null -> stringResource(MR.strings.friends_tug_waiting)
                model.lead == Lead.ME -> pluralStringResource(MR.plurals.friends_tug_lead, gap, gap)
                model.lead == Lead.RIVAL -> pluralStringResource(MR.plurals.friends_tug_behind, gap, gap, rival.name)
                else -> stringResource(MR.strings.friends_tug_tied)
            },
            color = ArenaColors.OnHero,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Live challenge: emoji, title, countdown and who leads. */
@Composable
fun ChallengeChip(card: ChallengeCard, model: ArenaUiModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFB13D), Color(0xFFFF5A3D)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = card.challenge.emoji, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.challenge.title,
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = leaderText(card, model) + " · " +
                    stringResource(MR.strings.friends_time_left, countdownText(card.challenge.end)),
                color = ArenaColors.OnHeroMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiveBadge()
    }
}

@Composable
fun leaderText(card: ChallengeCard, model: ArenaUiModel): String = when (card.leaders.size) {
    0 -> stringResource(MR.strings.friends_challenge_no_leader)
    1 -> {
        val id = card.leaders.first()
        stringResource(
            MR.strings.friends_challenge_leader,
            if (id == model.myId) stringResource(MR.strings.friends_you) else model.name(id),
        )
    }
    else -> stringResource(MR.strings.friends_challenge_tied)
}

@Composable
private fun arenaDescription(model: ArenaUiModel): String {
    val me = model.me ?: return ""
    val rival = model.rival
    return buildList {
        add(stringResource(MR.strings.friends_arena))
        add(stringResource(MR.strings.friends_level_and_tier, me.level.level, me.level.name, me.tier.label))
        if (rival != null) {
            add(
                stringResource(
                    MR.strings.friends_card_summary,
                    stringResource(MR.strings.friends_you),
                    rival.name,
                    me.todayChapters.toInt(),
                    rival.todayChapters.toInt(),
                ),
            )
        }
        model.activeChallenges.firstOrNull()?.let { add("${it.challenge.title}: ${leaderText(it, model)}") }
        model.quests.forEach { add("${it.quest.label} ${it.value.coerceAtMost(it.quest.target)}/${it.quest.target}") }
    }.joinToString(". ")
}
