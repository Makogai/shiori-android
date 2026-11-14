package mihon.feature.friends.data

import java.time.Instant

/** What the Arena section shows for one suggestion event, newest first. */
sealed interface SuggestionEvent {
    val at: Instant
    val suggestion: Suggestion

    /** Someone suggested a manga to me. */
    data class Received(override val suggestion: Suggestion, override val at: Instant) : SuggestionEvent

    /** I suggested a manga to someone. */
    data class Sent(override val suggestion: Suggestion, override val at: Instant) : SuggestionEvent

    /** Someone added a manga I suggested. */
    data class Accepted(override val suggestion: Suggestion, override val at: Instant) : SuggestionEvent

    /** Someone read a manga I suggested (README v9). */
    data class ReadIt(override val suggestion: Suggestion, override val at: Instant) : SuggestionEvent
}

/** The suggestion rules both apps share ("v3 — Suggestions" in the backend README). */
object SuggestionRules {

    const val MAX_NOTE = 200
    const val MAX_GENRES = 12

    /** Trimmed and capped; empty is no note. */
    fun note(text: String?): String? = text?.trim()?.take(MAX_NOTE)?.trimEnd()?.ifEmpty { null }

    fun genres(genres: List<String>?): List<String> =
        genres.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_GENRES)

    fun coverUrl(url: String?): String? = url?.takeIf {
        val lower = it.lowercase()
        lower.startsWith("http://") || lower.startsWith("https://")
    }

    /** One row per friend for sending [title] to [friends]. */
    fun inserts(
        me: String,
        friends: List<String>,
        sourceId: Long,
        sourceName: String?,
        mangaUrl: String,
        title: String,
        coverUrl: String?,
        genres: List<String>?,
        note: String?,
    ): List<SuggestionInsert> = friends.filter { it != me }.distinct().map { friend ->
        SuggestionInsert(
            fromUser = me,
            toUser = friend,
            sourceId = sourceId.toString(),
            sourceName = sourceName?.trim()?.ifEmpty { null },
            mangaUrl = mangaUrl,
            title = title.trim(),
            coverUrl = coverUrl(coverUrl),
            genres = genres(genres),
            note = note(note),
        )
    }

    /** Suggestions to me: pending first, then newest first. */
    fun forMe(all: List<Suggestion>, me: String): List<Suggestion> =
        all.filter { it.toUser == me }
            .sortedWith(compareByDescending<Suggestion> { it.pending }.thenByDescending { it.created })

    /** Suggestions from me, newest first. */
    fun sent(all: List<Suggestion>, me: String): List<Suggestion> =
        all.filter { it.fromUser == me }.sortedByDescending { it.created }

    /** Incoming pending suggestions. */
    fun badge(all: List<Suggestion>, me: String): Int = all.count { it.toUser == me && it.pending }

    /** [userId]'s suggestions that were added or read, as far as this device can see. */
    fun accepted(all: List<Suggestion>, userId: String): Int =
        all.count { it.fromUser == userId && it.accepted }

    /** The Arena's "From <name>" rows: incoming pending or added (not yet read), as [forMe] orders them. */
    fun open(all: List<Suggestion>, me: String, limit: Int = 3): List<Suggestion> =
        forMe(all, me)
            .filter { it.status == SuggestionStatus.PENDING || it.status == SuggestionStatus.ADDED }
            .take(limit)

    /** Ids of incoming pending suggestions whose manga is already a favourite, keyed by (source id, manga url). */
    fun autoAdded(all: List<Suggestion>, me: String, favorites: Set<Pair<Long, String>>): List<String> =
        all.filter { suggestion ->
            suggestion.toUser == me &&
                suggestion.pending &&
                suggestion.sourceId.toLongOrNull()?.let { (it to suggestion.mangaUrl) in favorites } == true
        }
            .map { it.id }

    /** [all] with [ids] answered locally, as the server's trigger would. */
    fun withStatus(all: List<Suggestion>, ids: Collection<String>, status: SuggestionStatus, now: Instant) =
        all.map {
            if (it.id !in ids) {
                it
            } else {
                it.copy(
                    status = status,
                    respondedAt = if (status == SuggestionStatus.PENDING) null else now.toString(),
                )
            }
        }

    /** [all] with [updated] replacing rows of the same id (or added in front). */
    fun merge(all: List<Suggestion>, updated: List<Suggestion>): List<Suggestion> {
        val byId = updated.associateBy { it.id }
        val kept = all.map { byId[it.id] ?: it }
        val known = all.mapTo(HashSet()) { it.id }
        return updated.filter { it.id !in known } + kept
    }

    /** The latest [limit] events involving me, newest first. */
    fun events(all: List<Suggestion>, me: String, limit: Int = 3): List<SuggestionEvent> =
        all.flatMap { suggestion ->
            when (me) {
                suggestion.toUser -> listOf(SuggestionEvent.Received(suggestion, suggestion.created))
                suggestion.fromUser -> listOfNotNull(
                    SuggestionEvent.Sent(suggestion, suggestion.created),
                    suggestion.responded
                        ?.takeIf { suggestion.accepted }
                        ?.let {
                            if (suggestion.status == SuggestionStatus.READ) {
                                SuggestionEvent.ReadIt(suggestion, it)
                            } else {
                                SuggestionEvent.Accepted(suggestion, it)
                            }
                        },
                )
                else -> emptyList()
            }
        }
            .sortedByDescending { it.at }
            .take(limit)
}
