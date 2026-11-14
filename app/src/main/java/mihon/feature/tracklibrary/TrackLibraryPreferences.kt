package mihon.feature.tracklibrary

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TrackLibraryPreferences(preferenceStore: PreferenceStore) {

    /** "trackerId:mangaId" for every entry the user skipped on "Track your library". */
    val skipped: Preference<Set<String>> = preferenceStore.getStringSet("track_library_skipped", emptySet())

    /** Trackers whose "Link your library?" offer after logging in was already shown. */
    val offeredTrackers: Preference<Set<String>> = preferenceStore.getStringSet(
        Preference.appStateKey("track_library_offered"),
        emptySet(),
    )

    fun isSkipped(trackerId: Long, mangaId: Long) = skipKey(trackerId, mangaId) in skipped.get()

    fun setSkipped(trackerId: Long, mangaId: Long, skip: Boolean) {
        val key = skipKey(trackerId, mangaId)
        skipped.set(if (skip) skipped.get() + key else skipped.get() - key)
    }

    /** Manga ids skipped for [trackerId]. */
    fun skippedFor(trackerId: Long): Flow<Set<Long>> = skipped.changes().map { keys -> skippedIds(keys, trackerId) }

    /** Marks the offer for [trackerId] as shown; true if it hadn't been before. */
    fun takeOffer(trackerId: Long): Boolean {
        val offered = offeredTrackers.get()
        if (trackerId.toString() in offered) return false
        offeredTrackers.set(offered + trackerId.toString())
        return true
    }

    companion object {
        fun skipKey(trackerId: Long, mangaId: Long) = "$trackerId:$mangaId"

        fun skippedIds(keys: Set<String>, trackerId: Long): Set<Long> {
            val prefix = "$trackerId:"
            return keys.mapNotNullTo(mutableSetOf()) { key ->
                key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()
            }
        }
    }
}
