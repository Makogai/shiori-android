package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/** README v5: only finished chapters read for 15 s count; my server rows are reconciled with that rule. */
class ReadReconcileTest {

    @Test
    fun `only finished chapters read for at least 15 seconds qualify`() {
        ReadRows.qualifies(read("/a", durationMs = 15_000)) shouldBe true
        ReadRows.qualifies(read("/a", durationMs = 14_999)) shouldBe false
        ReadRows.qualifies(read("/a", finished = false)) shouldBe false
        ReadRows.qualifies(read("/a", readAt = Instant.EPOCH)) shouldBe false
        ReadRows.buildAll(
            listOf(read("/ok"), read("/skimmed", durationMs = 3_000), read("/opened", finished = false)),
            USER,
        ).map { it.clientId } shouldContainExactly listOf(id("/ok"))
    }

    @Test
    fun `deletes rows that no longer qualify and resends missing ones since the start`() {
        val reads = listOf(
            read("/good", readAt = START.plusSeconds(1)),
            read("/unread-again", finished = false),
            read("/skimmed", durationMs = 4_000),
            read("/missing", readAt = START.plusSeconds(50)),
            read("/old", readAt = START.minusSeconds(50)),
        )
        val server = listOf(id("/good"), id("/unread-again"), id("/skimmed"), "gone-from-history")

        val diff = ReadReconcile.diff(reads, server, START.toEpochMilli())

        diff.stale shouldContainExactly listOf(id("/unread-again"), id("/skimmed"), "gone-from-history").sorted()
        diff.missing.map { it.chapterUrl } shouldContainExactly listOf("/missing")
        ReadReconcile.diff(reads, server, null).missing.shouldBeEmpty()
    }

    @Test
    fun `an empty local history deletes nothing`() {
        ReadReconcile.diff(emptyList(), listOf("x"), START.toEpochMilli()) shouldBe
            ReadReconcile.Diff(emptyList(), emptyList())
    }

    private fun id(chapterUrl: String) = LeagueHashing.clientId(1, "/manga/a", chapterUrl)

    private fun read(
        chapterUrl: String,
        readAt: Instant = START.plusSeconds(100),
        durationMs: Long = 60_000,
        finished: Boolean = true,
    ) = LocalRead(
        sourceId = 1,
        sourceName = "Source",
        mangaUrl = "/manga/a",
        mangaTitle = "Title",
        coverUrl = null,
        genres = null,
        chapterUrl = chapterUrl,
        chapterName = "Chapter",
        chapterNumber = 1.0,
        readAt = readAt,
        readDurationMs = durationMs,
        read = finished,
    )

    companion object {
        private const val USER = "6f1c2d4e-0000-4000-8000-000000000001"
        private val START = Instant.parse("2027-01-15T08:00:00Z")
    }
}
