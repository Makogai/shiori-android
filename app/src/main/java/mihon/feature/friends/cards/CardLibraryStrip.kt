package mihon.feature.friends.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The Library's cards strip ("Show Cards on Library"): my latest cards in a row, "See all" opening the album.
 * Nothing shows until I have a card. [onOpen] gets the card and the strip's ids for the detail carousel.
 */
@Composable
fun LibraryCardsStrip(
    onSeeAll: () -> Unit,
    onOpen: (card: ShioriCard, ids: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = metroViewModel<CardsViewModel>()
    val repository = viewModel.repository
    val album by repository.album.collectAsStateWithLifecycle()
    val me = repository.me
    // Loads the album here, not only from the hub; again once signed in if the session came late
    LaunchedEffect(me) { viewModel.load() }
    val recent = remember(album, me) { CardAlbum.recentMine(album, me, LIBRARY_STRIP) }
    if (recent.isEmpty()) return
    val ids = remember(recent) { recent.map { it.id } }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                text = "🃏 " + stringResource(MR.strings.cards_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) { Text(stringResource(MR.strings.friends_see_all)) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(recent, key = { it.id }) { card ->
                CardThumb(card = card, caption = card.grade.label, modifier = Modifier.width(72.dp)) {
                    onOpen(card, ids)
                }
            }
        }
    }
}

private const val LIBRARY_STRIP = 12
