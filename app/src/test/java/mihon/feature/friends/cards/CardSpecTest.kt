package mihon.feature.friends.cards

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Shiori Cards v1.2: display ranks, the 5:7 geometry and legacy images (BRIEF §2–4, §6, §10). */
class CardSpecTest {

    @Test
    fun `stored grades show as the new ranks, codes and order unchanged`() {
        CardGrade.entries.map { it.code to it.rank } shouldBe listOf(
            "C" to "E",
            "U" to "D",
            "R" to "C",
            "E" to "B",
            "L" to "A",
            "M" to "S",
            "S" to "SR",
        )
        // Parsing still reads the stored code, never the rank letter
        CardGrade.of("E") shouldBe CardGrade.EPIC
        CardGrade.of("S") shouldBe CardGrade.SECRET
        CardGrade.of("SR") shouldBe CardGrade.COMMON
        CardGrade.ofRank("SR") shouldBe CardGrade.SECRET
        CardGrade.ofRank("b") shouldBe CardGrade.EPIC
        CardGrade.ofRank("X") shouldBe null
        // E lowest … SR highest
        CardGrade.entries.sorted().map { it.rank } shouldBe listOf("E", "D", "C", "B", "A", "S", "SR")
        CardGrade.LEGENDARY.label shouldBe "Rank A"
        CardGrade.SECRET.label shouldBe "Rank SR"
        CardGrade.entries.none { listOf("Common", "Legendary", "Mythic", "Epic").any(it.label::contains) } shouldBe true
    }

    @Test
    fun `rank colours are BRIEF section 6's edge anchors`() {
        CardGrade.entries.map { it.rankHex } shouldBe
            listOf("#AAB0B8", "#55B99D", "#538DCB", "#A968D8", "#D6B774", "#CDDCE7", "#F0E8DE")
        CardGrade.LEGENDARY.rankColor shouldBe 0xFFD6B774.toInt()
    }

    @Test
    fun `serials pad to at least four digits and are never cut`() {
        CardSpec.cardIdText(3) shouldBe "No. 0003"
        CardSpec.cardIdText(42) shouldBe "No. 0042"
        CardSpec.cardIdText(9999) shouldBe "No. 9999"
        CardSpec.cardIdText(123456) shouldBe "No. 123456"
        CardSpec.cardIdText(0) shouldBe null
        CardSpec.cardIdText(null) shouldBe null
        CardSpec.serialDigits(13) shouldBe "0013"
        ShioriCard(id = "c", owner = "o", serial = 7).serialText shouldBe "No. 0007"
        ShioriCard(id = "c", owner = "o", serial = 7).serialDigits shouldBe "0007"
    }

    @Test
    fun `edition fractions pad both sides to the total's width, at least two`() {
        CardSpec.printText(1, 10) shouldBe "01/10"
        CardSpec.printText(1, 100) shouldBe "001/100"
        CardSpec.printText(7, 9) shouldBe "07/09"
        CardSpec.printText(0, 10) shouldBe null
        CardSpec.printText(11, 10) shouldBe null
        CardSpec.printText(null, 10) shouldBe null
    }

    @Test
    fun `the card is 5 by 7 with a 2_4 percent corner, scaled uniformly`() {
        CardSpec.ASPECT shouldBe (1500f / 2100f plusOrMinus 1e-7f)
        CardSpec.heightFor(1500) shouldBe 2100
        CardSpec.heightFor(300) shouldBe 420
        CardSpec.heightFor(1080) shouldBe 1512
        CardSpec.cornerRadius(1500f) shouldBe (36f plusOrMinus 1e-4f)
        CardSpec.cornerRadius(300f) shouldBe (7.2f plusOrMinus 1e-4f)
        CARD_CORNER_PERCENT shouldBe (2.4f plusOrMinus 1e-5f)
        CARD_ASPECT shouldBe (CardSpec.ASPECT plusOrMinus 1e-7f)
    }

    @Test
    fun `the reserved metadata regions lie inside the card`() {
        // The text anchor lies in its region, the region in its exclusion zone, both inside the card
        val id = CardSpec.CARD_ID
        (CardSpec.CARD_ID_BASELINE in id.top..id.bottom) shouldBe true
        CardSpec.CARD_ID_EXCLUSION.intersect(id) shouldBe id
        CardSpec.PRINT_EXCLUSION.intersect(CardSpec.PRINT) shouldBe CardSpec.PRINT
        (CardSpec.PRINT_BASELINE in CardSpec.PRINT.top..CardSpec.PRINT.bottom) shouldBe true
        CardSpec.EXCLUSIONS.forEach { (it.right < CardSpec.WIDTH && it.bottom < CardSpec.HEIGHT) shouldBe true }
    }

    @Test
    fun `5 by 7 art fills the card, legacy art is fitted and centred, never stretched`() {
        CardSpec.isFullArt(1500f, 2100f) shouldBe true
        CardSpec.isFullArt(1000f, 1400f) shouldBe true
        CardSpec.isFullArt(1024f, 1536f) shouldBe false
        CardSpec.artRect(1000f, 1400f) shouldBe CardBox(0f, 0f, 1500f, 2100f)
        CardSpec.needsBackdrop(1000f, 1400f) shouldBe false
        // 2:3 (1024 × 1536): full height, 1400 wide, centred
        CardSpec.artRect(1024f, 1536f) shouldBe CardBox(50f, 0f, 1400f, 2100f)
        CardSpec.needsBackdrop(1024f, 1536f) shouldBe true
        // Wider than 5:7 (square): full width, centred vertically
        CardSpec.artRect(1000f, 1000f) shouldBe CardBox(0f, 300f, 1500f, 1500f)
        // Same aspect as the image in every case
        listOf(1024f to 1536f, 1000f to 1000f, 900f to 2000f).forEach { (w, h) ->
            val r = CardSpec.artRect(w, h)
            (r.width / r.height) shouldBe (w / h plusOrMinus 1e-4f)
        }
        // Scaled outputs keep the same geometry
        CardGeometry.editRect(1024f, 1536f, 300f, 420f) shouldBe CardBox(10f, 0f, 280f, 420f)
        // Missing size: the whole card
        CardSpec.artRect(0f, 0f) shouldBe CardBox(0f, 0f, 1500f, 2100f)
    }
}
