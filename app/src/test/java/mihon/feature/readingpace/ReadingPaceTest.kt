package mihon.feature.readingpace

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.readingpace.PaceSample

class ReadingPaceTest {

    /** A chapter read to [pages] pages at [secondsPerPage]. */
    private fun sample(pages: Int, secondsPerPage: Double) =
        PaceSample((pages * secondsPerPage * 1000).toLong(), lastPageRead = pages - 1)

    @Test
    fun `too little history gives no pace`() {
        ReadingPace.of(listOf(sample(20, 5.0), sample(20, 5.0))).shouldBeNull()
    }

    @Test
    fun `the pace is the median and ignores implausible chapters`() {
        val samples = listOf(
            sample(20, 4.0),
            sample(20, 6.0),
            sample(20, 8.0),
            sample(20, 0.1),
            sample(10, 500.0),
            PaceSample(60_000, lastPageRead = 0),
        )
        ReadingPace.of(samples)?.secondsPerPage shouldBe 6.0
    }

    @Test
    fun `minutes round to the nearest`() {
        val pace = ReadingPace(6.0)
        pace.minutesFor(60) shouldBe 6
        pace.minutesFor(4) shouldBe 0
        pace.minutesFor(-3) shouldBe 0
    }

    @Test
    fun `median of an even count is the mean of the middle two`() {
        ReadingPace.median(listOf(4.0, 1.0, 3.0, 2.0)) shouldBe 2.5
    }
}
