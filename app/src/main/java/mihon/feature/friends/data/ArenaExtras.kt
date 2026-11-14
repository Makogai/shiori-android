package mihon.feature.friends.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One of my reads as the server has it (README v5 reconciliation). */
@Serializable
data class ServerRead(
    @SerialName("client_id") val clientId: String,
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class NamedCount(val name: String, val count: Int)

/**
 * The `profile_stats.stats` JSON (README v5): the app's own library statistics. Every key is always written
 * (`mean_score` as null when nothing is scored); missing keys read as their defaults.
 */
@Serializable
data class LibraryStats(
    @SerialName("library_count") val libraryCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("started_count") val startedCount: Int = 0,
    @SerialName("local_count") val localCount: Int = 0,
    @SerialName("chapters_total") val chaptersTotal: Int = 0,
    @SerialName("chapters_read") val chaptersRead: Int = 0,
    @SerialName("chapters_downloaded") val chaptersDownloaded: Int = 0,
    @SerialName("read_seconds") val readSeconds: Long = 0,
    @SerialName("tracked_count") val trackedCount: Int = 0,
    /** 0–10, one decimal; null when nothing is scored. */
    @SerialName("mean_score") val meanScore: Double? = null,
    @SerialName("trackers_used") val trackersUsed: Int = 0,
    @SerialName("top_genres") val topGenres: List<NamedCount> = emptyList(),
    @SerialName("top_sources") val topSources: List<NamedCount> = emptyList(),
    val app: String = "",
    @SerialName("app_version") val appVersion: String = "",
    /** The device's IANA zone (README v9: "today" is each player's own local day). */
    val timezone: String = "",
)

@Serializable
data class ProfileStatsRow(
    @SerialName("user_id") val userId: String,
    val stats: LibraryStats = LibraryStats(),
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val updatedInstant: Instant? get() = updatedAt?.let(::parseTimestamp)
}

@Serializable
data class ProfileStatsUpsert(
    @SerialName("user_id") val userId: String,
    val stats: LibraryStats,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class MemberJoined(@SerialName("joined_at") val joinedAt: String)

/** One library series as the stats need it. */
data class LibraryStatsEntry(
    /** Finished publishing with nothing left unread (as the Statistics screen counts it). */
    val completed: Boolean,
    val started: Boolean,
    val local: Boolean,
    val totalChapters: Long,
    val readChapters: Long,
    val genres: List<String>,
    val sourceName: String,
    val tracked: Boolean,
)

object LibraryStatsRules {

    const val TOP = 5

    /** Publish right after an upload ([force]), else at most every 30 minutes. */
    const val THROTTLE_MS = 30 * 60 * 1000L

    fun due(lastMillis: Long?, nowMillis: Long, force: Boolean): Boolean =
        force || lastMillis == null || nowMillis - lastMillis >= THROTTLE_MS || nowMillis < lastMillis

    fun build(
        entries: List<LibraryStatsEntry>,
        downloadedChapters: Int,
        readMillis: Long,
        meanScore: Double?,
        trackersUsed: Int,
        app: String,
        appVersion: String,
    ): LibraryStats {
        val genres = HashMap<String, Int>()
        val sources = HashMap<String, Int>()
        entries.forEach { entry ->
            entry.genres.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                .forEach { genres[it] = (genres[it] ?: 0) + 1 }
            sources[entry.sourceName] = (sources[entry.sourceName] ?: 0) + 1
        }
        return LibraryStats(
            libraryCount = entries.size,
            completedCount = entries.count { it.completed },
            startedCount = entries.count { it.started },
            localCount = entries.count { it.local },
            chaptersTotal = entries.sumOf { it.totalChapters }.toInt(),
            chaptersRead = entries.sumOf { it.readChapters }.toInt(),
            chaptersDownloaded = downloadedChapters,
            readSeconds = readMillis.coerceAtLeast(0) / 1000,
            trackedCount = entries.count { it.tracked },
            meanScore = meanScore?.takeIf { it.isFinite() }?.let { Math.round(it * 10) / 10.0 },
            trackersUsed = trackersUsed,
            topGenres = top(genres),
            topSources = top(sources),
            app = app,
            appVersion = appVersion,
        )
    }

    /** Largest counts first, ties by name, at most five. */
    fun top(counts: Map<String, Int>): List<NamedCount> = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(TOP)
        .map { NamedCount(it.key, it.value) }
}

/** One day of a player's activity in the Arena time zone. */
data class ActivityDay(val date: LocalDate, val rows: List<FeedRow>) {
    val chapters: Int get() = rows.size
    val seconds: Long get() = rows.sumOf { (it.durationS ?: 0).toLong() }
}

object ActivityDays {

    const val PAGE_SIZE = 100

    /** Newest day first, rows newest first. */
    fun group(rows: List<FeedRow>, zone: ZoneId): List<ActivityDay> = rows
        .mapNotNull { row -> row.readInstant?.let { row to it } }
        .sortedByDescending { it.second }
        .groupBy({ it.second.atZone(zone).toLocalDate() }, { it.first })
        .map { (date, dayRows) -> ActivityDay(date, dayRows) }

    /** "48 min", "1 h 12 min", "0 min". */
    fun minutes(seconds: Long): String {
        val minutes = seconds.coerceAtLeast(0) / 60
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** Older rows appended, duplicates dropped, newest first. */
    fun merge(rows: List<FeedRow>, page: List<FeedRow>): List<FeedRow> =
        (rows + page).distinctBy { listOf(it.readAt, it.seriesTitle, it.chapterName, it.chapterNumber) }
            .sortedByDescending { it.readInstant }
}

/** The v5 requests of the shared contract. */
class ArenaExtrasRequests(private val json: Json) {

    fun ownReads(me: String, offset: Int) = RestCall(
        method = "GET",
        path = "/rest/v1/reads?select=client_id,read_at&user_id=eq.$me&order=client_id" +
            "&limit=$READS_PAGE&offset=$offset",
    )

    fun deleteReads(me: String, clientIds: List<String>): List<RestCall> = clientIds.chunked(DELETE_CHUNK).map { ids ->
        RestCall(method = "DELETE", path = "/rest/v1/reads?user_id=eq.$me&client_id=in.(${ids.joinToString(",")})")
    }

    fun joinedAt(me: String, leagueId: String) = RestCall(
        method = "GET",
        path = "/rest/v1/league_members?league_id=eq.$leagueId&user_id=eq.$me&select=joined_at",
    )

    fun saveStats(me: String, stats: LibraryStats, now: Instant) = RestCall(
        method = "POST",
        path = "/rest/v1/profile_stats",
        body = json.encodeToString(
            ProfileStatsUpsert.serializer(),
            ProfileStatsUpsert(me, stats, DateTimeFormatter.ISO_INSTANT.format(now)),
        ),
        prefer = "resolution=merge-duplicates,return=minimal",
    )

    fun stats(users: List<String>) = RestCall(
        method = "GET",
        path = "/rest/v1/profile_stats?select=user_id,stats,updated_at&user_id=in.(${users.joinToString(",")})",
    )

    fun playerFeed(user: String, before: Instant?, limit: Int) = RestCall(
        method = "POST",
        path = "/rest/v1/rpc/player_feed",
        body = buildJsonObject {
            put("p_user", user)
            if (before ==
                null
            ) {
                put("p_before", JsonNull)
            } else {
                put("p_before", DateTimeFormatter.ISO_INSTANT.format(before))
            }
            put("p_limit", limit)
        }.toString(),
    )

    companion object {
        const val READS_PAGE = 1000
        const val DELETE_CHUNK = 100
    }
}
