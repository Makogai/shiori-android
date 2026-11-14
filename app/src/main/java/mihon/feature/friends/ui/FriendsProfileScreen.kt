package mihon.feature.friends.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.feature.friends.data.FriendsAccount
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.LeaguePeriod
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

class FriendsProfileScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val viewModel = metroViewModel<FriendsProfileViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is FriendsProfileViewModel.Event.Error ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.friends_error, event.message))
                    FriendsProfileViewModel.Event.Saved -> navigator.pop()
                    FriendsProfileViewModel.Event.Reset -> navigator.pop()
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.friends_profile),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            val profile = state.account.profile
            if (profile == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            var name by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
            var title by rememberSaveable(profile.title) { mutableStateOf(profile.title) }
            var avatarUri by remember { mutableStateOf<Uri?>(null) }
            var confirmReset by remember { mutableStateOf(false) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) avatarUri = uri
            }
            val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            val valid = name.trim().length in 1..MAX_NAME

            val myId = state.account.userId
            CompositionLocalProvider(
                LocalOpenPlayer provides { id -> if (id != myId) navigator.push(PlayerProfileScreen(id)) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    val stats = state.profile
                    val avatar = @Composable {
                        val player = stats?.player
                        if (player != null) {
                            LevelAvatar(
                                name = name.ifBlank { "?" },
                                url = avatarUri?.toString() ?: profile.avatarUrl,
                                level = player.level.level,
                                levelProgress = player.level.progress,
                                tier = player.tier,
                                size = 104.dp,
                                team = ArenaColors.Me,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        onClickLabel = stringResource(MR.strings.friends_pick_avatar),
                                        onClick = pick,
                                    ),
                            )
                        } else {
                            FriendAvatar(
                                name = name.ifBlank { "?" },
                                url = avatarUri?.toString() ?: profile.avatarUrl,
                                size = 104.dp,
                                ring = GoldColor,
                                modifier = Modifier.clip(CircleShape).clickable(onClick = pick),
                            )
                        }
                    }
                    if (stats != null) {
                        ProfileHeader(stats, avatar = avatar)
                        PlayerStatsSections(stats)
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { avatar() }
                    }

                    ArenaSection(stringResource(MR.strings.friends_edit_profile), "✏️") {
                        OutlinedButton(onClick = pick) { Text(stringResource(MR.strings.friends_pick_avatar)) }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(MAX_NAME) },
                            label = { Text(stringResource(MR.strings.friends_display_name)) },
                            supportingText = { Text("${name.trim().length}/$MAX_NAME") },
                            isError = !valid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(MR.strings.friends_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(MR.strings.friends_title_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = title == null,
                                onClick = { title = null },
                                label = { Text(stringResource(MR.strings.friends_title_none)) },
                            )
                            (state.earnedTitles + listOfNotNull(profile.title)).distinct().forEach { text ->
                                FilterChip(selected = title == text, onClick = { title = text }, label = { Text(text) })
                            }
                        }
                        Button(
                            enabled = valid && !state.busy,
                            onClick = {
                                scope.launch {
                                    val jpeg = avatarUri?.let {
                                        runCatching { AvatarImage.fromUri(context, it) }.getOrNull()
                                    }
                                    viewModel.save(name.trim(), jpeg, title)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(MR.strings.action_save))
                        }
                        TextButton(
                            onClick = { confirmReset = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(MR.strings.friends_reset_player))
                        }
                    }
                }
            }

            if (confirmReset) {
                AlertDialog(
                    onDismissRequest = { confirmReset = false },
                    title = { Text(stringResource(MR.strings.friends_reset_player)) },
                    text = { Text(stringResource(MR.strings.friends_reset_player_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmReset = false
                                viewModel.reset()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(MR.strings.action_reset))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmReset = false }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FriendsProfileViewModel(
    private val repository: FriendsRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(repository.account, repository.snapshot, busy) { account, snapshot, busy ->
        val userId = account.userId
        val month = if (snapshot != null && userId != null) {
            LeagueUiModel.build(snapshot, userId, LeaguePeriod.MONTH)
        } else {
            null
        }
        State(
            account = account,
            earnedTitles = month?.titlesOf(userId!!)?.map { it.text }.orEmpty(),
            profile = if (snapshot != null &&
                userId != null
            ) {
                PlayerProfileModel.build(snapshot, userId, userId)
            } else {
                null
            },
            busy = busy,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            State(repository.account.value),
        )

    fun save(name: String, avatar: ByteArray?, title: String?) = run(Event.Saved) {
        repository.updateProfile(name, avatar, title)
    }

    fun reset() = run(Event.Reset) { repository.resetPlayer() }

    private fun run(done: Event, block: suspend () -> Unit) {
        busy.value = true
        viewModelScope.launch {
            try {
                block()
                _events.send(done)
            } catch (e: Exception) {
                _events.send(Event.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                busy.value = false
            }
        }
    }

    data class State(
        val account: FriendsAccount,
        val earnedTitles: List<String> = emptyList(),
        val profile: PlayerProfileModel? = null,
        val busy: Boolean = false,
    )

    sealed interface Event {
        data class Error(val message: String) : Event
        data object Saved : Event
        data object Reset : Event
    }
}
