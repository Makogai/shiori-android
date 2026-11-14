package mihon.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import mihon.feature.shelves.BingeQueueShelf
import mihon.feature.shelves.ShelfCard
import mihon.feature.shelves.ShelfRow
import mihon.feature.shelves.SmartShelfRows
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IosSwitch
import tachiyomi.presentation.core.components.SegmentedPicker
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.theme.GroupedSectionFooter
import tachiyomi.presentation.core.theme.GroupedSectionHeader
import tachiyomi.presentation.core.theme.groupedRow
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Clock

/** A series in Up next: the chapter to open and how many arrived since it was last read. */
@Immutable
data class UpNextEntry(val item: LibraryManga, val next: Chapter, val newCount: Int)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibrarySectionsViewModel(
    getLibraryManga: GetLibraryManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    val preferences: LibraryExtrasPreferences,
) : ViewModel() {

    private val library = getLibraryManga.subscribe().map { list -> list.distinctBy { it.id } }

    val sections: StateFlow<List<LibrarySection>> =
        combine(preferences.sectionOrder.changes(), preferences.sectionsEnabled.changes(), LibrarySection::visible)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueReading: StateFlow<List<LibraryManga>> = library
        .map { LibraryInsights.continueReading(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upNext: StateFlow<List<UpNextEntry>> = combine(library, preferences.upNextDays.changes(), ::Pair)
        .mapLatest { (library, days) ->
            LibraryInsights.upNextCandidates(library, Clock.System.now(), days)
                .mapNotNull { item ->
                    val chapters = getChaptersByMangaId.await(item.id, applyScanlatorFilter = true)
                    val lastRead = item.lastRead ?: return@mapNotNull null
                    val fresh = chapters.filter { !it.read && it.dateFetch > lastRead }
                    val next = LibraryInsights.nextChapter(chapters)
                    if (fresh.isEmpty() || next == null) null else UpNextEntry(item, next, fresh.size)
                }
                .sortedByDescending { it.item.chapterFetchedAt }
                .take(UP_NEXT_LIMIT)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(from: Int, to: Int) {
        val order = LibrarySection.parseOrder(preferences.sectionOrder.get()).toMutableList()
        order.add(to, order.removeAt(from))
        preferences.sectionOrder.set(order.joinToString(",") { it.name })
    }

    fun toggle(section: LibrarySection) {
        val enabled = preferences.sectionsEnabled.get()
        preferences.sectionsEnabled.set(if (section.name in enabled) enabled - section.name else enabled + section.name)
    }

    private companion object {
        const val UP_NEXT_LIMIT = 12
    }
}

/** The Library's top rows, in the order picked on [LibrarySectionsScreen]. */
@Composable
fun LibrarySections(
    summary: @Composable () -> Unit,
    onOpenManga: (Long) -> Unit,
    onContinue: (LibraryManga) -> Unit,
    onOpenChapter: (mangaId: Long, chapterId: Long) -> Unit,
    onEditQueue: () -> Unit,
) {
    val viewModel = metroViewModel<LibrarySectionsViewModel>()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    sections.forEach { section ->
        when (section) {
            LibrarySection.Summary -> summary()
            LibrarySection.ContinueReading -> {
                val items by viewModel.continueReading.collectAsStateWithLifecycle()
                if (items.isNotEmpty()) {
                    ShelfRow(title = stringResource(section.title)) {
                        items(items, key = { it.id }) { item ->
                            ShelfCard(
                                manga = item.manga,
                                caption = stringResource(MR.strings.shelf_unread, item.unreadCount),
                                badge = null,
                            ) { onContinue(item) }
                        }
                    }
                }
            }
            LibrarySection.UpNext -> {
                val entries by viewModel.upNext.collectAsStateWithLifecycle()
                if (entries.isNotEmpty()) {
                    ShelfRow(title = stringResource(section.title)) {
                        items(entries, key = { it.item.id }) { entry ->
                            ShelfCard(
                                manga = entry.item.manga,
                                caption = entry.next.name,
                                badge = "+${entry.newCount}",
                            ) { onOpenChapter(entry.item.id, entry.next.id) }
                        }
                    }
                }
            }
            LibrarySection.Queue -> BingeQueueShelf(onOpenManga = onOpenManga, onEditQueue = onEditQueue)
            LibrarySection.SmartShelves -> SmartShelfRows(onOpenManga = onOpenManga)
        }
    }
}

/** Library sections: which rows show at the top of the Library, reordered by dragging. */
class LibrarySectionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<LibrarySectionsViewModel>()
        val storedOrder by viewModel.preferences.sectionOrder.collectAsState()
        val enabled by viewModel.preferences.sectionsEnabled.collectAsState()
        val upNextDays by viewModel.preferences.upNextDays.collectAsState()
        val order = remember(storedOrder) { LibrarySection.parseOrder(storedOrder) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.library_sections),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val listState = rememberLazyListState()
            val items = remember { order.toMutableStateList() }
            val reorderableState = rememberReorderableLazyListState(listState, padding) { from, to ->
                // The header item comes first
                items.add(to.index - 1, items.removeAt(from.index - 1))
                viewModel.move(from.index - 1, to.index - 1)
            }
            LaunchedEffect(order) {
                if (!reorderableState.isAnyItemDragging) {
                    items.clear()
                    items.addAll(order)
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) {
                item { GroupedSectionHeader(stringResource(MR.strings.library_sections_header)) }
                itemsIndexed(items, key = { _, section -> section.name }) { index, section ->
                    ReorderableItem(reorderableState, section.name) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .groupedRow(index, items.size)
                                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.draggableHandle().padding(12.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(section.title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(section.explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IosSwitch(checked = section.name in enabled, onCheckedChange = {
                                viewModel.toggle(section)
                            })
                        }
                    }
                }
                item { GroupedSectionFooter(stringResource(MR.strings.library_sections_footer)) }
                item {
                    Spacer(Modifier.height(8.dp))
                    GroupedSection(header = stringResource(MR.strings.library_section_up_next)) {
                        val choices = LibraryExtrasPreferences.DAY_CHOICES
                        SegmentedPicker(
                            options = choices.map { pluralStringResource(MR.plurals.day, it, it) },
                            selectedIndex = choices.indexOf(upNextDays).coerceAtLeast(0),
                            onSelect = { viewModel.preferences.upNextDays.set(choices[it]) },
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    GroupedSectionFooter(stringResource(MR.strings.library_section_up_next_footer))
                }
            }
        }
    }
}

/** Library → Display: sections, tracker badges and the stalled mark. */
@Composable
fun LibraryExtrasSettings(preferences: LibraryExtrasPreferences, onDismissRequest: () -> Unit) {
    val navigator = LocalNavigator.current
    val stalledDays by preferences.stalledDays.collectAsState()
    HeadingItem(MR.strings.library_sections)
    if (navigator != null) {
        TextButton(
            onClick = {
                onDismissRequest()
                navigator.push(LibrarySectionsScreen())
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(stringResource(MR.strings.library_sections_edit))
        }
    }
    CheckboxItem(label = stringResource(MR.strings.library_tracker_badges), pref = preferences.trackerBadges)
    SettingsChipRow(MR.strings.library_stalled_after) {
        LibraryExtrasPreferences.STALLED_CHOICES.forEach { days ->
            FilterChip(
                selected = stalledDays == days,
                onClick = { preferences.stalledDays.set(days) },
                label = {
                    Text(
                        if (days == 0) {
                            stringResource(MR.strings.off)
                        } else {
                            pluralStringResource(MR.plurals.day, days, days)
                        },
                    )
                },
            )
        }
    }
}
