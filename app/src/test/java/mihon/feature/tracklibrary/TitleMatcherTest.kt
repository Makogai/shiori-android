package mihon.feature.tracklibrary

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleMatcherTest {

    private fun sim(a: String, b: String) = TitleMatcher.similarity(a, b)

    @Test
    fun `case, punctuation, diacritics and spacing don't matter`() {
        sim("Dr. STONE", "Dr. Stone") shouldBe 100
        sim("Pokémon Adventures", "Pokemon Adventures") shouldBe 100
        sim("Frieren: Beyond Journey's End", "Frieren - Beyond Journey’s End") shouldBe 100
        sim("One Punch-Man", "Onepunch-Man") shouldBe 100
        sim("Spy×Family", "SPY x FAMILY") shouldBe 100
        sim("Kaguya-sama!!", "kaguya sama") shouldBe 100
        sim("Romeo & Juliet", "Romeo and Juliet") shouldBe 100
    }

    @Test
    fun `a leading article is ignored, word order too`() {
        sim("The Apothecary Diaries", "Apothecary Diaries") shouldBe 100
        sim("The Promised Neverland", "Promised Neverland, The") shouldBe 100
        // Only a leading "a" goes, so a one-letter title still compares
        sim("A", "A") shouldBe 100
    }

    @Test
    fun `romanisation variants fold together`() {
        sim("Shingeki no Kyojin", "Shingeki no Kyoujin") shouldBe 100
        sim("Kyōkai no Kanata", "Kyoukai no Kanata") shouldBe 100
        sim("Shōjo Shūmatsu Ryokō", "Shoujo Shuumatsu Ryokou") shouldBe 100
        sim("Ore wo Suki nano wa Omae dake ka yo", "Ore o Suki nano wa Omae dake ka yo") shouldBe 100
    }

    @Test
    fun `a subtitle on one side is a likely match but not a strong one`() {
        val subtitle = sim("Mushoku Tensei", "Mushoku Tensei: Isekai Ittara Honki Dasu")
        subtitle shouldBeLessThan TitleMatcher.STRONG_MATCH
        subtitle shouldBeGreaterThanOrEqual 50
        // Could be the sequel: never confirmed in bulk
        sim("Solo Leveling", "Solo Leveling: Ragnarok") shouldBeLessThan TitleMatcher.STRONG_MATCH
    }

    @Test
    fun `season numbers must agree`() {
        sim("Tower of God Season 2", "Tower of God 2nd Season") shouldBe 100
        sim("Overlord II", "Overlord 2") shouldBe 100
        sim("Tower of God Season 2", "Tower of God Season 3") shouldBeLessThanOrEqual 60
        sim("Tower of God", "Tower of God Season 2") shouldBeLessThanOrEqual 80
        sim("Kaguya-sama Love Is War", "Kaguya-sama Love Is War Part 2") shouldBeLessThan TitleMatcher.STRONG_MATCH
        sim("Mob Psycho 100", "Mob Psycho 100") shouldBe 100
    }

    @Test
    fun `small spelling differences stay strong, different series don't`() {
        sim("Boku no Hero Academia", "Boku no Hero Akademia") shouldBeGreaterThanOrEqual TitleMatcher.STRONG_MATCH
        sim("Berserk", "Vagabond") shouldBeLessThan 50
        sim("Naruto", "Boruto: Naruto Next Generations") shouldBeLessThan TitleMatcher.STRONG_MATCH
        sim("", "Naruto") shouldBe 0
        sim("!!!", "Naruto") shouldBe 0
    }

    @Test
    fun `an exact alternative title scores 100, a fuzzy one is discounted`() {
        TitleMatcher.score(
            ours = listOf("The Apothecary Diaries"),
            candidate = listOf("Kusuriya no Hitorigoto", "The Apothecary Diaries"),
        ) shouldBe 100
        TitleMatcher.score(
            ours = listOf("The Apothecary Diaries", "Kusuriya no Hitorigoto"),
            candidate = listOf("Kusuriya no Hitorigoto"),
        ) shouldBe 100

        // Shared numbers with the iOS implementation: set ratio 100, sort ratio 86.27 -> 93; through an alt -> 88
        TitleMatcher.score(listOf("Kusuriya no Hitorigoto"), listOf("Kusuriya no Hitorigoto Maomao")) shouldBe 93
        TitleMatcher.score(
            ours = listOf("Something Else", "Kusuriya no Hitorigoto"),
            candidate = listOf("Kusuriya no Hitorigoto Maomao"),
        ) shouldBe 88
    }

    @Test
    fun `strong match threshold is 90`() {
        TitleMatcher.isStrong(90) shouldBe true
        TitleMatcher.isStrong(89) shouldBe false
        TitleMatcher.isStrong(100) shouldBe true
    }

    @Test
    fun `alternative titles come from the description`() {
        val description = """
            A pharmacist in the palace.

            Alternative Titles: 薬屋のひとりごと; Kusuriya no Hitorigoto, The Apothecary Diaries
            Associated Names: Drugstore Soliloquy
        """.trimIndent()
        TitleMatcher.altTitlesFromDescription(description, "The Apothecary Diaries") shouldContainExactly listOf(
            "薬屋のひとりごと",
            "Kusuriya no Hitorigoto",
            "Drugstore Soliloquy",
        )
        TitleMatcher.altTitlesFromDescription("Just a summary.", "X") shouldBe emptyList()
        TitleMatcher.altTitlesFromDescription(null, "X") shouldBe emptyList()
    }

    @Test
    fun `search queries try the title, the title without subtitle, then alternative titles`() {
        val queries = TitleMatcher.searchQueries(
            title = "Mushoku Tensei: Jobless Reincarnation",
            altTitles = listOf("無職転生", "Mushoku Tensei"),
        )
        queries shouldContainExactly listOf("Mushoku Tensei: Jobless Reincarnation", "Mushoku Tensei", "無職転生")
        TitleMatcher.searchQueries("Re:Zero kara Hajimeru Isekai Seikatsu", emptyList()) shouldContainExactly
            listOf("Re:Zero kara Hajimeru Isekai Seikatsu")
        TitleMatcher.searchQueries("Berserk (Official Colored)", emptyList()) shouldContainExactly
            listOf("Berserk (Official Colored)", "Berserk")
    }
}
