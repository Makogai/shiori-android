package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StripLayoutTest {

    @Test
    fun `wide padding applies only when set and wide`() {
        StripLayout.sidePadding(sidePadding = 5, widePadding = 20, isWide = true) shouldBe 20
        StripLayout.sidePadding(sidePadding = 5, widePadding = 20, isWide = false) shouldBe 5
        StripLayout.sidePadding(sidePadding = 5, widePadding = StripLayout.SAME_AS_SIDE_PADDING, isWide = true) shouldBe
            5
    }
}
