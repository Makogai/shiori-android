package mihon.feature.tracklibrary

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Spaces requests to one tracker at least [interval] apart and backs off after a 429: 30 s, doubling per repeated
 * 429, at most 5 min, reset by the next success. [now] and [sleep] are injectable for tests.
 */
class RequestPacer(
    val interval: Duration,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()
    private var nextAt = 0L
    private var backoffs = 0

    /** Waits for a slot; [cost] reserves that many intervals for calls that make several requests. */
    suspend fun acquire(cost: Int = 1) {
        mutex.withLock {
            val wait = nextAt - now()
            if (wait > 0) sleep(wait)
            nextAt = maxOf(now(), nextAt) + interval.inWholeMilliseconds * cost
        }
    }

    fun onSuccess() {
        backoffs = 0
    }

    /** Pushes the next slot out by the current back-off and returns it. */
    fun onRateLimited(): Duration {
        val backoff = minOf(BASE_BACKOFF * (1 shl backoffs.coerceAtMost(4)), MAX_BACKOFF)
        backoffs++
        nextAt = maxOf(nextAt, now() + backoff.inWholeMilliseconds)
        return backoff
    }

    companion object {
        private val BASE_BACKOFF = 30.seconds
        private val MAX_BACKOFF = 5.minutes
        private const val MYANIMELIST_ID = 1L
        private const val ANILIST_ID = 2L

        /** AniList allows ~90/min (and its client throttles further); MAL publishes no limit, so stay well under. */
        fun intervalFor(trackerId: Long): Duration = when (trackerId) {
            ANILIST_ID -> 750.milliseconds
            MYANIMELIST_ID -> 2.seconds
            else -> 1500.milliseconds
        }

        fun isRateLimited(e: Throwable): Boolean = e is HttpException && e.code == 429
    }
}
