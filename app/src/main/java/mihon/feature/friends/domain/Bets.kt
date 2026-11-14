package mihon.feature.friends.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** A row of `bets` (README v6, "Weekly bets"): a player's stake on winning one weekly challenge. */
@Serializable
data class Bet(
    @SerialName("user_id") val userId: String,
    @SerialName("challenge_id") val challengeId: Int,
    val stake: Int,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class BetInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("challenge_id") val challengeId: Int,
    val stake: Int,
)

/** How an ended challenge came out, as settlement needs it. */
data class ChallengeOutcome(val challengeId: Int, val end: Instant, val winners: Set<String>)

/** A bet on an ended challenge: [result] is +stake when won, −stake otherwise. */
data class SettledBet(
    val userId: String,
    val challengeId: Int,
    val stake: Int,
    val result: Long,
    val end: Instant,
) {
    val won: Boolean get() = result > 0
}

object Bets {

    const val MIN_STAKE = 10
    const val MAX_STAKE = 100
    const val STAKE_STEP = 10
    const val DEFAULT_STAKE = 50

    val STAKES: List<Int> = (MIN_STAKE..MAX_STAKE step STAKE_STEP).toList()

    private val MAX_LENGTH: Duration = Duration.ofDays(7)

    fun validStake(stake: Int): Boolean = stake in MIN_STAKE..MAX_STAKE && stake % STAKE_STEP == 0

    /** A weekly challenge lasts at most 7 days (the Grand Opening and the weekly rotation; not monthly specials). */
    fun isWeekly(start: Instant, end: Instant): Boolean = end > start && Duration.between(start, end) <= MAX_LENGTH

    /** Halfway through the challenge; bets close then. */
    fun closesAt(start: Instant, end: Instant): Instant = start.plus(Duration.between(start, end).dividedBy(2))

    /** Weekly, started, and before its halfway point (the server's `bet_open` with its own clock). */
    fun betable(start: Instant, end: Instant, now: Instant): Boolean =
        isWeekly(start, end) && now >= start && now < closesAt(start, end)

    /** +stake when [userId] is among the winners (ties share), otherwise −stake, including when nobody scored. */
    fun result(stake: Int, userId: String, winners: Set<String>): Long =
        if (userId in winners) stake.toLong() else -stake.toLong()

    /** Bets whose challenge has ended (an outcome is known), newest end first. */
    fun settle(bets: List<Bet>, outcomes: Map<Int, ChallengeOutcome>): List<SettledBet> =
        bets.mapNotNull { bet ->
            val outcome = outcomes[bet.challengeId] ?: return@mapNotNull null
            SettledBet(
                bet.userId,
                bet.challengeId,
                bet.stake,
                result(bet.stake, bet.userId, outcome.winners),
                outcome.end,
            )
        }.sortedWith(compareByDescending<SettledBet> { it.end }.thenBy { it.challengeId })
}
