package mihon.feature.friends.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionRules
import mihon.feature.friends.data.SuggestionStatus
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

enum class SuggestionsTab { FOR_ME, SENT }

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SuggestionsViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val repository: FriendsRepository,
    private val sourceManager: SourceManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val updateManga: UpdateManga,
    private val addTracks: AddTracks,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    private val tab = MutableStateFlow(SuggestionsTab.FOR_ME)
    private val players = MutableStateFlow<Map<String, BoardRow>>(emptyMap())
    private val busy = MutableStateFlow(false)

    /** Suggestion ids with a request running. */
    private val working = MutableStateFlow<Set<String>>(emptySet())
    private val categoryDialog = MutableStateFlow<CategoryDialog?>(null)

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(
        repository.account,
        repository.suggestions,
        players,
        combine(tab, busy, ::Pair),
        combine(working, categoryDialog, ::Pair),
    ) { account, suggestions, players, (tab, busy), (working, dialog) ->
        val me = account.userId.orEmpty()
        val all = suggestions.orEmpty()
        State(
            myId = me,
            tab = tab,
            loaded = suggestions != null,
            forMe = SuggestionRules.forMe(all, me),
            sent = SuggestionRules.sent(all, me),
            badge = SuggestionRules.badge(all, me),
            players = players,
            busy = busy,
            working = working,
            dialog = dialog,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), State())

    init {
        refresh()
    }

    fun setTab(value: SuggestionsTab) = tab.update { value }

    fun refresh() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                players.value = repository.arenaPlayers().associateBy { it.userId }
                repository.loadSuggestions()
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy.value = false
            }
        }
    }

    /** Opens the manga when its source is installed, else a global search for its title. */
    fun open(suggestion: Suggestion) = launchFor(suggestion.id) {
        val local = localManga(suggestion)
        _events.send(if (local != null) Event.OpenManga(local.first.id) else Event.Search(suggestion.title))
    }

    /** Adds to the library with the details screen's category rules, then marks the suggestion added. */
    fun addToLibrary(suggestion: Suggestion) = launchFor(suggestion.id) {
        val local = localManga(suggestion)
        if (local == null) {
            _events.send(Event.Search(suggestion.title))
            return@launchFor
        }
        val (manga, source) = local
        if (manga.favorite) {
            repository.answerSuggestion(suggestion.id, SuggestionStatus.ADDED)
            return@launchFor
        }
        val categories = getCategories.await().filterNot { it.isSystemCategory }
        val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        when {
            defaultCategory != null -> favorite(suggestion, manga, source, listOf(defaultCategory.id))
            defaultCategoryId == 0L || categories.isEmpty() -> favorite(suggestion, manga, source, emptyList())
            else -> {
                val selected = getCategories.await(manga.id).mapTo(HashSet()) { it.id }
                categoryDialog.value = CategoryDialog(
                    suggestion = suggestion,
                    manga = manga,
                    source = source,
                    initialSelection = categories.mapAsCheckboxState { it.id in selected },
                )
            }
        }
    }

    fun confirmCategories(dialog: CategoryDialog, categoryIds: List<Long>) {
        categoryDialog.value = null
        launchFor(dialog.suggestion.id) { favorite(dialog.suggestion, dialog.manga, dialog.source, categoryIds) }
    }

    fun dismissDialog() {
        categoryDialog.value = null
    }

    fun dismiss(suggestion: Suggestion) = launchFor(suggestion.id) {
        repository.answerSuggestion(suggestion.id, SuggestionStatus.DISMISSED)
    }

    /** "Read it" (README v9): the sender sees that I read it. */
    fun markRead(suggestion: Suggestion) = launchFor(suggestion.id) {
        repository.answerSuggestion(suggestion.id, SuggestionStatus.READ)
    }

    fun withdraw(suggestion: Suggestion) = launchFor(suggestion.id) {
        repository.withdrawSuggestion(suggestion.id)
    }

    private suspend fun favorite(suggestion: Suggestion, manga: Manga, source: Source, categoryIds: List<Long>) {
        withContext(Dispatchers.IO) {
            setMangaDefaultChapterFlags.await(manga)
            if (!updateManga.awaitUpdateFavorite(manga.id, true)) return@withContext
            setMangaCategories.await(manga.id, categoryIds)
            appScope.launch { addTracks.bindEnhancedTrackers(manga, source) }
        }
        repository.answerSuggestion(suggestion.id, SuggestionStatus.ADDED)
    }

    /** The suggested manga in the local database (inserted as browsing does), or null without its source. */
    private suspend fun localManga(suggestion: Suggestion): Pair<Manga, Source>? = withContext(Dispatchers.IO) {
        val sourceId = suggestion.sourceId.toLongOrNull() ?: return@withContext null
        val source = sourceManager.get(sourceId) ?: return@withContext null
        val manga = networkToLocalManga(
            Manga.create().copy(
                source = sourceId,
                url = suggestion.mangaUrl,
                title = suggestion.title,
                thumbnailUrl = SuggestionRules.coverUrl(suggestion.coverUrl),
                genre = suggestion.genres.ifEmpty { null },
            ),
        )
        manga to source
    }

    private fun launchFor(id: String, block: suspend () -> Unit) {
        if (id in working.value) return
        working.update { it + id }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                fail(e)
            } finally {
                working.update { it - id }
            }
        }
    }

    private fun fail(e: Exception) {
        logcat(LogPriority.ERROR, e)
        _events.trySend(Event.Error(e.message ?: e.javaClass.simpleName))
    }

    @Immutable
    data class CategoryDialog(
        val suggestion: Suggestion,
        val manga: Manga,
        val source: Source,
        val initialSelection: List<CheckboxState<Category>>,
    )

    @Immutable
    data class State(
        val myId: String = "",
        val tab: SuggestionsTab = SuggestionsTab.FOR_ME,
        val loaded: Boolean = false,
        val forMe: List<Suggestion> = emptyList(),
        val sent: List<Suggestion> = emptyList(),
        val badge: Int = 0,
        val players: Map<String, BoardRow> = emptyMap(),
        val busy: Boolean = false,
        val working: Set<String> = emptySet(),
        val dialog: CategoryDialog? = null,
    )

    sealed interface Event {
        data class OpenManga(val mangaId: Long) : Event
        data class Search(val query: String) : Event
        data class Error(val message: String) : Event
    }
}
