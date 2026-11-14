package mihon.feature.ratings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.feature.common.utils.seriesKey
import mihon.feature.queue.BingeQueue
import mihon.feature.queue.ReorderableSeriesList
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.EmojiEvents
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.theme.groupedRow

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TopListsViewModel(
    private val ratings: PersonalRatings,
    private val queue: BingeQueue,
) : ViewModel() {

    val lists: StateFlow<List<TopList>> = ratings.topLists.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ratings.topLists.get())

    /** The series of list [id], in its order. */
    fun entries(id: Long) = ratings.topLists.changes()
        .map { lists -> queue.resolve(lists.find { it.id == id }?.entries.orEmpty(), emptyList()) }

    fun create(name: String) = ratings.createList(name)

    fun rename(id: Long, name: String) = ratings.renameList(id, name)

    fun delete(id: Long) = ratings.deleteList(id)

    fun move(id: Long, from: Int, to: Int) = ratings.move(id, from, to)

    fun remove(id: Long, manga: Manga) = ratings.remove(id, manga.seriesKey)
}

/** The user's top lists, from a series' details page. */
class TopListsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<TopListsViewModel>()
        val lists by viewModel.lists.collectAsStateWithLifecycle()
        var creating by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.top_lists),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.top_list_new),
                                    icon = MaterialSymbols.Rounded.Add,
                                    onClick = { creating = true },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { padding ->
            if (lists.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.top_lists_empty,
                    modifier = Modifier.padding(padding),
                    icon = MaterialSymbols.Rounded.EmojiEvents,
                )
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lists, key = { _, list -> list.id }) { index, list ->
                    GroupedRow(
                        title = list.name,
                        value = pluralStringResource(MR.plurals.top_list_count, list.entries.size, list.entries.size),
                        modifier = Modifier.groupedRow(index, lists.size),
                        onClick = { navigator.push(TopListScreen(list.id)) },
                    )
                }
            }
        }

        if (creating) {
            NameDialog(
                title = stringResource(MR.strings.top_list_new),
                initial = "",
                onConfirm = { viewModel.create(it) },
                onDismiss = { creating = false },
            )
        }
    }
}

/** One top list: drag to reorder, remove series, rename or delete the list. */
class TopListScreen(private val id: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<TopListsViewModel>()
        val lists by viewModel.lists.collectAsStateWithLifecycle()
        val list = lists.find { it.id == id }
        val entries by remember { viewModel.entries(id) }.collectAsStateWithLifecycle<List<Manga>?>(null)
        var renaming by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = list?.name.orEmpty(),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_rename_category),
                                    onClick = { renaming = true },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_delete),
                                    onClick = { deleting = true },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { padding ->
            val series = entries ?: return@Scaffold
            if (series.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.top_list_empty,
                    modifier = Modifier.padding(padding),
                    icon = MaterialSymbols.Rounded.EmojiEvents,
                )
                return@Scaffold
            }
            ReorderableSeriesList(
                entries = series,
                padding = padding,
                onMove = { from, to -> viewModel.move(id, from, to) },
                onRemove = { viewModel.remove(id, it) },
                onOpen = { navigator.push(MangaScreen(it.id)) },
            )
        }

        if (renaming && list != null) {
            NameDialog(
                title = stringResource(MR.strings.action_rename_category),
                initial = list.name,
                onConfirm = { viewModel.rename(id, it) },
                onDismiss = { renaming = false },
            )
        }
        if (deleting && list != null) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                text = { Text(stringResource(MR.strings.top_list_delete_confirm, list.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(id)
                            navigator.pop()
                        },
                    ) { Text(stringResource(MR.strings.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = false }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(state = name, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(
                enabled = name.text.isNotBlank(),
                onClick = {
                    onConfirm(name.text.toString())
                    onDismiss()
                },
            ) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
