package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.source.local.isLocal

/** My library stats in the shared v5 shape, counted the way the Statistics screen counts them. */
@Inject
@SingleIn(AppScope::class)
class LibraryStatsCollector(
    private val getLibraryManga: GetLibraryManga,
    private val getTotalReadDuration: GetTotalReadDuration,
    private val getTracksPerManga: GetTracksPerManga,
    private val trackerManager: TrackerManager,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
) {

    suspend fun collect(): LibraryStats = withContext(Dispatchers.IO) {
        val library = getLibraryManga.await().distinctBy { it.id }
        val loggedIn = trackerManager.loggedInTrackers()
        val loggedInIds = loggedIn.map { it.id }.toHashSet()
        val tracksPerManga = if (loggedInIds.isEmpty()) emptyMap() else getTracksPerManga.await()
        val tracks = library.associate { manga ->
            manga.id to tracksPerManga[manga.id].orEmpty().filter { it.trackerId in loggedInIds }
        }
        val meanScore = tracks.values
            .map { list -> list.filter { it.score > 0.0 } }
            .filter { it.isNotEmpty() }
            .map { list -> list.mapNotNull { trackerManager.get(it.trackerId)?.get10PointScore(it) }.average() }
            .filter { !it.isNaN() }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val entries = library.map { item ->
            LibraryStatsEntry(
                completed = item.manga.status.toInt() == SManga.COMPLETED && item.unreadCount == 0L,
                started = item.hasStarted,
                local = item.manga.isLocal(),
                totalChapters = item.totalChapters,
                readChapters = item.readCount,
                genres = item.manga.genre.orEmpty(),
                sourceName = sourceManager.getOrStub(item.manga.source).name,
                tracked = tracks[item.id].orEmpty().isNotEmpty(),
            )
        }
        LibraryStatsRules.build(
            entries = entries,
            downloadedChapters = downloadManager.getDownloadCount(),
            readMillis = getTotalReadDuration.await(),
            meanScore = meanScore,
            trackersUsed = loggedIn.size,
            app = FriendsRepository.APP,
            appVersion = BuildConfig.VERSION_NAME,
        ).copy(timezone = java.time.ZoneId.systemDefault().id)
    }
}
