package mihon.feature.autoscroll

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AutoScrollSpeedTest {

    @Test
    fun `speeds grow from the slowest level and are clamped`() {
        AutoScrollSpeed.dpPerSecond(1) shouldBe 20f
        AutoScrollSpeed.dpPerSecond(0) shouldBe 20f
        AutoScrollSpeed.dpPerSecond(99) shouldBe AutoScrollSpeed.dpPerSecond(10)
    }

    @Test
    fun `zooming in moves the content less`() {
        AutoScrollSpeed.distance(level = 1, seconds = 1f, density = 2f, zoom = 1f) shouldBe 40f
        AutoScrollSpeed.distance(level = 1, seconds = 1f, density = 2f, zoom = 2f) shouldBe 20f
        AutoScrollSpeed.distance(level = 1, seconds = 1f, density = 2f, zoom = 0.5f) shouldBe 40f
        AutoScrollSpeed.distance(level = 1, seconds = 0f, density = 2f, zoom = 1f) shouldBe 0f
    }

    @Test
    fun `steps are whole pixels with the rest carried over`() {
        AutoScrollSpeed.step(2.75f) shouldBe (2 to 0.75f)
        AutoScrollSpeed.step(0.5f) shouldBe (0 to 0.5f)
    }

    @Test
    fun `a series uses its own speed or else the default`() {
        val speeds = mapOf(1L to 7)
        AutoScrollSpeed.forSeries(speeds, 1L, default = 4) shouldBe 7
        AutoScrollSpeed.forSeries(speeds, 2L, default = 4) shouldBe 4
        AutoScrollSpeed.forSeries(speeds, null, default = 4) shouldBe 4
    }

    @Test
    fun `series speeds round-trip and skip bad entries`() {
        val speeds = mapOf(12L to 4, 40L to 7)
        AutoScrollSpeed.deserialize(AutoScrollSpeed.serialize(speeds)) shouldBe speeds
        AutoScrollSpeed.deserialize("") shouldBe emptyMap()
        AutoScrollSpeed.deserialize("x=3,5=,6=30") shouldBe mapOf(6L to 10)
    }
}
