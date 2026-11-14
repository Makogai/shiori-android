package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.feature.friends.domain.LocalRead
import tachiyomi.data.friends.FriendsRead
import tachiyomi.data.friends.FriendsReadsSource
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import java.time.Instant as JavaInstant

@Inject
@SingleIn(AppScope::class)
class LocalReads(
    private val source: FriendsReadsSource,
    private val sourceManager: SourceManager,
) {

    suspend fun readSince(after: JavaInstant): List<LocalRead> =
        source.readSince(after.toKotlinInstant()).map { it.toLocalRead() }

    fun subscribeReadTimes(): Flow<List<Pair<JavaInstant, Long>>> =
        source.subscribeReadTimes().map { rows -> rows.map { it.first.toJavaInstant() to it.second } }

    private suspend fun FriendsRead.toLocalRead() = LocalRead(
        sourceId = sourceId,
        sourceName = sourceManager.getOrStub(sourceId).name,
        mangaUrl = mangaUrl,
        mangaTitle = mangaTitle,
        coverUrl = coverUrl,
        genres = genres,
        chapterUrl = chapterUrl,
        chapterName = chapterName,
        chapterNumber = chapterNumber,
        readAt = readAt.toJavaInstant(),
        readDurationMs = readDurationMs,
        read = read,
    )
}
