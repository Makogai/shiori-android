package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class ArenaRulesTest {

    @Test
    fun `xp counts chapters plus minutes capped at 30 per chapter`() {
        Xp.reads(0, 0) shouldBe 0
        Xp.reads(3, 600) shouldBe 40
        // Two hours on one chapter only earns 30 minutes
        Xp.reads(1, 7_200) shouldBe 40
        // Minutes are floored
        Xp.reads(2, 119) shouldBe 21
        Xp.reads(0, 600) shouldBe 0
        Xp.total(readsXp = 40, challengeWins = 2, questsCompleted = 3) shouldBe 40 + 500 + 150
    }

    @Test
    fun `reads xp caps minutes per day, not over the whole set`() {
        val day = LocalDate.of(2026, 11, 12)
        val days = listOf(
            // 1 chapter, 2 hours: capped at 30 minutes that day
            DayTotal("a", day, 1, 7_200),
            // 4 chapters, 10 minutes: the spare cap from the first day doesn't carry over
            DayTotal("a", day.plusDays(1), 4, 600),
        )
        Xp.reads(days) shouldBe (10 + 30) + (40 + 10)
        // Over the whole set at once it would have been 50 + min(130, 150)
        Xp.reads(5, 7_800) shouldBe 180
    }

    @Test
    fun `level thresholds follow 50 L (L + 1)`() {
        (1..4).map(Levels::threshold) shouldContainExactly listOf(100L, 300L, 600L, 1_000L)
        Levels.threshold(10) shouldBe 5_500
        Levels.of(0).level shouldBe 0
        Levels.of(99).level shouldBe 0
        Levels.of(100).level shouldBe 1
        Levels.of(299).level shouldBe 1
        Levels.of(300).level shouldBe 2
        Levels.of(600).level shouldBe 3
        Levels.of(127_500).level shouldBe 50

        val info = Levels.of(200)
        info.floorXp shouldBe 100
        info.nextXp shouldBe 300
        info.toNext shouldBe 100
        info.progress shouldBe 0.5f
    }

    @Test
    fun `level names change every five levels then every ten`() {
        mapOf(
            0 to "Novice Reader",
            4 to "Novice Reader",
            5 to "Page Turner",
            9 to "Page Turner",
            10 to "Chapter Chaser",
            15 to "Binge Apprentice",
            20 to "Manga Knight",
            25 to "Arc Conqueror",
            30 to "Volume Lord",
            39 to "Volume Lord",
            40 to "Otaku Sage",
            49 to "Otaku Sage",
            50 to "Legendary Reader",
            99 to "Legendary Reader",
        ).forEach { (level, name) -> Levels.name(level) shouldBe name }
    }

    @Test
    fun `tiers by season xp`() {
        mapOf(
            0L to Tier.BRONZE,
            499L to Tier.BRONZE,
            500L to Tier.SILVER,
            1_499L to Tier.SILVER,
            1_500L to Tier.GOLD,
            2_999L to Tier.GOLD,
            3_000L to Tier.PLATINUM,
            5_000L to Tier.DIAMOND,
            7_999L to Tier.DIAMOND,
            8_000L to Tier.LEGEND,
            100_000L to Tier.LEGEND,
        ).forEach { (xp, tier) -> Tier.of(xp) shouldBe tier }
        Tier.BRONZE.next shouldBe Tier.SILVER
        Tier.LEGEND.next.shouldBeNull()
        Tier.entries.map { it.color } shouldContainExactly
            listOf(0xFFCD7F32, 0xFFC0C7D0, 0xFFF5C542, 0xFF5ED3C9, 0xFF6AA8FF, 0xFFB15CFF)
    }

    @Test
    fun `seasons are months numbered from November 2026`() {
        Seasons.number(YearMonth.of(2026, 11)) shouldBe 1
        Seasons.number(YearMonth.of(2026, 12)) shouldBe 2
        Seasons.number(YearMonth.of(2027, 1)) shouldBe 3
        Seasons.of(LocalDate.of(2026, 12, 31)) shouldBe YearMonth.of(2026, 12)
        Seasons.past(YearMonth.of(2026, 11)).shouldBeEmpty()
        Seasons.past(YearMonth.of(2027, 1)) shouldContainExactly listOf(YearMonth.of(2026, 12), YearMonth.of(2026, 11))
    }

    @Test
    fun `daily quests for the first days of the Arena`() {
        fun day(index: Long) = DailyQuests.forDay(DailyQuests.EPOCH.plusDays(index))
        day(0) shouldContainExactly listOf(Quest.READ_3, Quest.STREAK, Quest.MINUTES_20)
        day(1) shouldContainExactly listOf(Quest.READ_5, Quest.SERIES_2, Quest.NEW_SERIES)
        day(2) shouldContainExactly listOf(Quest.LATE, Quest.MINUTES_45, Quest.STREAK)
        day(3) shouldContainExactly listOf(Quest.MINUTES_20, Quest.OUT_READ, Quest.SERIES_2)
        day(4) shouldContainExactly listOf(Quest.NEW_SERIES, Quest.EARLY, Quest.MINUTES_45)
        // Before the epoch the index wraps around instead of going negative
        day(-1) shouldContainExactly listOf(Quest.EARLY, Quest.NEW_SERIES, Quest.LATE)
        DailyQuests.dayIndex(LocalDate.of(2026, 11, 11)) shouldBe 0
        DailyQuests.dayIndex(LocalDate.of(2026, 12, 1)) shouldBe 20
    }

    @Test
    fun `daily quests are always three different ones`() {
        (0L..400L).forEach { DailyQuests.forDay(DailyQuests.EPOCH.plusDays(it)).toSet() shouldHaveSize 3 }
    }

    @Test
    fun `quest progress and completion`() {
        fun progress(quest: Quest, stats: QuestDayStats) = DailyQuests.progress(quest, stats)
        val stats = QuestDayStats(chapters = 4, seconds = 1_259, series = 2, early = 1, late = 0, newSeries = true)

        progress(Quest.READ_3, stats).run {
            value shouldBe 4
            done shouldBe true
        }
        progress(Quest.READ_5, stats).run {
            value shouldBe 4
            done shouldBe false
            fraction shouldBe 0.8f
        }
        progress(Quest.MINUTES_20, stats).run {
            value shouldBe 20
            done shouldBe true
        }
        progress(Quest.MINUTES_45, stats).done shouldBe false
        progress(Quest.SERIES_2, stats).done shouldBe true
        progress(Quest.EARLY, stats).done shouldBe true
        progress(Quest.LATE, stats).done shouldBe false
        progress(Quest.STREAK, stats).run {
            value shouldBe 1
            done shouldBe true
        }
        progress(Quest.STREAK, QuestDayStats()).done shouldBe false
        progress(Quest.NEW_SERIES, stats).done shouldBe true
        progress(Quest.NEW_SERIES, stats.copy(newSeries = false)).done shouldBe false

        // Out-read: strictly more chapters than every other player, and at least one
        progress(Quest.OUT_READ, stats.copy(othersBest = 3)).done shouldBe true
        progress(Quest.OUT_READ, stats.copy(othersBest = 4)).done shouldBe false
        progress(Quest.OUT_READ, QuestDayStats(othersBest = 0)).done shouldBe false
        progress(Quest.OUT_READ, stats.copy(othersBest = null)).done shouldBe true
    }

    @Test
    fun `each achievement unlocks at its target`() {
        val targets = mapOf(
            Achievement.FIRST_CHAPTER to LifetimeStats(chapters = 1),
            Achievement.BOOKWORM to LifetimeStats(chapters = 100),
            Achievement.LIBRARY_CARD to LifetimeStats(chapters = 500),
            Achievement.ARCHIVIST to LifetimeStats(chapters = 1_000),
            Achievement.WEEK_WARRIOR to LifetimeStats(bestStreak = 7),
            Achievement.UNSTOPPABLE to LifetimeStats(bestStreak = 30),
            Achievement.NIGHT_OWL to LifetimeStats(night = 50),
            Achievement.EARLY_BIRD to LifetimeStats(early = 50),
            Achievement.GENRE_HOPPER to LifetimeStats(genres = 10),
            Achievement.COLLECTOR to LifetimeStats(series = 25),
            Achievement.MARATHONER to LifetimeStats(seconds = 36_000),
            Achievement.CHAMPION to LifetimeStats(challengeWins = 1),
            Achievement.DYNASTY to LifetimeStats(bestWinRun = 3),
            Achievement.PERFECTIONIST to LifetimeStats(perfectDays = 7),
            Achievement.TASTE_MAKER to LifetimeStats(suggestionsAdded = 3),
        )
        targets.keys shouldContainExactly Achievement.entries.toSet()
        targets.forEach { (achievement, stats) ->
            Achievements.progress(achievement, stats).unlocked shouldBe true
        }
        val justShort = mapOf(
            Achievement.FIRST_CHAPTER to LifetimeStats(),
            Achievement.BOOKWORM to LifetimeStats(chapters = 99),
            Achievement.LIBRARY_CARD to LifetimeStats(chapters = 499),
            Achievement.ARCHIVIST to LifetimeStats(chapters = 999),
            Achievement.WEEK_WARRIOR to LifetimeStats(bestStreak = 6),
            Achievement.UNSTOPPABLE to LifetimeStats(bestStreak = 29),
            Achievement.NIGHT_OWL to LifetimeStats(night = 49),
            Achievement.EARLY_BIRD to LifetimeStats(early = 49),
            Achievement.GENRE_HOPPER to LifetimeStats(genres = 9),
            Achievement.COLLECTOR to LifetimeStats(series = 24),
            Achievement.MARATHONER to LifetimeStats(seconds = 35_999),
            Achievement.CHAMPION to LifetimeStats(),
            Achievement.DYNASTY to LifetimeStats(challengeWins = 5, bestWinRun = 2),
            Achievement.PERFECTIONIST to LifetimeStats(perfectDays = 6),
            Achievement.TASTE_MAKER to LifetimeStats(suggestionsAdded = 2),
        )
        justShort.forEach { (achievement, stats) ->
            Achievements.progress(achievement, stats).unlocked shouldBe false
        }
        Achievements.evaluate(LifetimeStats(chapters = 120)).filter {
            it.unlocked
        }.map { it.achievement } shouldContainExactly
            listOf(Achievement.FIRST_CHAPTER, Achievement.BOOKWORM)
        Achievements.progress(Achievement.LIBRARY_CARD, LifetimeStats(chapters = 125)).fraction shouldBe 0.25f
    }

    @Test
    fun `challenge winners need the top score above zero and share ties`() {
        ChallengeRules.winners(mapOf("a" to 5.0, "b" to 3.0)) shouldBe setOf("a")
        ChallengeRules.winners(mapOf("a" to 5.0, "b" to 5.0, "c" to 1.0)) shouldBe setOf("a", "b")
        ChallengeRules.winners(mapOf("a" to 0.0, "b" to 0.0)) shouldBe emptySet()
        ChallengeRules.winners(emptyMap()) shouldBe emptySet()
        ChallengeRules.winners(mapOf("a" to 12.5, "b" to 12.4)) shouldBe setOf("a")
    }

    @Test
    fun `win runs count consecutive challenges and shared wins`() {
        val results = listOf(setOf("a"), setOf("a", "b"), setOf("b"), setOf("a"), setOf("a"), setOf("a"), emptySet())
        ChallengeRules.bestWinRun(results, "a") shouldBe 3
        ChallengeRules.bestWinRun(results, "b") shouldBe 2
        ChallengeRules.bestWinRun(results, "c") shouldBe 0
    }

    @Test
    fun `rival is the player right above, or right below when first`() {
        val ranking = listOf("a", "b", "c")
        RivalryRules.rivalOf(ranking, "a") shouldBe "b"
        RivalryRules.rivalOf(ranking, "b") shouldBe "a"
        RivalryRules.rivalOf(ranking, "c") shouldBe "b"
        RivalryRules.rivalOf(listOf("a"), "a").shouldBeNull()
        RivalryRules.rivalOf(ranking, "x").shouldBeNull()
    }

    @Test
    fun `tug of war share`() {
        RivalryRules.tugShare(0, 0) shouldBe 0.5f
        RivalryRules.tugShare(3, 1) shouldBe 0.75f
        RivalryRules.tugShare(0, 4) shouldBe 0f
    }

    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `head to head record counts completed days by chapters then seconds`() {
        val daily = listOf(
            DayTotal("me", monday, 3, 0),
            DayTotal("rival", monday, 1, 0),
            DayTotal("me", monday.plusDays(1), 2, 100),
            DayTotal("rival", monday.plusDays(1), 2, 50),
            // Nobody read on Wednesday: not a tie
            DayTotal("me", monday.plusDays(3), 1, 60),
            DayTotal("rival", monday.plusDays(3), 1, 60),
            DayTotal("rival", monday.plusDays(4), 2, 0),
            DayTotal("third", monday.plusDays(4), 9, 0),
            // Today doesn't count yet
            DayTotal("me", monday.plusDays(5), 9, 0),
        )
        RivalryRules.record(daily, "me", "rival", today = monday.plusDays(5)) shouldBe
            HeadToHeadRecord(won = 2, lost = 1, tied = 1)
    }

    @Test
    fun `lead changes follow the running week total`() {
        val daily = listOf(
            DayTotal("me", monday, 3, 0),
            DayTotal("rival", monday, 1, 0),
            // Rival takes over: 3 vs 6
            DayTotal("rival", monday.plusDays(1), 5, 0),
            // Tie at 6 vs 6 keeps the rival as leader
            DayTotal("me", monday.plusDays(2), 3, 0),
            // Back to me: 8 vs 6
            DayTotal("me", monday.plusDays(3), 2, 0),
            // Last week doesn't count
            DayTotal("rival", monday.minusDays(1), 50, 0),
        )
        RivalryRules.leadChanges(daily, "me", "rival", monday, today = monday.plusDays(3)) shouldBe 2
        RivalryRules.leadChanges(daily, "me", "rival", monday, today = monday) shouldBe 0
        RivalryRules.leadChanges(emptyList(), "me", "rival", monday, today = monday.plusDays(6)) shouldBe 0
    }

    @Test
    fun `win streak counts days won in a row ending yesterday`() {
        val daily = listOf(
            DayTotal("me", monday, 1, 0),
            DayTotal("rival", monday, 4, 0),
            DayTotal("me", monday.plusDays(1), 4, 0),
            DayTotal("me", monday.plusDays(2), 2, 10),
            DayTotal("rival", monday.plusDays(2), 2, 5),
            // Today losing so far doesn't break it
            DayTotal("rival", monday.plusDays(3), 4, 0),
        )
        RivalryRules.winStreak(daily, "me", "rival", today = monday.plusDays(3)) shouldBe 2
        RivalryRules.winStreak(daily, "rival", "me", today = monday.plusDays(3)) shouldBe 0
        // Losing a finished day ends it, and so does a day nobody read
        RivalryRules.winStreak(daily, "me", "rival", today = monday.plusDays(4)) shouldBe 0
        RivalryRules.winStreak(daily, "me", "rival", today = monday.plusDays(5)) shouldBe 0
    }

    @Test
    fun `places are shared on ties`() {
        Places.of(mapOf("a" to 5L, "b" to 5L, "c" to 3L)) shouldBe mapOf("a" to 1, "b" to 1, "c" to 3)
    }

    @Test
    fun `upload cursor starts at registration and moves to now once for pre-Arena installs`() {
        val now = 1_763_000_000_000L
        // A v1 install that already uploaded: jump to now, no backfill
        UploadCursor(
            uploadedUntil = 1_700_000_000_000L,
            arenaStarted = false,
        ).onLaunch(registered = true, now = now) shouldBe
            UploadCursor(now, arenaStarted = true)
        // Only once
        UploadCursor(now - 5_000, arenaStarted = true).onLaunch(registered = true, now = now) shouldBe
            UploadCursor(now - 5_000, arenaStarted = true)
        // Not registered yet: registering sets the cursor
        UploadCursor(0, arenaStarted = false).onLaunch(registered = false, now = now) shouldBe
            UploadCursor(0, arenaStarted = true)
        UploadCursor.onRegistered(now) shouldBe UploadCursor(now, arenaStarted = true)
    }
}
