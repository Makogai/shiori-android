package mihon.feature.friends.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

object LeagueHashing {

    fun seriesKey(sourceId: Long, mangaUrl: String): String = sha256Prefix("$sourceId|$mangaUrl")

    fun clientId(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
        sha256Prefix("$sourceId|$mangaUrl|$chapterUrl")

    private fun sha256Prefix(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            hex.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return hex.substring(0, 32)
    }

    private const val HEX = "0123456789abcdef"
}

/** A chapter the user has read, as stored in the local database. */
data class LocalRead(
    val sourceId: Long,
    val sourceName: String?,
    val mangaUrl: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val genres: List<String>?,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Double,
    val readAt: Instant,
    val readDurationMs: Long,
    /** The chapter is marked read (finished). */
    val read: Boolean = true,
)

@Serializable
data class ReadRow(
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("read_at") val readAt: String,
    @SerialName("series_key") val seriesKey: String,
    @SerialName("series_title") val seriesTitle: String,
    @SerialName("cover_url") val coverUrl: String?,
    @SerialName("source_name") val sourceName: String?,
    @SerialName("chapter_name") val chapterName: String?,
    @SerialName("chapter_number") val chapterNumber: Double?,
    val genres: List<String>,
    @SerialName("duration_s") val durationS: Int,
)

object ReadRows {

    const val BATCH_SIZE = 500
    const val MAX_GENRES = 12
    const val MAX_DURATION_S = 86_400

    /** Reads shorter than this (opening a chapter, scrolling a page) don't count. */
    const val MIN_READ_MS = 15_000L

    /**
     * Whether a history entry counts as a read (README v5): the chapter is marked read (finished), it has a history
     * time, and it was read for at least 15 seconds. Chapters only marked read, or only opened, are never uploaded.
     */
    fun qualifies(read: LocalRead): Boolean =
        read.read && read.readAt.toEpochMilli() > 0 && read.readDurationMs >= MIN_READ_MS

    /** Rows for the reads that qualify. */
    fun buildAll(reads: List<LocalRead>, userId: String): List<ReadRow> =
        reads.filter(::qualifies).map { build(it, userId) }

    fun clientId(read: LocalRead): String = LeagueHashing.clientId(read.sourceId, read.mangaUrl, read.chapterUrl)

    fun build(read: LocalRead, userId: String): ReadRow {
        return ReadRow(
            userId = userId,
            clientId = LeagueHashing.clientId(read.sourceId, read.mangaUrl, read.chapterUrl),
            readAt = DateTimeFormatter.ISO_INSTANT.format(read.readAt),
            seriesKey = LeagueHashing.seriesKey(read.sourceId, read.mangaUrl),
            seriesTitle = read.mangaTitle,
            coverUrl = read.coverUrl?.takeIf { isHttpUrl(it) },
            sourceName = read.sourceName,
            chapterName = read.chapterName,
            chapterNumber = read.chapterNumber.takeIf { it >= 0 && it.isFinite() },
            genres = read.genres.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_GENRES),
            durationS = (read.readDurationMs / 1000).coerceIn(0, MAX_DURATION_S.toLong()).toInt(),
        )
    }

    fun batches(rows: List<ReadRow>): List<List<ReadRow>> = rows.chunked(BATCH_SIZE)

    private fun isHttpUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
