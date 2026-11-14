package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.relativeTimeSpanString
import mihon.feature.friends.domain.Race
import mihon.feature.friends.domain.RaceLeader
import mihon.feature.friends.domain.RaceRow
import mihon.feature.friends.domain.Races
import mihon.feature.friends.domain.chapterNumber
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** "Races" (README v6, section 4): a headline for the latest race and up to 5 cards; tap opens the series. */
@Composable
fun RacesSection(model: ArenaUiModel, onOpen: (Race) -> Unit) {
    if (model.races.isEmpty()) return
    ArenaSection(stringResource(MR.strings.friends_races), "🏁", card = false) {
        val first = model.races.first()
        Text(
            text = Races.headline(first, model.name(first.rivalId)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        model.races.take(Races.MAX_CARDS).forEach { race -> RaceCard(race, model, onOpen) }
    }
}

/** One race: cover, both players' chapters and last reads, and the split bar (iOS RaceCard). */
@Composable
private fun RaceCard(race: Race, model: ArenaUiModel, onOpen: (Race) -> Unit) {
    val rivalName = model.name(race.rivalId)
    val me = MaterialTheme.colorScheme.primary
    val rival = playerColor(race.rivalId, model)
    val badgeColor = when (race.leader) {
        RaceLeader.ME -> me
        RaceLeader.THEM -> rival
        RaceLeader.TIED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val total = (race.myNumber + race.theirNumber).coerceAtLeast(1.0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button) { onOpen(race) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SharedCover(
            coverUrl = race.coverUrl,
            seriesKey = race.seriesKey,
            title = race.title,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = race.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Races.badge(race, rivalName),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            RacerLine(stringResource(MR.strings.friends_you), race.mine, me)
            RacerLine(rivalName, race.theirs, rival)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val mine = (race.myNumber / total).toFloat()
                if (mine > 0f) {
                    Box(
                        Modifier
                            .weight(mine)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(me),
                    )
                }
                if (mine < 1f) {
                    Box(
                        Modifier
                            .weight(1f - mine)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(rival),
                    )
                }
            }
        }
    }
}

@Composable
private fun RacerLine(name: String, row: RaceRow, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        row.chapterNumber?.let {
            Text(
                text = stringResource(MR.strings.friends_race_chapter, chapterNumber(it)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        row.lastRead?.let {
            Text(
                text = relativeTimeSpanString(it.toEpochMilli()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
