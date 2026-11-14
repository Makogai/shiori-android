package mihon.feature.sourcehealth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class SourceHealthPreferences(
    preferenceStore: PreferenceStore,
) {

    /** Settings → Library → "Daily source health check". */
    val dailyCheck: Preference<Boolean> = preferenceStore.getBoolean("source_health_daily_check", true)

    /** Each source's [SourceHealthRecord], as JSON. */
    val records: Preference<String> = preferenceStore.getString(Preference.appStateKey("source_health_records"))

    /** How many checks each source has had, as JSON, so its series take turns. */
    val turns: Preference<String> = preferenceStore.getString(Preference.appStateKey("source_health_turns"))

    val lastCheck: Preference<Long> = preferenceStore.getLong(Preference.appStateKey("source_health_last_check"))
}
