package mihon.feature.friends.domain

import kotlinx.serialization.Serializable
import java.util.Locale

enum class OvertakeMetric { TODAY, CHALLENGE, SEASON }

/**
 * One scoreboard the overtake alerts watch (README v6, "Overtake alerts"): today's chapters (key = the Arena day),
 * an active challenge's scores (key = its id) or season XP (key = the season number).
 */
data class OvertakeBoard(
    val metric: OvertakeMetric,
    val periodKey: String,
    val scores: Map<String, Double>,
    /** Challenge title, or "Season N". */
    val label: String = "",
    /** The challenge's emoji. */
    val emoji: String = "",
)

data class OvertakeEvent(
    val metric: OvertakeMetric,
    val periodKey: String,
    val userId: String,
    val theirs: Double,
    val mine: Double,
    val label: String,
    val emoji: String,
) {
    /** Unique per event key, so the notification can use it as an id. */
    val key: String get() = Overtakes.key(metric, periodKey, userId)
}

/**
 * What the last check saw: which `(metric, period, player)` keys had that player strictly ahead of me. Keys that
 * are missing count as "not ahead", which is also how a new day, challenge or season starts.
 */
@Serializable
data class OvertakeState(val initialized: Boolean = false, val ahead: Set<String> = emptySet())

object Overtakes {

    fun key(metric: OvertakeMetric, periodKey: String, userId: String): String =
        "${metric.name.lowercase()}:$periodKey:$userId"

    /**
     * Compares [boards] with [state]. An event is "ahead" going from false to true; the very first check only
     * records. The new state keeps only the keys of the boards given (current day, active challenges, season).
     */
    fun check(
        state: OvertakeState,
        boards: List<OvertakeBoard>,
        myId: String,
    ): Pair<OvertakeState, List<OvertakeEvent>> {
        val ahead = LinkedHashSet<String>()
        val events = mutableListOf<OvertakeEvent>()
        boards.forEach { board ->
            val mine = board.scores[myId] ?: 0.0
            board.scores.toSortedMap().forEach { (userId, theirs) ->
                if (userId == myId || theirs <= mine) return@forEach
                val key = key(board.metric, board.periodKey, userId)
                ahead += key
                if (state.initialized && key !in state.ahead) {
                    events +=
                        OvertakeEvent(board.metric, board.periodKey, userId, theirs, mine, board.label, board.emoji)
                }
            }
        }
        return OvertakeState(initialized = true, ahead = ahead) to events
    }

    /** The notification text; the title is always "Reading Arena". */
    fun body(event: OvertakeEvent, name: String): String {
        val theirs = formatScore(event.theirs)
        val mine = formatScore(event.mine)
        return when (event.metric) {
            OvertakeMetric.TODAY -> "$name just passed you today — $theirs vs $mine chapters 📚"
            OvertakeMetric.CHALLENGE -> "$name just passed you in ${event.label} — $theirs vs $mine ${event.emoji}"
            OvertakeMetric.SEASON -> "$name just passed you in ${event.label} — $theirs vs $mine XP 👑"
        }.replace("  ", " ").trim()
    }

    /** One notification per check: the first message, plus " (+2 more)" when there are several. */
    fun summary(bodies: List<String>): String? = when (bodies.size) {
        0 -> null
        1 -> bodies[0]
        else -> "${bodies[0]} (+${bodies.size - 1} more)"
    }

    /** Integers when whole, else one decimal (`34.5`). */
    fun formatScore(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ROOT, "%.1f", value)

    fun seasonLabel(season: Int): String = "Season $season"
}
