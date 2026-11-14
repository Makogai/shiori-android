package mihon.feature.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.manga.components.MangaCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.feature.queue.BingeQueue
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryShelvesViewModel(
    getLibraryManga: GetLibraryManga,
    preferences: SmartShelfPreferences,
    private val queue: BingeQueue,
) : ViewModel() {

    private val library = getLibraryManga.subscribe().map { list -> list.distinctBy { it.id } }

    val shelves: StateFlow<List<Pair<SmartShelf, List<LibraryManga>>>> =
        combine(library, preferences.enabled.changes()) { library, enabled ->
            SmartShelfLogic.build(library, enabled, Clock.System.now())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queued: StateFlow<List<Manga>> = combine(queue.changes(), library) { keys, library ->
        queue.resolve(keys, library.map { it.manga })
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToQueue(manga: List<Manga>) = queue.add(manga)
}

/** The Up next queue and the smart shelves, under the Library summary. */
@Composable
fun LibraryShelves(
    onOpenManga: (Long) -> Unit,
    onEditQueue: () -> Unit,
) {
    val viewModel = metroViewModel<LibraryShelvesViewModel>()
    val queued by viewModel.queued.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()

    if (queued.isNotEmpty()) {
        ShelfRow(
            title = stringResource(MR.strings.binge_queue_title),
            action = stringResource(MR.strings.action_edit) to onEditQueue,
        ) {
            items(queued, key = { it.id }) { manga ->
                ShelfCard(manga = manga, caption = null, badge = null) { onOpenManga(manga.id) }
            }
        }
    }
    shelves.forEach { (shelf, items) ->
        ShelfRow(title = stringResource(shelf.titleRes)) {
            items(items, key = { it.id }) { item ->
                ShelfCard(
                    manga = item.manga,
                    caption = shelfCaption(shelf, item),
                    badge = item.unreadCount.takeIf { shelf == SmartShelf.NewChapters }?.let { "+$it" },
                ) { onOpenManga(item.id) }
            }
        }
    }
}

private val SmartShelf.titleRes: StringResource
    get() = when (this) {
        SmartShelf.NewChapters -> MR.strings.shelf_new_chapters
        SmartShelf.AlmostFinished -> MR.strings.shelf_almost_finished
        SmartShelf.Untouched -> MR.strings.shelf_untouched
        SmartShelf.CaughtUp -> MR.strings.shelf_caught_up
    }

@Composable
private fun shelfCaption(shelf: SmartShelf, item: LibraryManga): String? = when (shelf) {
    SmartShelf.AlmostFinished -> stringResource(
        MR.strings.shelf_percent_read,
        (item.readCount * 100.0 / item.totalChapters).roundToInt(),
    )
    SmartShelf.Untouched, SmartShelf.NewChapters -> stringResource(MR.strings.shelf_unread, item.unreadCount)
    SmartShelf.CaughtUp -> null
}

@Composable
private fun ShelfRow(
    title: String,
    action: Pair<String, () -> Unit>? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = if (action == null) 10.dp else 0.dp),
            )
            if (action != null) {
                TextButton(onClick = action.second) { Text(action.first) }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A cover with its title, as the iOS Library rows: an optional capsule badge and a caption. */
@Composable
fun ShelfCard(
    manga: Manga,
    caption: String?,
    badge: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            MangaCover.Book(data = manga.asMangaCover(), modifier = Modifier.fillMaxWidth())
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Library → Display: a switch per smart shelf. */
@Composable
fun SmartShelvesSettings(preferences: SmartShelfPreferences) {
    val enabled by preferences.enabled.collectAsState()
    HeadingItem(MR.strings.shelves_header)
    SmartShelf.entries.forEach { shelf ->
        CheckboxItem(
            label = stringResource(shelf.titleRes),
            checked = shelf in enabled,
            onClick = { preferences.enabled.set(if (shelf in enabled) enabled - shelf else enabled + shelf) },
        )
    }
}
