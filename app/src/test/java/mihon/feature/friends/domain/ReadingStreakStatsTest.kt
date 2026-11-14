package mihon.feature.friends.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ReadingStreakStatsTest {

    private val zone = ZoneId.of("Europe/Tallinn")

    // Wednesday 2026-11-11, 14:00 in Tallinn
    private val now = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun `computes streaks, weekly totals and the last seven days in the device timezone`() {
        val reads = listOf(
            "2026-11-11T08:00:00Z" to 60_000L,
            "2026-11-11T09:00:00Z" to 120_000L,
            "2026-11-10T21:30:00Z" to 30_000L, // 23:30 on the 10th locally
            "2026-11-09T22:30:00Z" to 0L, // 00:30 on the 10th locally
            "2026-11-08T21:59:00Z" to 600_000L, // Sunday 23:59 locally: last week
            "2026-11-02T10:00:00Z" to 1_000L, // Monday of last week
            "2026-10-20T10:00:00Z" to 1_000L,
            "2026-10-21T10:00:00Z" to 1_000L,
            "2026-10-22T10:00:00Z" to 1_000L,
            "2026-10-23T10:00:00Z" to 1_000L,
            "1970-01-01T00:00:00Z" to 5_000L, // reset history entries are ignored
        ).map { Instant.parse(it.first) to it.second }

        ReadingStreakStats.compute(reads, now, zone) shouldBe ReadingStreakStats(
            currentStreak = 2,
            bestStreak = 4,
            thisWeekChapters = 4,
            thisWeekSeconds = 210,
            lastWeekChapters = 2,
            lastWeekSeconds = 601,
            lastSevenDays = listOf(0, 0, 0, 1, 0, 2, 2),
        )
    }

    @Test
    fun `empty history has no streak`() {
        ReadingStreakStats.compute(emptyList(), now, zone) shouldBe ReadingStreakStats(
            currentStreak = 0,
            bestStreak = 0,
            thisWeekChapters = 0,
            thisWeekSeconds = 0,
            lastWeekChapters = 0,
            lastWeekSeconds = 0,
            lastSevenDays = List(7) { 0 },
        )
    }
}
