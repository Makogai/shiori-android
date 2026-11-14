package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LeagueAwardsTest {

    private val day = LocalDate.of(2026, 11, 3)

    @Test
    fun `gold and silver go to the top two of a pair`() {
        Medals.placings(listOf(total("a", 5), total("b", 3)), listOf("a", "b")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.SILVER)
    }

    @Test
    fun `bronze needs at least three members`() {
        val rows = listOf(total("a", 5), total("b", 3), total("c", 1))
        Medals.placings(rows, listOf("a", "b", "c")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.SILVER, "c" to Medal.BRONZE)
        // c left the league: two members can't earn bronze and former members don't place
        Medals.placings(rows, listOf("a", "b")) shouldBe mapOf("a" to Medal.GOLD, "b" to Medal.SILVER)
    }

    @Test
    fun `ties share the place and skip the next one`() {
        val rows = listOf(total("a", 5, 60), total("b", 5, 60), total("c", 2), total("d", 1))
        Medals.placings(rows, listOf("a", "b", "c", "d")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.GOLD, "c" to Medal.BRONZE)
    }

    @Test
    fun `seconds break chapter ties`() {
        Medals.placings(listOf(total("a", 5, 100), total("b", 5, 90)), listOf("a", "b")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.SILVER)
    }

    @Test
    fun `a period where nobody read awards nothing but members who read nothing still place`() {
        Medals.placings(listOf(total("a", 0), total("b", 0)), listOf("a", "b")).shouldBeEmpty()
        Medals.placings(emptyList(), listOf("a", "b", "c")).shouldBeEmpty()
        Medals.placings(listOf(total("a", 4), total("b", 0, 300)), listOf("a", "b", "c")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.SILVER, "c" to Medal.BRONZE)
        // Two members who read nothing tie for second
        Medals.placings(listOf(total("a", 4)), listOf("a", "b", "c")) shouldBe
            mapOf("a" to Medal.GOLD, "b" to Medal.SILVER, "c" to Medal.SILVER)
    }

    @Test
    fun `cabinets count only completed periods since the league was created`() {
        val today = LocalDate.of(2026, 11, 11) // Wednesday
        val created = LocalDate.of(2026, 10, 30)
        val daily = listOf(
            // Before the league: neither the day nor its week (ended Oct 26) count, its month (October) does
            DayTotal("a", LocalDate.of(2026, 10, 20), 10, 0),
            // Completed days in October and in the week of Nov 2
            DayTotal("b", LocalDate.of(2026, 10, 31), 2, 0),
            DayTotal("a", LocalDate.of(2026, 11, 2), 1, 0),
            DayTotal("b", LocalDate.of(2026, 11, 2), 3, 0),
            // Today: the day, week and month are still running
            DayTotal("a", today, 50, 0),
            // Not a member any more
            DayTotal("x", LocalDate.of(2026, 11, 2), 99, 0),
        )

        val cabinets = Medals.cabinets(daily, listOf("a", "b"), created, today)

        // Days: Oct 31 and Nov 2 b gold, a silver. Weeks of Oct 26 and Nov 2: b gold, a silver.
        // October: a 10 vs b 2 -> a gold, b silver.
        cabinets.getValue("a") shouldBe MedalCabinet(
            day = MedalTally(silver = 2),
            week = MedalTally(silver = 2),
            month = MedalTally(gold = 1),
        )
        cabinets.getValue("b") shouldBe MedalCabinet(
            day = MedalTally(gold = 2),
            week = MedalTally(gold = 2),
            month = MedalTally(silver = 1),
        )
        cabinets.keys shouldBe setOf("a", "b")
    }

    @Test
    fun `current streak ends today or yesterday`() {
        val today = LocalDate.of(2026, 11, 11)
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        Streaks.current(days, today) shouldBe 3
        Streaks.current(days - today, today) shouldBe 2
        Streaks.current(setOf(today.minusDays(2)), today) shouldBe 0
        Streaks.current(emptySet(), today) shouldBe 0
    }

    @Test
    fun `best streak is the longest run`() {
        val start = LocalDate.of(2026, 1, 30)
        val days = (0L..4).map { start.plusDays(it) }.toSet() + LocalDate.of(2026, 3, 1)
        Streaks.best(days) shouldBe 5
        Streaks.best(emptySet()) shouldBe 0
    }

    @Test
    fun `champion is first by chapters then seconds and nobody on a tie`() {
        Titles.compute(listOf(stats("a", chapters = 10, seconds = 5), stats("b", chapters = 10, seconds = 9)))
            .holder(LeagueTitle.CHAMPION) shouldBe "b"
        Titles.compute(listOf(stats("a", chapters = 10, seconds = 5), stats("b", chapters = 10, seconds = 5)))
            .holder(LeagueTitle.CHAMPION) shouldBe null
    }

    @Test
    fun `titles need at least one chapter this month`() {
        Titles.compute(listOf(stats("a", chapters = 0, seconds = 500, currentStreak = 9))).shouldBeEmpty()
    }

    @Test
    fun `streak master needs a streak of three days`() {
        Titles.compute(listOf(stats("a", currentStreak = 2), stats("b", currentStreak = 1)))
            .holder(LeagueTitle.STREAK_MASTER) shouldBe null
        Titles.compute(listOf(stats("a", currentStreak = 3), stats("b", currentStreak = 1)))
            .holder(LeagueTitle.STREAK_MASTER) shouldBe "a"
        Titles.compute(listOf(stats("a", currentStreak = 4), stats("b", currentStreak = 4)))
            .holder(LeagueTitle.STREAK_MASTER) shouldBe null
    }

    @Test
    fun `binge lord has the biggest single day`() {
        Titles.compute(listOf(stats("a", bestDay = 7), stats("b", bestDay = 12)))
            .holder(LeagueTitle.BINGE_LORD) shouldBe "b"
    }

    @Test
    fun `night owl and early bird count chapters in their hours`() {
        val titles = Titles.compute(
            listOf(stats("a", night = 4, morning = 0), stats("b", night = 1, morning = 2)),
        )
        titles.holder(LeagueTitle.NIGHT_OWL) shouldBe "a"
        titles.holder(LeagueTitle.EARLY_BIRD) shouldBe "b"
        Titles.compute(listOf(stats("a"), stats("b"))).holder(LeagueTitle.NIGHT_OWL) shouldBe null
        Titles.NIGHT_HOURS shouldBe 0..4
        Titles.MORNING_HOURS shouldBe 5..8
    }

    @Test
    fun `marathoner has the most reading time`() {
        Titles.compute(listOf(stats("a", chapters = 30, seconds = 100), stats("b", chapters = 1, seconds = 200)))
            .holder(LeagueTitle.MARATHONER) shouldBe "b"
    }

    @Test
    fun `speed reader has the lowest timed average with at least 20 chapters`() {
        val titles = Titles.compute(
            listOf(
                stats("a", chapters = 20, seconds = 2000), // 100 s
                stats("b", chapters = 40, seconds = 2400), // 60 s
                stats("c", chapters = 19, seconds = 19), // too few chapters
                stats("d", chapters = 50, seconds = 0), // untimed
            ),
        )
        titles.holder(LeagueTitle.SPEED_READER) shouldBe "b"
        Titles.compute(listOf(stats("a", chapters = 20, seconds = 1200), stats("b", chapters = 40, seconds = 2400)))
            .holder(LeagueTitle.SPEED_READER) shouldBe null
    }

    @Test
    fun `explorer reads the most distinct series`() {
        Titles.compute(listOf(stats("a", series = 3), stats("b", series = 8)))
            .holder(LeagueTitle.EXPLORER) shouldBe "b"
    }

    @Test
    fun `genre kings rule the league's top three genres with at least five chapters`() {
        val titles = Titles.compute(
            listOf(
                stats("a", genres = mapOf("Action" to 20, "Romance" to 4, "Drama" to 6, "Horror" to 1)),
                stats("b", genres = mapOf("Action" to 5, "Romance" to 6, "Drama" to 6, "Comedy" to 3)),
            ),
        )
        Titles.topGenres(
            listOf(
                stats("a", genres = mapOf("Action" to 20, "Romance" to 4, "Drama" to 6, "Horror" to 1)),
                stats("b", genres = mapOf("Action" to 5, "Romance" to 6, "Drama" to 6, "Comedy" to 3)),
            ),
        ) shouldContainExactly listOf("Action", "Drama", "Romance")
        titles.filter { it.title == LeagueTitle.GENRE_KING }
            .map { it.genre to it.holder } shouldContainExactlyInAnyOrder listOf("Action" to "a", "Romance" to "b")
        titles.first { it.genre == "Action" }.text shouldBe "🎭 Genre King · Action"
    }

    @Test
    fun `title text matches the shared contract`() {
        LeagueTitle.entries.map { TitleAward(it, "a").text } shouldContainExactly listOf(
            "👑 League Champion",
            "🔥 Streak Master",
            "📚 Binge Lord",
            "🦉 Night Owl",
            "🌅 Early Bird",
            "⏱️ Marathoner",
            "⚡ Speed Reader",
            "🧭 Explorer",
            "🎭 Genre King",
        )
    }

    @Test
    fun `head to head counts completed days of the month won by each side`() {
        val today = LocalDate.of(2026, 11, 11)
        val month = LocalDate.of(2026, 11, 1)
        val daily = listOf(
            DayTotal("me", LocalDate.of(2026, 10, 31), 9, 0), // last month
            DayTotal("me", LocalDate.of(2026, 11, 1), 3, 0),
            DayTotal("rival", LocalDate.of(2026, 11, 1), 2, 0),
            DayTotal("rival", LocalDate.of(2026, 11, 2), 1, 0),
            DayTotal("me", LocalDate.of(2026, 11, 3), 2, 10),
            DayTotal("rival", LocalDate.of(2026, 11, 3), 2, 10), // tie
            DayTotal("me", LocalDate.of(2026, 11, 4), 2, 20),
            DayTotal("rival", LocalDate.of(2026, 11, 4), 2, 10),
            DayTotal("rival", today, 5, 0), // still running
        )
        HeadToHead.monthlyWins(daily, "me", "rival", month, today) shouldBe (2 to 1)
    }

    private fun List<TitleAward>.holder(title: LeagueTitle) = singleOrNull { it.title == title }?.holder

    private fun total(user: String, chapters: Long, seconds: Long = 0) = DayTotal(user, day, chapters, seconds)

    private fun stats(
        user: String,
        chapters: Long = 1,
        seconds: Long = 0,
        series: Long = 0,
        genres: Map<String, Long> = emptyMap(),
        bestDay: Long = 0,
        night: Long = 0,
        morning: Long = 0,
        currentStreak: Int = 0,
    ) = MonthStats(user, chapters, seconds, series, genres, bestDay, night, morning, currentStreak)
}
