package mihon.feature.cleanup

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Storage
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.GroupedSectionHeader
import tachiyomi.presentation.core.theme.groupedRow

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StorageViewModel(
    private val getFavorites: GetFavorites,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
) : ViewModel() {

    @Immutable
    data class Item(val manga: Manga, val chapters: Int, val bytes: Long)

    private val mutableItems = MutableStateFlow<List<Item>?>(null)
    val items: StateFlow<List<Item>?> = mutableItems.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            mutableItems.value = withContext(Dispatchers.IO) {
                getFavorites.await()
                    .mapNotNull { manga ->
                        val chapters = downloadManager.getDownloadCount(manga)
                        if (chapters == 0) return@mapNotNull null
                        val dir = downloadProvider.findMangaDir(manga.title, sourceManager.getOrStub(manga.source))
                        Item(manga, chapters, dir?.size() ?: 0)
                    }
                    .sortedByDescending { it.bytes }
            }
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            downloadManager.deleteManga(item.manga, sourceManager.getOrStub(item.manga.source))
            mutableItems.value = mutableItems.value?.minus(item)
        }
    }

    private fun UniFile.size(): Long = if (isDirectory) listFiles().orEmpty().sumOf { it.size() } else length()
}

/** Settings → Downloads → Storage per series: downloaded space per library series, largest first. */
class StorageScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<StorageViewModel>()
        val items by viewModel.items.collectAsStateWithLifecycle()
        var deleting by remember { mutableStateOf<StorageViewModel.Item?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.storage_per_series),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val list = items
            when {
                list == null -> LoadingScreen(Modifier.padding(padding))
                list.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.storage_empty,
                    modifier = Modifier.padding(padding),
                    icon = MaterialSymbols.Rounded.Storage,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        GroupedSectionHeader(
                            stringResource(
                                MR.strings.storage_total,
                                Formatter.formatShortFileSize(context, list.sumOf { it.bytes }),
                            ),
                        )
                    }
                    itemsIndexed(list, key = { _, item -> item.manga.id }) { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .groupedRow(index, list.size)
                                .clickable { navigator.push(MangaScreen(item.manga.id)) }
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        ) {
                            MangaCover.Book(data = item.manga.asMangaCover(), modifier = Modifier.height(56.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.manga.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = pluralStringResource(
                                        MR.plurals.storage_chapters,
                                        item.chapters,
                                        item.chapters,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = Formatter.formatShortFileSize(context, item.bytes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { deleting = item }) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Delete,
                                    contentDescription = stringResource(MR.strings.action_delete),
                                )
                            }
                        }
                    }
                }
            }
        }

        deleting?.let { item ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                text = { Text(stringResource(MR.strings.storage_delete_confirm, item.manga.title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(item)
                            deleting = null
                        },
                    ) { Text(stringResource(MR.strings.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
    }
}
