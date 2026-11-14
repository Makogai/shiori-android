package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** README v6, "Overtake alerts". */
class OvertakesTest {

    private fun today(day: String, me: Double, filip: Double) =
        OvertakeBoard(OvertakeMetric.TODAY, day, mapOf("me" to me, "filip" to filip))

    private fun check(state: OvertakeState, vararg boards: OvertakeBoard) = Overtakes.check(
        state,
        boards.toList(),
        "me",
    )

    @Test
    fun `the first check only records`() {
        val (state, events) = check(OvertakeState(), today("2026-11-12", 3.0, 5.0))
        events.shouldBeEmpty()
        state shouldBe OvertakeState(initialized = true, ahead = setOf("today:2026-11-12:filip"))
    }

    @Test
    fun `an event is ahead going from false to true, once`() {
        val (s1, e1) = check(OvertakeState(), today("2026-11-12", 3.0, 2.0))
        e1.shouldBeEmpty()
        val (s2, e2) = check(s1, today("2026-11-12", 7.0, 8.0))
        e2.map { it.userId } shouldContainExactly listOf("filip")
        e2.single().theirs shouldBe 8.0
        e2.single().mine shouldBe 7.0
        // Still ahead: nothing new
        val (s3, e3) = check(s2, today("2026-11-12", 7.0, 9.0))
        e3.shouldBeEmpty()
        // I retake, then they pass again: a second event
        val (s4, e4) = check(s3, today("2026-11-12", 10.0, 9.0))
        e4.shouldBeEmpty()
        val (_, e5) = check(s4, today("2026-11-12", 10.0, 11.0))
        e5.size shouldBe 1
    }

    @Test
    fun `equal is not ahead`() {
        val (s1, _) = check(OvertakeState(), today("2026-11-12", 3.0, 2.0))
        check(s1, today("2026-11-12", 3.0, 3.0)).second.shouldBeEmpty()
    }

    @Test
    fun `a new period starts not ahead, so a friend already ahead did pass you`() {
        val (s1, _) = check(OvertakeState(), today("2026-11-12", 3.0, 5.0))
        val (s2, events) = check(s1, today("2026-11-13", 0.0, 1.0))
        events.single().periodKey shouldBe "2026-11-13"
        // Only current keys are kept
        s2.ahead shouldBe setOf("today:2026-11-13:filip")
    }

    @Test
    fun `challenge and season boards with their texts`() {
        val challenge = OvertakeBoard(
            OvertakeMetric.CHALLENGE,
            "7",
            mapOf("me" to 12.0, "filip" to 14.0),
            label = "Night Owl Week",
            emoji = "🦉",
        )
        val season = OvertakeBoard(
            OvertakeMetric.SEASON,
            "1",
            mapOf("me" to 1200.0, "filip" to 1240.0),
            label = Overtakes.seasonLabel(1),
        )
        val (_, events) = check(OvertakeState(initialized = true), today("2026-11-12", 7.0, 8.0), challenge, season)
        events.map { Overtakes.body(it, "Filip") } shouldContainExactly listOf(
            "Filip just passed you today — 8 vs 7 chapters 📚",
            "Filip just passed you in Night Owl Week — 14 vs 12 🦉",
            "Filip just passed you in Season 1 — 1240 vs 1200 XP 👑",
        )
        events.map { it.key }.distinct().size shouldBe 3
    }

    @Test
    fun `scores print as integers when whole, else with one decimal`() {
        Overtakes.formatScore(34.0) shouldBe "34"
        Overtakes.formatScore(34.5) shouldBe "34.5"
        Overtakes.formatScore(34.25) shouldBe "34.3"
    }
}
