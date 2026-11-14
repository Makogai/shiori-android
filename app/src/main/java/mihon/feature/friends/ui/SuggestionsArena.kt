package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.util.relativeTimeSpanString
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionEvent
import mihon.feature.friends.data.SuggestionRules
import mihon.feature.friends.data.SuggestionStatus
import mihon.feature.friends.domain.LeagueHashing
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** The Arena top-bar 📬 button with the number of new suggestions. */
@Composable
fun SuggestionsToolbarAction(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) Badge { Text(count.toString()) }
            },
        ) {
            Text(text = "📬", fontSize = 20.sp)
        }
    }
}

/** "📬 N new suggestions" under the quest pills of the Library card. */
@Composable
fun SuggestionsChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(MR.plurals.friends_new_suggestions, count, count),
        color = ArenaColors.OnHero,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * Arena section 14: new suggestion count, the open ones "From <name>" with Add / Dismiss / Read it
 * (README v9), the latest three events and "See all".
 */
@Composable
fun SuggestionsSection(
    model: ArenaUiModel,
    onSeeAll: () -> Unit,
    onAdd: (Suggestion) -> Unit,
    onAnswer: (Suggestion, SuggestionStatus) -> Unit,
) {
    val events = SuggestionRules.events(model.suggestions, model.myId)
    val unknown = stringResource(MR.strings.friends_unknown_player)
    fun name(userId: String) = model.players[userId]?.name ?: unknown
    ArenaSection(
        title = stringResource(MR.strings.friends_suggestions),
        emoji = "📬",
        trailing = {
            TextButton(onClick = onSeeAll) { Text(stringResource(MR.strings.friends_see_all)) }
        },
    ) {
        Text(
            text = stringResource(MR.strings.friends_suggestions_new_for_you, model.newSuggestions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (events.isEmpty()) {
            Text(
                text = stringResource(MR.strings.friends_suggestions_empty_sent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SuggestionRules.open(model.suggestions, model.myId).forEach { suggestion ->
            OpenSuggestionRow(
                suggestion = suggestion,
                from = name(suggestion.fromUser),
                onAdd = { onAdd(suggestion) },
                onAnswer = { onAnswer(suggestion, it) },
            )
        }
        events.forEach { event ->
            val suggestion = event.suggestion
            val text = when (event) {
                is SuggestionEvent.Received -> stringResource(
                    MR.strings.friends_suggestion_event_received,
                    name(suggestion.fromUser),
                    suggestion.title,
                )
                is SuggestionEvent.Accepted -> stringResource(
                    MR.strings.friends_suggestion_event_accepted,
                    name(suggestion.toUser),
                    suggestion.title,
                )
                is SuggestionEvent.ReadIt -> stringResource(
                    MR.strings.friends_suggestion_event_read,
                    name(suggestion.toUser),
                    suggestion.title,
                )
                is SuggestionEvent.Sent -> stringResource(
                    MR.strings.friends_suggestion_event_sent,
                    suggestion.title,
                    name(suggestion.toUser),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSeeAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "· " + relativeTimeSpanString(event.at.toEpochMilli()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A compact "From Filip" row: cover, title, source, note, status, and Add / Dismiss / Read it. */
@Composable
private fun OpenSuggestionRow(
    suggestion: Suggestion,
    from: String,
    onAdd: () -> Unit,
    onAnswer: (SuggestionStatus) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SharedCover(
            coverUrl = suggestion.coverUrl,
            seriesKey = remember(suggestion.sourceId, suggestion.mangaUrl) {
                suggestion.sourceId.toLongOrNull()?.let { LeagueHashing.seriesKey(it, suggestion.mangaUrl) }
            },
            title = suggestion.title,
            modifier = Modifier
                .width(40.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(MR.strings.friends_suggestion_from, from),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                )
            }
            suggestion.note?.let {
                Text(
                    text = "“$it”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(suggestion.status)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (suggestion.pending) {
                    OutlinedButton(onClick = onAdd) {
                        Text(stringResource(MR.strings.friends_suggestion_add_short))
                    }
                    OutlinedButton(onClick = { onAnswer(SuggestionStatus.DISMISSED) }) {
                        Text(stringResource(MR.strings.friends_suggestion_dismiss))
                    }
                }
                OutlinedButton(onClick = { onAnswer(SuggestionStatus.READ) }) {
                    Text(stringResource(MR.strings.friends_suggestion_mark_read))
                }
            }
        }
    }
}
