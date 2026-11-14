package mihon.feature.friends.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** v2 gamification rules from the shared contract (backend README, "The Reading Arena"); identical on both apps. */
object Xp {

    const val PER_CHAPTER = 10L
    const val MAX_MINUTES_PER_CHAPTER = 30L
    const val PER_CHALLENGE_WIN = 250L
    const val PER_QUEST = 50L

    /** XP for one Arena day of reads; minutes are floored and capped at 30 per chapter of that day. */
    fun reads(chapters: Long, seconds: Long): Long =
        PER_CHAPTER * chapters + minOf(seconds / 60, MAX_MINUTES_PER_CHAPTER * chapters)

    /** Reads XP over several days: the cap applies per day, so totals match the day-by-day history. */
    fun reads(days: Collection<DayTotal>): Long = days.sumOf { reads(it.chapters, it.seconds) }

    /** Lifetime XP (v6): reads + 250 per win + 50 per quest + settled bets; may be negative before [display]. */
    fun total(readsXp: Long, challengeWins: Int, questsCompleted: Int, bets: Long = 0): Long =
        readsXp + PER_CHALLENGE_WIN * challengeWins + PER_QUEST * questsCompleted + bets

    /** XP as shown and as level, tier, ranking and alerts use it: never below 0 (lost bets can go under). */
    fun display(xp: Long): Long = maxOf(0L, xp)

    /**
     * XP over time from the XP earned (or lost to bets) per day: a point at 0 the day before the first, then the
     * running total at the end of each day, plotted clamped at 0.
     */
    fun history(byDay: Map<LocalDate, Long>): List<Pair<LocalDate, Long>> {
        val dates = byDay.keys.sorted()
        if (dates.isEmpty()) return emptyList()
        var total = 0L
        return listOf(dates.first().minusDays(1) to 0L) + dates.map { day ->
            total += byDay.getValue(day)
            day to display(total)
        }
    }
}

data class LevelInfo(val level: Int, val xp: Long) {
    val name: String get() = Levels.name(level)

    /** Total XP at which [level] was reached. */
    val floorXp: Long get() = Levels.threshold(level)
    val nextXp: Long get() = Levels.threshold(level + 1)
    val toNext: Long get() = nextXp - xp

    /** Progress from this level to the next, 0..1. */
    val progress: Float get() = ((xp - floorXp).toFloat() / (nextXp - floorXp)).coerceIn(0f, 1f)
}

object Levels {

    /** Total XP needed for level [level]: 50 × L × (L + 1), so L1 = 100, L2 = 300, L3 = 600. */
    fun threshold(level: Int): Long = 50L * level * (level + 1)

    /** Level for [xp] lifetime XP; below 100 XP it's level 0. */
    fun of(xp: Long): LevelInfo {
        var level = 0
        while (threshold(level + 1) <= xp) level++
        return LevelInfo(level, xp)
    }

    fun name(level: Int): String = when {
        level < 5 -> "Novice Reader"
        level < 10 -> "Page Turner"
        level < 15 -> "Chapter Chaser"
        level < 20 -> "Binge Apprentice"
        level < 25 -> "Manga Knight"
        level < 30 -> "Arc Conqueror"
        level < 40 -> "Volume Lord"
        level < 50 -> "Otaku Sage"
        else -> "Legendary Reader"
    }
}

enum class Tier(val label: String, val minXp: Long, val color: Long) {
    BRONZE("Bronze", 0, 0xFFCD7F32),
    SILVER("Silver", 500, 0xFFC0C7D0),
    GOLD("Gold", 1_500, 0xFFF5C542),
    PLATINUM("Platinum", 3_000, 0xFF5ED3C9),
    DIAMOND("Diamond", 5_000, 0xFF6AA8FF),
    LEGEND("Legend", 8_000, 0xFFB15CFF),
    ;

    val next: Tier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(seasonXp: Long): Tier = entries.last { seasonXp >= it.minXp }
    }
}

/** Seasons are calendar months in the Arena timezone, numbered from November 2026. */
object Seasons {

    val FIRST: YearMonth = YearMonth.of(2026, 11)

    fun number(month: YearMonth): Int = ChronoUnit.MONTHS.between(FIRST, month).toInt() + 1

    fun of(day: LocalDate): YearMonth = YearMonth.from(day)

    /** Seasons that already ended before [current], newest first. */
    fun past(current: YearMonth): List<YearMonth> =
        generateSequence(current.minusMonths(1)) { it.minusMonths(1) }.takeWhile { it >= FIRST }.toList()
}

enum class Quest(val emoji: String, val label: String, val target: Long) {
    READ_3("📖", "Read 3 chapters", 3),
    MINUTES_20("⏳", "Read for 20 minutes", 20),
    SERIES_2("🎨", "Read 2 different series", 2),
    EARLY("🌅", "Read a chapter before 9:00", 1),
    LATE("🌙", "Read a chapter after 22:00", 1),
    STREAK("🔥", "Keep your streak alive", 1),
    OUT_READ("⚔️", "Out-read your rival today", 1),
    READ_5("📚", "Read 5 chapters", 5),
    NEW_SERIES("🧭", "Read a series you haven't read this week", 1),
    MINUTES_45("⏱️", "Read for 45 minutes", 45),
}

/** One player's numbers for one Arena day, as far as the quests need them. */
data class QuestDayStats(
    val chapters: Long = 0,
    val seconds: Long = 0,
    val series: Long = 0,
    /** Chapters read 00:00–08:59. */
    val early: Long = 0,
    /** Chapters read 22:00–23:59. */
    val late: Long = 0,
    /** Whether a series not read earlier this week (Monday until the start of this day) was read. */
    val newSeries: Boolean = false,
    /** Most chapters any other player read that day; null when playing alone. */
    val othersBest: Long? = null,
)

data class QuestProgress(val quest: Quest, val value: Long) {
    val done: Boolean get() = value >= quest.target
    val fraction: Float get() = (value.toFloat() / quest.target).coerceIn(0f, 1f)
}

object DailyQuests {

    /** Day 0 of the quest rotation. */
    val EPOCH: LocalDate = LocalDate.of(2026, 11, 11)

    const val PER_DAY = 3
    const val EARLY_BEFORE_HOUR = 9
    const val LATE_FROM_HOUR = 22

    fun dayIndex(day: LocalDate): Long = ChronoUnit.DAYS.between(EPOCH, day)

    /** Quest i = POOL[(D × 7 + i × 5) mod POOL.size], moving to the next index while it's already taken. */
    fun forDay(day: LocalDate): List<Quest> {
        val pool = Quest.entries
        val d = dayIndex(day)
        val picked = mutableListOf<Quest>()
        for (i in 0..<PER_DAY) {
            var index = Math.floorMod(d * 7 + i * 5, pool.size.toLong()).toInt()
            while (pool[index] in picked) index = (index + 1) % pool.size
            picked += pool[index]
        }
        return picked
    }

    fun progress(quest: Quest, stats: QuestDayStats): QuestProgress {
        val value = when (quest) {
            Quest.READ_3, Quest.READ_5 -> stats.chapters
            Quest.MINUTES_20, Quest.MINUTES_45 -> stats.seconds / 60
            Quest.SERIES_2 -> stats.series
            Quest.EARLY -> stats.early
            Quest.LATE -> stats.late
            Quest.STREAK -> stats.chapters.coerceAtMost(1)
            Quest.OUT_READ -> if (stats.chapters > 0 && stats.chapters > (stats.othersBest ?: 0)) 1 else 0
            Quest.NEW_SERIES -> if (stats.newSeries && stats.chapters > 0) 1 else 0
        }
        return QuestProgress(quest, value)
    }

    fun forDay(day: LocalDate, stats: QuestDayStats): List<QuestProgress> = forDay(day).map { progress(it, stats) }
}

/** [color] (ARGB) matches iOS. */
enum class Achievement(val emoji: String, val label: String, val target: Long, val color: Long) {
    FIRST_CHAPTER("📖", "First Chapter", 1, 0xFF34C759),
    BOOKWORM("🐛", "Bookworm", 100, 0xFF30B0C7),
    LIBRARY_CARD("🪪", "Library Card", 500, 0xFF5E5CE6),
    ARCHIVIST("🏛️", "Archivist", 1_000, 0xFFA2845E),
    WEEK_WARRIOR("🔥", "Week Warrior", 7, 0xFFFF9F0A),
    UNSTOPPABLE("☄️", "Unstoppable", 30, 0xFFFF375F),
    NIGHT_OWL("🦉", "Night Owl", 50, 0xFF5856D6),
    EARLY_BIRD("🌅", "Early Bird", 50, 0xFFFFB340),
    GENRE_HOPPER("🎭", "Genre Hopper", 10, 0xFFBF5AF2),
    COLLECTOR("🗂️", "Collector", 25, 0xFF0A84FF),
    MARATHONER("⏱️", "Marathoner", 10, 0xFF32D74B),
    CHAMPION("🏆", "Champion", 1, 0xFFF5C542),
    DYNASTY("👑", "Dynasty", 3, 0xFFFFD60A),
    PERFECTIONIST("🌟", "Perfectionist", 7, 0xFFFF6482),
    TASTE_MAKER("🎁", "Taste Maker", 3, 0xFF64D2FF),
}

/** A player's lifetime numbers from counted reads, as far as the achievements need them. */
data class LifetimeStats(
    val chapters: Long = 0,
    val seconds: Long = 0,
    val series: Long = 0,
    val genres: Int = 0,
    val bestStreak: Int = 0,
    /** Chapters read 00:00–04:59. */
    val night: Long = 0,
    /** Chapters read 05:00–08:59. */
    val early: Long = 0,
    val challengeWins: Int = 0,
    /** Most challenges won one after another. */
    val bestWinRun: Int = 0,
    val perfectDays: Int = 0,
    /** Own suggestions that friends added. */
    val suggestionsAdded: Int = 0,
)

data class AchievementProgress(val achievement: Achievement, val value: Long) {
    val unlocked: Boolean get() = value >= achievement.target
    val fraction: Float get() = (value.toFloat() / achievement.target).coerceIn(0f, 1f)
}

object Achievements {

    val NIGHT_HOURS = 0..4
    val EARLY_HOURS = 5..8

    fun evaluate(stats: LifetimeStats): List<AchievementProgress> = Achievement.entries.map { progress(it, stats) }

    fun progress(achievement: Achievement, stats: LifetimeStats): AchievementProgress {
        val value = when (achievement) {
            Achievement.FIRST_CHAPTER, Achievement.BOOKWORM, Achievement.LIBRARY_CARD, Achievement.ARCHIVIST ->
                stats.chapters
            Achievement.WEEK_WARRIOR, Achievement.UNSTOPPABLE -> stats.bestStreak.toLong()
            Achievement.NIGHT_OWL -> stats.night
            Achievement.EARLY_BIRD -> stats.early
            Achievement.GENRE_HOPPER -> stats.genres.toLong()
            Achievement.COLLECTOR -> stats.series
            Achievement.MARATHONER -> stats.seconds / 3_600
            Achievement.CHAMPION -> stats.challengeWins.toLong()
            Achievement.DYNASTY -> stats.bestWinRun.toLong()
            Achievement.PERFECTIONIST -> stats.perfectDays.toLong()
            Achievement.TASTE_MAKER -> stats.suggestionsAdded.toLong()
        }
        return AchievementProgress(achievement, value)
    }
}

object ChallengeRules {

    /** Players with the highest score above 0; ties share the win. */
    fun winners(scores: Map<String, Double>): Set<String> {
        val best = scores.values.maxOrNull() ?: return emptySet()
        if (best <= 0.0) return emptySet()
        return scores.filterValues { it == best }.keys
    }

    /**
     * Longest run of consecutive wins for [userId] over [results], each the winners of one ended challenge, in the
     * order the challenges ended.
     */
    fun bestWinRun(results: List<Set<String>>, userId: String): Int {
        var best = 0
        var run = 0
        for (winners in results) {
            run = if (userId in winners) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }
}

enum class Lead { ME, RIVAL, TIED }

data class HeadToHeadRecord(val won: Int = 0, val lost: Int = 0, val tied: Int = 0)

object RivalryRules {

    /** The player right above [me] in [ranking], or right below when [me] is first; null when alone. */
    fun rivalOf(ranking: List<String>, me: String): String? {
        val index = ranking.indexOf(me)
        if (index < 0 || ranking.size < 2) return null
        return if (index == 0) ranking[1] else ranking[index - 1]
    }

    /** My share of today's chapters, 0.5 when neither read. */
    fun tugShare(mine: Long, theirs: Long): Float =
        if (mine + theirs == 0L) 0.5f else mine.toFloat() / (mine + theirs)

    fun lead(mine: DayScore, theirs: DayScore): Lead {
        val cmp = compareValuesBy(mine, theirs, { it.chapters }, { it.seconds })
        return when {
            cmp > 0 -> Lead.ME
            cmp < 0 -> Lead.RIVAL
            else -> Lead.TIED
        }
    }

    /**
     * Completed days (before [today]) won, lost and tied against the rival: chapters, then seconds. Days on which
     * neither read don't count; reads only count from joining, so days before either joined fall out by themselves.
     */
    fun record(daily: List<DayTotal>, me: String, rival: String, today: LocalDate): HeadToHeadRecord {
        var won = 0
        var lost = 0
        var tied = 0
        days(daily, me, rival).filterKeys { it < today }.values.forEach { (mine, theirs) ->
            if (mine.chapters == 0L && theirs.chapters == 0L) return@forEach
            when (lead(mine, theirs)) {
                Lead.ME -> won++
                Lead.RIVAL -> lost++
                Lead.TIED -> tied++
            }
        }
        return HeadToHeadRecord(won, lost, tied)
    }

    /**
     * How often the leader of the running week total (chapters, then seconds) changed from Monday through
     * [today]. A tie keeps the previous leader; the first player to lead isn't a change.
     */
    fun leadChanges(daily: List<DayTotal>, me: String, rival: String, weekStart: LocalDate, today: LocalDate): Int {
        val byDay = days(daily, me, rival)
        var mine = DayScore()
        var theirs = DayScore()
        var leader: Lead? = null
        var changes = 0
        var day = weekStart
        while (day <= today) {
            byDay[day]?.let { (a, b) ->
                mine += a
                theirs += b
            }
            val now = lead(mine, theirs)
            if (now != Lead.TIED) {
                if (leader != null && leader != now) changes++
                leader = now
            }
            day = day.plusDays(1)
        }
        return changes
    }

    /** Days in a row won against the rival, ending yesterday. */
    fun winStreak(daily: List<DayTotal>, me: String, rival: String, today: LocalDate): Int {
        val byDay = days(daily, me, rival)
        var count = 0
        var day = today.minusDays(1)
        while (true) {
            val (mine, theirs) = byDay[day] ?: return count
            if (lead(mine, theirs) != Lead.ME) return count
            count++
            day = day.minusDays(1)
        }
    }

    private fun days(daily: List<DayTotal>, me: String, rival: String): Map<LocalDate, Pair<DayScore, DayScore>> =
        daily.filter { it.userId == me || it.userId == rival }
            .groupBy { it.day }
            .mapValues { (_, rows) ->
                fun score(id: String) = rows.firstOrNull { it.userId == id }
                    ?.let { DayScore(it.chapters, it.seconds) } ?: DayScore()
                score(me) to score(rival)
            }
}

data class DayScore(val chapters: Long = 0, val seconds: Long = 0) {
    operator fun plus(other: DayScore) = DayScore(chapters + other.chapters, seconds + other.seconds)
}

/** Shared places on ties, competition style (1, 1, 3), for any score where higher is better. */
object Places {
    fun <T : Comparable<T>> of(scores: Map<String, T>): Map<String, Int> =
        scores.mapValues { (_, score) -> 1 + scores.values.count { it > score } }
}

/** Where read uploads continue from; v2 starts every player at 0, so nothing older than joining is uploaded. */
data class UploadCursor(
    /** Newest uploaded read time, epoch milliseconds; reads after it are uploaded next. */
    val uploadedUntil: Long,
    /** Whether the cursor was already moved for the Arena (v2). */
    val arenaStarted: Boolean,
) {
    /** On launch: an install that synced before v2 moves its cursor to [now] once instead of backfilling. */
    fun onLaunch(registered: Boolean, now: Long): UploadCursor = when {
        arenaStarted -> this
        registered -> UploadCursor(maxOf(uploadedUntil, now), arenaStarted = true)
        else -> copy(arenaStarted = true)
    }

    companion object {
        /** A new player only uploads what they read from registering on. */
        fun onRegistered(now: Long) = UploadCursor(now, arenaStarted = true)
    }
}
