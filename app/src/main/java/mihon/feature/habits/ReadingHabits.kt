package mihon.feature.habits

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.habits.FinishedRead
import tachiyomi.data.habits.FinishedReadsSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.toJavaInstant

@Inject
@SingleIn(AppScope::class)
class ReadingHabits(
    private val source: FinishedReadsSource,
    private val preferences: HabitsPreferences,
) {

    fun subscribeReads(): Flow<List<FinishedRead>> = source.subscribe()

    /** The daily goal, or null when it's off. */
    fun subscribeGoal(): Flow<Int?> = combine(
        preferences.dailyGoalEnabled.changes(),
        preferences.dailyGoal.changes(),
    ) { enabled, goal -> goal.takeIf { enabled } }

    fun goal(): Int? = preferences.dailyGoal.get().takeIf { preferences.dailyGoalEnabled.get() }

    fun subscribeReadingSeconds(): Flow<Map<LocalDate, Long>> =
        preferences.readingSeconds.changes().map(ReadingSeconds::decode)

    suspend fun todayCount(): Int =
        ReadingDays.countOn(source.subscribe().first().map { it.readAt.toJavaInstant() }, LocalDate.now(), zone())

    suspend fun lastRead(mangaId: Long) = source.lastRead(mangaId)

    companion object {
        fun zone(): ZoneId = ZoneId.systemDefault()

        /** Today's date, emitted again at every local midnight. */
        fun currentDay(): Flow<LocalDate> = flow {
            while (true) {
                val now = LocalDateTime.now()
                emit(now.toLocalDate())
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
            }
        }
            .distinctUntilChanged()
    }
}

fun FinishedRead.localDate(zone: ZoneId): LocalDate = readAt.toJavaInstant().atZone(zone).toLocalDate()
