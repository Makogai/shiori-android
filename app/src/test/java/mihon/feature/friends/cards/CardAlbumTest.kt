package mihon.feature.friends.cards

import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.FeedRow
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

/** Album filters and sorts, the showcase, the Arena day and the merged feed. */
class CardAlbumTest {

    private fun card(
        id: String,
        grade: String = "C",
        theme: String = "action",
        serial: Long = 1,
        created: String = "2026-11-10T10:00:00Z",
        series: String = "Alpha",
        key: String? = series.lowercase(),
        owner: String = "me",
        favorite: Boolean = false,
    ) = ShioriCard(
        id = id,
        owner = owner,
        serial = serial,
        gradeCode = grade,
        themeCode = theme,
        cardName = id,
        seriesTitle = series,
        seriesKey = key,
        createdAt = created,
        favorite = favorite,
    )

    private val a = card("a", grade = "R", serial = 5, created = "2026-11-10T10:00:00Z", series = "beta")
    private val b = card("b", grade = "L", serial = 2, created = "2026-11-11T10:00:00Z", owner = "filip")
    private val c =
        card("c", grade = "R", serial = 9, created = "2026-11-12T10:00:00Z", series = "Alpha", theme = "romance")
    private val d = card("d", grade = "C", serial = 1, created = "2026-11-12T10:00:00Z", series = "gamma", key = null)
    private val cards = listOf(a, b, c, d)

    private val names = mapOf("me" to "You", "filip" to "Filip")
    private fun ids(list: List<ShioriCard>, sort: CardAlbumSort, name: (String) -> String = { names.getValue(it) }) =
        CardAlbum.apply(list, CardAlbumFilter(), sort, name).map { it.id }

    @Test
    fun `sorts by serial, with ties to the higher serial`() {
        // Newest and Oldest go by the Arena-wide serial, not the pull time
        ids(cards, CardAlbumSort.NEWEST) shouldBe listOf("c", "a", "b", "d")
        ids(cards, CardAlbumSort.OLDEST) shouldBe listOf("d", "b", "a", "c")
        ids(cards, CardAlbumSort.GRADE) shouldBe listOf("b", "c", "a", "d")
        ids(cards, CardAlbumSort.SERIES) shouldBe listOf("c", "b", "a", "d")
        // Owner A–Z by display name, ignoring case; the injected names decide
        ids(cards, CardAlbumSort.OWNER) shouldBe listOf("b", "c", "a", "d")
        ids(cards, CardAlbumSort.OWNER) { if (it == "filip") "zed" else "You" } shouldBe listOf("c", "a", "d", "b")
        ids(cards, CardAlbumSort.OWNER) { if (it == "filip") "abc" else "ABE" } shouldBe listOf("b", "c", "a", "d")
    }

    @Test
    fun `mine is my cards, everyone the whole album`() {
        val mine = CardAlbum.scoped(cards, CardCollectionScope.MINE, "me")
        mine.map { it.id } shouldBe listOf("a", "c", "d")
        CardAlbum.scoped(cards, CardCollectionScope.EVERYONE, "me") shouldBe cards
        CardAlbum.scoped(cards, CardCollectionScope.MINE, null) shouldBe emptyList()
        // Owner in Mine: every owner is equal, so it is Newest
        ids(mine, CardAlbumSort.OWNER) shouldBe ids(mine, CardAlbumSort.NEWEST)
        ids(mine, CardAlbumSort.NEWEST) shouldBe listOf("c", "a", "d")
        // Owner is offered only for Everyone; Serial is gone
        CardAlbumSort.offered(CardCollectionScope.MINE) shouldBe
            listOf(CardAlbumSort.NEWEST, CardAlbumSort.OLDEST, CardAlbumSort.GRADE, CardAlbumSort.SERIES)
        CardAlbumSort.offered(CardCollectionScope.EVERYONE).last() shouldBe CardAlbumSort.OWNER
        // The per-device value
        CardCollectionScope.of("everyone") shouldBe CardCollectionScope.EVERYONE
        CardCollectionScope.of("mine") shouldBe CardCollectionScope.MINE
        CardCollectionScope.of(null) shouldBe CardCollectionScope.MINE
        CardCollectionScope.of("arena") shouldBe CardCollectionScope.MINE
        CardCollectionScope.EVERYONE.value shouldBe "everyone"
    }

    @Test
    fun `filters by grades, themes, series and owner`() {
        fun ids(filter: CardAlbumFilter) = CardAlbum.apply(cards, filter, CardAlbumSort.NEWEST).map { it.id }
        ids(CardAlbumFilter(grades = setOf(CardGrade.RARE))) shouldBe listOf("c", "a")
        ids(CardAlbumFilter(grades = setOf(CardGrade.RARE, CardGrade.COMMON))) shouldBe listOf("c", "a", "d")
        ids(CardAlbumFilter(themes = setOf(CardTheme.ROMANCE))) shouldBe listOf("c")
        ids(CardAlbumFilter(series = "alpha")) shouldBe listOf("c", "b")
        ids(CardAlbumFilter(series = "gamma")) shouldBe listOf("d")
        ids(CardAlbumFilter(owner = "filip")) shouldBe listOf("b")
        CardAlbumFilter().isActive shouldBe false
        CardAlbumFilter(owner = "x").isActive shouldBe true
        CardAlbum.series(cards) shouldBe listOf("alpha" to "Alpha", "beta" to "beta", "gamma" to "gamma")
    }

    @Test
    fun `counts every grade and showcases the rarest, a favourite, then the lowest serial`() {
        CardAlbum.counts(cards).toMap() shouldBe mapOf(
            CardGrade.COMMON to 1,
            CardGrade.UNCOMMON to 0,
            CardGrade.RARE to 2,
            CardGrade.EPIC to 0,
            CardGrade.LEGENDARY to 1,
            CardGrade.MYTHIC to 0,
            CardGrade.SECRET to 0,
        )
        CardAlbum.rarest(cards) shouldBe b
        CardAlbum.rarest(listOf(a, c)) shouldBe a
        CardAlbum.rarest(listOf(a, c.copy(favorite = true)))?.id shouldBe "c"
        CardAlbum.rarest(emptyList()) shouldBe null
    }

    @Test
    fun `texts on the card and the Arena day`() {
        val zone = ZoneId.of("Europe/Belgrade")
        val newYear = card("n", created = "2026-12-31T23:30:00Z").copy(season = 2, serial = 12345)
        newYear.seasonText(zone) shouldBe "S2·2027"
        newYear.serialText shouldBe "No. 12345"
        CardAlbum.day(Instant.parse("2026-12-31T23:30:00Z"), zone) shouldBe "2027-01-01"
        newYear.copy(chapterNumber = 41.5).seriesLine shouldBe "Alpha · Ch. 41.5"
        newYear.copy(chapterName = "Side story").seriesLine shouldBe "Alpha · Side story"
        newYear.seriesLine shouldBe "Alpha"
        CardGrade.EPIC.label shouldBe "Rank B"
        CardSeriesContext.genres(listOf("Action, Fantasy", " Drama ", "Action", "")) shouldBe
            listOf("Action", "Fantasy", "Drama")
        CardSeriesContext.genres((1..20).map { "G$it" }).size shouldBe 12
    }

    @Test
    fun `the feed merges reads with R+ pulls by time`() {
        fun read(at: String) = FeedRow(userId = "filip", readAt = at, seriesTitle = "S")
        val reads = listOf(read("2026-11-12T11:00:00+00:00"), read("2026-11-10T09:00:00+00:00"))
        val merged = CardAlbum.merged(reads, cards, limit = 10)
        merged.map {
            when (it) {
                is CardFeedItem.Read -> "read@" + it.row.readAt.take(10)
                is CardFeedItem.Pull -> it.card.id
            }
        } shouldBe listOf("read@2026-11-12", "c", "b", "a", "read@2026-11-10")
        CardAlbum.merged(reads, cards, limit = 2).size shouldBe 2
    }

    @Test
    fun `only renders older than a ready frame are stale`() {
        val plain = card("r")
        CardAlbum.renderIsStale(plain) shouldBe false // no frame
        val framed = plain.copy(frameStatusCode = "ready", framePath = "r.png", frameReadyAt = "2026-11-12T10:01:00Z")
        CardAlbum.renderIsStale(framed) shouldBe true // no render yet
        val early = framed.copy(renderPath = "me/r.png", renderUpdatedAt = "2026-11-12T10:00:30+00:00")
        CardAlbum.renderIsStale(early) shouldBe true
        CardAlbum.renderIsStale(early.copy(renderUpdatedAt = "2026-11-12T10:02:00.5+00:00")) shouldBe false
        CardAlbum.renderIsStale(early.copy(frameReadyAt = null)) shouldBe false
        CardAlbum.renderIsStale(early.copy(frameStatusCode = "failed", renderPath = null)) shouldBe false
    }

    @Test
    fun `a pending frame goes stale after five minutes`() {
        val pending = card("p", created = "2026-11-12T10:00:00Z").copy(frameStatusCode = "pending")
        CardAlbum.isStalePending(pending, Instant.parse("2026-11-12T10:04:00Z")) shouldBe false
        CardAlbum.isStalePending(pending, Instant.parse("2026-11-12T10:06:00Z")) shouldBe true
        CardAlbum.isStalePending(
            pending.copy(frameStatusCode = "ready"),
            Instant.parse("2026-11-12T11:00:00Z"),
        ) shouldBe
            false
        pending.copy(frameStatusCode = "ready", framePath = "p.png").customFramePath shouldBe "p.png"
        CardColor.argb("#C026D3") shouldBe 0xFFC026D3.toInt()
        CardColor.argb("#C026D380") shouldBe 0x80C026D3.toInt()
        CardColor.argb("nope") shouldBe null
    }

    @Test
    fun `sections by season, newest season first, keeping the sort inside`() {
        val zone = ZoneId.of("Europe/Tallinn")
        val s2a = card("s2a", grade = "E", created = "2026-12-31T23:30:00Z").copy(season = 2)
        val s2b = card("s2b", grade = "C", created = "2026-12-01T10:00:00Z").copy(season = 2)
        val s1 = cards
        val sorted = CardAlbum.apply(s1 + s2a + s2b, CardAlbumFilter(), CardAlbumSort.GRADE)
        val sections = CardAlbum.sections(sorted, zone)
        sections.map { it.title } shouldBe listOf("Season 2 · 2027", "Season 1 · 2026")
        sections.map { it.countText } shouldBe listOf("2 cards", "4 cards")
        sections[0].cards.map { it.id } shouldBe listOf("s2a", "s2b")
        sections[1].cards.map { it.id } shouldBe listOf("b", "c", "a", "d")
        // The album list the pager walks
        sections.flatMap { s -> s.cards.map { it.id } } shouldBe listOf("s2a", "s2b", "b", "c", "a", "d")
        CardAlbum.albumIds(sections) shouldBe listOf("s2a", "s2b", "b", "c", "a", "d")
        CardAlbum.sections(emptyList(), zone) shouldBe emptyList()
        CardAlbum.countText(1) shouldBe "1 card"
    }

    @Test
    fun `oldest puts the oldest season first, in both scopes`() {
        val zone = ZoneId.of("Europe/Tallinn")
        val s2a = card("s2a", serial = 20, created = "2026-12-01T10:00:00Z").copy(season = 2)
        val s2b = card("s2b", serial = 21, created = "2026-12-02T10:00:00Z", owner = "filip").copy(season = 2)
        val all = cards + s2a + s2b
        fun walk(scope: CardCollectionScope, sort: CardAlbumSort): List<String> {
            val shown = CardAlbum.apply(CardAlbum.scoped(all, scope, "me"), CardAlbumFilter(), sort) {
                names.getValue(it)
            }
            val sections = CardAlbum.sections(shown, zone, oldestFirst = sort == CardAlbumSort.OLDEST)
            return CardAlbum.albumIds(sections)
        }
        walk(CardCollectionScope.EVERYONE, CardAlbumSort.OLDEST) shouldBe listOf("d", "b", "a", "c", "s2a", "s2b")
        walk(CardCollectionScope.EVERYONE, CardAlbumSort.NEWEST) shouldBe listOf("s2b", "s2a", "c", "a", "b", "d")
        walk(CardCollectionScope.MINE, CardAlbumSort.OLDEST) shouldBe listOf("d", "a", "c", "s2a")
        walk(CardCollectionScope.MINE, CardAlbumSort.NEWEST) shouldBe listOf("s2a", "c", "a", "d")
        // Owner: Filip before You inside each season, newest season first
        walk(CardCollectionScope.EVERYONE, CardAlbumSort.OWNER) shouldBe listOf("s2b", "s2a", "b", "c", "a", "d")
        // A filter on the owner keeps the season order
        val filip = CardAlbum.apply(all, CardAlbumFilter(owner = "filip"), CardAlbumSort.OLDEST)
        CardAlbum.albumIds(CardAlbum.sections(filip, zone, oldestFirst = true)) shouldBe listOf("b", "s2b")
    }

    @Test
    fun `grid columns stay 104 to 160 wide`() {
        CardAlbum.columns(360f, 104f, 160f, 12f) shouldBe 3
        CardAlbum.columns(100f, 104f, 160f, 12f) shouldBe 1
        CardAlbum.columns(0f, 104f, 160f, 12f) shouldBe 1
        // 2 × 104 fit in 230, but cells would be 109 wide: fine
        CardAlbum.columns(230f, 104f, 160f, 12f) shouldBe 2
        listOf(320f, 411f, 600f, 840f, 1280f).forEach { width ->
            val n = CardAlbum.columns(width, 104f, 160f, 12f)
            val cell = (width - 12f * (n - 1)) / n
            (cell in 104f..160f) shouldBe true
        }
    }

    @Test
    fun `from friends is others' pulls newest first, and the collection line`() {
        CardAlbum.fromFriends(cards, "me", 20).map { it.id } shouldBe listOf("b")
        CardAlbum.fromFriends(cards, "filip", 2).map { it.id } shouldBe listOf("c", "d")
        CardAlbum.collectionText(cards) shouldBe "4 cards · 3 series"
    }

    @Test
    fun `the Library strip is my cards newest first`() {
        CardAlbum.recentMine(cards, "me", 12).map { it.id } shouldBe listOf("c", "d", "a")
        CardAlbum.recentMine(cards, "me", 2).map { it.id } shouldBe listOf("c", "d")
        CardAlbum.recentMine(cards, null, 12) shouldBe emptyList()
    }
}
