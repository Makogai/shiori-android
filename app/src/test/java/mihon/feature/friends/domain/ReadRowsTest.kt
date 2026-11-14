package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

class ReadRowsTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '\t',
        value = [
            "1\t/manga/a\t80f68ee5e91633713878aea2a16fa352",
            "0\t/manga/a\t81bd735263de3512adc4cbb8bfc4cbc7",
            "2499283573021220255\t/series/one-piece\t753909c2c70484e640cb2079c96851f7",
            "1\t/manga/ä\tbae7da390e1a9cf0b9982bead93e64f3",
        ],
    )
    fun `series key is the first 32 hex chars of SHA-256 of source and manga url`(
        sourceId: Long,
        mangaUrl: String,
        expected: String,
    ) {
        LeagueHashing.seriesKey(sourceId, mangaUrl) shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '\t',
        value = [
            "1\t/manga/a\t/chapter/1\t086c33067f5f5487e526196cc6d0c70d",
            "2499283573021220255\t/series/one-piece\t/series/one-piece/chapter-1100\t" +
                "9a7150c614069ca3510d94700fca9bfd",
        ],
    )
    fun `client id hashes source, manga url and chapter url`(
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
        expected: String,
    ) {
        LeagueHashing.clientId(sourceId, mangaUrl, chapterUrl) shouldBe expected
    }

    @Test
    fun `builds a row from a history entry`() {
        val row = ReadRows.build(read(), USER)

        row shouldBe ReadRow(
            userId = USER,
            clientId = "086c33067f5f5487e526196cc6d0c70d",
            readAt = "2026-11-10T20:15:30.250Z",
            seriesKey = "80f68ee5e91633713878aea2a16fa352",
            seriesTitle = "Title",
            coverUrl = "https://example.org/cover.jpg",
            sourceName = "Source",
            chapterName = "Chapter 1",
            chapterNumber = 1.0,
            genres = listOf("Action", "Comedy"),
            durationS = 754,
        )
    }

    @Test
    fun `history entries without a read time are not uploaded`() {
        val rows = ReadRows.buildAll(
            listOf(read(readAt = Instant.EPOCH), read(chapterUrl = "/chapter/2")),
            USER,
        )
        rows.map { it.clientId } shouldContainExactly listOf(LeagueHashing.clientId(1, "/manga/a", "/chapter/2"))
    }

    @Test
    fun `serializes the contract field names`() {
        val json = Json { explicitNulls = true }.encodeToString(ReadRow.serializer(), ReadRows.build(read(), USER))
        json shouldBe """{"user_id":"$USER","client_id":"086c33067f5f5487e526196cc6d0c70d",""" +
            """"read_at":"2026-11-10T20:15:30.250Z","series_key":"80f68ee5e91633713878aea2a16fa352",""" +
            """"series_title":"Title","cover_url":"https://example.org/cover.jpg","source_name":"Source",""" +
            """"chapter_name":"Chapter 1","chapter_number":1.0,"genres":["Action","Comedy"],"duration_s":754}"""
    }

    @Test
    fun `clamps duration to a day and never below zero`() {
        ReadRows.build(read(durationMs = 90_000_000), USER).durationS shouldBe 86_400
        ReadRows.build(read(durationMs = -5_000), USER).durationS shouldBe 0
        ReadRows.build(read(durationMs = 999), USER).durationS shouldBe 0
    }

    @Test
    fun `drops unknown chapter numbers and non-http covers`() {
        val row = ReadRows.build(read(chapterNumber = -1.0, cover = "file:///cover.jpg"), USER)
        row.chapterNumber shouldBe null
        row.coverUrl shouldBe null
        ReadRows.build(read(cover = "HTTP://example.org/c.png"), USER).coverUrl shouldBe "HTTP://example.org/c.png"
        ReadRows.build(read(cover = null), USER).coverUrl shouldBe null
    }

    @Test
    fun `keeps at most 12 trimmed non-empty genres`() {
        val genres = listOf(" Action ", "", "  ") + (1..20).map { "G$it" }
        ReadRows.build(read(genres = genres), USER).genres shouldContainExactly
            listOf("Action") + (1..11).map { "G$it" }
        ReadRows.build(read(genres = null), USER).genres shouldBe emptyList()
    }

    @Test
    fun `uploads in batches of at most 500 rows`() {
        val rows = (1..1201).map { ReadRows.build(read(chapterUrl = "/chapter/$it"), USER) }
        ReadRows.batches(rows).map { it.size } shouldContainExactly listOf(500, 500, 201)
        ReadRows.batches(emptyList()) shouldBe emptyList()
    }

    private fun read(
        readAt: Instant = Instant.parse("2026-11-10T20:15:30.250Z"),
        durationMs: Long = 754_999,
        chapterNumber: Double = 1.0,
        cover: String? = "https://example.org/cover.jpg",
        genres: List<String>? = listOf("Action", " Comedy "),
        chapterUrl: String = "/chapter/1",
    ) = LocalRead(
        sourceId = 1,
        sourceName = "Source",
        mangaUrl = "/manga/a",
        mangaTitle = "Title",
        coverUrl = cover,
        genres = genres,
        chapterUrl = chapterUrl,
        chapterName = "Chapter 1",
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDurationMs = durationMs,
    )

    companion object {
        private const val USER = "6f1c2d4e-0000-4000-8000-000000000001"
    }
}
