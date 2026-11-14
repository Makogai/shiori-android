package mihon.feature.friends.cards

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** CARD_SPEC §14 (pager), the v1.2 card back ("Pull back" + "Halo") and the print sheet. */
class CardBackTest {

    @Test
    fun `the pager walks the list without wrapping`() {
        val pager = CardPager(listOf("a", "b", "c"))
        pager.index("b") shouldBe 1
        pager.index("missing") shouldBe 0
        pager.next(1) shouldBe 2
        pager.next(2) shouldBe 2
        pager.previous(1) shouldBe 0
        pager.previous(0) shouldBe 0
        pager.label(2) shouldBe "3 of 3"
        pager.showsIndicator shouldBe true
        CardPager(listOf("a")).showsIndicator shouldBe false
        CardPager(listOf("a")).label(0) shouldBe "1 of 1"
        CardPager(emptyList()).next(0) shouldBe 0
        CardPager((1..15).map { "$it" }).label(2) shouldBe "3 of 15"
        // The capsule under the carousel
        CardPager((1..15).map { "$it" }).caption(2, "Filip") shouldBe "3 of 15 · Filip"
        CardPager(listOf("a")).caption(0, "You") shouldBe "You"
    }

    @Test
    fun `the prefetch window is the current card, then its nearest neighbours, clamped`() {
        val pager = CardPager((0 until 10).map { "c$it" })
        pager.prefetchWindow(5) shouldBe listOf(5, 6, 4, 7, 3)
        pager.prefetchWindow(0) shouldBe listOf(0, 1, 2)
        pager.prefetchWindow(1) shouldBe listOf(1, 2, 0, 3)
        pager.prefetchWindow(9) shouldBe listOf(9, 8, 7)
        pager.prefetchWindow(42) shouldBe listOf(9, 8, 7)
        pager.prefetchWindow(5, radius = 1) shouldBe listOf(5, 6, 4)
        CardPager(listOf("a")).prefetchWindow(0) shouldBe listOf(0)
        CardPager(emptyList()).prefetchWindow(0) shouldBe emptyList()
        pager.windowIds(0) shouldBe listOf("c0", "c1", "c2")
        // Loads for cards that left the window stop: the old window minus the new
        pager.leaving(from = 5, to = 6) shouldBe setOf("c3")
        pager.leaving(from = 0, to = 1) shouldBe emptySet()
        pager.leaving(from = 2, to = 9) shouldBe setOf("c0", "c1", "c2", "c3", "c4")
        CardPager(listOf("a")).leaving(0, 0) shouldBe emptySet()
    }

    @Test
    fun `prefetching starts what's missing and cancels what left the window`() {
        val plan = CardPrefetchPlan.of(
            window = listOf("c5", "c6", "c4", "c7", "c3"),
            held = setOf("c5", "c4"),
            loading = setOf("c6", "c1", "c2"),
        )
        plan.start shouldBe listOf("c7", "c3")
        plan.cancel shouldBe setOf("c1", "c2")
        CardPrefetchPlan.of(listOf("a"), emptySet(), emptySet()) shouldBe CardPrefetchPlan(listOf("a"), emptySet())
    }

    @Test
    fun `full images are held for at most 7 cards, least recently used out first`() {
        val cache = CardPrefetchCache<Int>()
        (0 until 7).forEach { cache.put("c$it", it) }
        cache.ids shouldBe (0 until 7).map { "c$it" }.toSet()
        // Using c0 keeps it; c1 is now the oldest
        cache["c0"] shouldBe 0
        cache.put("c7", 7)
        cache.ids shouldBe (setOf("c0") + (2..7).map { "c$it" })
        cache["c1"] shouldBe null
        CardPrefetchCache<Int>(capacity = 1).apply {
            put("a", 1)
            put("b", 2)
        }.ids shouldBe setOf("b")
    }

    @Test
    fun `the thumbnail shows until the full card is rendered`() {
        CardLoadStage.of(thumbnail = false, full = false) shouldBe CardLoadStage.WAITING
        CardLoadStage.of(thumbnail = true, full = false) shouldBe CardLoadStage.THUMBNAIL
        CardLoadStage.of(thumbnail = true, full = true) shouldBe CardLoadStage.FULL
        CardLoadStage.of(thumbnail = false, full = true) shouldBe CardLoadStage.FULL
    }

    @Test
    fun `the carousel's card is 86 percent wide up to 420, neighbours smaller, dimmed and pulled in`() {
        CardCarousel.cardWidth(400f) shouldBe (344f plusOrMinus 0.01f)
        CardCarousel.sidePadding(400f) shouldBe (28f plusOrMinus 0.01f)
        CardCarousel.cardWidth(800f) shouldBe 420f
        CardCarousel.sidePadding(800f) shouldBe 190f
        CardCarousel.cardWidth(0f) shouldBe 0f
        CardCarousel.scale(0f) shouldBe 1f
        CardCarousel.alpha(0f) shouldBe 1f
        CardCarousel.pull(0f) shouldBe 0f
        CardCarousel.scale(1f) shouldBe (0.9f plusOrMinus 0.0001f)
        CardCarousel.alpha(-1f) shouldBe (0.65f plusOrMinus 0.0001f)
        // Halfway through a drag, halfway between
        CardCarousel.scale(0.5f) shouldBe (0.95f plusOrMinus 0.0001f)
        CardCarousel.alpha(-0.5f) shouldBe (0.825f plusOrMinus 0.0001f)
        // Farther cards stay at the neighbours' look
        CardCarousel.scale(2.5f) shouldBe (0.9f plusOrMinus 0.0001f)
        // Toward the centre: the right neighbour moves left, the left one right
        CardCarousel.pull(1f) shouldBe -8f
        CardCarousel.pull(-1f) shouldBe 8f
        CardCarousel.pull(0.25f) shouldBe -2f
        CardCarousel.pull(3f) shouldBe -8f
    }

    @Test
    fun `the back is the 750 x 1050 board, scaled uniformly with the front's 2_4 percent corners`() {
        (CardBackLayout.WIDTH / CardBackLayout.HEIGHT) shouldBe (CardSpec.ASPECT plusOrMinus 1e-6f)
        CardBackLayout.CORNER shouldBe (18f plusOrMinus 1e-4f)
        CardBackLayout.TITLE_SCALE shouldBe 1.25f
        CardBackLayout.LOGO_SCALE shouldBe 1.35f
        CardBackLayout.HALO_RADIUS shouldBe (351f plusOrMinus 1e-3f)
        CardBackLayout.LOGO_SIZE shouldBe (162f plusOrMinus 1e-3f)
        CardBackLayout.SERIES_SIZE shouldBe 11.25f
        // Frames and marks stay inside the card
        listOf(CardBackLayout.OUTER, CardBackLayout.INNER).forEach {
            (it.left >= 0f && it.top >= 0f) shouldBe true
            (it.right <= CardBackLayout.WIDTH && it.bottom <= CardBackLayout.HEIGHT) shouldBe true
        }
        (CardBackLayout.SERIES_MAX_WIDTH < CardBackLayout.INNER.width) shouldBe true
        (CardBackLayout.NUMBER_BASELINE < CardBackLayout.INNER.bottom) shouldBe true
    }

    @Test
    fun `pull back ring, ticks and corners`() {
        CardBackLayout.dot(0) shouldBe CardBackDot(525f, 440f, 2.4f, 0.55f)
        CardBackLayout.dot(1).radius shouldBe 1.3f
        CardBackLayout.dot(3).radius shouldBe 2.4f
        CardBackLayout.dot(9).let {
            it.x shouldBe (375f plusOrMinus 1e-3f)
            it.y shouldBe (590f plusOrMinus 1e-3f)
        }
        (0 until CardBackLayout.DOTS).count { CardBackLayout.dot(it).radius == 2.4f } shouldBe 12
        CardBackLayout.tick(0) shouldBe listOf(493f, 440f, 509f, 440f)
        CardBackLayout.tick(12).let { (x1, y1, x2, y2) ->
            x1 shouldBe (375f plusOrMinus 1e-3f)
            y1 shouldBe (558f plusOrMinus 1e-3f)
            x2 shouldBe (375f plusOrMinus 1e-3f)
            y2 shouldBe (574f plusOrMinus 1e-3f)
        }
        CardBackLayout.CORNERS.map { it.sx to it.sy } shouldContainExactly listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)
        CardBackLayout.MARKS.size shouldBe 4
    }

    @Test
    fun `palettes follow the board's G table, by stored grade`() {
        CardBackLayout.palette(CardGrade.COMMON).a shouldBe 0xFFA9AFB8
        CardBackLayout.palette(CardGrade.COMMON).k shouldBe 0.55f
        CardBackLayout.palette(CardGrade.UNCOMMON).a shouldBe 0xFF62CDB8
        CardBackLayout.palette(CardGrade.RARE).metal shouldBe listOf(0xFFF4F8FF, 0xFFAFC4E0, 0xFF4F6A92)
        CardBackLayout.palette(CardGrade.EPIC).a shouldBe 0xFFB88CF2
        CardBackLayout.palette(CardGrade.LEGENDARY).bg2 shouldBe 0xFF2C1F0A
        CardBackLayout.palette(CardGrade.MYTHIC).let {
            it.aurora shouldBe true
            it.stars shouldBe false
            it.metal shouldBe listOf(0xFF5FFFE0, 0xFF8A7CFF, 0xFFFF6CD0)
        }
        CardBackLayout.palette(CardGrade.SECRET).let {
            it.stars shouldBe true
            it.aurora shouldBe false
            it.metal.size shouldBe 4
        }
        CardGrade.entries.filter { it < CardGrade.MYTHIC }.forEach {
            CardBackLayout.palette(it).aurora shouldBe false
            CardBackLayout.palette(it).stars shouldBe false
        }
        // Pattern strength: the grade's k at normal strength
        CardBackLayout.patternAlpha(CardGrade.UNCOMMON) shouldBe 0.72f
        CardBackLayout.patternAlpha(CardGrade.LEGENDARY) shouldBe 1f
        CardBackLayout.thumbGlow(CardGrade.COMMON) shouldBe (0.25f to 4f)
        CardBackLayout.thumbGlow(CardGrade.EPIC) shouldBe (0.55f to 8f)
        CardBackLayout.thumbGlow(CardGrade.MYTHIC) shouldBe (0.75f to 12f)
    }

    @Test
    fun `the back's text is the uppercase series and the number with the display rank`() {
        CardBackLayout.seriesText(" The Greatest Estate Developer ") shouldBe "THE GREATEST ESTATE DEVELOPER"
        CardBackLayout.numberText(13, CardGrade.LEGENDARY) shouldBe "№ 0013 · RANK A"
        CardBackLayout.numberText(12345, CardGrade.SECRET) shouldBe "№ 12345 · RANK SR"
        CardBackLayout.numberText(0, CardGrade.COMMON) shouldBe "RANK E"
        CardBackLayout.numberText(null, CardGrade.MYTHIC) shouldBe "RANK S"
        // Long titles shrink in 0.25 px steps, never below the minimum
        CardBackLayout.fit(11.25f, 7f, 640f) { it * 50 } shouldBe 11.25f
        CardBackLayout.SERIES_MAX_WIDTH shouldBe 640f
        CardBackLayout.fit(11.25f, 7f, 500f) { it * 50 } shouldBe 10f
        CardBackLayout.fit(11.25f, 7f, 10f) { it * 50 } shouldBe 7f
    }

    @Test
    fun `the SR sky is the board's seeded stars`() {
        val r = CardBackLayout.Rng(3)
        // Park-Miller: 3 × 16807 = 50421
        r.next() shouldBe ((50421.0 - 1) / 2147483646 plusOrMinus 1e-15)
        CardBackLayout.STAR_FIELD.size shouldBe 70
        CardBackLayout.STAR_FIELD.forEach {
            (it.x in 0f..CardBackLayout.WIDTH && it.y in 0f..CardBackLayout.HEIGHT) shouldBe true
            (it.radius in 0.5f..1.8f) shouldBe true
            (it.alpha in 0.2f..0.8f) shouldBe true
        }
    }

    @Test
    fun `print sheet geometry`() {
        CardPrintSheet.TRIM_W shouldBe 744
        CardPrintSheet.TRIM_H shouldBe 1039
        CardPrintSheet.PAGE_W shouldBe 1842
        CardPrintSheet.PAGE_H shouldBe 1275
        CardPrintSheet.PAGE_W_PT shouldBe (442.08 plusOrMinus 1e-9)
        CardPrintSheet.PAGE_H_PT shouldBe (306.0 plusOrMinus 1e-9)
        CardPrintSheet.FRONT shouldBe CardBox(118f, 118f, 744f, 1039f)
        CardPrintSheet.BACK shouldBe CardBox(980f, 118f, 744f, 1039f)
        CardPrintSheet.scale().let { (x, y) ->
            x shouldBe (0.4960f plusOrMinus 0.00005f)
            y shouldBe (0.4948f plusOrMinus 0.00005f)
        }
        // 63 × 88 mm isn't quite 5:7: the card is fitted uniformly and centred, never stretched
        CardPrintSheet.fit(CardPrintSheet.FRONT).let {
            it.height shouldBe (1039f plusOrMinus 1e-3f)
            it.width shouldBe (742.143f plusOrMinus 1e-3f)
            (it.width / it.height) shouldBe (CardSpec.ASPECT plusOrMinus 1e-6f)
            it.centerX shouldBe (CardPrintSheet.FRONT.centerX plusOrMinus 1e-3f)
        }

        val marks = CardPrintSheet.cropMarks(CardPrintSheet.FRONT)
        marks.size shouldBe 8
        // Top-left: the top edge extended left and the left edge extended up, 24 px out, 59 px long
        marks shouldContainExactly listOf(
            CardPrintSheet.Line(94f, 118f, 35f, 118f),
            CardPrintSheet.Line(118f, 94f, 118f, 35f),
            CardPrintSheet.Line(94f, 1157f, 35f, 1157f),
            CardPrintSheet.Line(118f, 1181f, 118f, 1240f),
            CardPrintSheet.Line(886f, 118f, 945f, 118f),
            CardPrintSheet.Line(862f, 94f, 862f, 35f),
            CardPrintSheet.Line(886f, 1157f, 945f, 1157f),
            CardPrintSheet.Line(862f, 1181f, 862f, 1240f),
        )
        CardPrintSheet.MARKS.size shouldBe 16
        // Everything on the page; the caption sits below the marks
        CardPrintSheet.MARKS.forEach {
            listOf(it.x1, it.x2).all { x -> x in 0f..CardPrintSheet.PAGE_W.toFloat() } shouldBe true
            listOf(it.y1, it.y2).all { y -> y in 0f..CardPrintSheet.PAGE_H.toFloat() } shouldBe true
        }
        (CardPrintSheet.MARKS.maxOf { maxOf(it.y1, it.y2) } < CardPrintSheet.CAPTION_BASELINE) shouldBe true
        CardPrintSheet.caption("Zoro", "No. 0042") shouldBe "Zoro · No. 0042 · 63 × 88 mm · print at 100 %"
    }
}
