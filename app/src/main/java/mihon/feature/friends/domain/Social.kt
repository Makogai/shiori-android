package mihon.feature.friends.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A read in the Arena: `client_id` alone isn't unique (two players reading the same chapter share it). */
data class ReadKey(val ownerId: String, val clientId: String)

/** The read a reaction or comment is on, embedded by the "new on my reads" requests. */
@Serializable
data class ReadRef(
    @SerialName("series_title") val seriesTitle: String? = null,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
)

/** A row of `feed_reactions` (README v6). */
@Serializable
data class FeedReaction(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("reactor_id") val reactorId: String,
    val emoji: String,
    @SerialName("created_at") val createdAt: String? = null,
    val reads: ReadRef? = null,
) {
    val read: ReadKey get() = ReadKey(ownerId, clientId)
    val created: Instant? get() = createdAt?.let(::parseInstant)
}

/** A row of `feed_comments` (README v6). */
@Serializable
data class FeedComment(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
    val reads: ReadRef? = null,
) {
    val read: ReadKey get() = ReadKey(ownerId, clientId)
    val created: Instant? get() = createdAt?.let(::parseInstant)
}

@Serializable
data class ReactionUpsert(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("reactor_id") val reactorId: String,
    val emoji: String,
)

@Serializable
data class CommentInsert(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
)

/** Reactions and comments of one read. */
data class ReadSocial(
    val reactions: List<FeedReaction> = emptyList(),
    /** Oldest first. */
    val comments: List<FeedComment> = emptyList(),
)

data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean, val enabled: Boolean)

object Reactions {

    val EMOJI: List<String> = listOf("🔥", "😂", "😭", "😍", "👀")

    const val MAX_COMMENT = 200

    /**
     * The chip row under a read: on my own reads only the emoji with a count, not tappable; on others' reads all
     * five, mine highlighted.
     */
    fun chips(reactions: List<FeedReaction>, ownerId: String, myId: String): List<ReactionChip> {
        val counts = reactions.groupingBy { it.emoji }.eachCount()
        if (ownerId == myId) {
            return EMOJI.filter { (counts[it] ?: 0) > 0 }.map { ReactionChip(it, counts.getValue(it), false, false) }
        }
        val mine = reactions.firstOrNull { it.reactorId == myId }?.emoji
        return EMOJI.map { ReactionChip(it, counts[it] ?: 0, it == mine, true) }
    }

    /** Tapping my current emoji removes it (null), any other sets it. */
    fun toggle(current: String?, tapped: String): String? = if (current == tapped) null else tapped

    /** My reaction replaced by [emoji] (or removed when null). */
    fun withMine(
        reactions: List<FeedReaction>,
        read: ReadKey,
        myId: String,
        emoji: String?,
        now: Instant,
    ): List<FeedReaction> {
        val others = reactions.filter { it.reactorId != myId }
        if (emoji == null) return others
        return others +
            FeedReaction(read.ownerId, read.clientId, myId, emoji, DateTimeFormatter.ISO_INSTANT.format(now))
    }

    /** The trimmed body when it can be sent (1–200 characters), else null. */
    fun commentBody(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_COMMENT }
}

/**
 * The "new on my reads" cursor (README v6, "No repeats"): the server's own `created_at` normalised to UTC with all
 * six fractional digits and a `Z`, so `created_at=gt.<cursor>` never returns the same row twice.
 */
object SocialCursor {

    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    /** "2026-11-11T10:00:00.123+00:00" → "2026-11-11T10:00:00.123000Z"; null when it isn't a timestamp. */
    fun normalize(value: String): String? = parseInstant(value.trim().replace(' ', 'T'))?.let(::format)

    fun format(instant: Instant): String = FORMAT.format(instant)

    /** Percent-encoded for the query string (a `+` would turn into a space; a normalised cursor has none). */
    fun encode(cursor: String): String = URLEncoder.encode(cursor, Charsets.UTF_8.name())

    /** The later of two normalised cursors (fixed width, so text order is time order). */
    fun max(a: String, b: String?): String = if (b != null && b > a) b else a
}

/** "New reactions and comments on my reads" notifications (README v6, "Notifications" and section 3 "Alerts"). */
object SocialAlerts {

    const val BODY_MAX = 80
    const val SEEN_MAX = 300

    data class Plan(
        /** The new cursor ([SocialCursor] form); never moves back. */
        val cursor: String,
        /** Keys already announced, newest last, at most [SEEN_MAX]. */
        val seen: List<String>,
        /** At most one reactions message. */
        val reactions: String? = null,
        /** At most one comments message. */
        val comments: String? = null,
    ) {
        val messages: List<String> get() = listOfNotNull(reactions, comments)
    }

    fun key(reaction: FeedReaction): String =
        "r|${reaction.ownerId}|${reaction.clientId}|${reaction.reactorId}|${reaction.emoji}"

    fun key(comment: FeedComment): String = "c|${comment.id}"

    /**
     * The first check (no [cursor]) sets the cursor to [now] and posts nothing. Later checks skip rows not newer than
     * the cursor or already in [seen], post at most one reactions and one comments message, move the cursor to the
     * newest `created_at` received and remember the announced keys.
     */
    fun plan(
        cursor: String?,
        now: Instant,
        reactions: List<FeedReaction>,
        comments: List<FeedComment>,
        seen: List<String>,
        name: (String) -> String,
    ): Plan {
        val since = cursor?.let(SocialCursor::normalize) ?: return Plan(SocialCursor.format(now), seen)
        val seenSet = seen.toHashSet()
        fun fresh(createdAt: String?, key: String): String? =
            createdAt?.let(SocialCursor::normalize)?.takeIf { it > since && key !in seenSet }

        val newReactions = reactions.mapNotNull { r -> fresh(r.createdAt, key(r))?.let { it to r } }
            .sortedByDescending { it.first }
            .distinctBy { key(it.second) }
            .map { it.second }
        val newComments = comments.mapNotNull { c -> fresh(c.createdAt, key(c))?.let { it to c } }
            .sortedByDescending { it.first }
            .distinctBy { key(it.second) }
            .map { it.second }

        val received = (reactions.map { it.createdAt } + comments.map { it.createdAt })
            .mapNotNull { it?.let(SocialCursor::normalize) }
            .maxOrNull()
        // Oldest first, so the newest keys are the ones kept
        val announced = (newReactions.map(::key) + newComments.map(::key)).reversed()
        return Plan(
            cursor = SocialCursor.max(since, received),
            seen = (seen + announced).takeLast(SEEN_MAX),
            reactions = reactionsMessage(newReactions, name),
            comments = commentsMessage(newComments, name),
        )
    }

    /** [reactions] newest first: "Filip reacted 🔥 to One Piece Ch. 40", else "<who> reacted to <what>". */
    fun reactionsMessage(reactions: List<FeedReaction>, name: (String) -> String): String? = when (reactions.size) {
        0 -> null
        1 -> reactions.single().let { "${name(it.reactorId)} reacted ${it.emoji} to ${readLabel(it.reads)}" }
        else -> "${who(reactions.map { it.reactorId }, name)} reacted to ${what(reactions.map { it.read to it.reads })}"
    }

    /** [comments] newest first: "Filip commented on One Piece Ch. 40: “…”", else "<who> commented on <what>". */
    fun commentsMessage(comments: List<FeedComment>, name: (String) -> String): String? = when (comments.size) {
        0 -> null
        1 -> comments.single().let { "${name(it.authorId)} commented on ${readLabel(it.reads)}: “${cut(it.body)}”" }
        else -> "${who(comments.map { it.authorId }, name)} commented on ${what(comments.map { it.read to it.reads })}"
    }

    /** "Filip" / "Filip and Anna" / "Filip and 3 others" (distinct people, newest first). */
    fun who(userIds: List<String>, name: (String) -> String): String {
        val people = userIds.distinct()
        return when (people.size) {
            0 -> ""
            1 -> name(people[0])
            2 -> "${name(people[0])} and ${name(people[1])}"
            else -> "${name(people[0])} and ${people.size - 1} others"
        }
    }

    /** The read's label for one read, else "5 of your reads". */
    fun what(reads: List<Pair<ReadKey, ReadRef?>>): String {
        val distinct = reads.distinctBy { it.first }
        return if (distinct.size == 1) readLabel(distinct[0].second) else "${distinct.size} of your reads"
    }

    /** "One Piece Ch. 40", else the chapter name after the title. */
    fun readLabel(read: ReadRef?): String {
        val title = read?.seriesTitle?.takeIf { it.isNotBlank() } ?: "your read"
        val number = read?.chapterNumber?.takeIf { it >= 0 }
        val chapter = when {
            number != null -> "Ch. " + chapterNumber(number)
            !read?.chapterName.isNullOrBlank() -> read.chapterName.trim()
            else -> null
        }
        return listOfNotNull(title, chapter).joinToString(" ")
    }

    /** Comment bodies are cut to 80 characters. */
    fun cut(body: String): String = body.trim().replace(Regex("\\s+"), " ").take(BODY_MAX)
}

/** "40" for whole chapter numbers, else "40.5". */
fun chapterNumber(number: Double): String = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

/** Postgres returns offsets like "+00:00"; Instant.parse needs "Z" on older java.time. */
internal fun parseInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
