package mihon.feature.recommendations

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RecommendationTitlesTest {

    @Test
    fun `keeps the preferred title and adds a different English one`() {
        recommendationTitles("Shingeki no Kyojin", "Shingeki no Kyojin", "Attack on Titan") shouldBe
            ("Shingeki no Kyojin" to "Attack on Titan")
    }

    @Test
    fun `drops the English title when it matches apart from case or is missing`() {
        recommendationTitles("One Piece", null, "ONE PIECE") shouldBe ("One Piece" to null)
        recommendationTitles("Monster", null, " monster ") shouldBe ("Monster" to null)
        recommendationTitles("Berserk", "Berserk", null) shouldBe ("Berserk" to null)
        recommendationTitles("Vagabond", null, "  ") shouldBe ("Vagabond" to null)
    }

    @Test
    fun `falls back to romaji, then English, else nothing`() {
        recommendationTitles(null, "Kimetsu no Yaiba", "Demon Slayer") shouldBe ("Kimetsu no Yaiba" to "Demon Slayer")
        recommendationTitles(" ", null, "Demon Slayer") shouldBe ("Demon Slayer" to null)
        recommendationTitles(null, null, null) shouldBe null
    }
}
