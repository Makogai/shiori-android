package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Keep
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.IosSearchField
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.readableAccent

@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: (() -> Unit)?,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            if (navigateUp == null) {
                // The Search tab: a large title over an always visible search field, as on iOS.
                Column {
                    // iOS shows the Search tab's title small and centred over the field.
                    AppBar(
                        titleContent = {
                            Text(
                                text = stringResource(MR.strings.action_search),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        scrollBehavior = scrollBehavior,
                    )
                    IosSearchField(
                        query = searchQuery.orEmpty(),
                        onQueryChange = onChangeSearchQuery,
                        placeholder = stringResource(MR.strings.search_all_sources_hint),
                        onSearch = onSearch,
                    )
                }
            } else {
                SearchToolbar(
                    searchQuery = searchQuery,
                    onChangeSearchQuery = onChangeSearchQuery,
                    onSearch = onSearch,
                    onClickCloseSearch = navigateUp,
                    navigateUp = navigateUp,
                    scrollBehavior = scrollBehavior,
                )
            }
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress / total.toFloat() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: make this UX better; it only applies when triggering a new search
            if (!hideSourceFilter) {
                FilterChip(
                    colors = iosChipColors(),
                    border = null,
                    shape = CircleShape,
                    selected = sourceFilter == SourceFilter.PinnedOnly,
                    onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Keep,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.pinned_sources))
                    },
                )
                FilterChip(
                    colors = iosChipColors(),
                    border = null,
                    shape = CircleShape,
                    selected = sourceFilter == SourceFilter.All,
                    onClick = { onChangeSearchFilter(SourceFilter.All) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.all))
                    },
                )

                VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))
            }

            FilterChip(

                colors = iosChipColors(),

                border = null,

                shape = CircleShape,
                selected = onlyShowHasResults,
                onClick = { onToggleResults() },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )
        }

        HorizontalDivider()
    }
}

/** iOS filter capsules: accent fill when selected, a faint accent tint otherwise. */
@Composable
private fun iosChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    labelColor = MaterialTheme.colorScheme.readableAccent(),
    iconColor = MaterialTheme.colorScheme.readableAccent(),
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
