package mihon.feature.recaps

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.habits.FinishedRead
import tachiyomi.domain.manga.model.MangaCover
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Instant

class RecapsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)
    private var nextChapter = 0L

    private fun read(mangaId: Long, title: String, day: Int, minutes: Long = 10) = FinishedRead(
        chapterId = nextChapter++,
        mangaId = mangaId,
        mangaTitle = title,
        chapterName = "",
        chapterNumber = 1.0,
        readAt = Instant.parse("2026-11-%02dT12:00:00Z".format(day)),
        readDurationMs = minutes * 60_000,
        cover = MangaCover(mangaId, 0, true, null, null),
    )

    private val reads = listOf(
        read(1, "A", 2),
        read(1, "A", 9),
        read(1, "A", 9),
        read(1, "A", 10),
        read(2, "B", 11),
    )

    private val week = RecapPeriod.containing(RecapKind.Week, today)

    @Test
    fun `weeks run Monday to Sunday and keys round-trip`() {
        week.start shouldBe LocalDate.of(2026, 11, 9)
        week.key shouldBe "week-2026-W46"
        RecapPeriod.parse(week.key) shouldBe week
        RecapPeriod.parse("month-2026-11") shouldBe RecapPeriod(RecapKind.Month, LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `a week's recap totals, compares and ranks`() {
        val recap = RecapLibrary(reads, zone).recap(week, today)
        recap.totals shouldBe RecapTotals(chapters = 4, readMillis = 40 * 60_000, series = 2)
        recap.previous.chapters shouldBe 1
        recap.chaptersChange shouldBe 3.0
        recap.inProgress shouldBe true
        recap.days shouldHaveSize 7
        recap.longestStreak shouldBe 3
        recap.busiestDay shouldBe (LocalDate.of(2026, 11, 9) to 2)
        recap.topSeries.map { it.title } shouldContainExactly listOf("A", "B")
        recap.newSeries.map { it.title } shouldContainExactly listOf("B")
    }

    @Test
    fun `a drop only shows once the period is over`() {
        val library = RecapLibrary(reads, zone)
        val next = RecapPeriod(RecapKind.Week, week.start.plusWeeks(1))
        library.recap(next, today).chaptersChange shouldBe null
        library.recap(next, LocalDate.of(2026, 11, 30)).chaptersChange shouldBe -1.0
    }

    @Test
    fun `past periods list only ended ones with reading, newest first`() {
        val library = RecapLibrary(reads, zone)
        library.pastPeriods(RecapKind.Week, today).map { it.first.start } shouldContainExactly
            listOf(LocalDate.of(2026, 11, 2))
        library.pastPeriods(RecapKind.Week, LocalDate.of(2026, 11, 16)).map { it.first.start } shouldContainExactly
            listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2))
        library.pastPeriods(RecapKind.Month, today) shouldHaveSize 0
        library.pastPeriods(RecapKind.Month, LocalDate.of(2026, 12, 1)).single().second.chapters shouldBe 5
    }

    @Test
    fun `a period ends at the start of the next one`() {
        week.lastDay shouldBe LocalDate.of(2026, 11, 15)
        week.hasEnded(LocalDate.of(2026, 11, 15)) shouldBe false
        week.hasEnded(LocalDate.of(2026, 11, 16)) shouldBe true
        RecapPeriod.containing(RecapKind.Week, LocalDate.of(2026, 11, 15)) shouldBe week
        RecapPeriod.containing(RecapKind.Week, LocalDate.of(2026, 11, 16)).start shouldBe LocalDate.of(2026, 11, 16)
    }

    @Test
    fun `a week across New Year belongs to its ISO year`() {
        val newYear = RecapPeriod.containing(RecapKind.Week, LocalDate.of(2027, 1, 1))
        newYear.start shouldBe LocalDate.of(2026, 12, 28)
        newYear.key shouldBe "week-2026-W53"
        RecapPeriod.parse(newYear.key) shouldBe newYear
    }

    @Test
    fun `reads fall on the local day`() {
        val lateSunday = read(1, "A", 15).copy(readAt = Instant.parse("2026-11-15T23:30:00Z"))
        val library = RecapLibrary(listOf(lateSunday), ZoneId.of("Europe/Tallinn"))
        library.recap(week, today).totals.chapters shouldBe 0
        library.recap(RecapPeriod(RecapKind.Week, LocalDate.of(2026, 11, 16)), today).totals.chapters shouldBe 1
    }
}
