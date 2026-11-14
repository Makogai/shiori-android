package mihon.feature.friends.cards

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** README v7 requests and the pull function's answers. */
class CardRequestsTest {

    private val requests = CardRequests(FriendsJson)

    private val context = CardSeriesContext(
        seriesTitle = "Solo Leveling",
        sourceName = "Asura Scans",
        chapterName = "Chapter 41",
        chapterNumber = 41.0,
        genres = listOf("Action", "Fantasy"),
        seriesKey = "a".repeat(32),
    )

    private val cardJson = """
        {"id": "c1", "owner": "u1", "serial": 42, "grade": "E", "theme": "martial_arts",
         "effects": ["aura_glow", "foil_noise"], "card_name": "The Crimson Swordsman", "flavor": "Steel sings.",
         "stats": {"power": 70, "aura": 60, "drama": 50}, "accent_hex": "#C026D3",
         "scores": {"composition": 7.5, "emotion": 8, "action": 9, "iconic": 8.5, "art_quality": 7,
                    "quality": 8.0, "suggested_grade": "E", "base_grade": "L", "luck": "none", "character_known": false},
         "series_key": "${"a".repeat(32)}", "series_title": "Solo Leveling", "source_name": "Asura Scans",
         "chapter_name": "Chapter 41", "chapter_number": 41.0, "genres": ["Action"],
         "art_path": "u1/abc.jpg", "created_at": "2026-11-12T10:15:30.123456+00:00", "season": 1,
         "favorite": false, "frame_status": "pending", "frame_path": null, "frame_started_at": null,
         "frame_brief": "gold"}
    """.trimIndent()

    @Test
    fun `art goes to card-art under my lowercase id with a fresh lowercase uuid`() {
        val uuid = UUID.fromString("0A12E000-0000-4000-8000-0000000000AB")
        val path = requests.artPath("ABCDEF", uuid)
        path shouldBe "abcdef/0a12e000-0000-4000-8000-0000000000ab.jpg"
        requests.uploadArtPath(path) shouldBe "/storage/v1/object/card-art/$path"
    }

    @Test
    fun `pull sends the series context with chapter_number null when unknown`() {
        val call = requests.pull(CardPullRequest("u1/x.jpg", context))
        call.method shouldBe "POST"
        call.path shouldBe "/functions/v1/pull-card"
        val body = FriendsJson.parseToJsonElement(call.body!!).jsonObject
        body.keys shouldBe setOf(
            "art_path",
            "series_title",
            "source_name",
            "chapter_name",
            "chapter_number",
            "genres",
            "series_key",
        )
        body["art_path"]!!.jsonPrimitive.content shouldBe "u1/x.jpg"
        body["chapter_number"]!!.jsonPrimitive.content shouldBe "41.0"
        body["genres"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("Action", "Fantasy")

        val unnumbered = requests.pull(CardPullRequest("u1/x.jpg", context.copy(chapterNumber = null)))
        FriendsJson.parseToJsonElement(unnumbered.body!!).jsonObject["chapter_number"] shouldBe JsonNull
    }

    @Test
    fun `card_name is sent only when a name was typed`() {
        fun sent(name: String?) =
            FriendsJson.parseToJsonElement(requests.pull(CardPullRequest("u1/x.jpg", context, name)).body!!).jsonObject

        listOf(null, "", "   ", " \n\t ").forEach { blank ->
            sent(blank).containsKey("card_name") shouldBe false
            sent(blank)["art_path"]!!.jsonPrimitive.content shouldBe "u1/x.jpg"
        }
        val named = sent("  Sung Jinwoo ")
        named["card_name"]!!.jsonPrimitive.content shouldBe "Sung Jinwoo"
        named["series_key"]!!.jsonPrimitive.content shouldBe "a".repeat(32)
    }

    @Test
    fun `the card name is cut to the limit`() {
        val long = "a".repeat(40)
        CardRules.NAME_LIMIT shouldBe 28
        CardPullRequest("u1/x.jpg", context, long).cardName shouldBe "a".repeat(28)
        CardRules.normalizedName("b".repeat(27) + "  c") shouldBe "b".repeat(27)
        CardRules.limitedNameInput(long).length shouldBe 28
        CardRules.limitedNameInput("Ai\nHoshino") shouldBe "Ai Hoshino"
    }

    @Test
    fun `a pulled card decodes with its frame job`() {
        val result = requests.decodePull(
            """{"valid": true, "card": $cardJson, "pulls_today": 3, "daily_limit": 10, "frame_job": "client"}""",
        )
        result.shouldBeInstanceOf<CardPullResult.Pulled>()
        result.pullsToday shouldBe 3
        result.startFrame shouldBe true
        val card = result.card
        card.grade shouldBe CardGrade.EPIC
        card.theme shouldBe CardTheme.MARTIAL_ARTS
        card.serialText shouldBe "No. 0042"
        card.stats.total shouldBe 180
        card.scores!!.artQuality shouldBe 7.0
        card.frameStatus shouldBe CardFrameStatus.PENDING
        card.customFramePath shouldBe null
        card.seriesLine shouldBe "Solo Leveling · Ch. 41"

        val background = requests.decodePull(
            """{"valid": true, "card": $cardJson, "pulls_today": 4, "daily_limit": 10, "frame_job": "background"}""",
        )
        (background as CardPullResult.Pulled).startFrame shouldBe false
    }

    @Test
    fun `an invalid panel is not a card and says why`() {
        val result = requests.decodePull(
            """{"valid": false, "reason": "That's a menu screenshot, not a panel.", "pulls_today": 2, "daily_limit": 10}""",
        )
        result shouldBe CardPullResult.Invalid("That's a menu screenshot, not a panel.", 2)
        requests.decodePull("""{"valid": false}""") shouldBe
            CardPullResult.Invalid("That doesn't look like a panel.", 0)
    }

    @Test
    fun `429 is the daily limit, 502 the busy judge, anything else as the server put it`() {
        val limit = CardRequests.pullError(429, "Come back tomorrow (10/10 today)")
        limit.shouldBeInstanceOf<CardPullError.DailyLimit>()
        limit.message shouldBe "Come back tomorrow (10/10 today)"

        val busy = CardRequests.pullError(502, "The card judge is busy right now — try again in a minute.")
        busy.shouldBeInstanceOf<CardPullError.JudgeUnavailable>()
        busy.message shouldBe "The card judge is busy right now — try again in a minute."
        CardRequests.pullError(504, "HTTP 504").message shouldBe CardRequests.JUDGE_BUSY

        val other = CardRequests.pullError(403, "Join the Arena to pull cards.")
        other.shouldBeInstanceOf<CardPullError.Other>()
        other.message shouldBe "Join the Arena to pull cards."
    }

    @Test
    fun `album, my cards, one card and the feed`() {
        requests.cards(owner = null).let {
            it.method shouldBe "GET"
            it.path shouldBe "/rest/v1/cards?select=*&order=created_at.desc&limit=500"
        }
        requests.cards(owner = "me").path shouldBe "/rest/v1/cards?select=*&owner=eq.me&order=created_at.desc&limit=500"
        requests.card("c1").path shouldBe "/rest/v1/cards?id=eq.c1&select=*"
        requests.feed().let {
            it.method shouldBe "POST"
            it.path shouldBe "/rest/v1/rpc/card_feed"
            FriendsJson.parseToJsonElement(it.body!!).jsonObject["p_limit"]!!.jsonPrimitive.content shouldBe "50"
        }
        val cards = requests.decodeCards("[$cardJson]")
        cards.single().id shouldBe "c1"
        requests.decodeCards("[]") shouldBe emptyList()
    }

    @Test
    fun `favourite patches only favorite, delete removes my row and then the art`() {
        requests.favorite("c1", true).let {
            it.method shouldBe "PATCH"
            it.path shouldBe "/rest/v1/cards?id=eq.c1"
            it.prefer shouldBe "return=minimal"
            FriendsJson.parseToJsonElement(it.body!!).jsonObject.keys shouldBe setOf("favorite")
            it.body shouldBe """{"favorite":true}"""
        }
        val card = requests.decodeCards("[$cardJson]").single()
        val (row, art) = requests.delete(card, "u1")
        row.method shouldBe "DELETE"
        row.path shouldBe "/rest/v1/cards?id=eq.c1&owner=eq.u1"
        art.method shouldBe "DELETE"
        art.path shouldBe "/storage/v1/object/card-art/u1/abc.jpg"
    }

    @Test
    fun `images, frame start and today's count`() {
        requests.image(CardRequests.FRAME_BUCKET, "c1.png").path shouldBe
            "/storage/v1/object/authenticated/card-frames/c1.png"
        requests.startFrame("c1").let {
            it.path shouldBe "/functions/v1/frame-card"
            it.body shouldBe """{"card_id":"c1"}"""
        }
        requests.pullsToday("u1", "2026-11-12").path shouldBe
            "/rest/v1/card_pulls?select=count&user_id=eq.u1&day=eq.2026-11-12"
        requests.decodePullsToday("""[{"count": 3}]""") shouldBe 3
        requests.decodePullsToday("[]") shouldBe 0
    }

    @Test
    fun `a malformed answer throws`() {
        shouldThrow<Exception> { requests.decodePull("not json") }
    }

    @Test
    fun `the render goes to card-renders under my id and is recorded with only the render columns`() {
        val path = requests.renderPath("U1", "C1")
        path shouldBe "u1/c1.png"
        requests.uploadRenderPath(path) shouldBe "/storage/v1/object/card-renders/u1/c1.png"
        requests.recordRender("c1", path, Instant.parse("2026-11-12T10:00:00Z")).let {
            it.method shouldBe "PATCH"
            it.path shouldBe "/rest/v1/cards?id=eq.c1"
            it.prefer shouldBe "return=minimal"
            val body = FriendsJson.parseToJsonElement(it.body!!).jsonObject
            body.keys shouldBe setOf("render_path", "render_updated_at")
            body.getValue("render_path").jsonPrimitive.content shouldBe "u1/c1.png"
            body.getValue("render_updated_at").jsonPrimitive.content shouldBe "2026-11-12T10:00:00Z"
        }
    }

    @Test
    fun `render columns decode`() {
        val card = requests.decodeCards(
            "[" + cardJson.replace(
                "\"frame_brief\": \"gold\"",
                "\"frame_brief\": \"gold\", \"frame_ready_at\": \"2026-11-12T10:17:00+00:00\", " +
                    "\"render_path\": \"u1/c1.png\", \"render_updated_at\": \"2026-11-12T10:16:00.5+00:00\"",
            ) + "]",
        ).single()
        card.renderPath shouldBe "u1/c1.png"
        card.renderUpdatedAt shouldBe "2026-11-12T10:16:00.5+00:00"
        card.frameReadyAt shouldBe "2026-11-12T10:17:00+00:00"
        requests.decodeCards("[$cardJson]").single().renderPath shouldBe null
    }
}
