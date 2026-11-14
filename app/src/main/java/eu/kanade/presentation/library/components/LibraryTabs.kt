package eu.kanade.presentation.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.readableAccent

/** Category tabs as the iOS app's capsules: the active one filled with the accent colour. */
@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    val listState = rememberLazyListState()
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex >= 0) listState.animateScrollToItem(currentPageIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryCapsule(
                name = category.visualName,
                count = getItemCountForCategory(category),
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
            )
        }
    }
}

@Composable
private fun CategoryCapsule(
    name: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.card, label = "capsule")
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.readableAccent(colors.card),
        label = "capsuleText",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
                modifier = Modifier.alpha(0.75f),
            )
        }
    }
}
