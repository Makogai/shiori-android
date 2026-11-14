package mihon.feature.friends.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** A row of RPC `arena_races` (README v6): one player's latest read in a series at least two players read. */
@Serializable
data class RaceRow(
    @SerialName("series_key") val seriesKey: String,
    @SerialName("user_id") val userId: String,
    @SerialName("series_title") val seriesTitle: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    val chapters: Long = 0,
) {
    val lastRead: Instant? get() = lastReadAt?.let(::parseInstant)
}

enum class RaceLeader { ME, THEM, TIED }

/** Me against one other player in a series we both read. */
data class Race(val mine: RaceRow, val theirs: RaceRow) {
    val seriesKey: String get() = mine.seriesKey
    val title: String get() = mine.seriesTitle?.takeIf { it.isNotBlank() } ?: theirs.seriesTitle.orEmpty()
    val coverUrl: String? get() = mine.coverUrl ?: theirs.coverUrl
    val rivalId: String get() = theirs.userId
    val myNumber: Double get() = mine.chapterNumber ?: 0.0
    val theirNumber: Double get() = theirs.chapterNumber ?: 0.0
    val gap: Double get() = abs(myNumber - theirNumber)
    val leader: RaceLeader
        get() = when {
            myNumber > theirNumber -> RaceLeader.ME
            myNumber < theirNumber -> RaceLeader.THEM
            else -> RaceLeader.TIED
        }

    /** The most recent read of either player. */
    val lastRead: Instant
        get() = listOfNotNull(mine.lastRead, theirs.lastRead).maxOrNull() ?: Instant.EPOCH
}

object Races {

    const val MAX_CARDS = 5

    /**
     * Every series both I and another player have rows for, both with a chapter number ≥ 0; one race per other
     * player. Most recent read of either player first.
     */
    fun build(rows: List<RaceRow>, myId: String): List<Race> {
        fun valid(row: RaceRow) = row.chapterNumber?.let { it >= 0 && it.isFinite() } == true
        return rows.groupBy { it.seriesKey }.values.flatMap { series ->
            val mine = series.firstOrNull { it.userId == myId && valid(it) } ?: return@flatMap emptyList()
            series.filter { it.userId != myId && valid(it) }
                .distinctBy { it.userId }
                .map { Race(mine, it) }
        }.sortedWith(compareByDescending<Race> { it.lastRead }.thenBy { it.title.lowercase() }.thenBy { it.rivalId })
    }

    /** Integer when whole, else one decimal. */
    fun formatGap(gap: Double): String =
        if (gap % 1.0 == 0.0) gap.toLong().toString() else String.format(Locale.ROOT, "%.1f", gap)

    /** "🏁 You're 3 chapters ahead in …" / "🏁 Filip is 1 chapter ahead in …" / "🏁 Neck and neck in …". */
    fun headline(race: Race, rivalName: String): String {
        val gap = formatGap(race.gap)
        val unit = if (race.gap == 1.0) "chapter" else "chapters"
        return when (race.leader) {
            RaceLeader.ME -> "🏁 You're $gap $unit ahead in ${race.title}"
            RaceLeader.THEM -> "🏁 $rivalName is $gap $unit ahead in ${race.title}"
            RaceLeader.TIED -> "🏁 Neck and neck in ${race.title} at Ch. ${chapterNumber(race.myNumber)}"
        }
    }

    /** "You +3" / "Filip +3" / "Tied". */
    fun badge(race: Race, rivalName: String): String = when (race.leader) {
        RaceLeader.ME -> "You +${formatGap(race.gap)}"
        RaceLeader.THEM -> "$rivalName +${formatGap(race.gap)}"
        RaceLeader.TIED -> "Tied"
    }
}
