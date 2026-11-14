package mihon.feature.jssources

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Host API level this app implements, as `extensionHostAPILevel` in the iOS app:
 * 1 is HTTP, HTML, preferences, hashing and isolated evaluation; 2 adds WebView runs and image transforms.
 */
const val JS_HOST_API_LEVEL = 2

internal val jsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** One entry of the repository's `index.json`. */
@Serializable
data class JsExtensionInfo(
    val name: String,
    val pkg: String,
    val versionCode: Int,
    val versionName: String,
    val lang: String,
    val nsfw: Boolean,
    val api: Int,
    val file: String,
    val sha256: String,
    val icon: String? = null,
    val sources: List<JsExtensionSourceInfo>,
)

@Serializable
data class JsExtensionSourceInfo(
    val name: String,
    val lang: String,
    /** Decimal string: source IDs are 64-bit and don't fit in a JavaScript number. */
    val id: String,
    val baseUrl: String,
)

/** What a script says about one of its sources, read once after loading. */
@Serializable
internal data class JsSourceInfo(
    val name: String,
    val lang: String,
    val baseUrl: String,
    val supportsLatest: Boolean,
    val versionId: Int? = null,
    val id: String? = null,
    val client: JsClientConfig = JsClientConfig(),
    val preferences: List<JsonObject> = emptyList(),
    val hasImageTransform: Boolean? = null,
)

@Serializable
internal data class JsClientConfig(
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val rateLimits: List<JsRateLimit> = emptyList(),
)

@Serializable
internal data class JsRateLimit(val permits: Int, val periodMs: Long, val intervalMs: Long = 0)

/** `SManga` as scripts see it, with upstream's field names. */
@Serializable
internal data class JsManga(
    val url: String = "",
    val title: String = "",
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = SManga.UNKNOWN,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("update_strategy") val updateStrategy: Int = 0,
    val initialized: Boolean = false,
) {
    fun toSManga(): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnailUrl
        it.update_strategy = UpdateStrategy.entries.getOrElse(updateStrategy) { UpdateStrategy.ALWAYS_UPDATE }
        it.initialized = initialized
    }

    companion object {
        fun from(manga: SManga) = JsManga(
            url = manga.url,
            title = manga.title,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            status = manga.status,
            thumbnailUrl = manga.thumbnail_url,
            updateStrategy = manga.update_strategy.ordinal,
            initialized = manga.initialized,
        )
    }
}

@Serializable
internal data class JsChapter(
    val url: String = "",
    val name: String = "",
    // A script may hand back a computed, non-integral time
    @SerialName("date_upload") val dateUpload: Double = 0.0,
    @SerialName("chapter_number") val chapterNumber: Float = -1f,
    val scanlator: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload.toLong()
        it.chapter_number = chapterNumber
        it.scanlator = scanlator
    }

    companion object {
        fun from(chapter: SChapter) = JsChapter(
            url = chapter.url,
            name = chapter.name,
            dateUpload = chapter.date_upload.toDouble(),
            chapterNumber = chapter.chapter_number,
            scanlator = chapter.scanlator,
        )
    }
}

@Serializable
internal data class JsPage(val index: Int, val url: String = "", val imageUrl: String? = null) {
    fun toPage() = Page(index, url, imageUrl)

    companion object {
        fun from(page: Page) = JsPage(page.index, page.url, page.imageUrl)
    }
}

@Serializable
internal data class JsMangasPage(val mangas: List<JsManga>, val hasNextPage: Boolean) {
    fun toMangasPage() = MangasPage(mangas.map { it.toSManga() }, hasNextPage)
}

@Serializable
internal data class JsMangaUpdate(val manga: JsManga, val chapters: List<JsChapter>)

@Serializable
internal data class JsImageRequest(val url: String, val headers: Map<String, String> = emptyMap())

/** How to unscramble an image: the output size and, per tile, where it is in the download and where it goes. */
@Serializable
data class JsImageTransform(val width: Double, val height: Double, val tiles: List<JsImageTile>)

@Serializable
data class JsImageTile(
    val sx: Double,
    val sy: Double,
    val sw: Double,
    val sh: Double,
    val dx: Double,
    val dy: Double,
    val dw: Double? = null,
    val dh: Double? = null,
)

@Serializable
data class JsWebViewRequest(
    val url: String,
    val userAgent: String? = null,
    val headers: Map<String, String>? = null,
    /** Runs before the page's own scripts; may call `__tachiyomi.post(text)` and `__tachiyomi.done()`. */
    val script: String? = null,
    /** Evaluated every half second after load; a non-empty string ends the run. */
    val pollScript: String? = null,
    val timeoutMs: Long = 30_000,
    val loadImages: Boolean? = null,
    val visible: Boolean? = null,
)

@Serializable
data class JsWebViewResult(
    val payloads: List<String>,
    val result: String?,
    val html: String,
    val url: String,
    val timedOut: Boolean,
)
