package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.Challenge
import mihon.feature.friends.data.ChallengeScore
import mihon.feature.friends.data.HourRow
import mihon.feature.friends.data.League
import mihon.feature.friends.data.QuestFacts
import mihon.feature.friends.domain.DayTotal
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.ReadingStreakStats
import mihon.feature.friends.domain.Tier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private object PreviewArena {
    const val ME = "me"
    const val RIVAL = "rival"
    private val zone = ZoneId.of("Europe/Belgrade")
    private val now = Instant.parse("2026-11-13T19:30:00Z")
    private val today = LocalDate.of(2026, 11, 13)

    private fun row(id: String, name: String, chapters: Long, seconds: Long, series: Long = 3) = BoardRow(
        userId = id,
        displayName = name,
        chapters = chapters,
        seconds = seconds,
        series = series,
        genres = mapOf("Action" to chapters / 2, "Romance" to chapters / 3),
    )

    private fun challenge(id: Int, title: String, emoji: String, start: String, end: String) = Challenge(
        id = id,
        title = title,
        emoji = emoji,
        description = "Most chapters before Monday wins the trophy.",
        metric = "chapters",
        startsAt = start,
        endsAt = end,
    )

    val snapshot = ArenaSnapshot(
        arena = League(
            id = "0a12e000-0000-4000-8000-000000000001",
            name = "The Reading Arena",
            inviteCode = "ARENA1",
            timezone = zone.id,
            createdAt = "2026-11-11T08:00:00+00:00",
        ),
        zone = zone,
        now = now,
        boards = mapOf(
            LeaguePeriod.TODAY to listOf(row(ME, "Marko", 5, 2_400, 2), row(RIVAL, "Ana", 3, 1_500, 1)),
            LeaguePeriod.WEEK to listOf(row(ME, "Marko", 31, 14_000), row(RIVAL, "Ana", 34, 16_200)),
            LeaguePeriod.MONTH to listOf(row(ME, "Marko", 31, 14_000), row(RIVAL, "Ana", 34, 16_200)),
            LeaguePeriod.ALL to listOf(row(ME, "Marko", 31, 14_000), row(RIVAL, "Ana", 34, 16_200)),
        ),
        daily = listOf(
            DayTotal(ME, today.minusDays(2), 12, 5_000),
            DayTotal(RIVAL, today.minusDays(2), 9, 4_000),
            DayTotal(ME, today.minusDays(1), 14, 6_600),
            DayTotal(RIVAL, today.minusDays(1), 22, 10_700),
            DayTotal(ME, today, 5, 2_400),
            DayTotal(RIVAL, today, 3, 1_500),
        ),
        hours = listOf(HourRow(ME, 23, 4), HourRow(RIVAL, 7, 6)),
        lifetimeHours = listOf(HourRow(ME, 23, 4), HourRow(RIVAL, 7, 6)),
        feed = emptyList(),
        challenges = listOf(
            challenge(1, "Grand Opening", "🎉", "2026-11-11T08:00:00+00:00", "2026-11-15T23:00:00+00:00"),
            challenge(100, "Chapter Sprint", "🏃", "2026-11-15T23:00:00+00:00", "2026-11-22T23:00:00+00:00"),
        ),
        challengeBoards = mapOf(1 to listOf(ChallengeScore(ME, 31.0), ChallengeScore(RIVAL, 34.0))),
        seasonBoards = emptyMap(),
        questFacts = mapOf(
            today to mapOf(ME to QuestFacts(series = 2, late = 1), RIVAL to QuestFacts(series = 1, early = 2)),
        ),
    )

    val model = ArenaUiModel.build(snapshot, ME)
}

@PreviewLightDark
@Composable
private fun LibraryArenaCardPreview() {
    TachiyomiPreviewTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            LibraryArenaCard(
                summary = ArenaSummary(
                    configured = true,
                    registered = true,
                    model = PreviewArena.model,
                    showCard = true,
                ),
                onClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ArenaHeroesPreview() {
    TachiyomiPreviewTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            SeasonHero(PreviewArena.model)
            RivalryHero(PreviewArena.model)
        }
    }
}

@PreviewLightDark
@Composable
private fun ArenaSectionsPreview() {
    TachiyomiPreviewTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            ChallengesSection(PreviewArena.model)
            QuestsSection(PreviewArena.model)
            AchievementsSection(PreviewArena.model)
        }
    }
}

@PreviewLightDark
@Composable
private fun TierBadgesPreview() {
    TachiyomiPreviewTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tier.entries.forEach { TierBadge(it, 36.dp) }
            }
            Tier.entries.forEach { TierPill(it) }
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadingStreakCardPreview() {
    TachiyomiPreviewTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            ReadingStreakCard(
                ReadingStreakStats(
                    currentStreak = 6,
                    bestStreak = 12,
                    thisWeekChapters = 31,
                    thisWeekSeconds = 14_000,
                    lastWeekChapters = 22,
                    lastWeekSeconds = 9_800,
                    lastSevenDays = listOf(3, 0, 5, 8, 12, 14, 5),
                ),
            )
        }
    }
}
