package mihon.feature.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import mihon.feature.common.utils.seriesKey
import mihon.feature.pagebookmarks.PageBookmark
import mihon.feature.pagebookmarks.PageBookmarks
import mihon.feature.queue.BingeQueue
import mihon.feature.ratings.PersonalRatings
import mihon.feature.ratings.TopList
import mihon.feature.recommendations.AniListRecommendations
import mihon.feature.recommendations.RecommendationsLoader
import mihon.feature.recommendations.RecommendationsState
import mihon.feature.rhythm.ReleaseRhythm
import mihon.feature.rhythm.ReleaseRhythmLogic
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import kotlin.time.Clock

/** What the manga details page adds under the description: more like this, rhythm, score, queue, lists, page bookmarks. */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
class MangaExtrasViewModel(
    @Assisted private val mangaId: Long,
    getManga: GetManga,
    chapterRepository: ChapterRepository,
    private val queue: BingeQueue,
    private val ratings: PersonalRatings,
    private val pageBookmarks: PageBookmarks,
    private val recommendations: AniListRecommendations,
    libraryPreferences: LibraryPreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaExtrasViewModel
    }

    /** A page bookmark with the id of its chapter, when that chapter is still listed. */
    @Immutable
    data class Bookmark(val bookmark: PageBookmark, val chapterId: Long?)

    @Immutable
    data class State(
        val manga: Manga,
        val rhythm: ReleaseRhythm?,
        val score: Int?,
        val inQueue: Boolean,
        val topLists: List<TopList>,
        val bookmarks: List<Bookmark>,
    )

    val showPersonalBox = libraryPreferences.detailsPersonalBox
    val showMoreLikeThis = libraryPreferences.detailsMoreLikeThis
    val showEnglishTitles = libraryPreferences.detailsMoreLikeThisEnglish
    val moreLikeThisExpanded = libraryPreferences.detailsMoreLikeThisExpanded

    private val mangaFlow = getManga.subscribe(mangaId)

    val state: StateFlow<State?> = mangaFlow.flatMapLatest { manga ->
        val key = manga.seriesKey
        combine(
            chapterRepository.getChapterByMangaIdAsFlow(mangaId),
            pageBookmarks.subscribe(manga),
            ratings.scores.changes(),
            ratings.topLists.changes(),
            queue.changes(),
        ) { chapters, bookmarks, scores, lists, queued ->
            val chapterIds = chapters.associate { it.url to it.id }
            State(
                manga = manga,
                rhythm = ReleaseRhythmLogic.infer(
                    dates = chapters.map { (it.dateUpload ?: it.dateFetch).toEpochMilliseconds() },
                    now = Clock.System.now().toEpochMilliseconds(),
                ),
                score = scores[key],
                inQueue = key in queued,
                topLists = lists,
                bookmarks = bookmarks.map { Bookmark(it, chapterIds[it.chapterUrl]) },
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** AniList's recommendations, asked for only once "More like this" is expanded. */
    private val recommendationsLoader = RecommendationsLoader(viewModelScope) {
        val manga = mangaFlow.first()
        try {
            if (manga.isLocal()) emptyList() else recommendations.get(manga)
        } catch (e: Exception) {
            if (e !is CancellationException) logcat(LogPriority.WARN, e) { "AniList recommendations failed" }
            throw e
        }
    }
    val recommended: StateFlow<RecommendationsState> = recommendationsLoader.state

    /** Loads "More like this" unless it's loading or loaded; retries after a failure. */
    fun loadRecommendations() = recommendationsLoader.load()

    fun setScore(score: Int?) {
        state.value?.manga?.let { ratings.setScore(it, score) }
    }

    fun toggleQueue() {
        state.value?.manga?.let(queue::toggle)
    }

    fun toggleTopList(id: Long) {
        state.value?.manga?.let { ratings.toggle(id, it) }
    }

    fun createTopList(name: String) {
        val manga = state.value?.manga ?: return
        ratings.toggle(ratings.createList(name).id, manga)
    }

    fun removeBookmark(bookmark: PageBookmark) = pageBookmarks.remove(bookmark)
}
