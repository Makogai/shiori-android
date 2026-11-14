package mihon.feature.tracklibrary

import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/** The queue against a fake tracker; no network. */
class LibraryTrackMatcherTest {

    private val trackerId = 2L
    private val queries = mutableListOf<String>()
    private val tracker = mockk<BaseTracker> {
        every { id } returns trackerId
        every { name } returns "AniList"
    }
    private val addTracks = mockk<AddTracks>(relaxed = true)
    private val trackerManager = mockk<TrackerManager> { every { get(trackerId) } returns tracker }

    private fun result(remoteId: Long, title: String, vararg alt: String) = TrackSearch.create(trackerId).apply {
        remote_id = remoteId
        this.title = title
        alternative_titles = alt.toList()
    }

    private fun answer(results: Map<String, List<TrackSearch>>) {
        coEvery { tracker.search(any()) } answers {
            val query = firstArg<String>()
            synchronized(queries) { queries += query }
            results[query].orEmpty()
        }
    }

    private fun TestScope.matcher() =
        LibraryTrackMatcher(backgroundScope, trackerManager, addTracks).apply {
            // No real waiting in tests
            newPacer = { RequestPacer(1.milliseconds, now = { 0L }, sleep = {}) }
        }

    private suspend fun LibraryTrackMatcher.awaitState(mangaId: Long): MatchState {
        val key = MatchKey(trackerId, mangaId)
        return states.first { it[key] is MatchState.Found || it[key] is MatchState.Failed }.getValue(key)
    }

    @Test
    fun `a strong hit on the title ends the search`() = runTest {
        answer(mapOf("Dr. Stone" to listOf(result(1, "Dr. STONE"), result(2, "Dr. Stone: Reboot"))))
        val matcher = matcher()
        matcher.enqueue(trackerId, listOf(MatchSubject(10, "Dr. Stone", listOf("Doctor Stone"))))

        val state = matcher.awaitState(10) as MatchState.Found
        state.candidates.map { it.result.remote_id } shouldContainExactly listOf(1L, 2L)
        state.candidates.first().score shouldBe 100
        queries shouldContainExactly listOf("Dr. Stone")
    }

    @Test
    fun `without a strong hit it tries the short title and alternative titles, ranking all results`() = runTest {
        answer(
            mapOf(
                "The Apothecary Diaries: Maomao" to listOf(result(5, "Apothecary Diaries Spin-off")),
                "The Apothecary Diaries" to emptyList(),
                "Kusuriya no Hitorigoto" to listOf(result(7, "Kusuriya no Hitorigoto")),
            ),
        )
        val matcher = matcher()
        val subject = MatchSubject(11, "The Apothecary Diaries: Maomao", listOf("Kusuriya no Hitorigoto"))
        matcher.enqueue(trackerId, listOf(subject))

        val state = matcher.awaitState(11) as MatchState.Found
        queries shouldContainExactly listOf(
            "The Apothecary Diaries: Maomao",
            "The Apothecary Diaries",
            "Kusuriya no Hitorigoto",
        )
        // Exact alternative-title hit ranks first
        state.best!!.result.remote_id shouldBe 7L
        state.best!!.score shouldBe 100
    }

    @Test
    fun `a tracker's own alternative titles count`() = runTest {
        answer(mapOf("Frieren" to listOf(result(3, "Sousou no Frieren", "Frieren"))))
        val matcher = matcher()
        matcher.enqueue(trackerId, listOf(MatchSubject(12, "Frieren", emptyList())))
        (matcher.awaitState(12) as MatchState.Found).best!!.score shouldBe 100
    }

    @Test
    fun `429 is retried after backing off`() = runTest {
        var calls = 0
        coEvery { tracker.search(any()) } answers {
            if (calls++ == 0) throw HttpException(429)
            listOf(result(1, "Berserk"))
        }
        val matcher = matcher()
        matcher.enqueue(trackerId, listOf(MatchSubject(13, "Berserk", emptyList())))
        (matcher.awaitState(13) as MatchState.Found).best!!.score shouldBe 100
        calls shouldBe 2
    }

    @Test
    fun `other errors fail the entry, and a retry searches again`() = runTest {
        var fail = true
        coEvery { tracker.search(any()) } answers {
            if (fail) throw IllegalStateException("offline")
            listOf(result(1, "Berserk"))
        }
        val matcher = matcher()
        val subject = MatchSubject(14, "Berserk", emptyList())
        matcher.enqueue(trackerId, listOf(subject))
        matcher.awaitState(14) shouldBe MatchState.Failed("offline")

        // Known entries aren't queued again
        fail = false
        matcher.enqueue(trackerId, listOf(subject))
        matcher.states.value[MatchKey(trackerId, 14)] shouldBe MatchState.Failed("offline")

        matcher.retry(trackerId, subject)
        (matcher.awaitState(14) as MatchState.Found).best!!.score shouldBe 100
    }

    @Test
    fun `stopping drops pending searches and keeps the queue stopped until resumed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { tracker.search(any()) } coAnswers {
            gate.await()
            listOf(result(1, firstArg()))
        }
        val matcher = matcher()
        val subjects = listOf(MatchSubject(20, "One", emptyList()), MatchSubject(21, "Two", emptyList()))
        matcher.enqueue(trackerId, subjects)
        matcher.states.first { it[MatchKey(trackerId, 20)] == MatchState.Searching }

        matcher.cancel(trackerId)
        matcher.states.value.keys.filter { it.trackerId == trackerId } shouldBe emptyList()
        matcher.paused.value shouldBe setOf(trackerId)
        matcher.enqueue(trackerId, subjects)
        matcher.states.value.keys shouldBe emptySet()

        gate.complete(Unit)
        matcher.resume(trackerId, subjects)
        matcher.paused.value shouldBe emptySet()
        (matcher.awaitState(20) as MatchState.Found).best!!.score shouldBe 100
        (matcher.awaitState(21) as MatchState.Found).best!!.score shouldBe 100
    }

    @Test
    fun `bulk binding goes through AddTracks for each entry and reports the count`() = runTest {
        val matcher = matcher()
        val a = result(1, "A")
        val b = result(2, "B")
        coEvery { addTracks.bind(tracker, b, 31) } throws IllegalStateException("nope")
        matcher.bindAll(trackerId, listOf(30L to a, 31L to b))

        matcher.messages.first() shouldBe LibraryTrackMatcher.Message.BulkDone(linked = 1, total = 2)
        coVerify { addTracks.bind(tracker, a, 30) }
        a.manga_id shouldBe 30L
        matcher.binding.value shouldBe emptySet()
    }
}
