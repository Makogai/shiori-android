package mihon.feature.friends.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.feature.friends.domain.ReadKey
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUser,
)

@Serializable
data class AuthUser(val id: String)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val app: String? = null,
    val title: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class League(
    val id: String,
    val name: String,
    @SerialName("invite_code") val inviteCode: String,
    val timezone: String = "UTC",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val createdInstant: Instant? get() = createdAt?.let(::parseTimestamp)
}

@Serializable
data class BoardRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val title: String? = null,
    val app: String? = null,
    val chapters: Long = 0,
    val seconds: Long = 0,
    val series: Long = 0,
    val genres: Map<String, Long> = emptyMap(),
    @SerialName("top_series") val topSeries: List<TopSeries> = emptyList(),
)

@Serializable
data class TopSeries(
    val key: String,
    val title: String? = null,
    val cover: String? = null,
    val chapters: Long = 0,
    val seconds: Long = 0,
)

@Serializable
data class DailyRow(
    @SerialName("user_id") val userId: String,
    val day: String,
    val chapters: Long = 0,
    val seconds: Long = 0,
) {
    val date: LocalDate get() = LocalDate.parse(day.take(10))
}

@Serializable
data class HourRow(
    @SerialName("user_id") val userId: String,
    val hour: Int,
    val chapters: Long = 0,
)

@Serializable
data class FeedRow(
    @SerialName("user_id") val userId: String,
    @SerialName("read_at") val readAt: String,
    @SerialName("series_title") val seriesTitle: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("series_key") val seriesKey: String? = null,
    /** Only `player_feed` (v5) returns it. */
    @SerialName("duration_s") val durationS: Int? = null,
    /** v6: identifies the read with [userId] for reactions and comments; absent before 006_social.sql. */
    @SerialName("client_id") val clientId: String? = null,
) {
    val readInstant: Instant? get() = parseTimestamp(readAt)

    val readKey: ReadKey? get() = clientId?.takeIf { it.isNotBlank() }?.let { ReadKey(userId, it) }
}

@Serializable
data class Challenge(
    val id: Int,
    val title: String,
    val emoji: String,
    val description: String,
    val metric: String,
    val genre: String? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
) {
    val start: Instant get() = parseTimestamp(startsAt) ?: Instant.EPOCH
    val end: Instant get() = parseTimestamp(endsAt) ?: Instant.EPOCH

    fun isActive(now: Instant): Boolean = start <= now && now < end
    fun hasEnded(now: Instant): Boolean = end <= now
}

@Serializable
data class ChallengeScore(
    @SerialName("user_id") val userId: String,
    val score: Double = 0.0,
)

/** Per-player numbers for one Arena day that the daily quests need beyond `league_daily`. */
@Serializable
data class QuestFacts(
    val series: Long = 0,
    val early: Long = 0,
    val late: Long = 0,
    @SerialName("new_series") val newSeries: Boolean = false,
)

@Serializable
data class ProfileUpsert(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    val app: String,
    val title: String?,
    @SerialName("updated_at") val updatedAt: String,
)

// Postgres returns offsets like "+00:00", which older java.time's Instant.parse rejects
fun parseTimestamp(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
