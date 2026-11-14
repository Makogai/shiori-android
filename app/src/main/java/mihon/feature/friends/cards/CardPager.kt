package mihon.feature.friends.cards

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The detail carousel's index logic (CARD_SPEC §14): no wrap-around, "3 of 15 · Filip", the prefetch window. */
data class CardPager(val ids: List<String>) {

    val count: Int get() = ids.size

    /** The page of [id]; 0 when missing. */
    fun index(of: String): Int = ids.indexOf(of).coerceAtLeast(0)

    fun next(index: Int): Int = minOf(index + 1, count - 1).coerceAtLeast(0)

    fun previous(index: Int): Int = maxOf(index - 1, 0)

    fun label(index: Int): String = "${index + 1} of $count"

    /** The indicator shows only for more than one card. */
    val showsIndicator: Boolean get() = count > 1

    /** The capsule under the carousel: "3 of 15 · Filip"; a single card just its owner. */
    fun caption(index: Int, owner: String): String = if (showsIndicator) "${label(index)} · $owner" else owner

    /**
     * The pages whose full images are loaded around [index]: index − [radius] … index + [radius] clamped to the list,
     * the current page first, then the nearest neighbours (next before previous).
     */
    fun prefetchWindow(index: Int, radius: Int = PREFETCH_RADIUS): List<Int> {
        if (count == 0) return emptyList()
        val center = index.coerceIn(0, count - 1)
        return buildList {
            add(center)
            for (step in 1..radius) {
                if (center + step < count) add(center + step)
                if (center - step >= 0) add(center - step)
            }
        }
    }

    /** The cards of [prefetchWindow] around [index]. */
    fun windowIds(index: Int, radius: Int = PREFETCH_RADIUS): List<String> = prefetchWindow(index, radius).map(ids::get)

    /** The cards whose loads stop when the current page goes from [from] to [to]: the old window minus the new. */
    fun leaving(from: Int, to: Int, radius: Int = PREFETCH_RADIUS): Set<String> =
        windowIds(from, radius).toSet() - windowIds(to, radius).toSet()

    companion object {
        const val PREFETCH_RADIUS = 2

        /** Full images are held for at most this many cards (the ±2 window plus a couple just left). */
        const val HELD = 7
    }
}

/** What to do when the window moves: loads to start (in window order) and in-flight loads to cancel. */
data class CardPrefetchPlan(val start: List<String>, val cancel: Set<String>) {
    companion object {
        fun of(window: List<String>, held: Set<String>, loading: Set<String>): CardPrefetchPlan = CardPrefetchPlan(
            start = window.filter { it !in held && it !in loading },
            cancel = loading - window.toSet(),
        )
    }
}

/** The full images kept for the carousel: at most [capacity] cards, the least recently used dropped first. */
class CardPrefetchCache<V>(private val capacity: Int = CardPager.HELD) {
    private val held = LinkedHashMap<String, V>(16, 0.75f, true)

    val ids: Set<String> get() = held.keys.toSet()

    operator fun get(id: String): V? = held[id]

    fun put(id: String, value: V) {
        held[id] = value
        while (held.size > capacity) held.remove(held.keys.first())
    }
}

/** What a carousel card shows: nothing yet, its album thumbnail, or the full card (cross-faded in over it). */
enum class CardLoadStage {
    WAITING,
    THUMBNAIL,
    FULL,
    ;

    companion object {
        /** The thumbnail stays until the full card is rendered; with neither, an empty (never dark) box. */
        fun of(thumbnail: Boolean, full: Boolean): CardLoadStage = when {
            full -> FULL
            thumbnail -> THUMBNAIL
            else -> WAITING
        }
    }
}

/**
 * The detail carousel's geometry (CARD_SPEC §14), in dp. [offset] is a page's distance from the centre in pages,
 * positive to the right, fractional while dragging.
 */
object CardCarousel {
    const val WIDTH_SHARE = 0.86f
    const val MAX_WIDTH = 420f

    /** Neighbours are pulled this far toward the centre (a net −8 dp gap). */
    const val PULL = 8f
    const val NEIGHBOUR_SCALE = 0.9f
    const val NEIGHBOUR_ALPHA = 0.65f
    const val CROSSFADE_MS = 250
    const val SNAP_DAMPING = 0.85f

    /** dp of room above and below the cards for the tilt's 3-D swing (its near edge grows ~8 dp at full tilt). */
    const val SWING_ROOM = 20f

    fun cardWidth(available: Float): Float = max(0f, min(WIDTH_SHARE * available, MAX_WIDTH))

    /** The side content padding that centres the card. */
    fun sidePadding(available: Float): Float = max(0f, (available - cardWidth(available)) / 2)

    fun distance(offset: Float): Float = min(abs(offset), 1f)

    fun scale(offset: Float): Float = 1f - (1f - NEIGHBOUR_SCALE) * distance(offset)

    fun alpha(offset: Float): Float = 1f - (1f - NEIGHBOUR_ALPHA) * distance(offset)

    /** The sideways shift toward the centre. */
    fun pull(offset: Float): Float = if (offset > 0f) -PULL * distance(offset) else PULL * distance(offset)
}
