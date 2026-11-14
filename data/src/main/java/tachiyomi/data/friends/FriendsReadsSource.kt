package tachiyomi.data.friends

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import kotlin.time.Instant

/** A read chapter for the Friends League upload. */
data class FriendsRead(
    val sourceId: Long,
    val mangaUrl: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val genres: List<String>?,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Double,
    val readAt: Instant,
    val readDurationMs: Long,
    /** The chapter is marked read (finished). */
    val read: Boolean,
)

@Inject
@SingleIn(AppScope::class)
class FriendsReadsSource(private val database: Database) {

    /** History entries last read after [after]. */
    suspend fun readSince(after: Instant): List<FriendsRead> =
        database.friendsQueries.readsSince(after).awaitAsList().map {
            FriendsRead(
                sourceId = it.source_id,
                mangaUrl = it.manga_url,
                mangaTitle = it.remote_title,
                coverUrl = it.remote_cover,
                genres = it.remote_genre,
                chapterUrl = it.chapter_url,
                chapterName = it.remote_name,
                chapterNumber = it.remote_chapter_number,
                readAt = it.read_at!!,
                readDurationMs = it.read_duration,
                read = it.user_read,
            )
        }

    /** Every history entry's last-read time and total duration in milliseconds. */
    fun subscribeReadTimes(): Flow<List<Pair<Instant, Long>>> =
        database.friendsQueries.readTimes().subscribeToList().map { rows ->
            rows.mapNotNull { row -> row.read_at?.let { it to row.read_duration } }
        }
}
