package mihon.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import mihon.feature.recommendations.Recommendation
import mihon.feature.recommendations.RecommendationsState
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSectionHeader

/**
 * "More like this" under the description: a header that expands or collapses AniList's recommendations. The
 * choice is remembered for every series, and AniList is only asked once the row is expanded.
 */
@Composable
fun MoreLikeThisSection(
    state: RecommendationsState,
    expanded: Boolean,
    showEnglishTitles: Boolean,
    onToggle: () -> Unit,
    onLoad: () -> Unit,
    onSearch: (String) -> Unit,
) {
    LaunchedEffect(expanded) {
        if (expanded) onLoad()
    }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val stateText = stringResource(
        if (expanded) MR.strings.more_like_this_expanded else MR.strings.more_like_this_collapsed,
    )

    Column {
        GroupedSectionHeader(
            title = stringResource(MR.strings.more_like_this),
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText },
            trailing = {
                Icon(
                    imageVector = MaterialSymbols.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            when (state) {
                RecommendationsState.Idle, RecommendationsState.Loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
                is RecommendationsState.Loaded -> Covers(state.items, showEnglishTitles, onSearch)
                RecommendationsState.Empty -> Message(stringResource(MR.strings.more_like_this_none))
                RecommendationsState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Message(stringResource(MR.strings.more_like_this_failed), Modifier.weight(1f))
                    TextButton(onClick = onLoad, modifier = Modifier.padding(end = 16.dp)) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun Covers(items: List<Recommendation>, showEnglishTitles: Boolean, onSearch: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.title }) { item ->
            // English instead of romaji when that's on and AniList has a different English title.
            val title = if (showEnglishTitles) item.englishTitle ?: item.title else item.title
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clickable { onSearch(title) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MangaCover.Book(data = item.cover, modifier = Modifier.fillMaxWidth())
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
