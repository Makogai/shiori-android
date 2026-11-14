package mihon.feature.tracklibrary

import androidx.compose.runtime.Immutable
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

@Immutable
data class NeedsRow(
    val manga: Manga,
    val subject: MatchSubject,
    val state: MatchState?,
    val skipped: Boolean,
    val binding: Boolean,
) {
    val best get() = state.best
}

@Immutable
data class TrackedRow(val manga: Manga, val track: Track, val subject: MatchSubject)

@Immutable
data class TrackLibraryContent(
    val total: Int = 0,
    val tracked: List<TrackedRow> = emptyList(),
    /** Untracked entries shown: all of them with "Show skipped", else the unskipped ones. */
    val needs: List<NeedsRow> = emptyList(),
    val toCheck: Int = 0,
    val skippedCount: Int = 0,
) {
    val strongMatches get() = TrackLibraryRows.strongMatches(needs)

    /** Entries still waiting for, or in, a search. */
    val pending get() = needs.count { it.state is MatchState.Queued || it.state is MatchState.Searching }
}

object TrackLibraryRows {

    fun build(
        library: List<Manga>,
        tracks: Map<Long, List<Track>>,
        trackerId: Long,
        skipped: Set<Long>,
        states: Map<MatchKey, MatchState>,
        binding: Set<MatchKey>,
        showSkipped: Boolean,
        subjectOf: (Manga) -> MatchSubject = TrackLibraryRows::subjectOf,
    ): TrackLibraryContent {
        val sorted = library.distinctBy { it.id }.sortedBy { it.title.lowercase() }
        val tracked = mutableListOf<TrackedRow>()
        val needs = mutableListOf<NeedsRow>()
        var toCheck = 0
        var skippedCount = 0
        sorted.forEach { manga ->
            val subject = subjectOf(manga)
            val track = tracks[manga.id]?.find { it.trackerId == trackerId }
            if (track != null) {
                tracked += TrackedRow(manga, track, subject)
                return@forEach
            }
            val isSkipped = manga.id in skipped
            if (isSkipped) skippedCount++ else toCheck++
            if (isSkipped && !showSkipped) return@forEach
            val key = MatchKey(trackerId, manga.id)
            needs += NeedsRow(manga, subject, states[key], isSkipped, key in binding)
        }
        return TrackLibraryContent(
            total = sorted.size,
            tracked = tracked,
            needs = needs,
            toCheck = toCheck,
            skippedCount = skippedCount,
        )
    }

    fun subjectOf(manga: Manga) = MatchSubject(
        mangaId = manga.id,
        title = manga.title,
        altTitles = TitleMatcher.altTitlesFromDescription(manga.description, manga.title),
    )

    /** What "Confirm all strong matches" binds: shown, unskipped, not binding, best result at or above 90 %. */
    fun strongMatches(rows: List<NeedsRow>): List<NeedsRow> {
        return rows.filter { !it.skipped && !it.binding && it.best?.isStrong == true }
    }
}
