package eu.kanade.domain.track.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

/** Binding pushes local progress to the tracker only when it's ahead; it never lowers it. */
class LocalProgressAheadTest {

    private fun chapters(vararg read: Pair<Double, Boolean>) = read.mapIndexed { i, (number, isRead) ->
        Chapter.create().copy(id = i.toLong(), chapterNumber = number, read = isRead)
    }

    @Test
    fun `local ahead of the tracker is pushed`() {
        localProgressAhead(chapters(1.0 to true, 2.0 to true, 3.0 to false), trackerProgress = 1.0) shouldBe 2.0
        localProgressAhead(chapters(1.0 to true, 2.0 to true), trackerProgress = 0.0) shouldBe 2.0
    }

    @Test
    fun `tracker ahead or level is left alone`() {
        localProgressAhead(chapters(1.0 to true, 2.0 to true, 3.0 to false), trackerProgress = 10.0) shouldBe null
        localProgressAhead(chapters(1.0 to true, 2.0 to true), trackerProgress = 2.0) shouldBe null
    }

    @Test
    fun `only the unbroken read run from the first chapter counts, in chapter order`() {
        // 3 read but 2 not: progress is 1
        localProgressAhead(chapters(3.0 to true, 1.0 to true, 2.0 to false), trackerProgress = 0.0) shouldBe 1.0
        localProgressAhead(chapters(1.0 to false, 2.0 to true), trackerProgress = 0.0) shouldBe null
        localProgressAhead(emptyList(), trackerProgress = 0.0) shouldBe null
    }
}
