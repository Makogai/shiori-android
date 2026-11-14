package eu.kanade.tachiyomi.ui.reader.capture

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StitchPlanTest {

    @Test
    fun `pages are scaled to the widest page capped at the maximum width`() {
        val selection = StripSelection(StripAnchor(0, 0.5f), StripAnchor(1, 0.5f))
        val plan = StitchPlan.make(selection, listOf(PixelSize(2880, 4000), PixelSize(1440, 1000))).shouldNotBeNull()
        plan.width shouldBe 1440
        plan.slices.size shouldBe 2
        // The bottom half of the 2880 wide page, halved
        plan.slices[0].crop shouldBe PixelRect(0, 2000, 2880, 2000)
        plan.slices[0].destination shouldBe PixelRect(0, 0, 1440, 1000)
        // The top half of the second page, at its own scale
        plan.slices[1].crop shouldBe PixelRect(0, 0, 1440, 500)
        plan.slices[1].destination shouldBe PixelRect(0, 1000, 1440, 500)
        plan.height shouldBe 1500
        plan.truncated shouldBe false
    }

    @Test
    fun `narrow pages are enlarged to the common width`() {
        val selection = StripSelection(StripAnchor(0, 0f), StripAnchor(1, 1f))
        val plan = StitchPlan.make(selection, listOf(PixelSize(720, 1000), PixelSize(360, 500))).shouldNotBeNull()
        plan.width shouldBe 720
        plan.slices[1].destination shouldBe PixelRect(0, 1000, 720, 1000)
        plan.height shouldBe 2000
    }

    @Test
    fun `tall selections stop at the maximum height`() {
        val selection = StripSelection(StripAnchor(0, 0f), StripAnchor(2, 1f))
        val size = PixelSize(800, 1000)
        val plan = StitchPlan.make(selection, listOf(size, size, size), maxWidth = 800, maxHeight = 1500)
            .shouldNotBeNull()
        plan.height shouldBe 1500
        plan.slices.size shouldBe 2
        plan.slices[1].crop.height shouldBe 500
        plan.truncatedAt shouldBe StripAnchor(1, 0.5f)
    }

    @Test
    fun `pages without a size are left out`() {
        val selection = StripSelection(StripAnchor(0, 0f), StripAnchor(1, 1f))
        val plan = StitchPlan.make(selection, listOf(null, PixelSize(100, 100))).shouldNotBeNull()
        plan.slices.map { it.page } shouldBe listOf(1)
        StitchPlan.make(selection, listOf(null, null)).shouldBeNull()
    }

    @Test
    fun `an empty stretch makes no plan`() {
        val selection = StripSelection(StripAnchor(0, 0.5f), StripAnchor(0, 0.5f))
        StitchPlan.make(selection, listOf(PixelSize(100, 100))).shouldBeNull()
    }

    @Test
    fun `crops stay inside the page`() {
        val selection = StripSelection(StripAnchor(0, 0.999f), StripAnchor(0, 1f))
        val plan = StitchPlan.make(selection, listOf(PixelSize(100, 999))).shouldNotBeNull()
        val crop = plan.slices.single().crop
        (crop.bottom <= 999) shouldBe true
        (crop.height >= 1) shouldBe true
    }

    @Test
    fun `shown pages span the slices`() {
        val selection = StripSelection(StripAnchor(3, 0.5f), StripAnchor(5, 0.2f))
        val sizes = List(6) { PixelSize(100, 100) }
        val plan = StitchPlan.make(selection, sizes).shouldNotBeNull()
        shownPages(plan.slices) { it + 1 } shouldBe 4..6
        shownPages(emptyList()) { it + 1 }.shouldBeNull()
    }

    @Test
    fun `sample sizes keep at least the target width`() {
        sampleSizeFor(cropWidth = 2880, targetWidth = 1440) shouldBe 2
        sampleSizeFor(cropWidth = 2879, targetWidth = 1440) shouldBe 1
        sampleSizeFor(cropWidth = 6000, targetWidth = 1000) shouldBe 4
        sampleSizeFor(cropWidth = 500, targetWidth = 1000) shouldBe 1
    }

    @Test
    fun `big screenshots are jpegs`() {
        encodesAsPng(1440, 5000) shouldBe true
        encodesAsPng(1440, 6000) shouldBe false
    }
}
