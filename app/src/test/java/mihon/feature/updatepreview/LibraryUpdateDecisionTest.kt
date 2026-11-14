package mihon.feature.updatepreview

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_TRACKER_FINISHED
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class LibraryUpdateDecisionTest {

    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    private fun item(
        categories: List<Long> = listOf(1),
        total: Long = 10,
        read: Long = 10,
        status: Int = SManga.ONGOING,
        strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        nextUpdate: Instant? = null,
    ) = LibraryManga(
        manga = Manga.create().copy(
            id = 1,
            status = status.toLong(),
            updateStrategy = strategy,
            nextUpdate = nextUpdate,
        ),
        categories = categories,
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    )

    private fun rules(
        included: Set<Long> = emptySet(),
        excluded: Set<Long> = emptySet(),
        restrictions: Set<String> = emptySet(),
    ) = UpdateRules(included, excluded, restrictions, fetchWindowUpperBound = now)

    private fun reason(item: LibraryManga, rules: UpdateRules, trackerFinished: Boolean = false) =
        LibraryUpdateDecision.skipReason(item, rules, trackerFinished)

    @Test
    fun `no settings checks everything`() {
        reason(item(read = 0), rules()) shouldBe null
    }

    @Test
    fun `excluded categories win over included ones`() {
        reason(item(categories = listOf(1, 2)), rules(included = setOf(1), excluded = setOf(2))) shouldBe
            SkipReason.ExcludedCategory
        reason(item(categories = listOf(3)), rules(included = setOf(1))) shouldBe SkipReason.NotIncludedCategory
        reason(item(categories = listOf(1)), rules(included = setOf(1))) shouldBe null
    }

    @Test
    fun `fetch once skips only once chapters exist`() {
        reason(item(strategy = UpdateStrategy.ONLY_FETCH_ONCE), rules()) shouldBe SkipReason.OnlyFetchOnce
        reason(item(strategy = UpdateStrategy.ONLY_FETCH_ONCE, total = 0, read = 0), rules()) shouldBe null
    }

    @Test
    fun `restrictions apply in order`() {
        val all = rules(
            restrictions = setOf(
                MANGA_NON_COMPLETED,
                MANGA_TRACKER_FINISHED,
                MANGA_HAS_UNREAD,
                MANGA_NON_READ,
                MANGA_OUTSIDE_RELEASE_PERIOD,
            ),
        )
        reason(item(status = SManga.COMPLETED, read = 0), all, trackerFinished = true) shouldBe SkipReason.Completed
        reason(item(read = 0), all, trackerFinished = true) shouldBe SkipReason.TrackerFinished
        reason(item(read = 5), all) shouldBe SkipReason.HasUnread
        reason(item(nextUpdate = now + 2.days), all) shouldBe SkipReason.OutsideReleasePeriod
        reason(item(nextUpdate = now - 1.days), all) shouldBe null
    }

    @Test
    fun `not started needs chapters and no reads`() {
        val notStarted = rules(restrictions = setOf(MANGA_NON_READ))
        reason(item(read = 0), notStarted) shouldBe SkipReason.NotStarted
        reason(item(total = 0, read = 0), notStarted) shouldBe null
    }

    @Test
    fun `tracker rule is off unless picked`() {
        reason(item(), rules(), trackerFinished = true) shouldBe null
    }
}
