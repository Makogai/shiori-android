package mihon.feature.friends.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.util.relativeTimeSpanString
import mihon.feature.friends.ui.ArenaSection
import mihon.feature.friends.ui.ArenaSectionTitle
import mihon.feature.friends.ui.SectionTrailing
import mihon.feature.friends.ui.arenaCard
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val CARDS_ACCENT = androidx.compose.ui.graphics.Color(0xFFC026D3)

/** The Arena's "Shiori Cards" section: my counts per grade and the way to the Shiori Cards hub. */
@Composable
fun ArenaCardsSection() {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<CardsViewModel>()
    val repository = viewModel.repository
    val album by repository.album.collectAsStateWithLifecycle()
    val pulled by repository.pulledToday.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    LifecycleResumeEffect(Unit) {
        repository.resumeWatching()
        onPauseOrDispose {}
    }
    val mine = album.filter { it.owner == repository.me }
    // A compact entry to the hub (CARD_SPEC §16): the whole card opens it, as on iOS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArenaSectionTitle(
            title = stringResource(MR.strings.cards_title),
            emoji = "🃏",
            trailing = { PulledToday(pulled[repository.me] ?: 0) },
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { navigator.push(CardsHubScreen()) }
                .arenaCard(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "🃏", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(MR.strings.cards_open_hub),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(MR.strings.cards_album_counts, mine.size, album.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = MaterialSymbols.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (mine.isNotEmpty()) CardGradeCounts(mine)
        }
    }
}

/** A profile's cards: the collection by grade, the rarest card and today's pulls. */
@Composable
fun ProfileCardsSection(userId: String) {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<CardsViewModel>()
    val repository = viewModel.repository
    val album by repository.album.collectAsStateWithLifecycle()
    val pulled by repository.pulledToday.collectAsStateWithLifecycle()
    LaunchedEffect(userId) {
        viewModel.load()
        viewModel.loadPulledToday(userId)
    }
    val cards = album.filter { it.owner == userId }
    ArenaSection(
        title = stringResource(MR.strings.cards_profile),
        emoji = "🃏",
        trailing = { PulledToday(pulled[userId] ?: 0) },
    ) {
        CardGradeCounts(cards)
        val rarest = CardAlbum.rarest(cards)
        if (rarest == null) {
            Text(
                text = stringResource(MR.strings.cards_none_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CardThumb(card = rarest, caption = rarest.grade.label, modifier = Modifier.width(110.dp)) {
                    navigator.push(CardDetailScreen(rarest.id))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(MR.strings.cards_rarest),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = rarest.cardName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = rarest.grade.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = repository.accent(rarest.grade),
                    )
                    Text(
                        text = rarest.seriesLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(MR.strings.cards_collected, cards.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "Pulled today 3/10" at the end of a section title. */
@Composable
internal fun PulledToday(count: Int) {
    SectionTrailing(stringResource(MR.strings.cards_pulled_today, count, CardRules.DAILY_LIMIT))
}

/** A feed line for a rank C+ pull: "Filip pulled Rank B · The Crimson Swordsman"; tap opens the card. */
@Composable
fun CardFeedRow(card: ShioriCard) {
    val navigator = LocalNavigator.currentOrThrow
    val repository = metroViewModel<CardsViewModel>().repository
    val accent = repository.accent(card.grade)
    Row(
        modifier = Modifier.clickable { navigator.push(CardDetailScreen(card.id)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = card.grade.rank, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    MR.strings.cards_feed_pulled,
                    cardOwnerName(repository, card.owner),
                    card.grade.label,
                    card.cardName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${card.seriesTitle} · ${relativeTimeSpanString(card.createdInstant.toEpochMilli())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CardMiniature(card, Modifier.width(28.dp), glow = false)
    }
}
