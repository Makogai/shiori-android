package mihon.feature.sourcehealth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.source.health.SourceHealthRecorder
import mihon.domain.source.health.SourceOperation
import mihon.domain.source.health.recording
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import mihon.icons.materialsymbols.R as MaterialSymbolsR

/**
 * Keeps each source's recent request outcomes (library updates, browsing, details, pages and the daily health
 * check) so the Source health screen can say which sources are struggling, and runs the health checks. The local
 * source is not tracked.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SourceHealthMonitor(
    private val context: Context,
    private val preferences: SourceHealthPreferences,
    private val getLibraryManga: GetLibraryManga,
    private val chapterRepository: ChapterRepository,
    private val sourceManager: SourceManager,
    // Lazy: the repository's paging sources record here
    private val sourceRepository: Lazy<SourceRepository>,
    private val json: Json,
) : SourceHealthRecorder {

    private val lock = Any()

    private val mutableRecords = MutableStateFlow(load())
    val records: StateFlow<Map<Long, SourceHealthRecord>> = mutableRecords.asStateFlow()

    private val mutableChecking = MutableStateFlow(emptySet<Long>())

    /** Sources being checked right now. */
    val checking: StateFlow<Set<Long>> = mutableChecking.asStateFlow()

    private val dailyMutex = Mutex()

    // A check that ignores cancellation is left to finish here instead of holding up the caller
    private val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun record(sourceId: Long, operation: SourceOperation, error: Throwable?) {
        if (sourceId == LocalSource.ID) return
        val now = now()
        val outcome = if (error == null) {
            SourceOutcome(now, operation)
        } else {
            val kind = SourceErrorKind.classify(error) ?: return
            SourceOutcome(now, operation, kind, error.message?.take(MAX_MESSAGE_LENGTH))
        }
        synchronized(lock) {
            val records = mutableRecords.value
            val updated = records + (sourceId to (records[sourceId] ?: SourceHealthRecord()).appending(outcome))
            mutableRecords.value = updated
            preferences.records.set(json.encodeToString(updated))
        }
    }

    fun status(sourceId: Long): SourceHealthStatus {
        return SourceHealthLogic.status(records.value[sourceId], now())
    }

    /** Sources with library series that need attention: the Browse badge. */
    fun problemCount(): Flow<Int> {
        val librarySources = sourceRepository.value.getSourcesWithFavoriteCount()
            .map { sources -> sources.map { it.first.id } - LocalSource.ID }
        return combine(records, librarySources) { records, sources ->
            val now = now()
            sources.count { SourceHealthLogic.status(records[it], now).isProblem }
        }
            .distinctUntilChanged()
    }

    fun snapshot(): Map<Long, SourceHealthStatus> {
        val now = now()
        return records.value.mapValues { (_, record) -> SourceHealthLogic.status(record, now) }
    }

    /** The daily check, when it is on and a day has passed. The network restrictions are the caller's. */
    suspend fun runDailyCheckIfDue() {
        if (!dailyMutex.tryLock()) return
        try {
            val now = now()
            val lastRun = preferences.lastCheck.get().takeIf { it > 0 }
            if (!SourceHealthSchedule.isDue(preferences.dailyCheck.get(), lastRun, now)) return
            preferences.lastCheck.set(now)
            check()
        } finally {
            dailyMutex.unlock()
        }
    }

    /**
     * Checks every source with library series (or just [sourceId]): one series each, taking turns, its chapter list
     * fetched with a timeout and nothing saved. Alerts about sources that got worse.
     */
    suspend fun check(sourceId: Long? = null) = withContext(Dispatchers.IO) {
        val library = libraryManga()
        val bySource = library.groupBy { it.source }
            .filterKeys { sourceId == null || it == sourceId }
        val before = snapshot()
        coroutineScope {
            bySource.forEach { (id, manga) ->
                val source = sourceManager.get(id) ?: return@forEach
                if (!startChecking(id)) return@forEach
                launch {
                    try {
                        check(source, manga)
                    } finally {
                        mutableChecking.update { it - id }
                    }
                }
            }
        }
        alertChanges(before, library)
    }

    private fun startChecking(sourceId: Long): Boolean = synchronized(lock) {
        if (sourceId in mutableChecking.value) return false
        mutableChecking.update { it + sourceId }
        true
    }

    private suspend fun check(source: Source, manga: List<Manga>) {
        val item = SourceHealthSchedule.series(manga, nextTurn(source.id)) { it.id } ?: return
        try {
            recording(source.id, SourceOperation.HealthCheck) {
                val chapters = chapterRepository.getChapterByMangaId(item.id)
                    .sortedBy { it.sourceOrder }
                    .map(Chapter::toSChapter)
                withCheckTimeout {
                    source.getMangaUpdate(item.toSManga(), chapters, fetchDetails = false, fetchChapters = true)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.INFO, e) { "Health check of ${source.name} failed" }
        }
    }

    private fun nextTurn(sourceId: Long): Int = synchronized(lock) {
        val turns = preferences.turns.get()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { json.decodeFromString<Map<Long, Int>>(it) }.getOrNull() }
            .orEmpty()
        val turn = turns[sourceId] ?: 0
        preferences.turns.set(json.encodeToString(turns + (sourceId to turn + 1)))
        turn
    }

    /** Gives up after [CHECK_TIMEOUT] even if [block] ignores cancellation, as a source's blocking call may. */
    private suspend fun <T> withCheckTimeout(block: suspend () -> T): T {
        val work = checkScope.async { block() }
        return try {
            withTimeout(CHECK_TIMEOUT) { work.await() }
        } catch (e: TimeoutCancellationException) {
            work.cancel()
            throw SourceHealthTimeoutException()
        } catch (e: CancellationException) {
            work.cancel()
            throw e
        }
    }

    /** Posts a notification for each library source that newly needs attention since [before]. */
    suspend fun alertChanges(before: Map<Long, SourceHealthStatus>, library: List<Manga>? = null) {
        val counts = (library ?: libraryManga()).groupingBy { it.source }.eachCount()
        counts.forEach { (sourceId, series) ->
            val status = status(sourceId)
            if (!SourceHealthLogic.shouldAlert(before[sourceId] ?: SourceHealthStatus.Unknown, status)) return@forEach
            notify(sourceId, sourceManager.getOrStub(sourceId).name, status, series)
        }
    }

    private suspend fun libraryManga(): List<Manga> {
        return getLibraryManga.await()
            .map { it.manga }
            .distinctBy { it.id }
            .filter { it.source != LocalSource.ID }
    }

    private fun notify(sourceId: Long, name: String, status: SourceHealthStatus, series: Int) {
        val title = when (status) {
            SourceHealthStatus.NeedsCloudflare -> MR.strings.source_health_alert_cloudflare
            SourceHealthStatus.Dead -> MR.strings.source_health_alert_dead
            else -> MR.strings.source_health_alert_failing
        }
            .let { context.stringResource(it, name) }
        val body = context.pluralStringResource(MR.plurals.source_health_alert_body, series, series)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SOURCE_HEALTH)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.notify(notificationId(sourceId), Notifications.CHANNEL_SOURCE_HEALTH) {
            setSmallIcon(MaterialSymbolsR.drawable.rounded_stethoscope)
            setContentTitle(title)
            setContentText(body)
            setStyle(NotificationCompat.BigTextStyle().bigText(body))
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }
    }

    private fun load(): Map<Long, SourceHealthRecord> {
        val stored = preferences.records.get().takeIf { it.isNotEmpty() } ?: return emptyMap()
        return try {
            json.decodeFromString(stored)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Dropping unreadable source health records" }
            emptyMap()
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        const val ACTION_OPEN_SOURCE_HEALTH = "mihon.feature.sourcehealth.OPEN"

        /** How long one source's check may take. */
        val CHECK_TIMEOUT = 15.seconds

        private const val MAX_MESSAGE_LENGTH = 300

        /** One per source, away from the app's other (small, mostly negative) notification ids. */
        private fun notificationId(sourceId: Long): Int = 2_000_000 + (sourceId.hashCode() and 0x3FFFFFF)
    }
}
