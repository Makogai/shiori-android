package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.feature.friends.domain.Bets
import mihon.feature.friends.domain.SettledBet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

val WinColor = Color(0xFF34C759)

/** The 🎲 part of a live weekly challenge card: bet on yourself until halfway, then who bet what. */
@Composable
fun BetPanel(card: ChallengeCard, model: ArenaUiModel, onPlaceBet: (ChallengeCard) -> Unit) {
    if (!card.weekly) return
    val mine = card.betOf(model.myId)
    val others = card.bets.filter { it.userId != model.myId && it.userId in model.players }
    if (mine == null && !card.betable && others.isEmpty()) return
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B2470), Color(0xFF1C1336))))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            mine != null -> Text(
                text = stringResource(MR.strings.friends_bet_placed, mine.stake, card.challenge.title),
                color = ArenaColors.OnHero,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            card.betable -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(MR.strings.friends_bet_on_yourself),
                        color = ArenaColors.OnHero,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(MR.strings.friends_bet_closes_in, countdownText(card.closesAt)),
                        color = ArenaColors.OnHeroMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Button(
                    onClick = { onPlaceBet(card) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC53D),
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(stringResource(MR.strings.friends_bet_place), fontWeight = FontWeight.Black)
                }
            }
        }
        others.forEach { bet ->
            Text(
                text = stringResource(MR.strings.friends_bet_other, model.name(bet.userId), bet.stake),
                color = ArenaColors.OnHeroMuted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** "🎲 Bet won: +50 XP" (green) / "🎲 Bet lost: −50 XP" (coral) on an ended challenge. */
@Composable
fun BetResult(bet: SettledBet?, modifier: Modifier = Modifier) {
    bet ?: return
    Text(
        text = if (bet.won) {
            stringResource(MR.strings.friends_bet_won, bet.stake)
        } else {
            stringResource(MR.strings.friends_bet_lost, bet.stake)
        },
        color = if (bet.won) WinColor else ArenaColors.Rival,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Stake picker: 10…100 XP in steps of 10, default 50. */
@Composable
fun StakeSheet(card: ChallengeCard, onDismissRequest: () -> Unit, onConfirm: (Int) -> Unit) {
    var stake by rememberSaveable(card.challenge.id) { mutableIntStateOf(Bets.DEFAULT_STAKE) }
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.friends_bet_on_yourself),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${card.challenge.emoji} ${card.challenge.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(MR.strings.friends_bet_sheet_summary, card.challenge.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(MR.strings.friends_bet_stake, stake),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = ArenaColors.Xp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Bets.STAKES.forEach { value ->
                    FilterChip(
                        selected = value == stake,
                        onClick = { stake = value },
                        label = { Text(value.toString()) },
                    )
                }
            }
            Text(
                text = stringResource(MR.strings.friends_bet_closes_in, countdownText(card.closesAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onConfirm(stake) }, modifier = Modifier.fillMaxWidth()) {
                Text("🎲 " + stringResource(MR.strings.friends_bet_place))
            }
        }
    }
}

/** Under the XP-over-time chart: "🎲 Action Week  +50 XP", newest first. */
@Composable
fun SettledBetsList(bets: List<Pair<String, SettledBet>>) {
    if (bets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(MR.strings.friends_settled_bets),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        bets.forEach { (title, bet) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎲 $title",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (if (bet.won) "+" else "−") + "${bet.stake} XP",
                    color = if (bet.won) WinColor else ArenaColors.Rival,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background((if (bet.won) WinColor else ArenaColors.Rival).copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
