package tachiyomi.data.readingpace

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database

/** A chapter in the reading history: how long it was read and the last page reached (0-based). */
data class PaceSample(val readDurationMs: Long, val lastPageRead: Int)

@Inject
@SingleIn(AppScope::class)
class ReadingPaceSource(private val database: Database) {

    /** The most recently read chapters with a reading time, newest first. */
    suspend fun recent(limit: Long): List<PaceSample> =
        database.readingPaceQueries.recentSamples(limit).awaitAsList().map {
            PaceSample(it.read_duration, it.user_last_page_read)
        }
}
