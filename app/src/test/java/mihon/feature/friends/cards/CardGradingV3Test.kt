package mihon.feature.friends.cards

import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.math.abs

/** Grading pipeline v3: the optional rank fields in `scores`, the rank progress line and the reveal's roulette. */
class CardGradingV3Test {

    private val requests = CardRequests(FriendsJson)

    private fun decode(grade: String, scores: String): ShioriCard =
        requests.decodeCards("""[{"id":"c1","owner":"u1","grade":"$grade","scores":$scores}]""").single()

    @Test
    fun `old scores decode without the v3 fields`() {
        val card =
            decode("R", """{"composition":7,"emotion":6.5,"art_quality":8,"suggested_grade":"R","luck":"none"}""")
        val scores = card.scores!!
        scores.composition shouldBe 7.0
        scores.artQuality shouldBe 8.0
        scores.story shouldBe null
        scores.rankScore shouldBe null
        scores.pipelineVersion shouldBe null
        CardRankProgress.of(card) shouldBe null
    }

    @Test
    fun `v3 scores decode`() {
        val card = decode(
            "E",
            """{"composition":7,"story":8.5,"rank_score":68.4,"next_rank_score":76,"points_to_next_rank":7.6,""" +
                """"rank_progress":0.457,"next_grade":"L","rank_held_by":null,"pipeline_version":3}""",
        )
        val scores = card.scores!!
        scores.story shouldBe 8.5
        scores.rankScore shouldBe 68.4
        scores.nextRankScore shouldBe 76.0
        scores.pointsToNextRank shouldBe 7.6
        scores.rankProgress shouldBe 0.457
        scores.nextGrade shouldBe "L"
        scores.rankHeldBy shouldBe null
        scores.pipelineVersion shouldBe 3
    }

    @Test
    fun `a normal card shows the points to the next rank`() {
        val card = decode(
            "E",
            """{"rank_score":68.4,"next_rank_score":76,"points_to_next_rank":7.6,"rank_progress":0.457,"next_grade":"L"}""",
        )
        val rank = CardRankProgress.of(card)!!
        rank.grade.rank shouldBe "B"
        rank.status shouldBe CardRankStatus.Next(7.6, CardGrade.LEGENDARY)
        (rank.status as CardRankStatus.Next).next.rank shouldBe "A"
        CardRankProgress.format(rank.score, Locale.ROOT) shouldBe "68.4"
        CardRankProgress.format(7.6, Locale.ROOT) shouldBe "7.6"
        (abs(rank.progress - 0.457f) < 0.0001f) shouldBe true
    }

    @Test
    fun `SR is the top rank`() {
        val card = decode(
            "S",
            """{"rank_score":96.2,"next_rank_score":100,"points_to_next_rank":0,"rank_progress":0.37,"next_grade":null}""",
        )
        CardRankProgress.of(card)!!.status shouldBe CardRankStatus.Top
    }

    @Test
    fun `a held card says so instead of points`() {
        val card = decode(
            "M",
            """{"rank_score":95.1,"next_rank_score":100,"points_to_next_rank":0,"rank_progress":1,""" +
                """"next_grade":"S","rank_held_by":"S30"}""",
        )
        CardRankProgress.of(card)!!.status shouldBe CardRankStatus.Held(CardGrade.MYTHIC)
    }

    @Test
    fun `missing progress falls back to the rank's band`() {
        CardRankProgress.bandProgress(CardGrade.EPIC, 69.0) shouldBe 0.5
        CardRankProgress.bandProgress(CardGrade.SECRET, 100.0) shouldBe 1.0
    }

    @Test
    fun `the roulette ends on the card's rank and slows down`() {
        CardGrade.entries.forEach { grade ->
            val ticks = CardRankRoulette.ticks(grade)
            ticks.last().grade shouldBe grade
            ticks.first().grade shouldBe CardGrade.COMMON
            // Each tick is the next rank up (wrapping after SR): no jumps
            val steps = ticks.zipWithNext().all { (a, b) ->
                b.grade.ordinal == (a.grade.ordinal + 1) % CardGrade.entries.size
            }
            steps shouldBe true
            ticks.zipWithNext().all { (a, b) -> b.delayMs >= a.delayMs } shouldBe true
            (abs(ticks.sumOf { it.delayMs } - CardRankRoulette.TOTAL_MS) <= ticks.size) shouldBe true
        }
    }
}
