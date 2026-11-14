package mihon.feature.sourcehealth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import tachiyomi.domain.source.model.Source as DomainSource

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SourceHealthViewModel(
    private val monitor: SourceHealthMonitor,
    preferences: SourceHealthPreferences,
    getLibraryManga: GetLibraryManga,
    private val sourceManager: SourceManager,
) : ViewModel() {

    /** The source whose site was opened in a WebView, checked again once the user is back. */
    private var webViewSource: Long? = null

    val state: StateFlow<State> = combine(
        getLibraryManga.subscribe(),
        monitor.records,
        monitor.checking,
        preferences.dailyCheck.changes(),
        preferences.lastCheck.changes(),
    ) { library, records, checking, dailyCheck, lastCheck ->
        val now = Clock.System.now().toEpochMilliseconds()
        val items = library
            .map { it.manga }
            .distinctBy { it.id }
            .filter { it.source != LocalSource.ID }
            .groupBy({ it.source }, { it.id })
            .map { (sourceId, mangaIds) ->
                val source = sourceManager.getOrStub(sourceId)
                val record = records[sourceId]
                Item(
                    source = DomainSource(
                        id = source.id,
                        lang = source.lang,
                        name = source.name,
                        supportsLatest = false,
                        isStub = source is StubSource,
                    ),
                    status = SourceHealthLogic.status(record, now),
                    record = record,
                    mangaIds = mangaIds,
                    homeUrl = (source as? HttpSource)?.getHomeUrl(),
                    checking = sourceId in checking,
                )
            }
            .sortedWith(
                compareByDescending<Item> { it.status }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.source.name },
            )
        State(
            isLoading = false,
            items = items,
            dailyCheck = dailyCheck,
            lastCheck = lastCheck.takeIf { it > 0 },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), State())

    fun check(sourceId: Long? = null) {
        viewModelScope.launch { monitor.check(sourceId) }
    }

    fun openedWebView(sourceId: Long) {
        webViewSource = sourceId
    }

    /** Called when the screen shows again, as after its WebView closes. */
    fun onResume() {
        val sourceId = webViewSource ?: return
        webViewSource = null
        check(sourceId)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<Item> = emptyList(),
        val dailyCheck: Boolean = true,
        val lastCheck: Long? = null,
    )

    @Immutable
    data class Item(
        val source: DomainSource,
        val status: SourceHealthStatus,
        val record: SourceHealthRecord?,
        /** The source's library series. */
        val mangaIds: List<Long>,
        /** Null when the extension is not installed or has no website. */
        val homeUrl: String?,
        val checking: Boolean,
    ) {
        val installed: Boolean
            get() = !source.isStub
    }
}
