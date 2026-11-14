package mihon.feature.friends.domain

import mihon.feature.friends.data.BoardRow
import java.time.ZoneId

/** One player's chapters on their own local day (README v9, "Today"). */
data class TodayEntry(
    val userId: String,
    val name: String,
    val chapters: Long,
    val seconds: Long,
    /** Competition style: one more than the number of players strictly ahead. */
    val place: Int,
    val zone: ZoneId,
) {
    /** 1–3 for players with at least one chapter, else null (no medal). */
    val medal: Int? get() = place.takeIf { chapters > 0 && it <= 3 }
}

/** The "Today" rules both apps share: each player's zone, one board per zone, the ranked row. */
object LocalToday {

    /** A player's published zone, else the Arena's. */
    fun zone(timezone: String?, fallback: ZoneId): ZoneId =
        timezone?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback

    /**
     * Each player's score from the board of their own zone (keyed by zone id), ranked by chapters,
     * then seconds, then name; equal chapters and seconds share a place.
     */
    fun entries(
        names: Map<String, String>,
        zones: Map<String, ZoneId>,
        boards: Map<String, List<BoardRow>>,
    ): List<TodayEntry> {
        val scores = zones.mapValues { (id, zone) ->
            boards[zone.id]?.firstOrNull { it.userId == id }.let { (it?.chapters ?: 0L) to (it?.seconds ?: 0L) }
        }
        return scores.map { (id, score) ->
            val ahead = scores.values.count { other ->
                other.first > score.first || (other.first == score.first && other.second > score.second)
            }
            TodayEntry(id, names[id].orEmpty(), score.first, score.second, ahead + 1, zones.getValue(id))
        }
            .sortedWith(
                compareByDescending<TodayEntry> { it.chapters }
                    .thenByDescending { it.seconds }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }
}
