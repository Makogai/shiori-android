package mihon.feature.shelves

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.model.LibraryManga
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Automatic rows at the top of the Library, in the order they show. */
enum class SmartShelf(val enabledByDefault: Boolean) {
    NewChapters(true),
    AlmostFinished(true),
    Untouched(true),
    CaughtUp(false),
}

@Inject
@SingleIn(AppScope::class)
class SmartShelfPreferences(preferenceStore: PreferenceStore) {

    /** Library → Display → smart shelves. */
    val enabled: Preference<Set<SmartShelf>> = preferenceStore.getObjectSetFromStringSet(
        key = "library_smart_shelves",
        defaultValue = SmartShelf.entries.filter { it.enabledByDefault }.toSet(),
        serializer = { it.name },
        deserializer = { name -> SmartShelf.entries.find { it.name == name } },
    )
}

object SmartShelfLogic {
    val UNTOUCHED_AFTER = 30.days
    const val ALMOST_FINISHED_SHARE = 0.8
    const val LIMIT = 20

    /** Which shelves [item] belongs on at [now]. */
    fun shelves(item: LibraryManga, now: Instant): Set<SmartShelf> {
        if (item.totalChapters == 0L) return emptySet()
        val unread = item.unreadCount
        if (unread == 0L) {
            return if (item.hasStarted) setOf(SmartShelf.CaughtUp) else emptySet()
        }
        return buildSet {
            val lastRead = item.lastRead
            val fetched = item.chapterFetchedAt
            if (item.hasStarted && lastRead != null && fetched != null && fetched > lastRead) {
                add(SmartShelf.NewChapters)
            }
            if (item.readCount.toDouble() / item.totalChapters >= ALMOST_FINISHED_SHARE) {
                add(SmartShelf.AlmostFinished)
            }
            val lastTouched = lastRead ?: item.manga.favoriteAt
            if (lastTouched != null && now - lastTouched >= UNTOUCHED_AFTER) {
                add(SmartShelf.Untouched)
            }
        }
    }

    /** The [enabled] shelves that have series, each sorted for what it is about and capped at [LIMIT]. */
    fun build(
        library: List<LibraryManga>,
        enabled: Set<SmartShelf>,
        now: Instant,
    ): List<Pair<SmartShelf, List<LibraryManga>>> {
        if (enabled.isEmpty()) return emptyList()
        val byShelf = library.distinctBy { it.id }
            .flatMap { item -> shelves(item, now).filter { it in enabled }.map { it to item } }
            .groupBy({ it.first }, { it.second })
        return SmartShelf.entries.mapNotNull { shelf ->
            val items = byShelf[shelf] ?: return@mapNotNull null
            val sorted = when (shelf) {
                SmartShelf.NewChapters -> items.sortedByDescending { it.chapterFetchedAt }
                SmartShelf.AlmostFinished -> items.sortedByDescending { it.readCount.toDouble() / it.totalChapters }
                // Longest forgotten first
                SmartShelf.Untouched -> items.sortedBy { it.lastRead ?: it.manga.favoriteAt }
                SmartShelf.CaughtUp -> items.sortedByDescending { it.lastRead }
            }
            shelf to sorted.take(LIMIT)
        }
    }
}
