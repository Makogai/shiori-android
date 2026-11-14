package mihon.feature.habits

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReadingDaysTest {

    private val zone = ZoneId.of("Europe/Tallinn")
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun `counts today's chapters on the local calendar day`() {
        val reads = listOf(
            "2026-11-14T08:00:00Z",
            "2026-11-13T22:30:00Z", // 00:30 on the 14th locally
            "2026-11-13T21:30:00Z", // 23:30 on the 13th locally
            "2026-11-14T21:59:00Z", // 23:59 on the 14th locally
            "2026-11-14T22:01:00Z", // 00:01 on the 15th locally
        ).map(Instant::parse)

        ReadingDays.countOn(reads, today, zone) shouldBe 3
        ReadingDays.countByDay(reads, zone)[today.minusDays(1)] shouldBe 1
    }

    @Test
    fun `streaks run to today, or to yesterday while today is still open`() {
        val counts = mapOf(today to 2, today.minusDays(1) to 1, today.minusDays(2) to 5, today.minusDays(4) to 1)

        ReadingDays.streak(counts, today) shouldBe 3
        ReadingDays.streak(counts - today, today) shouldBe 2
        ReadingDays.streak(mapOf(today.minusDays(2) to 1), today) shouldBe 0
    }

    @Test
    fun `goal streak counts only days that met the goal`() {
        val counts = mapOf(today to 1, today.minusDays(1) to 3, today.minusDays(2) to 4, today.minusDays(3) to 2)

        ReadingDays.goalStreak(counts, goal = 3, today = today) shouldBe 2
        ReadingDays.goalStreak(counts, goal = 1, today = today) shouldBe 4
        ReadingDays.goalLeft(today = 7, goal = 10) shouldBe 3
        ReadingDays.goalLeft(today = 12, goal = 10) shouldBe 0
        ReadingDays.goalLeft(today = 7, goal = null) shouldBe null
    }

    @Test
    fun `active time stops counting after two idle minutes`() {
        val counter = ActiveTimeCounter()
        counter.ping(0) shouldBe 0
        counter.ping(30_000) shouldBe 30_000
        counter.ping(630_000) shouldBe 120_000
        counter.stop(640_000) shouldBe 10_000
        counter.ping(700_000) shouldBe 0
    }

    @Test
    fun `reading seconds add up per day`() {
        val value = ReadingSeconds.add(ReadingSeconds.add("", today, 30), today, 45)
        ReadingSeconds.decode(ReadingSeconds.add(value, today.minusDays(1), 10)) shouldBe
            mapOf(today to 75L, today.minusDays(1) to 10L)
    }
}
