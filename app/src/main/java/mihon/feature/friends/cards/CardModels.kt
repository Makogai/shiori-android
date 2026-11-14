package mihon.feature.friends.cards

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.feature.friends.data.parseTimestamp
import java.time.Instant
import java.time.ZoneId

/**
 * A card's rarity (cards/CARD_SPEC.md §2), lowest first. [code] is what the server stores and grades by (unchanged);
 * players see [rank] (Shiori Cards v1.2): C→E, U→D, R→C, E→B, L→A, M→S, S→SR. [rankHex] is the rank's edge colour
 * (BRIEF §6). The order (and so every sort) stays E lowest … SR highest.
 */
enum class CardGrade(val code: String, val rank: String, val rankHex: String) {
    COMMON("C", "E", "#AAB0B8"),
    UNCOMMON("U", "D", "#55B99D"),
    RARE("R", "C", "#538DCB"),
    EPIC("E", "B", "#A968D8"),
    LEGENDARY("L", "A", "#D6B774"),
    MYTHIC("M", "S", "#CDDCE7"),
    SECRET("S", "SR", "#F0E8DE"),
    ;

    /** "Rank B". */
    val label: String get() = "Rank $rank"

    /** The rank colour as an ARGB int. */
    val rankColor: Int get() = CardColor.argb(rankHex, -1)

    /** Pulls of this grade or better show in the Arena feed. */
    val isFeedWorthy: Boolean get() = this >= RARE

    /** Confetti on the reveal. */
    val celebrates: Boolean get() = this >= LEGENDARY

    companion object {
        fun of(code: String?): CardGrade = entries.firstOrNull { it.code == code } ?: COMMON

        /** The grade a display rank stands for ("SR" → [SECRET]); null for anything else. */
        fun ofRank(rank: String?): CardGrade? = entries.firstOrNull { it.rank.equals(rank?.trim(), ignoreCase = true) }
    }
}

/** The 13 themes (§3). */
enum class CardTheme(val code: String, val title: String) {
    ACTION("action", "Action"),
    ROMANCE("romance", "Romance"),
    FANTASY("fantasy", "Fantasy"),
    HORROR("horror", "Horror"),
    COMEDY("comedy", "Comedy"),
    SCIFI("scifi", "Sci-fi"),
    SPORTS("sports", "Sports"),
    SLICE_OF_LIFE("slice_of_life", "Slice of life"),
    DRAMA("drama", "Drama"),
    MYSTERY("mystery", "Mystery"),
    MARTIAL_ARTS("martial_arts", "Martial arts"),
    ISEKAI("isekai", "Isekai"),
    NEUTRAL("neutral", "Neutral"),
    ;

    companion object {
        fun of(code: String?): CardTheme = entries.firstOrNull { it.code == code } ?: NEUTRAL
    }
}

/** The custom frame's progress (CARD_SPEC §12); the server only ever moves pending → ready | failed. */
enum class CardFrameStatus(val code: String) {
    NONE("none"),
    PENDING("pending"),
    READY("ready"),
    FAILED("failed"),
    ;

    companion object {
        fun of(code: String?): CardFrameStatus = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * What a ready custom frame image is (CARD_SPEC §12): a border around the art (the app still draws the name, series
 * line and footer) or a full card edit (R/E/L/M/S: the AI drew the whole card, scene, gem, name and flavor; the app
 * draws nothing over it). Missing or unknown reads as border. L/M/S edits may also come animated (`frame_anim`).
 */
enum class CardFrameStyle(val code: String) {
    BORDER("border"),
    EDIT("edit"),
    ;

    companion object {
        fun of(code: String?): CardFrameStyle = entries.firstOrNull { it.code == code } ?: BORDER
    }
}

/** POWER · AURA · DRAMA, 0–99 each. */
@Serializable
data class CardStats(val power: Int = 0, val aura: Int = 0, val drama: Int = 0) {
    val total: Int get() = power + aura + drama
}

/** The judge's scores (0–10) plus what the server noted while deciding. */
@Serializable
data class CardScores(
    val composition: Double? = null,
    val emotion: Double? = null,
    val action: Double? = null,
    val iconic: Double? = null,
    @SerialName("art_quality") val artQuality: Double? = null,
    val quality: Double? = null,
    @SerialName("suggested_grade") val suggestedGrade: String? = null,
    @SerialName("base_grade") val baseGrade: String? = null,
    val luck: String? = null,
)

/** One row of `cards` (backend README v7). */
@Serializable
data class ShioriCard(
    val id: String,
    val owner: String,
    val serial: Long = 0,
    @SerialName("grade") val gradeCode: String = "C",
    @SerialName("theme") val themeCode: String = "neutral",
    val effects: List<String> = emptyList(),
    @SerialName("card_name") val cardName: String = "",
    val flavor: String = "",
    val stats: CardStats = CardStats(),
    @SerialName("accent_hex") val accentHex: String? = null,
    val scores: CardScores? = null,
    @SerialName("series_key") val seriesKey: String? = null,
    @SerialName("series_title") val seriesTitle: String = "",
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    val genres: List<String>? = null,
    @SerialName("art_path") val artPath: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val season: Int = 1,
    val favorite: Boolean = false,
    @SerialName("frame_status") val frameStatusCode: String? = null,
    @SerialName("frame_path") val framePath: String? = null,
    /** When the frame became ready (server clock; 009_card_renders.sql). */
    @SerialName("frame_ready_at") val frameReadyAt: String? = null,
    /** The owner's rendered card in `card-renders` ("<owner>/<card id>.png"), for Discord. */
    @SerialName("render_path") val renderPath: String? = null,
    /** When the render was last uploaded (server clock). */
    @SerialName("render_updated_at") val renderUpdatedAt: String? = null,
    /** "border" or "edit": what the ready frame image holds; null or unknown is a border. */
    @SerialName("frame_style") val frameStyleCode: String? = null,
    /** An animated card edit (L/M/S): its GIF in `card-frames`, else null. */
    @SerialName("frame_anim") val frameAnimPath: String? = null,
) {
    val grade: CardGrade get() = CardGrade.of(gradeCode)

    val theme: CardTheme get() = CardTheme.of(themeCode)

    val frameStatus: CardFrameStatus get() = CardFrameStatus.of(frameStatusCode)

    val createdInstant: Instant get() = parseTimestamp(createdAt) ?: Instant.EPOCH

    /** The custom frame to draw instead of the pack's. */
    val customFramePath: String? get() = framePath?.takeIf { frameStatus == CardFrameStatus.READY }

    val frameStyle: CardFrameStyle get() = CardFrameStyle.of(frameStyleCode)

    /** The card shows as an AI card edit: its ready frame image (or GIF) is the whole card. */
    val isCardEdit: Boolean get() = frameStyle == CardFrameStyle.EDIT && frameImagePath != null

    /**
     * What still renders draw as the frame: `frame_path` (a PNG, or for an animated card the GIF, whose frame 0 is
     * decoded), else an edit's `frame_anim`.
     */
    val frameImagePath: String? get() = customFramePath ?: frameAnimationPath

    /**
     * The animated card edit's GIF in `card-frames` (a "card-frames/" prefix dropped), when the card is a ready edit:
     * `frame_anim`, else a `frame_path` that is a GIF (L/M/S store one object for both). Otherwise the still shows.
     */
    val frameAnimationPath: String?
        get() {
            if (frameStyle != CardFrameStyle.EDIT || frameStatus != CardFrameStatus.READY) return null
            val gif = framePath?.takeIf { it.lowercase().endsWith(".gif") }
            val path = frameAnimPath?.takeIf { it.isNotEmpty() } ?: gif ?: return null
            return path.removePrefix(CardRequests.FRAME_BUCKET + "/")
        }

    /** "No. 0042" (more digits as they are; see [CardSpec.cardIdText]). */
    val serialText: String get() = CardSpec.cardIdText(serial) ?: ""

    /** "Ch. 41" from the number, else the chapter's name. */
    val chapterText: String
        get() {
            val number = chapterNumber
            if (number != null && number >= 0) return "Ch. " + formatChapterNumber(number)
            return chapterName.orEmpty()
        }

    /** "Series title · Ch. 41". */
    val seriesLine: String get() = chapterText.let { if (it.isEmpty()) seriesTitle else "$seriesTitle · $it" }

    /** "0042" (at least four digits, never cut), "" without a serial. */
    val serialDigits: String get() = CardSpec.serialDigits(serial).orEmpty()

    /** "S1·2026": the season and the pull's year in the Arena's zone. */
    fun seasonText(zone: ZoneId): String = "S$season·${createdInstant.atZone(zone).year}"
}

fun formatChapterNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

/** What the reader knows about the series when a card is made. */
data class CardSeriesContext(
    val seriesTitle: String,
    val sourceName: String,
    val chapterName: String,
    val chapterNumber: Double?,
    val genres: List<String>,
    val seriesKey: String,
) {
    companion object {
        const val MAX_GENRES = 12

        /** The manga's genres, split on commas, trimmed, at most [MAX_GENRES]. */
        fun genres(raw: List<String>?): List<String> = raw.orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_GENRES)
    }
}

/**
 * The body of `POST /functions/v1/pull-card`; `chapter_number` is sent as null rather than left out, `card_name`
 * (the player's own name for the card, else the AI names it) only when set.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CardPullRequest(
    @SerialName("art_path") val artPath: String,
    @SerialName("series_title") val seriesTitle: String,
    @SerialName("source_name") val sourceName: String,
    @SerialName("chapter_name") val chapterName: String,
    @SerialName("chapter_number") val chapterNumber: Double?,
    val genres: List<String>,
    @SerialName("series_key") val seriesKey: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("card_name")
    val cardName: String? = null,
) {
    constructor(artPath: String, context: CardSeriesContext, cardName: String? = null) : this(
        artPath = artPath,
        seriesTitle = context.seriesTitle,
        sourceName = context.sourceName,
        chapterName = context.chapterName,
        chapterNumber = context.chapterNumber,
        genres = context.genres,
        seriesKey = context.seriesKey,
        cardName = CardRules.normalizedName(cardName),
    )
}

/** The function's 200 answer. */
@Serializable
data class CardPullResponse(
    val valid: Boolean = false,
    val reason: String? = null,
    val card: ShioriCard? = null,
    @SerialName("pulls_today") val pullsToday: Int? = null,
    @SerialName("daily_limit") val dailyLimit: Int? = null,
    /** "background" (the server makes the frame), "client" (call frame-card once) or "none". */
    @SerialName("frame_job") val frameJob: String? = null,
)

sealed interface CardPullResult {
    /** [startFrame]: the app has to call frame-card once. */
    data class Pulled(val card: ShioriCard, val pullsToday: Int, val startFrame: Boolean) : CardPullResult

    /** Not a panel; not counted. */
    data class Invalid(val reason: String, val pullsToday: Int) : CardPullResult
}

sealed class CardPullError(message: String) : Exception(message) {
    class DailyLimit(val used: Int, val limit: Int) : CardPullError("Come back tomorrow ($used/$limit today)")
    class JudgeUnavailable(message: String) : CardPullError(message)
    class Other(message: String) : CardPullError(message)
}

object CardRules {
    const val DAILY_LIMIT = 10
    const val ART_WIDTH = 1428
    const val ART_HEIGHT = 2028
    const val JPEG_QUALITY = 85
    const val NAME_LIMIT = 28

    /** The name field shows its counter from this many characters on. */
    const val NAME_COUNTER_FROM = NAME_LIMIT - 8

    /** A typed card name: trimmed and cut to [NAME_LIMIT]; null when nothing is left. */
    fun normalizedName(name: String?): String? = name?.trim()?.take(NAME_LIMIT)?.trim()?.takeIf { it.isNotEmpty() }

    /** What the name field keeps while typing: one line, at most [NAME_LIMIT] characters. */
    fun limitedNameInput(text: String): String = text.replace('\n', ' ').take(NAME_LIMIT)

    /** Polling a pending custom frame. */
    const val POLL_INTERVAL_MS = 5_000L

    // Naming (up to 90 s) runs before the frame, so allow 5 minutes like iOS
    const val POLL_LIMIT_MS = 300_000L

    /** A card still pending after this long is shown with the pack frame. */
    const val STALE_PENDING_MS = 5 * 60_000L

    /** The rendered card uploaded for Discord: 1080 × 1512, drawn smaller when the PNG would be too big. */
    val RENDER_WIDTHS = listOf(1080, 918, 756)
    const val RENDER_MAX_BYTES = 2_500_000
}

object CardColor {
    /** "#RRGGBB" or "#RRGGBBAA" as an ARGB int, null when malformed. */
    fun argb(hex: String?): Int? {
        val text = hex?.trim()?.removePrefix("#") ?: return null
        if (text.length != 6 && text.length != 8) return null
        val value = text.toLongOrNull(16) ?: return null
        return if (text.length == 6) {
            (0xFF000000 or value).toInt()
        } else {
            (((value and 0xFF) shl 24) or (value ushr 8)).toInt()
        }
    }

    fun argb(hex: String?, fallback: Int): Int = argb(hex) ?: fallback
}
