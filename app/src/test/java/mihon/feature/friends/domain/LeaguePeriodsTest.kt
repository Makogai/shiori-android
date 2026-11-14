package mihon.feature.friends.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class LeaguePeriodsTest {

    private val tallinn = ZoneId.of("Europe/Tallinn")

    @Test
    fun `today runs from local midnight to the next one`() {
        LeaguePeriods.range(LeaguePeriod.TODAY, instant("2026-11-11T12:00:00Z"), tallinn) shouldBe
            range("2026-11-10T22:00:00Z", "2026-11-11T22:00:00Z")
        // Already the 12th in Tallinn
        LeaguePeriods.range(LeaguePeriod.TODAY, instant("2026-11-11T23:30:00Z"), tallinn) shouldBe
            range("2026-11-11T22:00:00Z", "2026-11-12T22:00:00Z")
    }

    @Test
    fun `days around DST changes are 23 and 25 hours long`() {
        LeaguePeriods.range(LeaguePeriod.TODAY, instant("2026-03-29T12:00:00Z"), tallinn) shouldBe
            range("2026-03-28T22:00:00Z", "2026-03-29T21:00:00Z")
        LeaguePeriods.range(LeaguePeriod.TODAY, instant("2026-10-25T12:00:00Z"), tallinn) shouldBe
            range("2026-10-24T21:00:00Z", "2026-10-25T22:00:00Z")
    }

    @Test
    fun `a day whose midnight is skipped starts at the first valid time`() {
        val havana = ZoneId.of("America/Havana")
        val day = LeaguePeriods.dayRange(LocalDate.of(2026, 3, 8), havana)
        day.from.atZone(havana).toLocalTime().hour shouldBe 1
        LeaguePeriods.dayRange(LocalDate.of(2026, 3, 7), havana).to shouldBe day.from
    }

    @Test
    fun `weeks start on Monday in the league timezone`() {
        val week = range("2026-11-08T22:00:00Z", "2026-11-15T22:00:00Z")
        LeaguePeriods.range(LeaguePeriod.WEEK, instant("2026-11-11T12:00:00Z"), tallinn) shouldBe week
        LeaguePeriods.range(LeaguePeriod.WEEK, instant("2026-11-08T22:00:00Z"), tallinn) shouldBe week
        // Sunday 23:59 local is still the same week
        LeaguePeriods.range(LeaguePeriod.WEEK, instant("2026-11-15T21:59:59Z"), tallinn) shouldBe week
        LeaguePeriods.range(LeaguePeriod.WEEK, instant("2026-11-15T22:00:00Z"), tallinn) shouldBe
            range("2026-11-15T22:00:00Z", "2026-11-22T22:00:00Z")
    }

    @Test
    fun `week containing the DST change is an hour longer`() {
        LeaguePeriods.range(LeaguePeriod.WEEK, instant("2026-10-25T12:00:00Z"), tallinn) shouldBe
            range("2026-10-18T21:00:00Z", "2026-10-25T22:00:00Z")
    }

    @Test
    fun `month runs from the 1st to the 1st`() {
        LeaguePeriods.range(LeaguePeriod.MONTH, instant("2026-10-15T12:00:00Z"), tallinn) shouldBe
            range("2026-09-30T21:00:00Z", "2026-10-31T22:00:00Z")
        LeaguePeriods.range(LeaguePeriod.MONTH, instant("2026-12-31T23:00:00Z"), ZoneOffset.UTC) shouldBe
            range("2026-12-01T00:00:00Z", "2027-01-01T00:00:00Z")
    }

    @Test
    fun `all time runs from 2000 to a day from now`() {
        LeaguePeriods.range(LeaguePeriod.ALL, instant("2026-11-11T12:00:00Z"), tallinn) shouldBe
            range("2000-01-01T00:00:00Z", "2026-11-12T12:00:00Z")
    }

    @Test
    fun `unknown or blank timezones fall back to UTC`() {
        LeaguePeriods.zone("Europe/Tallinn") shouldBe tallinn
        LeaguePeriods.zone("Not/AZone") shouldBe ZoneOffset.UTC
        LeaguePeriods.zone("") shouldBe ZoneOffset.UTC
        LeaguePeriods.zone(null) shouldBe ZoneOffset.UTC
    }

    private fun instant(value: String) = Instant.parse(value)

    private fun range(from: String, to: String) = TimeRange(instant(from), instant(to))
}
