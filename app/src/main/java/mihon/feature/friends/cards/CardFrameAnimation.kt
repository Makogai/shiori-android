package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.ByteBuffer

/** An animated card edit, decoded: the drawable that plays it and its first frame (for Reduce Motion). */
class CardFrameAnimation(val drawable: Drawable, val firstFrame: Bitmap) {

    companion object {
        /** AnimatedImageDrawable needs API 28; below that the static PNG edit shows. */
        fun canPlay(sdk: Int): Boolean = sdk >= Build.VERSION_CODES.P

        /** "GIF87a" or "GIF89a". */
        fun looksLikeGif(bytes: ByteArray?): Boolean {
            if (bytes == null || bytes.size < 6) return false
            val head = String(bytes, 0, 6, Charsets.US_ASCII)
            return head == "GIF87a" || head == "GIF89a"
        }

        /** The GIF as a looping drawable; null when it can't play here or doesn't decode (the static edit shows). */
        fun decode(bytes: ByteArray?): CardFrameAnimation? {
            if (!canPlay(Build.VERSION.SDK_INT) || !looksLikeGif(bytes)) return null
            return try {
                decodeP(bytes!!)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Decoding a card animation failed" }
                null
            }
        }

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
        private fun decodeP(bytes: ByteArray): CardFrameAnimation? {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            if (drawable !is AnimatedImageDrawable) return null
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            val first = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            return CardFrameAnimation(drawable, first)
        }
    }
}
