package mihon.feature.friends.data

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.time.Instant

class SuggestionsTest {

    private val requests = SuggestionRequests(FriendsJson)

    private fun suggestion(
        id: String,
        from: String,
        to: String,
        createdAt: String,
        status: SuggestionStatus = SuggestionStatus.PENDING,
        sourceId: String = "2499283573021220255",
        mangaUrl: String = "/manga/$id",
        respondedAt: String? = null,
    ) = Suggestion(
        id = id,
        fromUser = from,
        toUser = to,
        sourceId = sourceId,
        sourceName = "MangaDex",
        mangaUrl = mangaUrl,
        title = "Title $id",
        status = status,
        createdAt = createdAt,
        respondedAt = respondedAt,
    )

    @Test
    fun `decodes a server row keeping the 64-bit source id exact`() {
        val text = """
            [{"id":"a1","from_user":"kai","to_user":"me","source_id":"9223372036854775807",
              "source_name":"MangaDex","manga_url":"/title/1","title":"One Piece",
              "cover_url":"https://x/c.jpg","genres":["Action","Adventure"],"note":null,
              "status":"added","created_at":"2026-11-12T10:00:00.123456+00:00",
              "responded_at":"2026-11-12T11:00:00+00:00","extra":1}]
        """.trimIndent()
        val row = FriendsJson.decodeFromString(ListSerializer(Suggestion.serializer()), text).single()
        row.sourceId shouldBe "9223372036854775807"
        row.sourceId.toLong() shouldBe Long.MAX_VALUE
        row.status shouldBe SuggestionStatus.ADDED
        row.genres shouldContainExactly listOf("Action", "Adventure")
        row.note shouldBe null
        row.created shouldBe Instant.parse("2026-11-12T10:00:00.123456Z")
        row.responded shouldBe Instant.parse("2026-11-12T11:00:00Z")
    }

    @Test
    fun `send is an upsert of one row per friend with every key present`() {
        val rows = SuggestionRules.inserts(
            me = "me",
            friends = listOf("kai", "me", "kai", "ana"),
            sourceId = 2499283573021220255,
            sourceName = "  ",
            mangaUrl = "/series/one-piece",
            title = " One Piece ",
            coverUrl = "content://cover",
            genres = listOf(" Action ", "", "  ") + (1..20).map { "G$it" },
            note = "   ",
        )
        val call = requests.send(rows)
        call.method shouldBe "POST"
        call.path shouldBe "/rest/v1/suggestions?on_conflict=from_user,to_user,source_id,manga_url"
        call.prefer shouldBe "resolution=merge-duplicates,return=representation"

        val body = FriendsJson.parseToJsonElement(call.body!!).jsonArray
        body.map { it.jsonObject["to_user"] } shouldContainExactly listOf(JsonPrimitive("kai"), JsonPrimitive("ana"))
        val first = body.first().jsonObject
        first.keys shouldBe setOf(
            "from_user", "to_user", "source_id", "source_name", "manga_url", "title", "cover_url", "genres", "note",
        )
        first["from_user"] shouldBe JsonPrimitive("me")
        first["source_id"] shouldBe JsonPrimitive("2499283573021220255")
        first["source_name"] shouldBe JsonNull
        first["manga_url"] shouldBe JsonPrimitive("/series/one-piece")
        first["title"] shouldBe JsonPrimitive("One Piece")
        first["cover_url"] shouldBe JsonNull
        first["note"] shouldBe JsonNull
        val genres = first["genres"] as JsonArray
        genres.size shouldBe 12
        genres.first() shouldBe JsonPrimitive("Action")
    }

    @Test
    fun `note is trimmed, capped at 200 and empty is null`() {
        SuggestionRules.note(null) shouldBe null
        SuggestionRules.note("  ") shouldBe null
        SuggestionRules.note("  read it!  ") shouldBe "read it!"
        SuggestionRules.note("x".repeat(250))!!.length shouldBe 200
    }

    @Test
    fun `only http and https covers are sent`() {
        SuggestionRules.coverUrl("https://a/b.jpg") shouldBe "https://a/b.jpg"
        SuggestionRules.coverUrl("HTTP://a/b.jpg") shouldBe "HTTP://a/b.jpg"
        SuggestionRules.coverUrl("file:///sdcard/c.jpg") shouldBe null
        SuggestionRules.coverUrl(null) shouldBe null
    }

    @Test
    fun `list, answer, auto-mark and withdraw requests match the contract`() {
        requests.list() shouldBe RestCall("GET", "/rest/v1/suggestions?select=*&order=created_at.desc&limit=500")

        val answer = requests.answer("s1", "me", SuggestionStatus.DISMISSED)
        answer.method shouldBe "PATCH"
        answer.path shouldBe "/rest/v1/suggestions?id=eq.s1&to_user=eq.me"
        answer.prefer shouldBe "return=representation"
        FriendsJson.parseToJsonElement(answer.body!!) shouldBe JsonObject(mapOf("status" to JsonPrimitive("dismissed")))

        val mark = requests.markAdded(listOf("s1", "s2"), "me")
        mark.method shouldBe "PATCH"
        mark.path shouldBe "/rest/v1/suggestions?id=in.(s1,s2)&to_user=eq.me&status=eq.pending"
        mark.prefer shouldBe null
        FriendsJson.parseToJsonElement(mark.body!!) shouldBe JsonObject(mapOf("status" to JsonPrimitive("added")))

        requests.withdraw("s1", "me") shouldBe
            RestCall("DELETE", "/rest/v1/suggestions?id=eq.s1&from_user=eq.me&status=eq.pending")
    }

    private val all = listOf(
        suggestion("old-pending", "kai", "me", "2026-11-10T10:00:00Z"),
        suggestion("new-added", "kai", "me", "2026-11-12T10:00:00Z", SuggestionStatus.ADDED),
        suggestion("mid-pending", "ana", "me", "2026-11-11T10:00:00+00:00"),
        suggestion("dismissed", "kai", "me", "2026-11-12T09:00:00Z", SuggestionStatus.DISMISSED),
        suggestion(
            "sent-added",
            "me",
            "kai",
            "2026-11-09T10:00:00Z",
            SuggestionStatus.ADDED,
            respondedAt = "2026-11-12T12:00:00Z",
        ),
        suggestion("sent-pending", "me", "kai", "2026-11-11T12:00:00Z"),
        suggestion("others", "kai", "ana", "2026-11-12T13:00:00Z", SuggestionStatus.ADDED),
    )

    @Test
    fun `for me puts pending first then newest, sent is newest first`() {
        SuggestionRules.forMe(all, "me").map { it.id } shouldContainExactly
            listOf("mid-pending", "old-pending", "new-added", "dismissed")
        SuggestionRules.sent(all, "me").map { it.id } shouldContainExactly listOf("sent-pending", "sent-added")
    }

    @Test
    fun `badge counts incoming pending only`() {
        SuggestionRules.badge(all, "me") shouldBe 2
        SuggestionRules.badge(all, "kai") shouldBe 1
    }

    @Test
    fun `accepted counts the player's added suggestions`() {
        SuggestionRules.accepted(all, "me") shouldBe 1
        SuggestionRules.accepted(all, "kai") shouldBe 2
        SuggestionRules.accepted(all, "ana") shouldBe 0
    }

    @Test
    fun `auto-added matches incoming pending suggestions to library favourites by source id and url`() {
        val favorites = setOf(
            2499283573021220255L to "/manga/old-pending",
            // Already answered or sent by me: left alone
            2499283573021220255L to "/manga/dismissed",
            2499283573021220255L to "/manga/sent-pending",
            // Same url on another source isn't the same manga
            1L to "/manga/mid-pending",
        )
        SuggestionRules.autoAdded(all, "me", favorites) shouldContainExactly listOf("old-pending")
        SuggestionRules.autoAdded(all, "me", emptySet()).shouldBeEmpty()
        SuggestionRules.autoAdded(
            listOf(suggestion("bad", "kai", "me", "2026-11-12T10:00:00Z", sourceId = "not-a-number")),
            "me",
            setOf(0L to "/manga/bad"),
        ).shouldBeEmpty()
    }

    @Test
    fun `status changes and merges update rows by id`() {
        val now = Instant.parse("2026-11-12T15:00:00Z")
        val updated = SuggestionRules.withStatus(all, listOf("old-pending"), SuggestionStatus.ADDED, now)
        updated.first { it.id == "old-pending" }.status shouldBe SuggestionStatus.ADDED
        updated.first { it.id == "old-pending" }.responded shouldBe now
        SuggestionRules.badge(updated, "me") shouldBe 1

        val resent = suggestion("sent-added", "me", "kai", "2026-11-12T16:00:00Z")
        val fresh = suggestion("brand-new", "me", "ana", "2026-11-12T15:30:00Z")
        val merged = SuggestionRules.merge(all, listOf(resent, fresh))
        merged.size shouldBe all.size + 1
        merged.first { it.id == "sent-added" }.pending shouldBe true
        SuggestionRules.sent(merged, "me").map { it.id } shouldContainExactly
            listOf("sent-added", "brand-new", "sent-pending")
    }

    @Test
    fun `events are the latest three involving me`() {
        val events = SuggestionRules.events(all, "me")
        events.map { it::class to it.suggestion.id } shouldContainExactly listOf(
            SuggestionEvent.Accepted::class to "sent-added",
            SuggestionEvent.Received::class to "new-added",
            SuggestionEvent.Received::class to "dismissed",
        )
        SuggestionRules.events(all, "me", limit = 10).none { it.suggestion.id == "others" } shouldBe true
    }
}
