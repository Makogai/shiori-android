package mihon.feature.friends.cards

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The on-screen light effects per grade: light enough to keep the art readable, stronger with the grade. */
class CardShineTest {

    private val upFromRare =
        listOf(CardGrade.RARE, CardGrade.EPIC, CardGrade.LEGENDARY, CardGrade.MYTHIC, CardGrade.SECRET)

    @Test
    fun `common has none, every other grade shows something`() {
        CardShine.of(CardGrade.COMMON).isNone shouldBe true
        CardGrade.entries.drop(1).forEach { CardShine.of(it).isNone shouldBe false }
    }

    @Test
    fun `the glare stays soft, 0_2 to 0_3, and nothing passes the cap`() {
        upFromRare.forEach {
            val shine = CardShine.of(it)
            shine.glare shouldBeGreaterThan 0.19f
            shine.glare shouldBeLessThan 0.301f
        }
        CardGrade.entries.forEach {
            val shine = CardShine.of(it)
            shine.peak shouldBeLessThan CardShine.MAX_ALPHA + 0.001f
            shine.foilAlpha shouldBeLessThan 0.2f
        }
        CardShine.of(CardGrade.UNCOMMON).peak shouldBeLessThan 0.1f
    }

    @Test
    fun `only a few small glints, and only from legendary up`() {
        CardGrade.entries.forEach {
            val shine = CardShine.of(it)
            shine.sparkles shouldBeLessThanOrEqual CardShine.MAX_SPARKLES
            shine.sparkleAlpha shouldBeLessThan CardShine.MAX_SPARKLE_ALPHA + 0.001f
            if (it < CardGrade.LEGENDARY) shine.sparkles shouldBe 0
        }
    }

    @Test
    fun `the grades stay distinct`() {
        val shines = CardGrade.entries.map(CardShine::of)
        shines.distinct().size shouldBe shines.size
        CardShine.of(CardGrade.RARE).foil.isEmpty() shouldBe true
        CardShine.of(CardGrade.EPIC).foil.isNotEmpty() shouldBe true
        CardShine.of(CardGrade.SECRET).glare shouldBeGreaterThan CardShine.of(CardGrade.RARE).glare
    }

    @Test
    fun `thumbnails are softer but still there`() {
        val full = CardShine.of(CardGrade.LEGENDARY)
        val thumb = full.scaled(CardShine.THUMB_STRENGTH)
        thumb.glare shouldBeLessThan full.glare
        thumb.glare shouldBeGreaterThan full.glare / 2
        CardShine.NONE.scaled(CardShine.THUMB_STRENGTH).isNone shouldBe true
    }

    @Test
    fun `the glare and foil move with the tilt`() {
        val rest = CardShine.glarePosition(CardTilt())
        CardShine.glarePosition(CardTilt(1f, 0f)) shouldBeGreaterThan rest
        CardShine.glarePosition(CardTilt(-1f, 0f)) shouldBeLessThan rest
        CardShine.foilShift(CardTilt()) shouldBe 0f
        CardShine.foilShift(CardTilt(0.5f, 0f)) shouldBeGreaterThan 0f
    }

    @Test
    fun `glints stay well inside the card and twinkle within 0 to 1`() {
        repeat(CardShine.MAX_SPARKLES) { i ->
            val (x, y) = CardShine.point(i, salt = 7)
            (x in 0.1f..0.9f && y in 0.08f..0.92f) shouldBe true
            listOf(0.0, 0.4, 1.3, 7.7).forEach { t -> (CardShine.twinkle(i, t) in 0f..1f) shouldBe true }
        }
    }

    @Test
    fun `the live holo is a little stronger, capped, exports untouched`() {
        CardLayerPlan.liveEffectOpacity(CardLayerPlan.HOLO, 0.4f) shouldBe (0.52f plusOrMinus 0.001f)
        CardLayerPlan.liveEffectOpacity(CardLayerPlan.FOIL, 0.9f) shouldBe CardLayerPlan.LIVE_OPACITY_CAP
        CardLayerPlan.liveEffectOpacity("sparkle_burst", 0.9f) shouldBe 0.9f
    }
}
