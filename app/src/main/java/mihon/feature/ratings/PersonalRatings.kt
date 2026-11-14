package mihon.feature.ratings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.feature.common.utils.seriesKey
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga

/** A user-named, ordered list of series. */
@Serializable
data class TopList(
    val id: Long,
    val name: String,
    val entries: List<String> = emptyList(),
)

/** The user's own 1–10 score per series and their top lists. The private note is the series' notes. */
@Inject
@SingleIn(AppScope::class)
class PersonalRatings(preferenceStore: PreferenceStore, json: Json) {

    /** Scores by series key. */
    val scores: Preference<Map<String, Int>> = preferenceStore.getObjectFromString(
        key = "personal_scores",
        defaultValue = emptyMap(),
        serializer = { json.encodeToString(it) },
        deserializer = { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) },
    )

    val topLists: Preference<List<TopList>> = preferenceStore.getObjectFromString(
        key = "personal_top_lists",
        defaultValue = emptyList(),
        serializer = { json.encodeToString(it) },
        deserializer = { runCatching { json.decodeFromString<List<TopList>>(it) }.getOrDefault(emptyList()) },
    )

    /** Sets [manga]'s score, or clears it when [score] is null. */
    fun setScore(manga: Manga, score: Int?) {
        val key = manga.seriesKey
        scores.set(if (score == null) scores.get() - key else scores.get() + (key to score.coerceIn(MIN, MAX)))
    }

    fun createList(name: String): TopList {
        val lists = topLists.get()
        val list = TopList(id = (lists.maxOfOrNull { it.id } ?: 0) + 1, name = name.trim())
        topLists.set(lists + list)
        return list
    }

    fun renameList(id: Long, name: String) = update(id) { it.copy(name = name.trim()) }

    fun deleteList(id: Long) {
        topLists.set(topLists.get().filterNot { it.id == id })
    }

    fun toggle(id: Long, manga: Manga) = update(id) { list ->
        val key = manga.seriesKey
        list.copy(entries = if (key in list.entries) list.entries - key else list.entries + key)
    }

    fun remove(id: Long, key: String) = update(id) { it.copy(entries = it.entries - key) }

    fun move(id: Long, from: Int, to: Int) = update(id) { list ->
        if (from !in list.entries.indices || to !in list.entries.indices) return@update list
        list.copy(entries = list.entries.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun update(id: Long, transform: (TopList) -> TopList) {
        topLists.set(topLists.get().map { if (it.id == id) transform(it) else it })
    }

    companion object {
        const val MIN = 1
        const val MAX = 10
    }
}
