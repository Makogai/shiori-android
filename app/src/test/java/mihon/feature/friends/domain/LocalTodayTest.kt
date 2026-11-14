package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionEvent
import mihon.feature.friends.data.SuggestionRules
import mihon.feature.friends.data.SuggestionStatus
import org.junit.jupiter.api.Test
import java.time.ZoneId

class LocalTodayTest {

    private val belgrade = ZoneId.of("Europe/Belgrade")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun row(id: String, chapters: Long, seconds: Long) =
        BoardRow(userId = id, displayName = id, chapters = chapters, seconds = seconds)

    @Test
    fun `zone falls back to the Arena zone`() {
        LocalToday.zone(null, belgrade) shouldBe belgrade
        LocalToday.zone("", belgrade) shouldBe belgrade
        LocalToday.zone("Not/AZone", belgrade) shouldBe belgrade
        LocalToday.zone("Asia/Tokyo", belgrade) shouldBe tokyo
    }

    @Test
    fun `each player scores from the board of their own zone`() {
        val entries = LocalToday.entries(
            names = mapOf("me" to "You", "filip" to "Filip", "kai" to "Kai"),
            zones = mapOf("me" to belgrade, "filip" to tokyo, "kai" to belgrade),
            boards = mapOf(
                // Filip's Belgrade row must be ignored: his day is Tokyo's
                belgrade.id to listOf(row("me", 3, 600), row("filip", 9, 0), row("kai", 3, 600)),
                tokyo.id to listOf(row("filip", 2, 100)),
            ),
        )
        entries.map { it.userId } shouldContainExactly listOf("kai", "me", "filip")
        entries.map { it.place } shouldContainExactly listOf(1, 1, 3)
        entries.map { it.medal } shouldContainExactly listOf(1, 1, 3)
    }

    @Test
    fun `nobody with zero chapters gets a medal`() {
        val entries = LocalToday.entries(
            names = emptyMap(),
            zones = mapOf("a" to belgrade, "b" to belgrade),
            boards = mapOf(belgrade.id to listOf(row("a", 1, 0))),
        )
        entries.map { it.medal } shouldContainExactly listOf(1, null)
    }

    @Test
    fun `read counts as accepted and shows as an event`() {
        fun suggestion(id: String, from: String, to: String, status: SuggestionStatus) = Suggestion(
            id = id, fromUser = from, toUser = to, sourceId = "1", mangaUrl = "/$id", title = id,
            status = status, createdAt = "2026-11-12T10:00:00Z", respondedAt = "2026-11-13T10:00:00Z",
        )
        val all = listOf(
            suggestion("r", "me", "k", SuggestionStatus.READ),
            suggestion("p", "k", "me", SuggestionStatus.PENDING),
            suggestion("x", "k", "me", SuggestionStatus.READ),
        )
        SuggestionRules.accepted(all, "me") shouldBe 1
        SuggestionRules.open(all, "me").map { it.id } shouldContainExactly listOf("p")
        SuggestionRules.events(all, "me", limit = 5).filterIsInstance<SuggestionEvent.ReadIt>()
            .map { it.suggestion.id } shouldContainExactly listOf("r")
    }
}
