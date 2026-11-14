package mihon.feature.shelves

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SmartShelvesTest {

    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    private fun item(
        id: Long,
        total: Long,
        read: Long,
        lastRead: Instant? = now - 1.days,
        fetched: Instant? = now - 2.days,
        added: Instant? = now - 100.days,
    ) = LibraryManga(
        manga = Manga.create().copy(id = id, favoriteAt = added),
        categories = listOf(0),
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        latestUpload = null,
        chapterFetchedAt = fetched,
        lastRead = lastRead,
    )

    @Test
    fun `caught up needs everything read and a start`() {
        SmartShelfLogic.shelves(item(1, total = 10, read = 10), now) shouldBe setOf(SmartShelf.CaughtUp)
        SmartShelfLogic.shelves(item(2, total = 0, read = 0), now) shouldBe emptySet()
    }

    @Test
    fun `new chapters are ones fetched after the last read`() {
        SmartShelfLogic.shelves(item(1, total = 10, read = 2, fetched = now), now) shouldBe
            setOf(SmartShelf.NewChapters)
        SmartShelfLogic.shelves(item(2, total = 10, read = 2, fetched = now - 3.days), now) shouldBe emptySet()
    }

    @Test
    fun `almost finished from 80 percent read`() {
        SmartShelfLogic.shelves(item(1, total = 10, read = 8, fetched = null), now) shouldBe
            setOf(SmartShelf.AlmostFinished)
        SmartShelfLogic.shelves(item(2, total = 10, read = 7, fetched = null), now) shouldBe emptySet()
    }

    @Test
    fun `untouched after 30 days, by when it was added if never read`() {
        SmartShelfLogic.shelves(item(1, total = 10, read = 1, lastRead = now - 31.days, fetched = null), now) shouldBe
            setOf(SmartShelf.Untouched)
        SmartShelfLogic.shelves(item(2, total = 10, read = 0, lastRead = null), now) shouldBe
            setOf(SmartShelf.Untouched)
        SmartShelfLogic.shelves(item(3, total = 10, read = 0, lastRead = null, added = now - 5.days), now) shouldBe
            emptySet()
    }

    @Test
    fun `build keeps enabled shelves in order and sorts each`() {
        val library = listOf(
            item(1, total = 10, read = 9, fetched = null),
            item(2, total = 10, read = 10),
            item(3, total = 10, read = 8, fetched = null),
        )
        val shelves = SmartShelfLogic.build(library, setOf(SmartShelf.AlmostFinished), now)
        shelves.map { it.first } shouldContainExactly listOf(SmartShelf.AlmostFinished)
        shelves.single().second.map { it.id } shouldContainExactly listOf(1L, 3L)
    }
}
