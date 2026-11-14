package mihon.feature.cleanup

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ReadChapterCleanupTest {

    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    private fun chapter(id: Long, read: Boolean = true, bookmark: Boolean = false) =
        Chapter.create().copy(id = id, read = read, bookmark = bookmark)

    private val chapters = listOf(
        chapter(1),
        chapter(2),
        chapter(3, bookmark = true),
        chapter(4, read = false),
        chapter(5),
    )
    private val readAt = mapOf(
        1L to now - 4.days,
        2L to now - 2.days,
        3L to now - 10.days,
        4L to now - 10.days,
    )

    private fun expired(days: Int, keepBookmarked: Boolean = true) =
        ReadChapterCleanupLogic.expired(chapters, readAt, now, days, keepBookmarked).map { it.id }

    @Test
    fun `deletes read chapters read long enough ago`() {
        expired(3) shouldContainExactly listOf(1L)
        expired(1) shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `bookmarked chapters follow the keep setting`() {
        expired(3, keepBookmarked = false) shouldContainExactly listOf(1L, 3L)
    }

    @Test
    fun `off, unread and never opened chapters stay`() {
        expired(0).shouldBeEmpty()
        ReadChapterCleanupLogic.expired(listOf(chapter(6)), mapOf(6L to now - 23.hours), now, 1, true)
            .shouldBeEmpty()
    }
}
