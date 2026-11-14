package mihon.feature.friends.data

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.feature.friends.domain.CommentInsert
import mihon.feature.friends.domain.RaceRow
import mihon.feature.friends.domain.ReactionUpsert
import org.junit.jupiter.api.Test
import java.time.Instant

/** README v6 requests: reactions, comments, races and bets. */
class SocialRequestsTest {

    private val requests = SocialRequests(FriendsJson)

    @Test
    fun `loads reactions and comments for at most 100 distinct reads per request`() {
        val ids = (0 until 150).map { "%032x".format(it) }
        val reactions = requests.reactions(ids + ids.take(10))
        reactions.size shouldBe 2
        reactions.first().method shouldBe "GET"
        reactions.first().path shouldContain
            "/rest/v1/feed_reactions?select=owner_id,client_id,reactor_id,emoji,created_at&client_id=in.(${ids[0]},"
        val comments = requests.comments(ids)
        comments.size shouldBe 2
        comments.first().path shouldContain
            "/rest/v1/feed_comments?select=id,owner_id,client_id,author_id,body,created_at"
        comments.first().path shouldContain "&order=created_at"
        requests.reactions(emptyList()) shouldBe emptyList()
    }

    @Test
    fun `react upserts on the reaction key and unreact deletes my own`() {
        val react = requests.react(ReactionUpsert("filip", "c1", "me", "🔥"))
        react.method shouldBe "POST"
        react.path shouldBe "/rest/v1/feed_reactions?on_conflict=owner_id,client_id,reactor_id"
        react.prefer shouldBe "resolution=merge-duplicates,return=minimal"
        val body = FriendsJson.parseToJsonElement(react.body!!).jsonObject
        body.keys shouldBe setOf("owner_id", "client_id", "reactor_id", "emoji")
        body["emoji"]!!.jsonPrimitive.content shouldBe "🔥"
        requests.unreact("filip", "c1", "me").let {
            it.method shouldBe "DELETE"
            it.path shouldBe "/rest/v1/feed_reactions?owner_id=eq.filip&client_id=eq.c1&reactor_id=eq.me"
        }
    }

    @Test
    fun `comments are trimmed, returned, and deleted only by their author`() {
        val call = requests.comment(CommentInsert("filip", "c1", "me", "  so good "))
        call.path shouldBe "/rest/v1/feed_comments"
        call.prefer shouldBe "return=representation"
        FriendsJson.parseToJsonElement(call.body!!).jsonObject["body"]!!.jsonPrimitive.content shouldBe "so good"
        requests.deleteComment("id1", "me").path shouldBe "/rest/v1/feed_comments?id=eq.id1&author_id=eq.me"
    }

    @Test
    fun `new reactions and comments on my reads after the cursor`() {
        val cursor = "2026-11-12T10:00:00.123456Z"
        requests.newReactions("me", cursor).path shouldBe
            "/rest/v1/feed_reactions?select=owner_id,client_id,reactor_id,emoji,created_at," +
            "reads(series_title,chapter_number,chapter_name)" +
            "&owner_id=eq.me&reactor_id=neq.me&created_at=gt.2026-11-12T10%3A00%3A00.123456Z&order=created_at&limit=50"
        requests.newComments("me", cursor).path shouldBe
            "/rest/v1/feed_comments?select=id,owner_id,client_id,author_id,body,created_at," +
            "reads(series_title,chapter_number,chapter_name)" +
            "&owner_id=eq.me&author_id=neq.me&created_at=gt.2026-11-12T10%3A00%3A00.123456Z&order=created_at&limit=50"
    }

    @Test
    fun `races and bets`() {
        requests.races().let {
            it.method shouldBe "POST"
            it.path shouldBe "/rest/v1/rpc/arena_races"
            it.body shouldBe "{}"
        }
        requests.bets().path shouldBe "/rest/v1/bets?select=user_id,challenge_id,stake,created_at&order=created_at"
        val bet = requests.placeBet("me", 7, 50)
        bet.method shouldBe "POST"
        bet.path shouldBe "/rest/v1/bets"
        bet.prefer shouldBe "return=representation"
        val body = FriendsJson.parseToJsonElement(bet.body!!).jsonObject
        body.keys shouldBe setOf("user_id", "challenge_id", "stake")
        body["challenge_id"]!!.jsonPrimitive.content shouldBe "7"
        body["stake"]!!.jsonPrimitive.content shouldBe "50"
    }

    @Test
    fun `decodes race rows and feed rows with client ids`() {
        val races = """
            [{"series_key":"k","user_id":"u","series_title":"Solo Leveling","cover_url":null,
              "chapter_number":40,"chapter_name":"Ch 40","last_read_at":"2026-11-12T10:00:00+00:00","chapters":12}]
        """.trimIndent()
        val race = FriendsJson.decodeFromString(ListSerializer(RaceRow.serializer()), races).single()
        race.chapterNumber shouldBe 40.0
        race.lastRead shouldBe Instant.parse("2026-11-12T10:00:00Z")

        val feed = """
            [{"user_id":"u","read_at":"2026-11-12T10:00:00+00:00","series_title":"A","client_id":"c1"},
             {"user_id":"u","read_at":"2026-11-12T09:00:00+00:00","series_title":"B"}]
        """.trimIndent()
        val rows = FriendsJson.decodeFromString(ListSerializer(FeedRow.serializer()), feed)
        rows.map { it.readKey?.clientId } shouldContainExactly listOf("c1", null)
    }
}
