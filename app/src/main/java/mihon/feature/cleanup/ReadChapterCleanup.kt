package mihon.feature.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object ReadChapterCleanupLogic {
    /** Settings → Downloads choices, in days; 0 is off. */
    val CHOICES = listOf(0, 1, 3, 7, 30)

    /**
     * The [chapters] read at least [days] ago, by their last read time in [readAt]. A chapter marked read without
     * being opened has no read time and stays. Bookmarked ones stay when [keepBookmarked].
     */
    fun expired(
        chapters: List<Chapter>,
        readAt: Map<Long, Instant>,
        now: Instant,
        days: Int,
        keepBookmarked: Boolean,
    ): List<Chapter> {
        if (days <= 0) return emptyList()
        return chapters.filter { chapter ->
            val time = readAt[chapter.id]
            chapter.read && !(keepBookmarked && chapter.bookmark) && time != null && now - time >= days.days
        }
    }
}

/** Deletes downloaded chapters some days after they were read (Settings → Downloads). */
@Inject
class ReadChapterCleanup(
    private val preferences: DownloadPreferences,
    private val getFavorites: GetFavorites,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
) {
    suspend fun run() {
        val days = preferences.removeAfterReadDays.get()
        if (days <= 0) return
        // Mihon's own setting: whether bookmarked chapters may be deleted
        val keepBookmarked = !preferences.removeBookmarkedChapters.get()
        val now = Clock.System.now()
        val manga = getFavorites.await().filter { downloadManager.getDownloadCount(it) > 0 }
        if (manga.isEmpty()) return
        val ids = manga.map { it.id }
        val chapters = chapterRepository.getChaptersByMangaIds(ids)
        val history = historyRepository.getHistoryByMangaIds(ids)
        manga.forEach { item ->
            val readAt = history[item.id].orEmpty().mapNotNull { h -> h.readAt?.let { h.chapterId to it } }.toMap()
            val expired = ReadChapterCleanupLogic.expired(
                chapters[item.id].orEmpty(),
                readAt,
                now,
                days,
                keepBookmarked,
            )
                .filter { downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, item.title, item.source) }
            if (expired.isNotEmpty()) {
                // Leaves chapters of categories excluded from deletion, as Mihon's remove-after-read does
                downloadManager.deleteChapters(expired, item, sourceManager.getOrStub(item.source))
            }
        }
    }
}

class ReadChapterCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            applicationContext.appGraph.readChapterCleanup.run()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Read chapter cleanup failed" }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ReadChapterCleanup"

        /** Schedules the daily cleanup, or cancels it when it is off. */
        fun setupTask(context: Context) {
            if (context.appGraph.downloadPreferences.removeAfterReadDays.get() <= 0) {
                context.workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ReadChapterCleanupWorker>(1, TimeUnit.DAYS).build()
            context.workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
