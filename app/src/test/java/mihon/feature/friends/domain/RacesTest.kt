package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** README v6, "Same-series races". */
class RacesTest {

    private fun row(key: String, user: String, number: Double?, at: String, title: String = "Solo Leveling") =
        RaceRow(key, user, title, null, number, null, at, 10)

    @Test
    fun `a race needs both of us with chapter numbers, most recent first`() {
        val rows = listOf(
            row("a", "me", 40.0, "2026-11-12T10:00:00+00:00"),
            row("a", "filip", 37.0, "2026-11-12T08:00:00+00:00"),
            row("b", "me", 5.0, "2026-11-10T10:00:00+00:00", "Berserk"),
            row("b", "filip", 9.0, "2026-11-12T11:00:00+00:00", "Berserk"),
            // Filip has no number here
            row("c", "me", 3.0, "2026-11-12T12:00:00+00:00", "Vagabond"),
            row("c", "filip", null, "2026-11-12T12:00:00+00:00", "Vagabond"),
            // I'm not in this one
            row("d", "ana", 3.0, "2026-11-12T12:00:00+00:00", "Monster"),
            row("d", "filip", 4.0, "2026-11-12T12:00:00+00:00", "Monster"),
        )
        val races = Races.build(rows, "me")
        races.map { it.seriesKey } shouldContainExactly listOf("b", "a")
        races[0].leader shouldBe RaceLeader.THEM
        races[0].gap shouldBe 4.0
        races[1].leader shouldBe RaceLeader.ME
    }

    @Test
    fun `one race per other player`() {
        val rows = listOf(
            row("a", "me", 40.0, "2026-11-12T10:00:00+00:00"),
            row("a", "filip", 40.0, "2026-11-12T09:00:00+00:00"),
            row("a", "ana", 41.0, "2026-11-12T11:00:00+00:00"),
        )
        Races.build(rows, "me").map { it.rivalId } shouldContainExactly listOf("ana", "filip")
    }

    @Test
    fun `headlines and badges`() {
        val ahead = Race(row("a", "me", 40.0, "x"), row("a", "filip", 37.0, "x"))
        Races.headline(ahead, "Filip") shouldBe "🏁 You're 3 chapters ahead in Solo Leveling"
        Races.badge(ahead, "Filip") shouldBe "You +3"
        val behind = Race(row("a", "me", 40.0, "x"), row("a", "filip", 41.0, "x"))
        Races.headline(behind, "Filip") shouldBe "🏁 Filip is 1 chapter ahead in Solo Leveling"
        Races.badge(behind, "Filip") shouldBe "Filip +1"
        val tied = Race(row("a", "me", 40.0, "x"), row("a", "filip", 40.0, "x"))
        Races.headline(tied, "Filip") shouldBe "🏁 Neck and neck in Solo Leveling at Ch. 40"
        Races.badge(tied, "Filip") shouldBe "Tied"
        val half = Race(row("a", "me", 40.5, "x"), row("a", "filip", 40.0, "x"))
        Races.headline(half, "Filip") shouldBe "🏁 You're 0.5 chapters ahead in Solo Leveling"
    }
}
