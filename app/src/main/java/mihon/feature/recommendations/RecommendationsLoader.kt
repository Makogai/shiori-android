package mihon.feature.recommendations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a series' "More like this" row is. */
sealed interface RecommendationsState {
    /** Not asked for yet: the row is collapsed. */
    data object Idle : RecommendationsState
    data object Loading : RecommendationsState
    data class Loaded(val items: List<Recommendation>) : RecommendationsState

    /** AniList has none, or couldn't match the title. */
    data object Empty : RecommendationsState

    /** Offline or AniList failed; [RecommendationsLoader.load] tries again. */
    data object Failed : RecommendationsState
}

/**
 * Loads "More like this" only when asked (the row is expanded), once per page. After a failure the next [load]
 * tries again; while loading or once answered it does nothing.
 */
class RecommendationsLoader(
    private val scope: CoroutineScope,
    private val fetch: suspend () -> List<Recommendation>,
) {
    private val mutableState = MutableStateFlow<RecommendationsState>(RecommendationsState.Idle)
    val state: StateFlow<RecommendationsState> = mutableState.asStateFlow()

    fun load() {
        val current = mutableState.value
        if (current != RecommendationsState.Idle && current != RecommendationsState.Failed) return
        mutableState.value = RecommendationsState.Loading
        scope.launch {
            mutableState.value = try {
                val items = fetch()
                if (items.isEmpty()) RecommendationsState.Empty else RecommendationsState.Loaded(items)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                RecommendationsState.Failed
            }
        }
    }
}
