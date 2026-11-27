package mihon.feature.friends.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.util.LruCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * The bundled pack in `assets/shiori_cards`: the manifest and its images, decoded on demand at a sample size and
 * kept by byte count.
 */
@Inject
@SingleIn(AppScope::class)
class CardPack(private val context: Context) {

    val manifest: CardManifest? by lazy {
        try {
            context.assets.open("$ROOT/manifest.json").use { CardManifest.parse(it.reader().readText()) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Shiori Cards pack is missing or broken" }
            null
        }
    }

    /** The pack's display face for card names, else the system's bold serif. */
    val nameTypeface: Typeface by lazy {
        manifest?.fonts?.name
            ?.let { runCatching { Typeface.createFromAsset(context.assets, "$ROOT/$it") }.getOrNull() }
            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    /** The supporting sans (Inter Regular) for the card back's small lines. */
    val supportTypeface: Typeface by lazy { bundled(CardSpec.SUPPORT_FONT, Typeface.SANS_SERIF) }

    private fun bundled(path: String, fallback: Typeface): Typeface =
        runCatching { Typeface.createFromAsset(context.assets, path) }
            .onFailure { logcat(LogPriority.ERROR, it) { "Card font $path is missing" } }
            .getOrNull() ?: fallback

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The image at a manifest path, decoded at 1/[sample] of its size. */
    fun image(path: String?, sample: Int = 1): Bitmap? {
        if (path.isNullOrEmpty()) return null
        val key = "$sample|$path"
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = true
            inScaled = false
            inSampleSize = sample
        }
        val bitmap = try {
            context.assets.open("$ROOT/$path").use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Card pack image $path failed" }
            null
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /** An effect's image ready to composite: `tintWithAccent` effects take the card's accent, keeping their alpha. */
    fun effectImage(id: String, accentHex: String?, sample: Int): Bitmap? {
        val effect = manifest?.effects?.get(id) ?: return null
        val image = image(effect.file, sample) ?: return null
        if (effect.tintWithAccent != true) return image
        val key = "$sample|tint|$id|$accentHex"
        cache.get(key)?.let { return it }
        val tinted = image.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(tinted).drawPaint(
            Paint().apply {
                color = CardColor.argb(accentHex, WHITE)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            },
        )
        cache.put(key, tinted)
        return tinted
    }

    private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(MAX_CACHE).toInt()

    companion object {
        const val ROOT = "shiori_cards"
        private const val WHITE = -0x1
        private const val MAX_CACHE = 160L * 1024 * 1024
    }
}
