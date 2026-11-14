package mihon.feature.updatepreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.IosSearchField
import tachiyomi.presentation.core.components.SegmentedPicker
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.GroupedSectionFooter
import tachiyomi.presentation.core.theme.GroupedSectionHeader
import tachiyomi.presentation.core.theme.groupedRow
import tachiyomi.presentation.core.theme.separator

/** "What gets updated": the series a full library update checks, and the skipped ones grouped by reason. */
class UpdatePreviewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<UpdatePreviewViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var query by rememberSaveable { mutableStateOf("") }
        var tab by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.update_preview_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val preview = state
            if (preview == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            val shown = preview.matching(query)
            val onOpen = { id: Long -> navigator.push(MangaScreen(id)) }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(
                                MR.strings.update_preview_summary,
                                preview.willUpdate.size,
                                preview.skippedCount,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IosSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = stringResource(MR.strings.update_preview_search),
                        )
                        SegmentedPicker(
                            options = listOf(
                                stringResource(MR.strings.update_preview_will_update),
                                stringResource(MR.strings.update_preview_skipped),
                            ),
                            selectedIndex = tab,
                            onSelect = { tab = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (tab == 0) {
                    if (shown.willUpdate.isEmpty()) {
                        item(key = "none") { GroupedSectionFooter(noneText(query, MR.strings.update_preview_none)) }
                    } else {
                        entries("will", null, shown.willUpdate, onOpen)
                    }
                } else {
                    if (shown.skipped.isEmpty()) {
                        item(key = "none") {
                            GroupedSectionFooter(noneText(query, MR.strings.update_preview_nothing_skipped))
                        }
                    }
                    shown.skipped.forEach { (reason, entries) ->
                        entries(reason.name, reason, entries, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun noneText(query: String, empty: StringResource): String =
    stringResource(if (query.isBlank()) empty else MR.strings.update_preview_no_matches)

private fun LazyListScope.entries(
    key: String,
    reason: SkipReason?,
    entries: List<UpdatePreview.Entry>,
    onOpen: (Long) -> Unit,
) {
    item(key = "header-$key") {
        val title = stringResource(reason?.label ?: MR.strings.update_preview_will_update)
        GroupedSectionHeader("$title · ${entries.size}")
    }
    itemsIndexed(entries, key = { _, entry -> "$key-${entry.item.id}" }) { index, entry ->
        EntryRow(
            entry = entry,
            modifier = Modifier.groupedRow(index, entries.size),
            divider = index > 0,
            onClick = { onOpen(entry.item.id) },
        )
    }
}

@Composable
private fun EntryRow(entry: UpdatePreview.Entry, modifier: Modifier, divider: Boolean, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.separator,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MangaCover.Book(data = entry.item.manga.asMangaCover(), modifier = Modifier.width(36.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.item.manga.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
