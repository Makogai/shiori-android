package mihon.feature.queue

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import mihon.feature.common.utils.parseSeriesKey
import mihon.feature.common.utils.seriesKey
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.model.Manga

/** The user-ordered "Up next" queue across series, as series keys. */
@Inject
@SingleIn(AppScope::class)
class BingeQueue(
    preferenceStore: PreferenceStore,
    json: Json,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
) {

    private val preference = preferenceStore.getObjectFromString(
        key = "binge_queue",
        defaultValue = emptyList<String>(),
        serializer = { json.encodeToString(it) },
        deserializer = { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) },
    )

    fun changes(): Flow<List<String>> = preference.changes()

    fun get(): List<String> = preference.get()

    fun contains(manga: Manga): Boolean = manga.seriesKey in get()

    fun add(manga: List<Manga>) {
        val queue = get()
        preference.set(queue + manga.map { it.seriesKey }.filter { it !in queue }.distinct())
    }

    fun remove(key: String) {
        preference.set(get() - key)
    }

    fun toggle(manga: Manga) {
        if (contains(manga)) remove(manga.seriesKey) else add(listOf(manga))
    }

    fun move(from: Int, to: Int) {
        val queue = get().toMutableList()
        if (from !in queue.indices || to !in queue.indices) return
        queue.add(to, queue.removeAt(from))
        preference.set(queue)
    }

    /** The queued series in order, taken from [known] when there and looked up otherwise. */
    suspend fun resolve(keys: List<String>, known: List<Manga>): List<Manga> {
        val byKey = known.associateBy { it.seriesKey }
        return keys.mapNotNull { key ->
            byKey[key] ?: parseSeriesKey(key)?.let { (source, url) -> getMangaByUrlAndSourceId.await(url, source) }
        }
    }

    companion object {
        /** The entry to offer once the series [finished] has nothing left to read. */
        fun nextAfter(queue: List<String>, finished: String): String? = queue.firstOrNull { it != finished }
    }
}
