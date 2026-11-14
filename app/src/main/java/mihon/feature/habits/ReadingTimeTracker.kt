package mihon.feature.habits

import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.LocalDate

/**
 * Counts active reader time per day: the reader pings it on touches, keys and page changes, and stops it
 * when it goes to the background. Saved on stop and every [FLUSH_MILLIS] of reading.
 */
@Inject
@SingleIn(AppScope::class)
class ReadingTimeTracker(private val preferences: HabitsPreferences) {

    private val counter = ActiveTimeCounter()
    private var pendingMillis = 0L

    @Synchronized
    fun onActive() {
        pendingMillis += counter.ping(SystemClock.elapsedRealtime())
        if (pendingMillis >= FLUSH_MILLIS) flush()
    }

    @Synchronized
    fun onPause() {
        pendingMillis += counter.stop(SystemClock.elapsedRealtime())
        flush()
    }

    private fun flush() {
        val seconds = pendingMillis / 1000
        if (seconds <= 0) return
        pendingMillis -= seconds * 1000
        preferences.readingSeconds.set(
            ReadingSeconds.add(preferences.readingSeconds.get(), LocalDate.now(), seconds),
        )
    }

    private companion object {
        const val FLUSH_MILLIS = 60_000L
    }
}
