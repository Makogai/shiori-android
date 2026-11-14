package mihon.feature.friends.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.feature.friends.domain.LeagueHashing
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.model.Manga
import java.io.ByteArrayOutputStream

/** A series whose cover is about to be sent; [fallback] is the original http(s) cover URL. */
data class CoverSeries(val sourceId: Long, val mangaUrl: String, val fallback: String?) {
    val key: String get() = LeagueHashing.seriesKey(sourceId, mangaUrl)
}

/**
 * Publishes small copies of the covers this app shows to the shared `covers` bucket, so other players can see
 * covers whose source needs its own headers. Upload records are kept per device.
 */
@Inject
@SingleIn(AppScope::class)
class CoverThumbnails(
    private val context: Context,
    private val getManga: GetMangaByUrlAndSourceId,
) {

    private val prefs by lazy { context.getSharedPreferences("friends_covers", Context.MODE_PRIVATE) }

    /**
     * The cover URL to send for each series key: the shared thumbnail, uploading it first when it's missing or the
     * cover changed (at most [CoverRules.MAX_UPLOADS_PER_RUN] per call), else the original URL.
     */
    suspend fun publish(
        series: List<CoverSeries>,
        upload: suspend (seriesKey: String, jpeg: ByteArray) -> String,
    ): Map<String, String?> {
        var uploads = 0
        return series.distinctBy { it.key }.associate { item ->
            val key = item.key
            val record = record(key)
            val manga = getManga.await(item.mangaUrl, item.sourceId)
                ?: return@associate key to (record?.url ?: item.fallback)
            val fingerprint = CoverRules.fingerprint(manga.thumbnailUrl, manga.coverLastModified?.toEpochMilliseconds())
            if (!CoverRules.needsUpload(record, fingerprint)) return@associate key to record!!.url
            if (uploads >= CoverRules.MAX_UPLOADS_PER_RUN) return@associate key to item.fallback
            uploads++
            val url = try {
                thumbnail(manga)?.let { jpeg -> upload(key, jpeg) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Cover thumbnail upload failed" }
                null
            }
            if (url != null) save(key, CoverRecord(fingerprint, url))
            key to (url ?: item.fallback)
        }
    }

    /** The app's own cover (custom covers and source headers included) as a JPEG at most 300 px wide. */
    private suspend fun thumbnail(manga: Manga): ByteArray? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.asDrawable(context.resources)?.getBitmapOrNull()
            ?: return@withContext null
        val scaled = if (bitmap.width > CoverRules.MAX_WIDTH) {
            val height = (bitmap.height.toLong() * CoverRules.MAX_WIDTH / bitmap.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, CoverRules.MAX_WIDTH, height, true)
        } else {
            bitmap
        }
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, CoverRules.JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun record(key: String): CoverRecord? = prefs.getString(key, null)?.let {
        runCatching { FriendsJson.decodeFromString(CoverRecord.serializer(), it) }.getOrNull()
    }

    private fun save(key: String, record: CoverRecord) =
        prefs.edit { putString(key, FriendsJson.encodeToString(CoverRecord.serializer(), record)) }
}
