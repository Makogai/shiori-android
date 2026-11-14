package mihon.feature.habits

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class HabitsPreferences(preferenceStore: PreferenceStore) {

    val dailyGoalEnabled: Preference<Boolean> = preferenceStore.getBoolean("habits_daily_goal_enabled", false)

    /** Chapters a day. */
    val dailyGoal: Preference<Int> = preferenceStore.getInt("habits_daily_goal", 5)

    /** The reader's "Chapter 7 today · 3 left to goal" toast when a chapter is finished. */
    val chapterToast: Preference<Boolean> = preferenceStore.getBoolean("habits_chapter_toast", true)

    /** Active reader seconds per local day, as [ReadingSeconds] encodes them. */
    val readingSeconds: Preference<String> = preferenceStore.getString("habits_reading_seconds", "")

    /** Catch-up banners hidden with "Hide", as [CatchUp.hideKey]s. */
    val hiddenCatchUps: Preference<Set<String>> = preferenceStore.getStringSet("habits_hidden_catch_ups", emptySet())
}
