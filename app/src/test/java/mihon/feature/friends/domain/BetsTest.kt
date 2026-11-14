package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** README v6, "Weekly bets": betable window, settlement and XP with bets. */
class BetsTest {

    private val start = Instant.parse("2026-11-15T23:00:00Z")
    private val end = Instant.parse("2026-11-22T23:00:00Z")

    @Test
    fun `stakes are 10 to 100 in steps of 10 with 50 by default`() {
        Bets.STAKES shouldContainExactly listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        Bets.DEFAULT_STAKE shouldBe 50
        Bets.validStake(10) shouldBe true
        Bets.validStake(100) shouldBe true
        Bets.validStake(5) shouldBe false
        Bets.validStake(55) shouldBe false
        Bets.validStake(110) shouldBe false
    }

    @Test
    fun `only weekly challenges that started are betable until halfway`() {
        Bets.closesAt(start, end) shouldBe Instant.parse("2026-11-19T11:00:00Z")
        Bets.betable(start, end, start.minusSeconds(1)) shouldBe false
        Bets.betable(start, end, start) shouldBe true
        Bets.betable(start, end, Instant.parse("2026-11-19T10:59:59Z")) shouldBe true
        Bets.betable(start, end, Instant.parse("2026-11-19T11:00:00Z")) shouldBe false
        // A monthly special is not a week
        val monthEnd = Instant.parse("2026-12-31T23:00:00Z")
        Bets.isWeekly(Instant.parse("2026-11-30T23:00:00Z"), monthEnd) shouldBe false
        Bets.betable(Instant.parse("2026-11-30T23:00:00Z"), monthEnd, Instant.parse("2026-12-01T12:00:00Z")) shouldBe
            false
        // Exactly 7 days still counts
        Bets.isWeekly(start, start.plusSeconds(7 * 86_400)) shouldBe true
        Bets.isWeekly(start, start.plusSeconds(7 * 86_400 + 1)) shouldBe false
    }

    @Test
    fun `winners get the stake, everyone else loses it, also when nobody scored`() {
        Bets.result(50, "me", setOf("me")) shouldBe 50
        Bets.result(50, "me", setOf("me", "rival")) shouldBe 50
        Bets.result(30, "me", setOf("rival")) shouldBe -30
        Bets.result(30, "me", emptySet()) shouldBe -30
    }

    @Test
    fun `settles only bets on ended challenges, newest first`() {
        val bets = listOf(Bet("me", 1, 50), Bet("rival", 1, 20), Bet("me", 2, 70), Bet("me", 3, 10))
        val outcomes = mapOf(
            1 to ChallengeOutcome(1, Instant.parse("2026-11-15T23:00:00Z"), setOf("me")),
            2 to ChallengeOutcome(2, Instant.parse("2026-11-22T23:00:00Z"), emptySet()),
        )
        Bets.settle(bets, outcomes) shouldContainExactly listOf(
            SettledBet("me", 2, 70, -70, Instant.parse("2026-11-22T23:00:00Z")),
            SettledBet("me", 1, 50, 50, Instant.parse("2026-11-15T23:00:00Z")),
            SettledBet("rival", 1, 20, -20, Instant.parse("2026-11-15T23:00:00Z")),
        )
    }

    @Test
    fun `lifetime xp adds bets and is shown clamped at zero`() {
        Xp.total(readsXp = 100, challengeWins = 1, questsCompleted = 2, bets = -50) shouldBe 100 + 250 + 100 - 50
        Xp.display(-40) shouldBe 0
        Xp.display(40) shouldBe 40
        Levels.of(Xp.display(Xp.total(20, 0, 0, -100))).level shouldBe 0
    }

    @Test
    fun `xp history adds bet results on their day and plots clamped values`() {
        val day = LocalDate.of(2026, 11, 12)
        val history = Xp.history(mapOf(day to 30L, day.plusDays(3) to -100L, day.plusDays(4) to 120L))
        history shouldContainExactly listOf(
            day.minusDays(1) to 0L,
            day to 30L,
            day.plusDays(3) to 0L,
            day.plusDays(4) to 50L,
        )
        Xp.history(emptyMap()) shouldBe emptyList()
    }
}
