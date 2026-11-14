package mihon.feature.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import mihon.feature.friends.data.FriendsRepository
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/** The Arena model for the recap screens, built from the loaded snapshot. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class RecapsViewModel(repository: FriendsRepository) : ViewModel() {

    val arena: StateFlow<ArenaUiModel?> = combine(repository.account, repository.snapshot) { account, snapshot ->
        val myId = account.userId
        if (snapshot != null && myId != null) ArenaUiModel.build(snapshot, myId) else null
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** "All recaps": every past week, newest first. */
class RecapListScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<RecapsViewModel>()
        val arena by viewModel.arena.collectAsStateWithLifecycle()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.friends_recaps),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val model = arena
            when {
                model == null -> LoadingScreen(Modifier.padding(contentPadding))
                model.recaps.isEmpty() -> EmptyScreen(MR.strings.friends_recap_none, Modifier.padding(contentPadding))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    items(model.recaps, key = { it.week.challengeId }) { recap ->
                        RecapListRow(recap, model) { navigator.push(RecapDetailScreen(recap.week.challengeId)) }
                    }
                }
            }
        }
    }
}

/** One week's recap, with Share. */
class RecapDetailScreen(private val challengeId: Int) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<RecapsViewModel>()
        val arena by viewModel.arena.collectAsStateWithLifecycle()
        val model = arena
        val recap = model?.recaps?.firstOrNull { it.week.challengeId == challengeId }
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = recap?.let { stringResource(MR.strings.friends_recap_title, it.number) }.orEmpty(),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (model == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            if (recap == null) {
                EmptyScreen(MR.strings.friends_recap_none, Modifier.padding(contentPadding))
                return@Scaffold
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                RecapCard(recap, model)
            }
        }
    }
}
