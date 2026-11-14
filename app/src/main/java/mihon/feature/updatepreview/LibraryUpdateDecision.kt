package mihon.feature.updatepreview

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_TRACKER_FINISHED
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.time.Instant

/** Why a library update leaves a series out, in the order the checks run. */
enum class SkipReason(val label: StringResource) {
    ExcludedCategory(MR.strings.update_preview_reason_excluded_category),
    NotIncludedCategory(MR.strings.update_preview_reason_not_included),
    OnlyFetchOnce(MR.strings.skipped_reason_not_always_update),
    Completed(MR.strings.skipped_reason_completed),
    TrackerFinished(MR.strings.update_preview_reason_tracker_finished),
    HasUnread(MR.strings.skipped_reason_not_caught_up),
    NotStarted(MR.strings.skipped_reason_not_started),
    OutsideReleasePeriod(MR.strings.skipped_reason_not_in_release_period),
}

/** The settings a full library update applies; categories are empty when one category is updated. */
data class UpdateRules(
    val includedCategories: Set<Long>,
    val excludedCategories: Set<Long>,
    val restrictions: Set<String>,
    val fetchWindowUpperBound: Instant,
)

/** Shared by the update worker and the "What gets updated" preview so both always agree. */
object LibraryUpdateDecision {

    /** Why [item] is skipped, or null when it is checked. */
    fun skipReason(item: LibraryManga, rules: UpdateRules, trackerFinished: Boolean): SkipReason? {
        val restrictions = rules.restrictions
        return when {
            item.categories.any { it in rules.excludedCategories } -> SkipReason.ExcludedCategory
            rules.includedCategories.isNotEmpty() && item.categories.none { it in rules.includedCategories } ->
                SkipReason.NotIncludedCategory
            item.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && item.totalChapters > 0L ->
                SkipReason.OnlyFetchOnce
            MANGA_NON_COMPLETED in restrictions && item.manga.status.toInt() == SManga.COMPLETED ->
                SkipReason.Completed
            MANGA_TRACKER_FINISHED in restrictions && trackerFinished -> SkipReason.TrackerFinished
            MANGA_HAS_UNREAD in restrictions && item.unreadCount != 0L -> SkipReason.HasUnread
            MANGA_NON_READ in restrictions && item.totalChapters > 0L && !item.hasStarted -> SkipReason.NotStarted
            MANGA_OUTSIDE_RELEASE_PERIOD in restrictions &&
                item.manga.nextUpdate.let { it != null && it > rules.fetchWindowUpperBound } ->
                SkipReason.OutsideReleasePeriod
            else -> null
        }
    }
}

/** Whether any of [tracks] is completed or dropped on its tracker; each tracker numbers statuses its own way. */
fun TrackerManager.isFinished(tracks: List<Track>): Boolean = tracks.any { track ->
    val tracker = get(track.trackerId) ?: return@any false
    track.status == tracker.getCompletionStatus() || tracker.getStatus(track.status) == MR.strings.dropped
}
