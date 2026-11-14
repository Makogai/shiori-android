package mihon.feature.updatepreview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracksPerManga
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Immutable
data class UpdatePreview(
    val willUpdate: List<Entry>,
    /** In [SkipReason] order, empty groups left out. */
    val skipped: List<Pair<SkipReason, List<Entry>>>,
) {
    @Immutable
    data class Entry(val item: LibraryManga, val sourceName: String)

    val skippedCount: Int get() = skipped.sumOf { it.second.size }

    fun matching(query: String): UpdatePreview {
        val q = query.trim()
        if (q.isEmpty()) return this
        val match = { entry: Entry ->
            entry.item.manga.title.contains(q, ignoreCase = true) || entry.sourceName.contains(q, ignoreCase = true)
        }
        return UpdatePreview(
            willUpdate = willUpdate.filter(match),
            skipped = skipped.map { (reason, entries) -> reason to entries.filter(match) }
                .filter { it.second.isNotEmpty() },
        )
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpdatePreviewViewModel(
    getLibraryManga: GetLibraryManga,
    getTracksPerManga: GetTracksPerManga,
    libraryPreferences: LibraryPreferences,
    private val sourceManager: SourceManager,
    private val trackerManager: TrackerManager,
    private val fetchInterval: FetchInterval,
) : ViewModel() {

    private val rules = combine(
        libraryPreferences.updateCategories.changes(),
        libraryPreferences.updateCategoriesExclude.changes(),
        libraryPreferences.autoUpdateMangaRestrictions.changes(),
    ) { included, excluded, restrictions ->
        val timeZone = TimeZone.currentSystemDefault()
        UpdateRules(
            includedCategories = included.mapTo(mutableSetOf()) { it.toLong() },
            excludedCategories = excluded.mapTo(mutableSetOf()) { it.toLong() },
            restrictions = restrictions,
            fetchWindowUpperBound = fetchInterval
                .getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
                .endInclusive,
        )
    }

    val state: StateFlow<UpdatePreview?> = combine(
        getLibraryManga.subscribe().map { list -> list.distinctBy { it.id } },
        getTracksPerManga.subscribe(),
        rules,
    ) { library, tracks, rules ->
        val sourceNames = library.map { it.manga.source }.distinct()
            .associateWith { sourceManager.getOrStub(it).name }
        val entries = library.sortedBy { it.manga.title.lowercase() }.map { item ->
            val reason = LibraryUpdateDecision.skipReason(
                item,
                rules,
                trackerFinished = trackerManager.isFinished(tracks[item.id].orEmpty()),
            )
            reason to UpdatePreview.Entry(item, sourceNames[item.manga.source].orEmpty())
        }
        val skipped = entries.filter { it.first != null }.groupBy({ it.first!! }, { it.second })
        UpdatePreview(
            willUpdate = entries.filter { it.first == null }.map { it.second },
            skipped = SkipReason.entries.mapNotNull { reason -> skipped[reason]?.let { reason to it } },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)
}
