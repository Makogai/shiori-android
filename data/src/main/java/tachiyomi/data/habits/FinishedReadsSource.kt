package tachiyomi.data.habits

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

/** A finished chapter: its history entry is the moment it was last read, the chapter is marked read. */
data class FinishedRead(
    val chapterId: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Double,
    val readAt: Instant,
    val readDurationMs: Long,
    val cover: MangaCover,
)

@Inject
@SingleIn(AppScope::class)
class FinishedReadsSource(private val database: Database) {

    fun subscribe(): Flow<List<FinishedRead>> =
        database.habitsQueries.finishedReads().subscribeToList().map { rows ->
            rows.map {
                FinishedRead(
                    chapterId = it.chapter_id,
                    mangaId = it.manga_id,
                    mangaTitle = it.remote_title,
                    chapterName = it.remote_name,
                    chapterNumber = it.remote_chapter_number,
                    readAt = it.read_at!!,
                    readDurationMs = it.read_duration,
                    cover = MangaCover(
                        mangaId = it.manga_id,
                        sourceId = it.source_id,
                        isMangaFavorite = it.user_favorite_at != null,
                        url = it.remote_cover,
                        lastModified = it.state_cover_last_modified,
                    ),
                )
            }
        }

    /** The chapter id and time of the series' latest history entry. */
    suspend fun lastRead(mangaId: Long): Pair<Long, Instant>? =
        database.habitsQueries.lastReadOfManga(mangaId).awaitAsOneOrNull()?.let { it.chapter_id to it.read_at!! }
}
