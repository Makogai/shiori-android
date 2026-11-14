package mihon.feature.tracklibrary

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

class TrackLibraryRowsTest {

    private val anilist = 2L

    private fun manga(id: Long, title: String) = Manga.create().copy(id = id, title = title)

    private fun track(mangaId: Long, trackerId: Long) = Track(
        id = mangaId,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = mangaId,
        libraryId = null,
        title = "t$mangaId",
        lastChapterRead = 0.0,
        totalChapters = 0,
        status = 0,
        score = 0.0,
        remoteUrl = "",
        startDate = null,
        finishDate = null,
        private = false,
    )

    private fun found(vararg scores: Int) = MatchState.Found(
        scores.mapIndexed { i, score ->
            MatchCandidate(
                TrackSearch.create(anilist).apply {
                    remote_id = i.toLong()
                    title = "r$i"
                },
                score,
            )
        },
    )

    private val library = listOf(
        manga(1, "Beta"),
        manga(2, "alpha"),
        manga(3, "Gamma"),
        manga(4, "Delta"),
        manga(5, "Epsilon"),
        manga(6, "Zeta"),
    )

    private fun build(
        skipped: Set<Long> = emptySet(),
        states: Map<MatchKey, MatchState> = emptyMap(),
        binding: Set<MatchKey> = emptySet(),
        showSkipped: Boolean = false,
    ) = TrackLibraryRows.build(
        library = library,
        tracks = mapOf(1L to listOf(track(1, anilist)), 3L to listOf(track(3, trackerId = 1))),
        trackerId = anilist,
        skipped = skipped,
        states = states,
        binding = binding,
        showSkipped = showSkipped,
    )

    @Test
    fun `entries split into tracked and to check for the chosen tracker`() {
        val content = build(skipped = setOf(4))
        content.total shouldBe 6
        content.tracked.map { it.manga.id } shouldContainExactly listOf(1L)
        // Tracked on another tracker still needs this one; sorted by title, case-insensitive
        content.needs.map { it.manga.id } shouldContainExactly listOf(2L, 5L, 3L, 6L)
        content.toCheck shouldBe 4
        content.skippedCount shouldBe 1

        val withSkipped = build(skipped = setOf(4), showSkipped = true)
        withSkipped.needs.map { it.manga.id } shouldContainExactly listOf(2L, 4L, 5L, 3L, 6L)
        withSkipped.needs.single { it.manga.id == 4L }.skipped shouldBe true
        withSkipped.toCheck shouldBe 4
    }

    @Test
    fun `bulk confirm takes shown, unskipped, idle rows whose best result is 90 or more`() {
        val states = mapOf(
            MatchKey(anilist, 2) to found(95, 40),
            MatchKey(anilist, 3) to found(90),
            MatchKey(anilist, 4) to found(100),
            MatchKey(anilist, 5) to found(89),
            MatchKey(anilist, 6) to found(99),
            // Already tracked: never in the needs list
            MatchKey(anilist, 1) to found(100),
        )
        val content = build(
            skipped = setOf(4),
            states = states,
            binding = setOf(MatchKey(anilist, 6)),
            showSkipped = true,
        )
        content.strongMatches.map { it.manga.id } shouldContainExactly listOf(2L, 3L)
        content.strongMatches.map { it.best!!.result.remote_id } shouldContainExactly listOf(0L, 0L)

        build(states = mapOf(MatchKey(anilist, 2) to found())).strongMatches shouldBe emptyList()
        build(states = mapOf(MatchKey(anilist, 2) to MatchState.Searching)).strongMatches shouldBe emptyList()
    }

    @Test
    fun `pending counts queued and running searches`() {
        val content = build(
            states = mapOf(
                MatchKey(anilist, 2) to MatchState.Queued,
                MatchKey(anilist, 3) to MatchState.Searching,
                MatchKey(anilist, 5) to found(10),
                MatchKey(anilist, 6) to MatchState.Failed("x"),
            ),
        )
        content.pending shouldBe 2
    }
}
