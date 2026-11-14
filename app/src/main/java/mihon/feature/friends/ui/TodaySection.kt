package mihon.feature.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.feature.friends.data.ActivityDays
import mihon.feature.friends.domain.TodayEntry
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.TextStyle
import java.util.Locale

/** "Today" (README v9): each player's chapters on their own local day, as a small leaderboard. */
@Composable
fun TodaySection(model: ArenaUiModel) {
    ArenaSection(
        title = stringResource(MR.strings.friends_today),
        emoji = "☀️",
        trailing = {
            Text(
                text = stringResource(MR.strings.friends_today_local_day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        model.localToday.forEach { entry -> TodayRow(model, entry) }
    }
}

@Composable
private fun TodayRow(model: ArenaUiModel, entry: TodayEntry) {
    val player = model.players[entry.userId]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = when (entry.medal) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "${entry.place}."
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        FriendAvatar(name = player?.name ?: entry.name, url = player?.avatarUrl, size = 26.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.zone.id != model.zone.id) {
                Text(
                    text = entry.zone.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(MR.strings.friends_today_chapters, entry.chapters.toInt()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = ActivityDays.minutes(entry.seconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
        )
    }
}
