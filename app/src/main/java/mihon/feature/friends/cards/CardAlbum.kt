package mihon.feature.friends.cards

import mihon.feature.friends.data.FeedRow
import mihon.feature.friends.data.parseTimestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The album's filters (CARD_SPEC §11); empty sets mean "all". [series] is a series key, or the title without one. */
data class CardAlbumFilter(
    val grades: Set<CardGrade> = emptySet(),
    val themes: Set<CardTheme> = emptySet(),
    val series: String? = null,
    val owner: String? = null,
) {
    val isActive: Boolean get() = grades.isNotEmpty() || themes.isNotEmpty() || series != null || owner != null

    fun matches(card: ShioriCard): Boolean =
        (grades.isEmpty() || card.grade in grades) &&
            (themes.isEmpty() || card.theme in themes) &&
            (series == null || CardAlbum.seriesId(card) == series) &&
            (owner == null || card.owner == owner)
}

/** The album's sorts (CARD_SPEC §13). Serials are Arena-wide and unique, so they break every tie. */
enum class CardAlbumSort {
    /** Highest serial first. */
    NEWEST,

    /** Lowest serial first; the seasons too go oldest first. */
    OLDEST,

    /** Rarest first. */
    GRADE,

    /** Series title A–Z, ignoring case. */
    SERIES,

    /** Owner name A–Z, ignoring case (offered for Everyone only; in Mine all owners are equal, so it is Newest). */
    OWNER,
    ;

    companion object {
        /** The sorts the menu offers in [scope]: Owner only for Everyone. */
        fun offered(scope: CardCollectionScope): List<CardAlbumSort> =
            if (scope == CardCollectionScope.EVERYONE) entries else entries - OWNER
    }
}

/** The collection's "Mine | Everyone" (CARD_SPEC §13), kept per device as [value]. */
enum class CardCollectionScope(val value: String) {
    MINE("mine"),

    /** Every Arena player's cards. */
    EVERYONE("everyone"),
    ;

    companion object {
        fun of(value: String?): CardCollectionScope = entries.firstOrNull { it.value == value } ?: MINE
    }
}

/** One line of the Arena feed: a read or a card pull. */
sealed interface CardFeedItem {
    val instant: Instant

    data class Read(val row: FeedRow) : CardFeedItem {
        override val instant: Instant get() = row.readInstant ?: Instant.EPOCH
    }

    data class Pull(val card: ShioriCard) : CardFeedItem {
        override val instant: Instant get() = card.createdInstant
    }
}

/** A season's cards in the album (CARD_SPEC §13): "Season 2 · 2026" and "12 cards". */
data class CardAlbumSection(val season: Int, val year: Int, val cards: List<ShioriCard>) {
    val title: String get() = "Season $season · $year"
    val countText: String get() = CardAlbum.countText(cards.size)
}

object CardAlbum {

    /**
     * [sorted] split by season, newest season first ([oldestFirst]: oldest first, for the Oldest sort), each keeping
     * the given order; the year is that of the section's newest card in [zone]. The flattened sections are the album
     * list the detail pager walks.
     */
    fun sections(sorted: List<ShioriCard>, zone: ZoneId, oldestFirst: Boolean = false): List<CardAlbumSection> =
        sorted.groupBy { it.season }
            .toSortedMap(if (oldestFirst) compareBy { it } else compareByDescending { it })
            .map { (season, cards) ->
                val newest = cards.maxOf { it.createdInstant }
                CardAlbumSection(season, newest.atZone(zone).year, cards)
            }

    /** The album list the pager walks: the sections' cards in order. */
    fun albumIds(sections: List<CardAlbumSection>): List<String> = sections.flatMap { s -> s.cards.map { it.id } }

    /** The cards [scope] covers: mine, or everyone's. */
    fun scoped(cards: List<ShioriCard>, scope: CardCollectionScope, me: String?): List<ShioriCard> =
        if (scope == CardCollectionScope.EVERYONE) cards else cards.filter { it.owner == me }

    /** "1 card", "15 cards". */
    fun countText(count: Int): String = if (count == 1) "1 card" else "$count cards"

    /** "12 cards · 5 series" (CARD_SPEC §16). */
    fun collectionText(cards: List<ShioriCard>): String =
        "${countText(cards.size)} · ${cards.map(::seriesId).distinct().size} series"

    /** Grid cells are 104…160 dp wide. */
    const val GRID_MIN = 104f
    const val GRID_MAX = 160f

    /**
     * How many columns fit [width]: as many [min]-wide cells as fit, more when cells would get wider than [max];
     * at least one.
     */
    fun columns(width: Float, min: Float, max: Float, spacing: Float): Int {
        if (width <= 0f) return 1
        val byMin = ((width + spacing) / (min + spacing)).toInt()
        val byMax = kotlin.math.ceil((width + spacing) / (max + spacing)).toInt()
        return maxOf(1, byMin, byMax)
    }

    /** The hub's "From friends": others' pulls, newest first, at most [limit]. */
    fun fromFriends(cards: List<ShioriCard>, me: String?, limit: Int): List<ShioriCard> =
        cards.filter { it.owner != me }.sortedWith(newestFirst).take(limit)

    /** The Library strip: my cards, newest first, at most [limit]. */
    fun recentMine(cards: List<ShioriCard>, me: String?, limit: Int): List<ShioriCard> =
        if (me == null) emptyList() else cards.filter { it.owner == me }.sortedWith(newestFirst).take(limit)

    private val newestFirst = compareByDescending<ShioriCard> { it.createdInstant }.thenByDescending { it.serial }
    private val bySerialDescending = compareByDescending<ShioriCard> { it.serial }

    /**
     * The cards that pass [filter], in [sort] order; ties go to the higher serial. [ownerName] names an owner for
     * the Owner sort ("You" for me, else the Arena name).
     */
    fun apply(
        cards: List<ShioriCard>,
        filter: CardAlbumFilter,
        sort: CardAlbumSort,
        ownerName: (String) -> String = { it },
    ): List<ShioriCard> {
        val comparator = when (sort) {
            CardAlbumSort.NEWEST -> bySerialDescending
            CardAlbumSort.OLDEST -> compareBy<ShioriCard> { it.serial }
            CardAlbumSort.GRADE -> compareByDescending<ShioriCard> { it.grade }.then(bySerialDescending)
            CardAlbumSort.SERIES -> compareBy<ShioriCard, String>(String.CASE_INSENSITIVE_ORDER) { it.seriesTitle }
                .then(bySerialDescending)
            CardAlbumSort.OWNER -> {
                val names = HashMap<String, String>()
                compareBy<ShioriCard, String>(String.CASE_INSENSITIVE_ORDER) {
                    names.getOrPut(it.owner) { ownerName(it.owner) }
                }.then(bySerialDescending)
            }
        }
        return cards.filter(filter::matches).sortedWith(comparator)
    }

    fun seriesId(card: ShioriCard): String = card.seriesKey?.takeIf { it.isNotEmpty() } ?: card.seriesTitle

    /** The series in [cards] for the filter menu: id to title, by title. */
    fun series(cards: List<ShioriCard>): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        cards.forEach { seen.getOrPut(seriesId(it)) { it.seriesTitle } }
        return seen.toList().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
    }

    /** How many cards of each grade, every grade listed, commonest first. */
    fun counts(cards: List<ShioriCard>): List<Pair<CardGrade, Int>> =
        CardGrade.entries.map { grade -> grade to cards.count { it.grade == grade } }

    /** The showcase: the rarest grade, a favourite before others, then the lowest serial. */
    fun rarest(cards: List<ShioriCard>): ShioriCard? = cards.minWithOrNull(
        compareByDescending<ShioriCard> { it.grade }.thenByDescending { it.favorite }.thenBy { it.serial },
    )

    /** The Arena day ("yyyy-MM-dd") of [instant] in [zone], as `card_pulls.day` stores it. */
    fun day(instant: Instant, zone: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(zone))

    /** Reads merged with R+ pulls, newest first, at most [limit]. */
    fun merged(reads: List<FeedRow>, pulls: List<ShioriCard>, limit: Int): List<CardFeedItem> =
        (reads.map { CardFeedItem.Read(it) } + pulls.filter { it.grade.isFeedWorthy }.map { CardFeedItem.Pull(it) })
            .sortedByDescending { it.instant }
            .take(limit)

    /**
     * Whether the owner's render predates the card's custom frame (or is missing although the frame is ready): the
     * album catch-up re-renders those.
     */
    fun renderIsStale(card: ShioriCard): Boolean {
        if (card.frameStatus != CardFrameStatus.READY) return false
        val rendered = card.renderUpdatedAt?.let(::parseTimestamp)
        if (card.renderPath == null || rendered == null) return true
        val ready = card.frameReadyAt?.let(::parseTimestamp) ?: return false
        return rendered.isBefore(ready)
    }

    /** A pending frame stops being waited for once polling gave up or the card is older than 5 minutes. */
    fun isStalePending(card: ShioriCard, now: Instant): Boolean =
        card.frameStatus == CardFrameStatus.PENDING &&
            now.toEpochMilli() - card.createdInstant.toEpochMilli() > CardRules.STALE_PENDING_MS
}
