package mihon.feature.recommendations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.feature.common.utils.seriesKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Serializable
data class Recommendation(val title: String, val cover: String?)

@Serializable
private data class CachedRecommendations(val fetchedAt: Long, val items: List<Recommendation>)

/**
 * "More like this": AniList's `Media.recommendations` for a series, through its public GraphQL API without an
 * account. The series is the linked AniList entry when there is one, otherwise the best title match.
 */
@Inject
@SingleIn(AppScope::class)
class AniListRecommendations(
    private val networkHelper: NetworkHelper,
    private val json: Json,
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    preferenceStore: PreferenceStore,
) {

    private val cache = preferenceStore.getObjectFromString(
        key = Preference.appStateKey("anilist_recommendations"),
        defaultValue = emptyMap<String, CachedRecommendations>(),
        serializer = { json.encodeToString(it) },
        deserializer = {
            runCatching { json.decodeFromString<Map<String, CachedRecommendations>>(it) }.getOrDefault(emptyMap())
        },
    )

    /** Cached for [CACHE_FOR]; throws when AniList can't be reached. */
    suspend fun get(manga: Manga): List<Recommendation> {
        val key = manga.seriesKey
        val now = Clock.System.now().toEpochMilliseconds()
        cache.get()[key]?.takeIf { now - it.fetchedAt < CACHE_FOR.inWholeMilliseconds }?.let { return it.items }

        val mediaId = getTracks.await(manga.id).find { it.trackerId == trackerManager.aniList.id }?.remoteId
        val items = try {
            fetch(mediaId, manga.title)
        } catch (e: HttpException) {
            // AniList answers a search without a match with 404
            if (e.code == 404) emptyList() else throw e
        }
        val fresh = cache.get().filterValues { now - it.fetchedAt < CACHE_FOR.inWholeMilliseconds }
        cache.set(fresh + (key to CachedRecommendations(now, items)))
        return items
    }

    private suspend fun fetch(mediaId: Long?, title: String): List<Recommendation> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", QUERY)
            put(
                "variables",
                buildJsonObject {
                    if (mediaId != null) put("id", mediaId) else put("search", title)
                },
            )
        }
        val response = networkHelper.client.newCall(
            POST(API_URL, body = body.toString().toRequestBody("application/json".toMediaType())),
        )
            .awaitSuccess()
        val root = response.body.use { json.parseToJsonElement(it.string()) }.jsonObject
        val media = (root["data"] as? JsonObject)?.get("Media") as? JsonObject ?: return@withContext emptyList()
        val nodes = (media["recommendations"] as? JsonObject)?.get("nodes") as? JsonArray
            ?: return@withContext emptyList()
        nodes.mapNotNull { node ->
            val recommended = (node as? JsonObject)?.get("mediaRecommendation") as? JsonObject
                ?: return@mapNotNull null
            val titles = recommended["title"] as? JsonObject ?: return@mapNotNull null
            val name = listOf("userPreferred", "english", "romaji")
                .firstNotNullOfOrNull { titles[it]?.jsonPrimitive?.contentOrNull }
                ?: return@mapNotNull null
            val cover = (recommended["coverImage"] as? JsonObject)?.get("large")?.jsonPrimitive?.contentOrNull
            Recommendation(name, cover)
        }
    }

    companion object {
        val CACHE_FOR = 7.days
        private const val API_URL = "https://graphql.anilist.co/"
        private val QUERY = """
            query (${'$'}id: Int, ${'$'}search: String) {
              Media(id: ${'$'}id, search: ${'$'}search, type: MANGA) {
                recommendations(sort: RATING_DESC, perPage: 15) {
                  nodes { mediaRecommendation { title { userPreferred english romaji } coverImage { large } } }
                }
              }
            }
        """.trimIndent()
    }
}
