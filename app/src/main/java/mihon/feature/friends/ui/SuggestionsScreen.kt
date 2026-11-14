package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionStatus
import mihon.feature.friends.domain.LeagueHashing
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/** Everything suggested to me and by me, in two tabs. */
class SuggestionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<SuggestionsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is SuggestionsViewModel.Event.OpenManga -> navigator.push(MangaScreen(event.mangaId, true))
                    is SuggestionsViewModel.Event.Search -> navigator.push(GlobalSearchScreen(event.query))
                    is SuggestionsViewModel.Event.Error -> snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.friends_error, event.message),
                    )
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.friends_suggestions),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    SuggestionsTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { viewModel.setTab(tab) },
                            text = {
                                TabText(
                                    text = stringResource(
                                        when (tab) {
                                            SuggestionsTab.FOR_ME -> MR.strings.friends_suggestions_for_me
                                            SuggestionsTab.SENT -> MR.strings.friends_suggestions_sent
                                        },
                                    ),
                                    badgeCount = state.badge.takeIf { tab == SuggestionsTab.FOR_ME && it > 0 },
                                )
                            },
                        )
                    }
                }
                if (state.busy && !state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                PullRefresh(refreshing = state.busy && state.loaded, enabled = true, onRefresh = viewModel::refresh) {
                    val forMe = state.tab == SuggestionsTab.FOR_ME
                    val list = if (forMe) state.forMe else state.sent
                    if (state.loaded && list.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (forMe) {
                                    MR.strings.friends_suggestions_empty_for_me
                                } else {
                                    MR.strings.friends_suggestions_empty_sent
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(MaterialTheme.padding.large),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(list, key = { it.id }) { suggestion ->
                                val other = if (forMe) suggestion.fromUser else suggestion.toUser
                                SuggestionRow(
                                    suggestion = suggestion,
                                    incoming = forMe,
                                    other = state.players[other],
                                    working = suggestion.id in state.working,
                                    onOpen = { viewModel.open(suggestion) },
                                    onAdd = { viewModel.addToLibrary(suggestion) },
                                    onDismiss = { viewModel.dismiss(suggestion) },
                                    onWithdraw = { viewModel.withdraw(suggestion) },
                                    onRead = { viewModel.markRead(suggestion) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        state.dialog?.let { dialog ->
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = viewModel::dismissDialog,
                onEditCategories = { navigator.push(CategoryScreen()) },
                onConfirm = { include, _ -> viewModel.confirmCategories(dialog, include) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    incoming: Boolean,
    other: BoardRow?,
    working: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onWithdraw: () -> Unit,
    onRead: () -> Unit,
) {
    val otherName = other?.displayName ?: stringResource(MR.strings.friends_unknown_player)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SharedCover(
            coverUrl = suggestion.coverUrl,
            seriesKey = remember(suggestion.sourceId, suggestion.mangaUrl) {
                suggestion.sourceId.toLongOrNull()?.let { LeagueHashing.seriesKey(it, suggestion.mangaUrl) }
            },
            title = suggestion.title,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            suggestion.sourceName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FriendAvatar(
                    name = otherName,
                    url = other?.avatarUrl,
                    size = 20.dp,
                    modifier = Modifier.openPlayer(other?.userId),
                )
                Text(
                    text = stringResource(
                        if (incoming) MR.strings.friends_suggestion_from else MR.strings.friends_suggestion_to,
                        otherName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "· " + relativeTimeSpanString(suggestion.created.toEpochMilli()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            suggestion.note?.let {
                Text(
                    text = "“$it”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                )
            }
            StatusChip(suggestion.status)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incoming) {
                    OutlinedButton(onClick = onOpen, enabled = !working) {
                        Text(stringResource(MR.strings.friends_suggestion_open))
                    }
                    if (suggestion.pending) {
                        OutlinedButton(onClick = onAdd, enabled = !working) {
                            Text(stringResource(MR.strings.friends_suggestion_add))
                        }
                        OutlinedButton(onClick = onDismiss, enabled = !working) {
                            Text(stringResource(MR.strings.friends_suggestion_dismiss))
                        }
                    }
                    if (suggestion.status != SuggestionStatus.READ) {
                        OutlinedButton(onClick = onRead, enabled = !working) {
                            Text(stringResource(MR.strings.friends_suggestion_mark_read))
                        }
                    }
                } else if (suggestion.pending) {
                    OutlinedButton(onClick = onWithdraw, enabled = !working) {
                        Text(stringResource(MR.strings.friends_suggestion_withdraw))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: SuggestionStatus) {
    val (label, container, content) = when (status) {
        SuggestionStatus.PENDING -> Triple(
            MR.strings.friends_suggestion_pending,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SuggestionStatus.ADDED -> Triple(MR.strings.friends_suggestion_added, AddedColor, Color.White)
        SuggestionStatus.DISMISSED -> Triple(
            MR.strings.friends_suggestion_dismissed,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SuggestionStatus.READ -> Triple(MR.strings.friends_suggestion_read, ArenaColors.Xp, Color.White)
    }
    Text(
        text = stringResource(label),
        color = content,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

val AddedColor = Color(0xFF34C759)
