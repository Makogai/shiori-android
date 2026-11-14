package mihon.feature.library

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
class LibraryExtrasPreferences(preferenceStore: PreferenceStore) {

    /** Every [LibrarySection] name, in the order the user picked. */
    val sectionOrder: Preference<String> = preferenceStore.getString("library_sections_order", "")

    val sectionsEnabled: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_sections_enabled",
        LibrarySection.entries.filter { it.enabledByDefault }.mapTo(mutableSetOf()) { it.name },
    )

    /** How recently a series must have been read for its new chapters to show in Up next. */
    val upNextDays: Preference<Int> = preferenceStore.getInt("library_up_next_days", 14)

    /** Days without reading after which a started series is stalled; 0 turns the badge off. */
    val stalledDays: Preference<Int> = preferenceStore.getInt("library_stalled_days", 30)

    val filterStalled: Preference<TriState> = preferenceStore.getEnum("library_filter_stalled", TriState.DISABLED)

    val trackerBadges: Preference<Boolean> = preferenceStore.getBoolean("library_tracker_badges", false)

    companion object {
        val DAY_CHOICES = listOf(3, 7, 14, 30)
        val STALLED_CHOICES = listOf(0, 14, 30, 60, 90)
    }
}

/** The optional rows at the top of the Library. */
enum class LibrarySection(val title: StringResource, val explanation: StringResource, val enabledByDefault: Boolean) {
    Summary(MR.strings.library_section_summary, MR.strings.library_section_summary_info, true),
    ContinueReading(MR.strings.library_section_continue, MR.strings.library_section_continue_info, true),
    UpNext(MR.strings.library_section_up_next, MR.strings.library_section_up_next_info, false),
    Queue(MR.strings.binge_queue_title, MR.strings.library_section_queue_info, true),
    SmartShelves(MR.strings.shelves_header, MR.strings.library_section_shelves_info, true),
    ;

    companion object {
        /** The stored order, dropping unknown names and adding sections added since at the end. */
        fun parseOrder(stored: String): List<LibrarySection> {
            val order = stored.split(',').mapNotNull { name -> entries.find { it.name == name } }.distinct()
            return order + entries.filterNot { it in order }
        }

        fun visible(stored: String, enabled: Set<String>): List<LibrarySection> =
            parseOrder(stored).filter { it.name in enabled }
    }
}

/** A tracker status as a cover badge, the same across trackers. */
enum class TrackBadge {
    PlanToRead,
    Reading,
    OnHold,
    Dropped,
    Completed,
    ;

    companion object {
        /** Each tracker numbers its statuses its own way; their names are shared. */
        fun from(statusName: StringResource?): TrackBadge? = when (statusName) {
            MR.strings.plan_to_read -> PlanToRead
            MR.strings.reading, MR.strings.repeating -> Reading
            MR.strings.on_hold, MR.strings.paused, MR.strings.on_hold_list -> OnHold
            MR.strings.dropped -> Dropped
            MR.strings.completed -> Completed
            else -> null
        }

        /** The most settled status among a series' tracker entries. */
        fun summary(tracks: List<Track>, trackerManager: TrackerManager): TrackBadge? =
            tracks.mapNotNull { track ->
                from(trackerManager.get(track.trackerId)?.getStatus(track.status))
            }.maxOrNull()
    }
}

object LibraryInsights {

    /** Read before, not for [days], and still with unread chapters. Never-started series were never going. */
    fun isStalled(item: LibraryManga, days: Int, now: Instant): Boolean {
        val lastRead = item.lastRead ?: return false
        if (days <= 0 || item.unreadCount <= 0 || !item.hasStarted) return false
        return now - lastRead >= days.days
    }

    /** Started, not finished, most recently read first. */
    fun continueReading(library: List<LibraryManga>, limit: Int = 20): List<LibraryManga> = library
        .filter { it.hasStarted && it.unreadCount > 0 && it.lastRead != null }
        .sortedByDescending { it.lastRead }
        .take(limit)

    /** Series read in the last [days] with unread chapters fetched since. */
    fun upNextCandidates(library: List<LibraryManga>, now: Instant, days: Int): List<LibraryManga> = library.filter {
        val lastRead = it.lastRead ?: return@filter false
        val fetched = it.chapterFetchedAt ?: return@filter false
        now - lastRead <= days.days && it.unreadCount > 0 && fetched > lastRead
    }

    /** The first unread chapter after the furthest one read, or the first unread one. Source order 0 is newest. */
    fun nextChapter(chapters: List<Chapter>): Chapter? {
        val reading = chapters.sortedByDescending { it.sourceOrder }
        val furthest = reading.indexOfLast { it.read }
        return reading.drop(furthest + 1).firstOrNull { !it.read } ?: reading.firstOrNull { !it.read }
    }
}

/** "Surprise me": a random series worth starting or getting back to, favouring ones with more chapters waiting. */
object SurpriseLogic {

    sealed interface Reason {
        data object PlanToRead : Reason
        data class Stalled(val days: Long) : Reason
        data object NotStarted : Reason
    }

    data class Candidate(val item: LibraryManga, val reason: Reason) {
        /** The boost levels off so huge series don't always win. */
        val weight: Double get() = 1 + kotlin.math.sqrt(item.unreadCount.coerceAtMost(200).toDouble()) * 3
    }

    fun candidates(
        library: List<LibraryManga>,
        badges: Map<Long, TrackBadge>,
        stalledDays: Int,
        now: Instant,
    ): List<Candidate> = library.mapNotNull { item ->
        if (item.unreadCount <= 0) return@mapNotNull null
        when (badges[item.id]) {
            TrackBadge.PlanToRead -> return@mapNotNull Candidate(item, Reason.PlanToRead)
            TrackBadge.Dropped, TrackBadge.Completed -> return@mapNotNull null
            else -> {}
        }
        val lastRead = item.lastRead
        when {
            lastRead != null && LibraryInsights.isStalled(item, stalledDays.takeIf { it > 0 } ?: 30, now) ->
                Candidate(item, Reason.Stalled((now - lastRead).inWholeDays))
            !item.hasStarted && lastRead == null -> Candidate(item, Reason.NotStarted)
            else -> null
        }
    }

    /** A weighted random pick, skipping [excluding] unless nothing else is left. */
    fun pick(candidates: List<Candidate>, excluding: Set<Long>, random: Random = Random): Candidate? {
        val pool = candidates.filterNot { it.item.id in excluding }.ifEmpty { candidates }
        val total = pool.sumOf { it.weight }
        if (total <= 0) return null
        var target = random.nextDouble(total)
        for (candidate in pool) {
            target -= candidate.weight
            if (target < 0) return candidate
        }
        return pool.last()
    }
}
