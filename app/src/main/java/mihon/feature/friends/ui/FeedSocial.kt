package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.friends.data.ArenaSocial
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.FeedRow
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.FeedComment
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.Reactions
import mihon.feature.friends.domain.ReadKey
import mihon.feature.friends.domain.ReadSocial
import mihon.feature.friends.domain.chapterNumber
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/** Reactions and comments under feed reads (README v6, section 3). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FeedSocialViewModel(
    private val social: ArenaSocial,
    private val repository: FriendsRepository,
) : ViewModel() {

    val reads: StateFlow<Map<ReadKey, ReadSocial>> = social.social

    val players: StateFlow<Map<String, BoardRow>> = repository.snapshot
        .map { snapshot -> snapshot?.boards?.get(LeaguePeriod.ALL).orEmpty().associateBy { it.userId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val myId: String? get() = repository.myId

    fun load(keys: List<ReadKey>) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            try {
                social.load(keys)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Loading reactions and comments failed" }
            }
        }
    }

    fun react(read: ReadKey, emoji: String, onError: (String) -> Unit) {
        val me = myId ?: return
        val current = reads.value[read]?.reactions?.firstOrNull { it.reactorId == me }?.emoji
        launch(onError) { social.react(read, Reactions.toggle(current, emoji)) }
    }

    fun comment(read: ReadKey, text: String, onError: (String) -> Unit, onSent: () -> Unit) =
        launch(onError) {
            social.comment(read, text)
            onSent()
        }

    fun delete(comment: FeedComment, onError: (String) -> Unit) = launch(onError) { social.deleteComment(comment) }

    private fun launch(onError: (String) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e)
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

/** Loads reactions and comments for [rows] whenever the set of reads changes. */
@Composable
fun LoadFeedSocial(rows: List<FeedRow>) {
    val viewModel = metroViewModel<FeedSocialViewModel>()
    val keys = remember(rows) { rows.mapNotNull { it.readKey }.distinct() }
    LaunchedEffect(keys) { viewModel.load(keys) }
}

/** The chip row under a read: emoji with counts, then 💬 N; tapping 💬 opens the comments sheet. */
@Composable
fun FeedReactions(row: FeedRow, modifier: Modifier = Modifier) {
    val read = row.readKey ?: return
    val viewModel = metroViewModel<FeedSocialViewModel>()
    val reads by viewModel.reads.collectAsStateWithLifecycle()
    val myId = viewModel.myId ?: return
    val context = LocalContext.current
    var showComments by rememberSaveable(read) { mutableStateOf(false) }
    val social = reads[read] ?: ReadSocial()
    val onError: (String) -> Unit = { context.toast(context.stringResource(MR.strings.friends_error, it)) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Reactions.chips(social.reactions, read.ownerId, myId).forEach { chip ->
            SocialChip(
                text = if (chip.count > 0) "${chip.emoji} ${chip.count}" else chip.emoji,
                highlighted = chip.mine,
                onClick = if (chip.enabled) ({ viewModel.react(read, chip.emoji, onError) }) else null,
                description = "${chip.emoji} ${chip.count}",
            )
        }
        SocialChip(
            text = "💬 ${social.comments.size}",
            highlighted = false,
            onClick = { showComments = true },
            description = stringResource(MR.strings.friends_comments) + " ${social.comments.size}",
        )
    }

    if (showComments) {
        CommentsSheet(row = row, read = read, viewModel = viewModel, onDismissRequest = { showComments = false })
    }
}

@Composable
private fun SocialChip(text: String, highlighted: Boolean, onClick: (() -> Unit)?, description: String) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
        color = if (highlighted) primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (highlighted) primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(1.dp, if (highlighted) primary else Color.Transparent, CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = description
                selected = highlighted
            }
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** The read, who reacted, the comments (delete your own) and a field to write one. */
@Composable
private fun CommentsSheet(row: FeedRow, read: ReadKey, viewModel: FeedSocialViewModel, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val reads by viewModel.reads.collectAsStateWithLifecycle()
    val players by viewModel.players.collectAsStateWithLifecycle()
    val social = reads[read] ?: ReadSocial()
    val myId = viewModel.myId
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val onError: (String) -> Unit = {
        sending = false
        context.toast(context.stringResource(MR.strings.friends_error, it))
    }
    fun name(id: String) = if (id == myId) {
        context.stringResource(MR.strings.friends_you)
    } else {
        players[id]?.displayName ?: context.stringResource(MR.strings.friends_unknown_player)
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "💬 " + stringResource(MR.strings.friends_comments),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = listOfNotNull(row.seriesTitle, feedChapter(row)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(MR.strings.friends_reactions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (social.reactions.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.friends_reactions_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                social.reactions.forEach { reaction ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val player = players[reaction.reactorId]
                        FriendAvatar(name = name(reaction.reactorId), url = player?.avatarUrl, size = 22.dp)
                        Text(
                            text = "${name(reaction.reactorId)} ${reaction.emoji}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalDivider()
            if (social.comments.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.friends_comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            social.comments.forEach { comment ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FriendAvatar(
                        name = name(comment.authorId),
                        url = players[comment.authorId]?.avatarUrl,
                        size = 32.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listOfNotNull(
                                name(comment.authorId),
                                comment.created?.let { relativeTimeSpanString(it.toEpochMilli()) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
                        if (comment.authorId == myId) {
                            TextButton(onClick = { viewModel.delete(comment, onError) }) {
                                Text(stringResource(MR.strings.friends_comment_delete))
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Reactions.MAX_COMMENT) },
                label = { Text(stringResource(MR.strings.friends_comment_hint)) },
                supportingText = { Text("${text.trim().length}/${Reactions.MAX_COMMENT}") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !sending && Reactions.commentBody(text) != null,
                onClick = {
                    sending = true
                    viewModel.comment(read, text, onError) {
                        sending = false
                        text = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(MR.strings.friends_comment_send))
            }
        }
    }
}

/** "Ch. 40", else the chapter's name. */
fun feedChapter(row: FeedRow): String? {
    val number = row.chapterNumber
    return if (number != null &&
        number >= 0
    ) {
        "Ch. " + chapterNumber(number)
    } else {
        row.chapterName?.takeIf { it.isNotBlank() }
    }
}
