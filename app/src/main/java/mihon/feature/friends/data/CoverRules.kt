package mihon.feature.friends.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** What this device last uploaded for a series key. */
@Serializable
data class CoverRecord(val fingerprint: String, val url: String)

/** Shared cover thumbnails ("v4 — Shared cover thumbnails" in the backend README). */
object CoverRules {

    const val BUCKET = "covers"
    const val MAX_WIDTH = 300
    const val JPEG_QUALITY = 70
    const val MAX_UPLOADS_PER_RUN = 30

    private const val PUBLIC_PREFIX = "/storage/v1/object/public/$BUCKET/"

    /** The object path inside the storage API, for uploading. */
    fun objectPath(seriesKey: String): String = "/storage/v1/object/$BUCKET/$seriesKey.jpg"

    /** First 8 hex chars of SHA-256 of the uploaded bytes. */
    fun version(jpeg: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it) }.take(8)

    fun publicUrl(baseUrl: String, seriesKey: String, version: String? = null): String =
        "$baseUrl$PUBLIC_PREFIX$seriesKey.jpg" + (version?.let { "?v=$it" } ?: "")

    fun isStorageUrl(url: String): Boolean = PUBLIC_PREFIX in url

    /** What the app itself shows as the cover; a change means the thumbnail has to be made again. */
    fun fingerprint(thumbnailUrl: String?, coverLastModifiedMillis: Long?): String =
        "${thumbnailUrl.orEmpty()}|${coverLastModifiedMillis ?: ""}"

    fun needsUpload(record: CoverRecord?, fingerprint: String): Boolean =
        record == null || record.fingerprint != fingerprint

    /**
     * URLs to try for a cover, best first: the shared thumbnail ([coverUrl] when it already is one, else derived from
     * [seriesKey]), then the original [coverUrl].
     */
    fun candidates(coverUrl: String?, seriesKey: String?, baseUrl: String): List<String> {
        val shared = coverUrl?.takeIf(::isStorageUrl)
            ?: seriesKey?.takeIf { it.isNotEmpty() && baseUrl.isNotEmpty() }?.let { publicUrl(baseUrl, it) }
        return listOfNotNull(shared, coverUrl?.takeIf { it.isNotBlank() }).distinct()
    }

    /** First letter of the first two words, uppercase. */
    fun initials(title: String): String =
        title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } ?: word.firstOrNull() }
            .joinToString("") { it.uppercase() }

    /** Placeholder hue in degrees, stable for a title. */
    fun hue(title: String): Float = Math.floorMod(title.hashCode(), 360).toFloat()
}
