package mihon.feature.tracklibrary

import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RequestPacerTest {

    private var clock = 0L
    private val sleeps = mutableListOf<Long>()
    private fun pacer(interval: Long) = RequestPacer(
        interval = interval.milliseconds,
        now = { clock },
        sleep = { ms ->
            sleeps += ms
            clock += ms
        },
    )

    @Test
    fun `requests are spaced by the interval`() = runTest {
        val pacer = pacer(750)
        repeat(3) { pacer.acquire() }
        sleeps shouldBe listOf(750L, 750L)
        clock shouldBe 1500L
    }

    @Test
    fun `AniList stays at or under 80 a minute`() = runTest {
        val pacer = pacer(RequestPacer.intervalFor(2L).inWholeMilliseconds)
        repeat(81) { pacer.acquire() }
        // The 81st request starts no earlier than a minute after the first
        clock shouldBeGreaterThanOrEqual 60_000L
    }

    @Test
    fun `time spent elsewhere counts towards the gap`() = runTest {
        val pacer = pacer(1000)
        pacer.acquire()
        clock += 600
        pacer.acquire()
        sleeps shouldBe listOf(400L)
        clock += 5000
        pacer.acquire()
        sleeps shouldBe listOf(400L)
    }

    @Test
    fun `a costly call reserves several slots`() = runTest {
        val pacer = pacer(500)
        pacer.acquire(cost = 3)
        pacer.acquire()
        sleeps shouldBe listOf(1500L)
    }

    @Test
    fun `429 backs off, doubling up to five minutes, and a success resets it`() = runTest {
        val pacer = pacer(750)
        pacer.acquire()
        pacer.onRateLimited() shouldBe 30.seconds
        pacer.acquire()
        sleeps.last() shouldBe 30_000L
        pacer.onRateLimited() shouldBe 60.seconds
        pacer.onRateLimited() shouldBe 120.seconds
        pacer.onRateLimited() shouldBe 240.seconds
        pacer.onRateLimited() shouldBe 5.minutes
        pacer.onRateLimited() shouldBe 5.minutes
        pacer.onSuccess()
        pacer.onRateLimited() shouldBe 30.seconds
    }

    @Test
    fun `only HTTP 429 counts as rate limiting`() {
        RequestPacer.isRateLimited(HttpException(429)) shouldBe true
        RequestPacer.isRateLimited(HttpException(500)) shouldBe false
        RequestPacer.isRateLimited(IllegalStateException("429")) shouldBe false
    }
}
