package mihon.feature.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class LibraryExtrasTest {

    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    private fun item(id: Long, total: Long, read: Long, lastRead: Instant?, fetched: Instant? = now - 1.days) =
        LibraryManga(
            manga = Manga.create().copy(id = id),
            categories = listOf(0),
            totalChapters = total,
            readCount = read,
            bookmarkCount = 0,
            latestUpload = null,
            chapterFetchedAt = fetched,
            lastRead = lastRead,
        )

    @Test
    fun `fuzzy search folds accents and forgives typos in longer words`() {
        FuzzySearch.matches("pokemon", "Pokémon Adventures") shouldBe true
        FuzzySearch.matches("berserl", "Berserk") shouldBe true
        FuzzySearch.matches("kimetzu", "Kimetsu no Yaiba") shouldBe true
        FuzzySearch.matches("dont", "Don't Toy with Me") shouldBe true
        FuzzySearch.matches("cat", "Bat Girl") shouldBe false
        FuzzySearch.matches("onepiece", "One Piece") shouldBe true
        FuzzySearch.matches("naruto", "Bleach") shouldBe false
    }

    @Test
    fun `distance counts swapped neighbours once`() {
        FuzzySearch.distance("abcd", "abdc") shouldBe 1
        FuzzySearch.distance("kitten", "sitting") shouldBe 3
        FuzzySearch.distance("kitten", "sitting", limit = 1) shouldBe 2
    }

    @Test
    fun `stalled needs a start, unread chapters and enough days`() {
        LibraryInsights.isStalled(item(1, 10, 3, now - 40.days), 30, now) shouldBe true
        LibraryInsights.isStalled(item(2, 10, 3, now - 10.days), 30, now) shouldBe false
        LibraryInsights.isStalled(item(3, 10, 10, now - 40.days), 30, now) shouldBe false
        LibraryInsights.isStalled(item(4, 10, 0, null), 30, now) shouldBe false
        LibraryInsights.isStalled(item(5, 10, 3, now - 40.days), 0, now) shouldBe false
    }

    @Test
    fun `up next takes recently read series with chapters fetched since`() {
        val library = listOf(
            item(1, 10, 5, now - 3.days, fetched = now - 1.days),
            item(2, 10, 5, now - 3.days, fetched = now - 4.days),
            item(3, 10, 5, now - 30.days, fetched = now - 1.days),
            item(4, 10, 10, now - 3.days, fetched = now - 1.days),
        )
        LibraryInsights.upNextCandidates(library, now, 14).map { it.id } shouldContainExactly listOf(1L)
    }

    @Test
    fun `next chapter follows the furthest read one`() {
        fun chapter(order: Long, read: Boolean) = Chapter.create().copy(id = order, sourceOrder = order, read = read)
        // Source order 0 is the newest: reading order is 3, 2, 1, 0
        val chapters = listOf(chapter(0, false), chapter(1, false), chapter(2, true), chapter(3, false))
        LibraryInsights.nextChapter(chapters)?.id shouldBe 1L
        LibraryInsights.nextChapter(listOf(chapter(0, true), chapter(1, false)))?.id shouldBe 1L
        LibraryInsights.nextChapter(listOf(chapter(0, true))) shouldBe null
    }

    @Test
    fun `section order keeps known names and appends new sections`() {
        LibrarySection.parseOrder("UpNext,Bogus,Summary,UpNext") shouldContainExactly listOf(
            LibrarySection.UpNext,
            LibrarySection.Summary,
            LibrarySection.ContinueReading,
            LibrarySection.Queue,
            LibrarySection.SmartShelves,
        )
        LibrarySection.visible("Queue,Summary", setOf("Summary", "Queue")) shouldContainExactly
            listOf(LibrarySection.Queue, LibrarySection.Summary)
    }

    @Test
    fun `surprise picks planned, stalled or unstarted series and skips finished ones`() {
        val library = listOf(
            item(1, 10, 0, null),
            item(2, 10, 3, now - 60.days),
            item(3, 10, 3, now - 1.days),
            item(4, 10, 0, null),
            item(5, 10, 10, now - 60.days),
            item(6, 10, 3, now - 1.days),
        )
        val badges = mapOf(4L to TrackBadge.Dropped, 6L to TrackBadge.PlanToRead)
        val candidates = SurpriseLogic.candidates(library, badges, 30, now)
        candidates.associate { it.item.id to it.reason } shouldBe mapOf(
            1L to SurpriseLogic.Reason.NotStarted,
            2L to SurpriseLogic.Reason.Stalled(60),
            6L to SurpriseLogic.Reason.PlanToRead,
        )
        val seen = setOf(1L, 2L)
        SurpriseLogic.pick(candidates, seen, Random(1))?.item?.id shouldBe 6L
        SurpriseLogic.pick(emptyList(), emptySet()) shouldBe null
    }
}
