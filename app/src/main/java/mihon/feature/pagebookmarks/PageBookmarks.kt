package mihon.feature.pagebookmarks

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.feature.common.utils.seriesKey
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga

/** A bookmarked page: [page] is its index in the chapter. Chapters are named by URL, which a restore keeps. */
@Serializable
data class PageBookmark(
    val series: String,
    val chapterUrl: String,
    val chapterName: String,
    val page: Int,
    val note: String = "",
    val createdAt: Long,
)

/** Page bookmarks with an optional note, as opposed to Mihon's chapter bookmarks. */
@Inject
@SingleIn(AppScope::class)
class PageBookmarks(preferenceStore: PreferenceStore, json: Json) {

    private val preference = preferenceStore.getObjectFromString(
        key = "page_bookmarks",
        defaultValue = emptyList<PageBookmark>(),
        serializer = { json.encodeToString(it) },
        deserializer = { runCatching { json.decodeFromString<List<PageBookmark>>(it) }.getOrDefault(emptyList()) },
    )

    /** [manga]'s bookmarks, oldest first. */
    fun subscribe(manga: Manga): Flow<List<PageBookmark>> {
        val key = manga.seriesKey
        return preference.changes().map { all -> all.filter { it.series == key } }
    }

    fun find(manga: Manga, chapterUrl: String, page: Int): PageBookmark? {
        val key = manga.seriesKey
        return preference.get().find { it.series == key && it.chapterUrl == chapterUrl && it.page == page }
    }

    /** Adds the bookmark, or replaces the note of the one already on that page. */
    fun save(bookmark: PageBookmark) {
        preference.set(preference.get().filterNot { it.samePage(bookmark) } + bookmark)
    }

    fun remove(bookmark: PageBookmark) {
        preference.set(preference.get().filterNot { it.samePage(bookmark) })
    }

    private fun PageBookmark.samePage(other: PageBookmark) =
        series == other.series && chapterUrl == other.chapterUrl && page == other.page
}
