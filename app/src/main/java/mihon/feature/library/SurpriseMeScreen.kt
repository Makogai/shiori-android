package mihon.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.IosDefaults
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.readableAccent
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SurpriseMeViewModel(
    private val getLibraryManga: GetLibraryManga,
    private val getTracksPerManga: GetTracksPerManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackerManager: TrackerManager,
    private val preferences: LibraryExtrasPreferences,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Picked(val candidate: SurpriseLogic.Candidate, val hasMore: Boolean) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var candidates = emptyList<SurpriseLogic.Candidate>()
    private val seen = mutableSetOf<Long>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val library = getLibraryManga.subscribe().first().distinctBy { it.id }
            val badges = getTracksPerManga.await()
                .mapNotNull { (id, tracks) -> TrackBadge.summary(tracks, trackerManager)?.let { id to it } }
                .toMap()
            candidates = SurpriseLogic.candidates(library, badges, preferences.stalledDays.get(), Clock.System.now())
            another()
        }
    }

    fun another() {
        if (seen.size >= candidates.size) seen.clear()
        val next = SurpriseLogic.pick(candidates, seen)
        if (next == null) {
            _state.value = State.Empty
            return
        }
        seen += next.item.id
        _state.value = State.Picked(next, candidates.size > 1)
    }

    suspend fun nextChapterId(mangaId: Long): Long? = withContext(Dispatchers.IO) {
        LibraryInsights.nextChapter(getChaptersByMangaId.await(mangaId, applyScanlatorFilter = true))?.id
    }
}

/** "Surprise me": a random unread series worth starting or getting back to, with why it was picked. */
class SurpriseMeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<SurpriseMeViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.surprise_me),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            when (val current = state) {
                SurpriseMeViewModel.State.Loading -> LoadingScreen(Modifier.padding(padding))
                SurpriseMeViewModel.State.Empty -> EmptyScreen(
                    stringRes = MR.strings.surprise_me_empty,
                    modifier = Modifier.padding(padding),
                    icon = MaterialSymbols.Rounded.CollectionsBookmark,
                )
                is SurpriseMeViewModel.State.Picked -> Column(
                    modifier = Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    AnimatedContent(targetState = current, label = "surprise") { picked ->
                        SurpriseCard(
                            picked = picked,
                            onOpen = { navigator.push(MangaScreen(picked.candidate.item.id)) },
                            onRead = {
                                val mangaId = picked.candidate.item.id
                                viewModel.viewModelScope.launch {
                                    val chapterId = viewModel.nextChapterId(mangaId)
                                    if (chapterId != null) {
                                        context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
                                    } else {
                                        navigator.push(MangaScreen(mangaId))
                                    }
                                }
                            },
                            onAnother = viewModel::another,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurpriseCard(
    picked: SurpriseMeViewModel.State.Picked,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onAnother: () -> Unit,
) {
    val item = picked.candidate.item
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.card, RoundedCornerShape(IosDefaults.SectionRadius + 4.dp))
            .padding(20.dp),
    ) {
        MangaCover.Book(data = item.manga.asMangaCover(), modifier = Modifier.width(180.dp), onClick = onOpen)
        Text(
            text = item.manga.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = reasonText(picked.candidate.reason),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.readableAccent(MaterialTheme.colorScheme.card),
            textAlign = TextAlign.Center,
        )
        Text(
            text = pluralStringResource(MR.plurals.surprise_me_waiting, item.unreadCount.toInt(), item.unreadCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                Text(stringResource(MR.strings.surprise_me_read))
            }
            OutlinedButton(onClick = onAnother, enabled = picked.hasMore, modifier = Modifier.weight(1f)) {
                Text(stringResource(MR.strings.surprise_me_another))
            }
        }
    }
}

@Composable
private fun reasonText(reason: SurpriseLogic.Reason): String = when (reason) {
    SurpriseLogic.Reason.PlanToRead -> stringResource(MR.strings.surprise_me_plan_to_read)
    SurpriseLogic.Reason.NotStarted -> stringResource(MR.strings.surprise_me_not_started)
    is SurpriseLogic.Reason.Stalled -> pluralStringResource(
        MR.plurals.surprise_me_stalled,
        reason.days.toInt(),
        reason.days,
    )
}
