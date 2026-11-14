package mihon.feature.rhythm

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ReleaseRhythmTest {

    private val zone = ZoneOffset.UTC

    private fun millis(date: LocalDate) = date.atTime(12, 0).toInstant(zone).toEpochMilli()

    // A Thursday
    private val start = LocalDate.of(2026, 1, 1)

    private fun every(days: Long, count: Int) = (0 until count).map { millis(start.plusDays(it * days)) }

    @Test
    fun `weekly releases name the weekday`() {
        val dates = every(7, 6)
        ReleaseRhythmLogic.infer(dates, now = dates.last(), zone = zone) shouldBe
            ReleaseRhythm.Weekly(DayOfWeek.THURSDAY)
    }

    @Test
    fun `steady other intervals give every n days`() {
        val dates = every(3, 8)
        ReleaseRhythmLogic.infer(dates, now = dates.last(), zone = zone) shouldBe ReleaseRhythm.EveryNDays(3)
    }

    @Test
    fun `chapters on the same day count once`() {
        val dates = every(7, 5) + every(7, 5).map { it + 3_600_000 }
        ReleaseRhythmLogic.infer(dates, now = dates.max(), zone = zone) shouldBe
            ReleaseRhythm.Weekly(DayOfWeek.THURSDAY)
    }

    @Test
    fun `too few, irregular or stalled releases say nothing`() {
        ReleaseRhythmLogic.infer(every(7, 3), now = millis(start.plusDays(14)), zone = zone).shouldBeNull()
        val irregular = listOf(0L, 1, 9, 10, 25, 27, 40).map { millis(start.plusDays(it)) }
        ReleaseRhythmLogic.infer(irregular, now = irregular.last(), zone = zone).shouldBeNull()
        val weekly = every(7, 6)
        ReleaseRhythmLogic.infer(weekly, now = millis(start.plusDays(35 + 30)), zone = zone).shouldBeNull()
    }
}
