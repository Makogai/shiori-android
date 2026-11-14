package mihon.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.feature.common.utils.seriesKey
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.FormatListNumbered
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.theme.GroupedSectionFooter
import tachiyomi.presentation.core.theme.groupedRow

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BingeQueueViewModel(private val queue: BingeQueue) : ViewModel() {

    val entries: StateFlow<List<Manga>?> = queue.changes()
        .map { queue.resolve(it, emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun move(from: Int, to: Int) = queue.move(from, to)

    fun remove(manga: Manga) = queue.remove(manga.seriesKey)
}

/** Library → Up next → Edit: the queue, reordered by dragging. */
class BingeQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<BingeQueueViewModel>()
        val entries by viewModel.entries.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.binge_queue_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val list = entries ?: return@Scaffold
            if (list.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.binge_queue_empty,
                    modifier = Modifier.padding(padding),
                    icon = MaterialSymbols.Rounded.FormatListNumbered,
                )
                return@Scaffold
            }
            ReorderableSeriesList(
                entries = list,
                padding = padding,
                onMove = viewModel::move,
                onRemove = viewModel::remove,
                onOpen = { navigator.push(MangaScreen(it.id)) },
                footer = { item { GroupedSectionFooter(stringResource(MR.strings.binge_queue_footer)) } },
            )
        }
    }
}

/** Series in one inset-grouped card, numbered, with drag handles to reorder them and a button to remove one. */
@Composable
fun ReorderableSeriesList(
    entries: List<Manga>,
    padding: PaddingValues,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Manga) -> Unit,
    onOpen: (Manga) -> Unit,
    footer: LazyListScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    val items = remember { entries.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(listState, padding) { from, to ->
        items.add(to.index, items.removeAt(from.index))
        onMove(from.index, to.index)
    }
    LaunchedEffect(entries) {
        if (!reorderableState.isAnyItemDragging) {
            items.clear()
            items.addAll(entries)
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, manga -> manga.id }) { index, manga ->
            ReorderableItem(reorderableState, manga.id) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .groupedRow(index, items.size)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    MangaCover.Book(
                        data = manga.asMangaCover(),
                        modifier = Modifier.height(56.dp),
                        onClick = { onOpen(manga) },
                    )
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(manga) }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_remove),
                        )
                    }
                    Icon(
                        imageVector = MaterialSymbols.Rounded.DragHandle,
                        contentDescription = null,
                        modifier = Modifier
                            .draggableHandle()
                            .padding(12.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        footer()
    }
}
