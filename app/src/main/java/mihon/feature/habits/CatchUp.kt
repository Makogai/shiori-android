package mihon.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.formatChapterNumber
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.readableAccent
import tachiyomi.presentation.core.util.collectAsState
import java.time.Duration
import java.time.Instant
import kotlin.time.toJavaInstant

/** "You stopped at Ch. 84 · 23 new since" for a series left alone for [IDLE_DAYS] with unread chapters. */
data class CatchUp(val stoppedAt: Double, val newSince: Int, val hideKey: String) {

    companion object {
        const val IDLE_DAYS = 30L

        /**
         * [stoppedChapterId] and [lastReadAt] are the series' latest history entry; new chapters are the unread
         * ones numbered after it, or every unread one when the numbers don't tell.
         */
        fun of(
            mangaId: Long,
            stoppedChapterId: Long,
            lastReadAt: Instant,
            now: Instant,
            chapters: List<Chapter>,
        ): CatchUp? {
            if (Duration.between(lastReadAt, now).toDays() < IDLE_DAYS) return null
            val unread = chapters.filter { !it.read }
            if (unread.isEmpty()) return null
            val stopped = chapters.firstOrNull { it.id == stoppedChapterId } ?: return null
            val after = if (stopped.chapterNumber >= 0) unread.count { it.chapterNumber > stopped.chapterNumber } else 0
            return CatchUp(
                stoppedAt = stopped.chapterNumber,
                newSince = after.takeIf { it > 0 } ?: unread.size,
                hideKey = "$mangaId@${lastReadAt.toEpochMilli()}",
            )
        }
    }
}

/** The catch-up banner on the manga page: Resume continues reading, Hide hides it until the series is read again. */
@Composable
fun CatchUpBanner(mangaId: Long, chapters: List<Chapter>, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember { context.appGraph }
    val hidden by graph.habitsPreferences.hiddenCatchUps.collectAsState()
    val lastRead by produceState<Pair<Long, kotlin.time.Instant>?>(null, mangaId, chapters) {
        value = graph.readingHabits.lastRead(mangaId)
    }
    val (chapterId, readAt) = lastRead ?: return
    val catchUp = remember(chapters, chapterId, readAt) {
        CatchUp.of(mangaId, chapterId, readAt.toJavaInstant(), Instant.now(), chapters)
    } ?: return
    if (catchUp.hideKey in hidden) return
    val accent = MaterialTheme.colorScheme.readableAccent()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.card)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
    ) {
        Text(
            text = "👋 " + stringResource(
                MR.strings.habits_catch_up,
                formatChapterNumber(catchUp.stoppedAt),
                catchUp.newSince,
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { graph.habitsPreferences.hiddenCatchUps += catchUp.hideKey }) {
                Text(
                    stringResource(MR.strings.habits_catch_up_hide),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onResume) {
                Text(stringResource(MR.strings.habits_catch_up_resume), color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
