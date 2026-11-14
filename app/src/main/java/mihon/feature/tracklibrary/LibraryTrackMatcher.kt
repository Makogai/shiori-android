package mihon.feature.tracklibrary

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.util.system.logcat

data class MatchKey(val trackerId: Long, val mangaId: Long)

/** A library entry to find on a tracker. */
data class MatchSubject(val mangaId: Long, val title: String, val altTitles: List<String>) {
    val ownTitles get() = listOf(title) + altTitles
}

@Immutable
data class MatchCandidate(val result: TrackSearch, val score: Int) {
    val isStrong get() = TitleMatcher.isStrong(score)
}

sealed interface MatchState {
    data object Queued : MatchState
    data object Searching : MatchState

    /** Up to [MAX_CANDIDATES] results, best first; empty when the tracker found nothing. */
    data class Found(val candidates: List<MatchCandidate>) : MatchState

    data class Failed(val message: String?) : MatchState
}

val MatchState?.best get() = (this as? MatchState.Found)?.candidates?.firstOrNull()

/**
 * App-scoped matching queue behind "Track your library": one paced worker per tracker searches it for each queued
 * library entry and keeps the scored results for the session, so leaving the screen neither stops the work nor
 * loses it. Binding goes through [AddTracks], the manga screen's path.
 */
@Inject
@SingleIn(AppScope::class)
class LibraryTrackMatcher(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val trackerManager: TrackerManager,
    private val addTracks: AddTracks,
) {

    val states: StateFlow<Map<MatchKey, MatchState>>
        field = MutableStateFlow(emptyMap())

    val binding: StateFlow<Set<MatchKey>>
        field = MutableStateFlow(emptySet())

    private val _messages = Channel<Message>(Channel.UNLIMITED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    private val lock = Any()
    private val queues = mutableMapOf<Long, ArrayDeque<MatchSubject>>()
    private val workers = mutableMapOf<Long, Job>()
    private val pacers = mutableMapOf<Long, RequestPacer>()

    internal var newPacer: (Long) -> RequestPacer = { RequestPacer(RequestPacer.intervalFor(it)) }

    /** Trackers whose queue the user stopped; they don't queue again until [resume]. */
    val paused: StateFlow<Set<Long>>
        field = MutableStateFlow(emptySet())

    /**
     * Queues the [subjects] that have no result or pending search yet, in order, and starts the worker. Does nothing
     * while [trackerId] is paused.
     */
    fun enqueue(trackerId: Long, subjects: List<MatchSubject>) {
        if (trackerId in paused.value) return
        queue(trackerId, subjects)
    }

    fun resume(trackerId: Long, subjects: List<MatchSubject>) {
        paused.update { it - trackerId }
        queue(trackerId, subjects)
    }

    private fun queue(trackerId: Long, subjects: List<MatchSubject>) {
        val known = states.value
        val fresh = subjects.filter { MatchKey(trackerId, it.mangaId) !in known }
        if (fresh.isEmpty()) return
        states.update { map -> map + fresh.associate { MatchKey(trackerId, it.mangaId) to MatchState.Queued } }
        synchronized(lock) {
            queues.getOrPut(trackerId) { ArrayDeque() }.addAll(fresh)
            if (trackerId !in workers) startWorker(trackerId)
        }
    }

    /** Searches again for an entry whose search failed. */
    fun retry(trackerId: Long, subject: MatchSubject) {
        states.update { it - MatchKey(trackerId, subject.mangaId) }
        queue(trackerId, listOf(subject))
    }

    /** Stops [trackerId]'s queue until [resume]; finished results stay, pending ones are dropped. */
    fun cancel(trackerId: Long) {
        paused.update { it + trackerId }
        val job = synchronized(lock) {
            queues.remove(trackerId)
            workers.remove(trackerId)
        }
        job?.cancel()
        dropPending(trackerId)
    }

    /** A paced search of [tracker] for the "Other results" sheet; shares the queue's pacing. */
    suspend fun search(tracker: Tracker, query: String): List<TrackSearch> = pacedSearch(tracker, query)

    fun rank(subject: MatchSubject, results: Collection<TrackSearch>): List<MatchCandidate> {
        return results
            .map { MatchCandidate(it, TitleMatcher.score(subject.ownTitles, listOf(it.title) + it.alternative_titles)) }
            .sortedByDescending { it.score }
            .take(MAX_CANDIDATES)
    }

    fun bind(trackerId: Long, mangaId: Long, result: TrackSearch) {
        val key = MatchKey(trackerId, mangaId)
        if (!startBinding(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                val error = bindNow(trackerId, mangaId, result)
                if (error != null) _messages.trySend(Message.BindFailed(result.title, error))
            } finally {
                binding.update { it - key }
            }
        }
    }

    /** Binds every pair in turn, paced; reports how many were linked. */
    fun bindAll(trackerId: Long, matches: List<Pair<Long, TrackSearch>>) {
        val keys = matches.map { (mangaId, _) -> MatchKey(trackerId, mangaId) }.filter(::startBinding)
        if (keys.isEmpty()) return
        val pending = matches.filter { (mangaId, _) -> MatchKey(trackerId, mangaId) in keys }
        scope.launch(Dispatchers.IO) {
            var linked = 0
            pending.forEach { (mangaId, result) ->
                try {
                    if (bindNow(trackerId, mangaId, result) == null) linked++
                } finally {
                    binding.update { it - MatchKey(trackerId, mangaId) }
                }
            }
            _messages.trySend(Message.BulkDone(linked, pending.size))
        }
    }

    private fun startBinding(key: MatchKey): Boolean {
        var started = false
        binding.update { current ->
            started = key !in current
            current + key
        }
        return started
    }

    /** Returns null when bound, else the error message. */
    private suspend fun bindNow(trackerId: Long, mangaId: Long, result: TrackSearch): String? {
        val tracker = trackerManager.get(trackerId) ?: return null
        val pacer = pacerFor(trackerId)
        repeat(MAX_RATE_LIMIT_RETRIES) {
            // A bind reads the user's list entry, then adds or updates it
            pacer.acquire(cost = BIND_COST)
            try {
                result.manga_id = mangaId
                addTracks.bind(tracker, result, mangaId)
                pacer.onSuccess()
                return null
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!RequestPacer.isRateLimited(e)) {
                    logcat(LogPriority.ERROR, e) { "Track your library: bind failed on ${tracker.name}" }
                    return e.message ?: e::class.simpleName
                }
                pacer.onRateLimited()
            }
        }
        return "HTTP 429"
    }

    private fun startWorker(trackerId: Long) {
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) { work(trackerId) }
        workers[trackerId] = job
        job.start()
    }

    private suspend fun work(trackerId: Long) {
        val tracker = trackerManager.get(trackerId)
        val self = currentCoroutineContext()[Job]
        while (true) {
            val subject = synchronized(lock) {
                // A cancelled worker must not take from, or unregister, the queue's next worker
                if (workers[trackerId] !== self) return
                queues[trackerId]?.removeFirstOrNull().also { if (it == null) workers.remove(trackerId) }
            } ?: return
            val key = MatchKey(trackerId, subject.mangaId)
            if (tracker == null) {
                setState(key, MatchState.Failed(null))
                continue
            }
            setState(key, MatchState.Searching)
            val state = try {
                MatchState.Found(find(tracker, subject))
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Track your library: search failed on ${tracker.name}" }
                MatchState.Failed(e.message)
            }
            setState(key, state)
        }
    }

    /** Searches the title first, then the shortened title and alternative titles while there's no strong hit. */
    private suspend fun find(tracker: Tracker, subject: MatchSubject): List<MatchCandidate> {
        val results = LinkedHashMap<Long, TrackSearch>()
        for (query in TitleMatcher.searchQueries(subject.title, subject.altTitles)) {
            pacedSearch(tracker, query).forEach { results.putIfAbsent(it.remote_id, it) }
            if (rank(subject, results.values).firstOrNull()?.isStrong == true) break
        }
        return rank(subject, results.values)
    }

    private suspend fun pacedSearch(tracker: Tracker, query: String): List<TrackSearch> {
        val pacer = pacerFor(tracker.id)
        var attempt = 0
        while (true) {
            pacer.acquire()
            try {
                return tracker.search(query).also { pacer.onSuccess() }
            } catch (e: Throwable) {
                if (!RequestPacer.isRateLimited(e) || ++attempt >= MAX_RATE_LIMIT_RETRIES) throw e
                val backoff = pacer.onRateLimited()
                logcat(LogPriority.WARN) { "Track your library: ${tracker.name} rate limited, waiting $backoff" }
            }
        }
    }

    private fun pacerFor(trackerId: Long) = synchronized(lock) { pacers.getOrPut(trackerId) { newPacer(trackerId) } }

    private fun setState(key: MatchKey, state: MatchState) {
        states.update { it + (key to state) }
    }

    private fun dropPending(trackerId: Long) {
        states.update { map ->
            map.filterNot { (key, state) ->
                key.trackerId == trackerId && (state is MatchState.Queued || state is MatchState.Searching)
            }
        }
    }

    sealed interface Message {
        data class BindFailed(val title: String, val error: String?) : Message
        data class BulkDone(val linked: Int, val total: Int) : Message
    }

    companion object {
        const val MAX_CANDIDATES = 8
        private const val BIND_COST = 3
        private const val MAX_RATE_LIMIT_RETRIES = 5
    }
}
