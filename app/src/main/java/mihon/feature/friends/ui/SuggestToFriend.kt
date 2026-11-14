package mihon.feature.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.FriendsAccount
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.SuggestionRules
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SuggestToFriendViewModel(private val repository: FriendsRepository) : ViewModel() {

    /** Signed in to the Arena. */
    val canSuggest: StateFlow<Boolean> = repository.account
        .map(::signedIn)
        .stateIn(viewModelScope, SharingStarted.Eagerly, signedIn(repository.account.value))

    /** The Arena's other players, null while loading. */
    val players: StateFlow<List<BoardRow>?>
        field = MutableStateFlow(null)

    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()

    fun loadPlayers(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val me = repository.account.value.userId
                players.value = repository.arenaPlayers()
                    .filter { it.userId != me }
                    .sortedBy { it.displayName.lowercase() }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                players.value = emptyList()
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun send(
        friends: List<BoardRow>,
        manga: Manga,
        source: Source,
        note: String,
        onResult: (Result<List<BoardRow>>) -> Unit,
    ) {
        if (_sending.value) return
        _sending.value = true
        viewModelScope.launch {
            val result = runCatching {
                repository.suggest(
                    friends = friends.map { it.userId },
                    sourceId = source.id,
                    sourceName = source.name,
                    mangaUrl = manga.url,
                    title = manga.title,
                    coverUrl = manga.thumbnailUrl,
                    genres = manga.genre,
                    note = note,
                )
                friends
            }
            result.exceptionOrNull()?.let { logcat(LogPriority.ERROR, it) }
            _sending.value = false
            onResult(result)
        }
    }

    private fun signedIn(account: FriendsAccount) = account.configured && account.userId != null
}

/** The manga details "Suggest to a friend" sheet: pick Arena players, add a note, send. */
@Composable
fun SuggestToFriendSheet(
    viewModel: SuggestToFriendViewModel,
    manga: Manga,
    source: Source,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val players by viewModel.players.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPlayers { context.toast(context.stringResource(MR.strings.friends_error, it)) }
    }
    LaunchedEffect(players) {
        players?.singleOrNull()?.let { selected = setOf(it.userId) }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "📬 " + stringResource(MR.strings.friends_suggest_to_friend),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val list = players
            when {
                list == null -> Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text(
                    text = stringResource(MR.strings.friends_suggestion_no_players),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> list.forEach { player ->
                    val checked = player.userId in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox) {
                                selected = if (it) selected + player.userId else selected - player.userId
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FriendAvatar(name = player.displayName, url = player.avatarUrl, size = 40.dp)
                        Text(
                            text = player.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(SuggestionRules.MAX_NOTE) },
                label = { Text(stringResource(MR.strings.friends_suggestion_note)) },
                supportingText = { Text("${note.trim().length}/${SuggestionRules.MAX_NOTE}") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !sending && list.orEmpty().any { it.userId in selected },
                onClick = {
                    val friends = list.orEmpty().filter { it.userId in selected }
                    viewModel.send(friends, manga, source, note) { result ->
                        result.onSuccess { sent ->
                            context.toast(
                                sent.singleOrNull()?.let {
                                    context.stringResource(MR.strings.friends_suggested_to, it.displayName)
                                } ?: context.pluralStringResource(
                                    MR.plurals.friends_suggested_to_friends,
                                    sent.size,
                                    sent.size,
                                ),
                            )
                            onDismissRequest()
                        }
                        result.onFailure {
                            context.toast(
                                context.stringResource(MR.strings.friends_error, it.message ?: it.javaClass.simpleName),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(MR.strings.friends_suggestion_send))
            }
        }
    }
}
