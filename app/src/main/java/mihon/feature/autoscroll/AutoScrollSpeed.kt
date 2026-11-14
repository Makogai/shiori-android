package mihon.feature.autoscroll

import kotlin.math.floor
import kotlin.math.pow

/** The long strip's auto-scroll speeds, as levels from slow to fast. */
object AutoScrollSpeed {
    val levels = 1..10
    const val DEFAULT_LEVEL = 4

    /** The slowest level's speed on screen, in dp a second; each level is [FACTOR] times the one before. */
    private const val SLOWEST = 20f
    private const val FACTOR = 1.35f

    fun clamp(level: Int): Int = level.coerceIn(levels)

    /** From 20 to about 300 dp a second. */
    fun dpPerSecond(level: Int): Float = SLOWEST * FACTOR.pow(clamp(level) - 1)

    /**
     * How far the strip's content moves in [seconds], in pixels: zoomed in, the content moves less so the page still
     * passes the screen at the same speed.
     */
    fun distance(level: Int, seconds: Float, density: Float, zoom: Float): Float {
        if (seconds <= 0f) return 0f
        return dpPerSecond(level) * density * seconds / zoom.coerceAtLeast(1f)
    }

    /** Splits [pending] pixels into whole ones to scroll now and the rest, so slow speeds don't round away. */
    fun step(pending: Float): Pair<Int, Float> {
        val step = floor(pending).toInt()
        return step to pending - step
    }

    /** The series' own level when it has one, else [default]. */
    fun forSeries(speeds: Map<Long, Int>, mangaId: Long?, default: Int): Int =
        clamp(mangaId?.let { speeds[it] } ?: default)

    /** "12=4,40=7": series ids to levels. */
    fun serialize(speeds: Map<Long, Int>): String =
        speeds.entries.joinToString(",") { "${it.key}=${it.value}" }

    fun deserialize(text: String, range: IntRange = levels): Map<Long, Int> = text.split(',')
        .mapNotNull { entry ->
            val (id, level) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val mangaId = id.toLongOrNull() ?: return@mapNotNull null
            level.toIntOrNull()?.let { mangaId to it.coerceIn(range) }
        }
        .toMap()
}
