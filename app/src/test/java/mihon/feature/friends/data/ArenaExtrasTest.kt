package mihon.feature.friends.data

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** README v5 requests, the stats JSON, throttling and the activity day groups. */
class ArenaExtrasTest {

    private val requests = ArenaExtrasRequests(FriendsJson)

    @Test
    fun `pages my reads and deletes them in chunks of my own rows`() {
        requests.ownReads("me", 1000).path shouldBe
            "/rest/v1/reads?select=client_id,read_at&user_id=eq.me&order=client_id&limit=1000&offset=1000"
        val ids = (0 until 250).map { "%032x".format(it) }
        val deletes = requests.deleteReads("me", ids)
        deletes.map { it.method }.distinct() shouldContainExactly listOf("DELETE")
        deletes.size shouldBe 3
        deletes.first().path shouldContain "user_id=eq.me&client_id=in.(${ids[0]},${ids[1]},"
        requests.deleteReads("me", emptyList()) shouldBe emptyList()
    }

    @Test
    fun `stats upsert writes every key with a null mean score`() {
        val call = requests.saveStats("me", LibraryStats(libraryCount = 3, app = "mihon"), Instant.EPOCH)
        call.path shouldBe "/rest/v1/profile_stats"
        call.prefer shouldBe "resolution=merge-duplicates,return=minimal"
        val body = FriendsJson.parseToJsonElement(call.body!!).jsonObject
        body["user_id"]!!.jsonPrimitive.content shouldBe "me"
        val stats = body["stats"]!!.jsonObject
        stats.keys.size shouldBe 16
        stats["library_count"]!!.jsonPrimitive.content shouldBe "3"
        stats["mean_score"] shouldBe JsonNull
    }

    @Test
    fun `reads another app's stats row tolerating missing keys`() {
        val text = """
            [{"user_id":"u2","updated_at":"2026-11-12T10:00:00+00:00",
              "stats":{"library_count":12,"mean_score":null,"top_genres":[{"name":"Action","count":5}],
                       "app":"tachiyomi-ios","extra":1}}]
        """.trimIndent()
        val row = FriendsJson.decodeFromString(ListSerializer(ProfileStatsRow.serializer()), text).single()
        row.stats.libraryCount shouldBe 12
        row.stats.meanScore shouldBe null
        row.stats.completedCount shouldBe 0
        row.stats.topGenres shouldContainExactly listOf(NamedCount("Action", 5))
        row.updatedInstant shouldBe Instant.parse("2026-11-12T10:00:00Z")
    }

    @Test
    fun `player feed sends the user, the cursor and the page size`() {
        val first = FriendsJson.parseToJsonElement(requests.playerFeed("u2", null, 100).body!!) as JsonObject
        first["p_user"]!!.jsonPrimitive.content shouldBe "u2"
        first["p_before"] shouldBe JsonNull
        first["p_limit"]!!.jsonPrimitive.content shouldBe "100"
        val next = FriendsJson.parseToJsonElement(
            requests.playerFeed("u2", Instant.parse("2026-11-12T10:00:00.123Z"), 100).body!!,
        ) as JsonObject
        next["p_before"]!!.jsonPrimitive.content shouldBe "2026-11-12T10:00:00.123Z"
    }

    @Test
    fun `builds stats from the library`() {
        val entries = listOf(
            entry(
                completed = true,
                genres = listOf("Action", " Comedy", "Action"),
                source = "MangaDex",
                tracked = true,
            ),
            entry(started = true, genres = listOf("Action"), source = "MangaDex"),
            entry(local = true, genres = listOf("Drama"), source = "Local source"),
        )
        val stats = LibraryStatsRules.build(entries, 7, 125_900, 7.345, 2, "mihon", "0.19")
        stats.libraryCount shouldBe 3
        stats.completedCount shouldBe 1
        stats.startedCount shouldBe 2
        stats.localCount shouldBe 1
        stats.chaptersTotal shouldBe 30
        stats.chaptersRead shouldBe 15
        stats.chaptersDownloaded shouldBe 7
        stats.readSeconds shouldBe 125
        stats.trackedCount shouldBe 1
        stats.meanScore shouldBe 7.3
        stats.topGenres shouldContainExactly
            listOf(NamedCount("Action", 2), NamedCount("Comedy", 1), NamedCount("Drama", 1))
        stats.topSources.first() shouldBe NamedCount("MangaDex", 2)
        LibraryStatsRules.build(emptyList(), 0, 0, null, 0, "mihon", "").meanScore shouldBe null
    }

    @Test
    fun `publishes at most every thirty minutes unless forced`() {
        val last = 1_800_000_000_000
        LibraryStatsRules.due(null, last, force = false) shouldBe true
        LibraryStatsRules.due(last, last + 29 * 60_000, force = false) shouldBe false
        LibraryStatsRules.due(last, last + 30 * 60_000, force = false) shouldBe true
        LibraryStatsRules.due(last, last + 60_000, force = true) shouldBe true
    }

    @Test
    fun `groups activity by day in the Arena time zone`() {
        val zone = ZoneId.of("Europe/Belgrade")
        val rows = listOf(
            feed("2026-11-11T23:30:00Z", 1800),
            feed("2026-11-12T09:00:00Z", 1080),
            feed("2026-11-11T12:00:00Z", 600),
            feed("2026-11-09T12:00:00Z", 4320),
        )
        val days = ActivityDays.group(rows, zone)
        days.map { it.date } shouldContainExactly listOf(
            LocalDate.of(2026, 11, 12),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 11, 9),
        )
        days[0].chapters shouldBe 2
        days[0].rows.first().readAt shouldBe "2026-11-12T09:00:00Z"
        ActivityDays.minutes(days[0].seconds) shouldBe "48 min"
        ActivityDays.minutes(days[2].seconds) shouldBe "1 h 12 min"
        ActivityDays.minutes(0) shouldBe "0 min"
        ActivityDays.merge(rows.take(2), rows.drop(1)).size shouldBe 4
    }

    private fun feed(readAt: String, seconds: Int) =
        FeedRow(userId = "u", readAt = readAt, seriesTitle = "A", chapterName = "Ch", durationS = seconds)

    private fun entry(
        completed: Boolean = false,
        started: Boolean = completed,
        local: Boolean = false,
        genres: List<String> = emptyList(),
        source: String = "Source",
        tracked: Boolean = false,
    ) = LibraryStatsEntry(completed, started, local, 10, 5, genres, source, tracked)
}
