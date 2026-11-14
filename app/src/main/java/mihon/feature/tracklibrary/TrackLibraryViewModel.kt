package mihon.feature.tracklibrary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class TrackLibraryViewModel(
    private val trackerManager: TrackerManager,
    private val matcher: LibraryTrackMatcher,
    private val preferences: TrackLibraryPreferences,
    private val deleteTrack: DeleteTrack,
    getLibraryManga: GetLibraryManga,
    getTracksPerManga: GetTracksPerManga,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    val messages: Flow<LibraryTrackMatcher.Message> = matcher.messages

    val sheet: StateFlow<Sheet?>
        field = MutableStateFlow<Sheet?>(null)

    private val selectedTrackerId = MutableStateFlow<Long?>(null)
    private val showSkipped = MutableStateFlow(false)
    private val subjects = mutableMapOf<Long, Pair<Manga, MatchSubject>>()
    private var sheetSearch: Job? = null

    init {
        val trackers = trackerManager.loggedInTrackersFlow()
            .map { list -> list.filterNot { it is EnhancedTracker } }
            .distinctUntilChanged()

        viewModelScope.launch {
            trackers.collect { list ->
                selectedTrackerId.update { id ->
                    id?.takeIf { selected -> list.any { it.id == selected } } ?: list.firstOrNull()?.id
                }
                state.update { it.copy(trackers = list, loaded = true) }
            }
        }

        val library = getLibraryManga.subscribe().map { list -> list.map { it.manga } }
        val inputs = combine(library, getTracksPerManga.subscribe(), selectedTrackerId, showSkipped, ::Inputs)

        @OptIn(ExperimentalCoroutinesApi::class)
        val skipped = selectedTrackerId.flatMapLatest { id -> id?.let(preferences::skippedFor) ?: flowOf(emptySet()) }

        viewModelScope.launch {
            combine(inputs, skipped, matcher.states, matcher.binding, matcher.paused) {
                    inputs,
                    skipped,
                    states,
                    binding,
                    paused,
                ->
                val trackerId = inputs.trackerId ?: return@combine null
                Built(
                    trackerId,
                    trackerId in paused,
                    inputs.showSkipped,
                    TrackLibraryRows.build(
                        library = inputs.library,
                        tracks = inputs.tracks,
                        trackerId = trackerId,
                        skipped = skipped,
                        states = states,
                        binding = binding,
                        showSkipped = inputs.showSkipped,
                        subjectOf = ::cachedSubject,
                    ),
                )
            }
                .flowOn(Dispatchers.Default)
                .collect { built ->
                    state.update {
                        it.copy(
                            selectedTrackerId = built?.trackerId,
                            content = built?.content ?: TrackLibraryContent(),
                            showSkipped = built?.showSkipped ?: false,
                            paused = built?.paused ?: false,
                        )
                    }
                    if (built != null) matcher.enqueue(built.trackerId, built.content.unsearched())
                }
        }
    }

    private fun cachedSubject(manga: Manga): MatchSubject {
        synchronized(subjects) {
            subjects[manga.id]
                ?.takeIf { (cached, _) -> cached.title == manga.title && cached.description == manga.description }
                ?.let { return it.second }
            return TrackLibraryRows.subjectOf(manga).also { subjects[manga.id] = manga to it }
        }
    }

    fun selectTracker(trackerId: Long) {
        selectedTrackerId.value = trackerId
    }

    fun setShowSkipped(show: Boolean) {
        showSkipped.value = show
    }

    fun confirm(row: NeedsRow) {
        val trackerId = selectedTrackerId.value ?: return
        val best = row.best ?: return
        matcher.bind(trackerId, row.manga.id, best.result)
    }

    fun confirmStrongMatches() {
        val trackerId = selectedTrackerId.value ?: return
        val rows = state.value.content.strongMatches
        matcher.bindAll(trackerId, rows.map { row -> row.manga.id to row.best!!.result })
    }

    fun setSkipped(row: NeedsRow, skip: Boolean) {
        val trackerId = selectedTrackerId.value ?: return
        preferences.setSkipped(trackerId, row.manga.id, skip)
    }

    fun retry(row: NeedsRow) {
        val trackerId = selectedTrackerId.value ?: return
        matcher.retry(trackerId, row.subject)
    }

    fun stop() {
        val trackerId = selectedTrackerId.value ?: return
        matcher.cancel(trackerId)
    }

    fun resume() {
        val trackerId = selectedTrackerId.value ?: return
        matcher.resume(trackerId, state.value.content.unsearched())
    }

    private fun TrackLibraryContent.unsearched() = needs.filter { it.state == null }.map { it.subject }

    fun unlink(row: TrackedRow) {
        viewModelScope.launch(Dispatchers.IO) { deleteTrack.await(row.manga.id, row.track.trackerId) }
    }

    /** Opens "Other results" with the cached results for [subject]. */
    fun openSheet(manga: Manga, subject: MatchSubject) {
        val trackerId = selectedTrackerId.value ?: return
        sheetSearch?.cancel()
        val cached = matcher.states.value[MatchKey(trackerId, manga.id)] as? MatchState.Found
        sheet.value = Sheet(
            manga = manga,
            subject = subject,
            query = subject.title,
            results = cached?.candidates,
        )
        if (cached == null) searchInSheet(subject.title)
    }

    fun searchInSheet(query: String) {
        val current = sheet.value ?: return
        val tracker = selectedTracker() ?: return
        sheetSearch?.cancel()
        sheet.value = current.copy(query = query, results = null, error = null)
        sheetSearch = viewModelScope.launch(Dispatchers.IO) {
            val update = try {
                val results = matcher.search(tracker, query)
                sheet.value?.copy(results = matcher.rank(current.subject, results))
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                sheet.value?.copy(results = emptyList(), error = e.message ?: e::class.simpleName)
            }
            sheet.update { if (it?.manga?.id == current.manga.id) update else it }
        }
    }

    fun pickInSheet(result: TrackSearch) {
        val trackerId = selectedTrackerId.value ?: return
        val current = sheet.value ?: return
        matcher.bind(trackerId, current.manga.id, result)
        closeSheet()
    }

    fun closeSheet() {
        sheetSearch?.cancel()
        sheet.value = null
    }

    private fun selectedTracker(): Tracker? = selectedTrackerId.value?.let(trackerManager::get)

    private data class Inputs(
        val library: List<Manga>,
        val tracks: Map<Long, List<Track>>,
        val trackerId: Long?,
        val showSkipped: Boolean,
    )

    private data class Built(
        val trackerId: Long,
        val paused: Boolean,
        val showSkipped: Boolean,
        val content: TrackLibraryContent,
    )

    @Immutable
    data class State(
        val loaded: Boolean = false,
        val trackers: List<Tracker> = emptyList(),
        val selectedTrackerId: Long? = null,
        val content: TrackLibraryContent = TrackLibraryContent(),
        val showSkipped: Boolean = false,
        /** The user stopped the queue for this tracker. */
        val paused: Boolean = false,
    )

    @Immutable
    data class Sheet(
        val manga: Manga,
        val subject: MatchSubject,
        val query: String,
        /** Null while searching. */
        val results: List<MatchCandidate>?,
        val error: String? = null,
    )
}
