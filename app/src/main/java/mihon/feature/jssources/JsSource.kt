package mihon.feature.jssources

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A source that lives in a JavaScript extension. To the rest of the app it is an ordinary [HttpSource]; every call goes
 * to the script through its [JsExtensionRuntime].
 */
class JsSource internal constructor(
    private val runtime: JsExtensionRuntime,
    private val loaded: JsLoadedSource,
    /** Package of the extension the source comes from, such as `en.mangahere`. */
    val pkg: String,
    private val environment: JsEnvironment,
) : HttpSource(), ConfigurableSource {

    private val index = loaded.index

    override val id: Long = loaded.id
    override val name: String = loaded.info.name
    override val lang: String = loaded.info.lang
    override val baseUrl: String = loaded.info.baseUrl
    override val supportsLatest: Boolean = loaded.info.supportsLatest
    override val versionId: Int = loaded.info.versionId ?: 1

    override val client: OkHttpClient get() = runtime.client

    // The app adds its User-Agent when a request has none
    override fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        loaded.headers.forEach { (name, value) -> set(name, value) }
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        call("getPopularManga", JsMangasPage.serializer(), JsonPrimitive(page)).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (!supportsLatest) throw UnsupportedOperationException("Latest updates")
        return call("getLatestUpdates", JsMangasPage.serializer(), JsonPrimitive(page)).toMangasPage()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = call(
        "getSearchManga",
        JsMangasPage.serializer(),
        JsonPrimitive(page),
        JsonPrimitive(query),
        JsFilters.toJson(filters),
    ).toMangasPage()

    /** The filters come from the script, which answers at once; the browse screen asks for them off the main thread. */
    override fun getFilterList(): FilterList = runCatching {
        runBlocking {
            JsFilters.fromJson(
                jsJson.parseToJsonElement(runtime.invoke(index, "getFilterList", JsonArray(emptyList()))).jsonArray,
            )
        }
    }.getOrElse { FilterList() }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val update = call(
            "getMangaUpdate",
            JsMangaUpdate.serializer(),
            jsJson.encodeToJsonElement(JsManga.serializer(), JsManga.from(manga)),
            jsJson.encodeToJsonElement(ListSerializer(JsChapter.serializer()), chapters.map(JsChapter::from)),
            JsonPrimitive(fetchDetails),
            JsonPrimitive(fetchChapters),
        )
        val updated = update.manga.toSManga().also { it.memo = manga.memo }
        return SMangaUpdate(updated, update.chapters.map { it.toSChapter() })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = call(
        "getPageList",
        ListSerializer(JsPage.serializer()),
        jsJson.encodeToJsonElement(JsChapter.serializer(), JsChapter.from(chapter)),
    ).map { it.toPage() }

    override suspend fun getImageUrl(page: Page): String =
        call("getImageUrl", String.serializer(), jsJson.encodeToJsonElement(JsPage.serializer(), JsPage.from(page)))

    /** Called by [getImage] on a download thread; the script answers without network work, so blocking is short. */
    override fun imageRequest(page: Page): Request = runBlocking {
        val spec = call(
            "getImageRequest",
            JsImageRequest.serializer(),
            jsJson.encodeToJsonElement(JsPage.serializer(), JsPage.from(page)),
        )
        val url = spec.url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: ${spec.url}")
        val headers = Headers.Builder().apply { spec.headers.forEach { (name, value) -> set(name, value) } }
        JsFetch.IMAGE.apply(headers, url, "GET", null, baseUrl.toHttpUrlOrNull(), environment.acceptLanguage)
        val limiter = runtime.rateLimitRule(index, spec.url)?.let { loaded.rateLimiters.getOrNull(it) }
        Request.Builder()
            .url(url)
            .headers(headers.build())
            .tag(
                JsRequestTag::class.java,
                JsRequestTag(
                    cookies = runtime.cookies(index, spec.url),
                    rateLimiter = limiter,
                    transform = imageTransform(page),
                ),
            )
            .build()
    }

    /** Asks the script how to unscramble the downloaded image, given its size, and redraws it; null when it doesn't. */
    private fun imageTransform(page: Page): (suspend (ByteArray) -> ByteArray)? {
        val tools = environment.imageTools
        if (loaded.info.hasImageTransform != true || tools == null) return null
        val pageJson = jsJson.encodeToJsonElement(JsPage.serializer(), JsPage.from(page))
        return transform@{ bytes ->
            val (width, height) = tools.size(bytes) ?: return@transform bytes
            val transform = call(
                "getImageTransform",
                JsImageTransform.serializer().nullable,
                pageJson,
                JsonPrimitive(width),
                JsonPrimitive(height),
            ) ?: return@transform bytes
            tools.apply(bytes, transform)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        loaded.info.preferences.forEach { json -> preference(screen, json)?.let(screen::addPreference) }
    }

    private fun preference(screen: PreferenceScreen, json: JsonObject): androidx.preference.Preference? {
        val key = json.string("key") ?: return null
        val title = json.string("title").orEmpty()
        val summary = json.string("summary")?.ifEmpty { null }
        val default = json["default"]
        val entries = (json["entries"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        val titles = entries.map { it.string("title").orEmpty() }.toTypedArray()
        val values = entries.map { it.string("value").orEmpty() }.toTypedArray()
        return when (json.string("type")) {
            "toggle" -> SwitchPreferenceCompat(screen.context).apply {
                setDefaultValue((default as? JsonPrimitive)?.booleanOrNull ?: false)
                this.summary = summary
            }
            "list" -> ListPreference(screen.context).apply {
                this.entries = titles
                entryValues = values
                setDefaultValue((default as? JsonPrimitive)?.contentOrNull)
                this.summary = summary ?: "%s"
            }
            "multiselect" -> MultiSelectListPreference(screen.context).apply {
                this.entries = titles
                entryValues = values
                setDefaultValue(
                    (default as? JsonArray)?.map {
                        it.jsonPrimitive.content
                    }?.toSet() ?: emptySet<String>(),
                )
                this.summary = summary
            }
            "text" -> EditTextPreference(screen.context).apply {
                setDefaultValue((default as? JsonPrimitive)?.contentOrNull)
                dialogTitle = title
                this.summary = summary
            }
            else -> null
        }?.apply {
            this.key = key
            this.title = title
        }
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private suspend fun <T> call(method: String, serializer: KSerializer<T>, vararg arguments: JsonElement): T =
        jsJson.decodeFromString(serializer, runtime.invoke(index, method, JsonArray(arguments.toList())))
}
