package eu.kanade.tachiyomi.ui.reader.capture

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StripSelectionTest {

    private val layout = StripLayout(listOf(100f, 200f, 50f))

    @Test
    fun `anchors and offsets round trip`() {
        layout.totalHeight shouldBe 350f
        layout.anchor(0f) shouldBe StripAnchor(0, 0f)
        layout.anchor(150f) shouldBe StripAnchor(1, 0.25f)
        layout.anchor(325f) shouldBe StripAnchor(2, 0.5f)
        layout.y(StripAnchor(1, 0.25f)) shouldBe 150f
        layout.y(StripAnchor(2, 1f)) shouldBe 350f
    }

    @Test
    fun `anchors are clamped to the strip`() {
        layout.anchor(-40f) shouldBe StripAnchor(0, 0f)
        layout.anchor(9999f) shouldBe StripAnchor(2, 1f)
    }

    @Test
    fun `gaps belong to the next page`() {
        val gapped = StripLayout(listOf(100f, 100f), gap = 12f)
        gapped.totalHeight shouldBe 212f
        gapped.top(1) shouldBe 112f
        gapped.anchor(105f) shouldBe StripAnchor(1, 0f)
        gapped.anchor(162f) shouldBe StripAnchor(1, 0.5f)
    }

    @Test
    fun `selections are ordered and keep a minimum height`() {
        val selection = layout.selection(300f, 120f)
        selection.top shouldBe StripAnchor(1, 0.1f)
        selection.bottom shouldBe StripAnchor(2, 0f)
        val thin = layout.selection(340f, 345f, minHeight = 40f)
        layout.range(thin) shouldBe 310f..350f
    }

    @Test
    fun `selection pages and spans`() {
        val selection = StripSelection(StripAnchor(0, 0.5f), StripAnchor(2, 0f))
        // Ending right at page 2's top does not take page 2
        selection.pages shouldBe 0..1
        selection.span(0) shouldBe 0.5f..1f
        selection.span(1) shouldBe 0f..1f
        val inside = StripSelection(StripAnchor(1, 0.2f), StripAnchor(1, 0.6f))
        inside.pages shouldBe 1..1
        inside.span(1) shouldBe 0.2f..0.6f
    }

    @Test
    fun `visible selection follows the current page`() {
        // The current page (index 1) starts 50 pixels above the screen; the screen is 120 tall
        val selection = layout.visibleSelection(current = 1, frameTop = -50f, viewport = 120f)
        selection.top shouldBe StripAnchor(1, 0.25f)
        selection.bottom shouldBe StripAnchor(1, 0.85f)
    }

    @Test
    fun `anchors order by page then fraction`() {
        (StripAnchor(1, 0.9f) < StripAnchor(2, 0f)) shouldBe true
        (StripAnchor(2, 0.1f) < StripAnchor(2, 0.2f)) shouldBe true
        maxOf(StripAnchor(3, 0f), StripAnchor(2, 1f)) shouldBe StripAnchor(3, 0f)
    }

    @Test
    fun `autoscroll only near the edges`() {
        autoscrollSpeed(fingerY = 400f, viewport = 800f, max = 22f) shouldBe 0f
        autoscrollSpeed(fingerY = 0f, viewport = 800f, max = 22f) shouldBe -22f
        autoscrollSpeed(fingerY = 800f, viewport = 800f, max = 22f) shouldBe 22f
        autoscrollSpeed(fingerY = 700f, viewport = 800f, max = 22f) shouldBe 11f
        autoscrollSpeed(fingerY = 10f, viewport = 0f, max = 22f) shouldBe 0f
    }
}
