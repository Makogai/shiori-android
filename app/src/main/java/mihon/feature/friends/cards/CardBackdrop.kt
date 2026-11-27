package mihon.feature.friends.cards

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The backdrop under a legacy (not 5:7) AI card: the same image covering the card, blurred and darkened. Made small
 * ([SHRINK] of the output) and drawn scaled up. Blurred with [RenderEffect] on API 31+; before that (or if the GPU
 * path fails) a darkened scaled copy without blur.
 */
object CardBackdrop {

    /** The backdrop is made at 1/[SHRINK] of the card's size. */
    const val SHRINK = 6

    /** Blur radius as a share of the backdrop's width. */
    const val BLUR_SHARE = 0.035f

    /** Black over the copy: the art in front stays the subject. */
    const val DARKEN = 0.5f

    private val cache = LruCache<String, Bitmap>(6)

    /** The backdrop for [image] on a card [width] × [height] pixels (any scale; the caller draws it over the card). */
    fun of(image: Bitmap, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0 || image.width <= 0 || image.height <= 0) return null
        val w = max(width / SHRINK, 8)
        val h = max((w * height.toFloat() / width).roundToInt(), 8)
        val key = "${System.identityHashCode(image)}|${image.width}x${image.height}|$w"
        cache.get(key)?.let { return it }
        val small = cover(image, w, h)
        val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { blur(small, w * BLUR_SHARE) }
                .onFailure { logcat(LogPriority.WARN, it) { "Card backdrop blur failed" } }
                .getOrNull() ?: small
        } else {
            small
        }
        val out = if (blurred.isMutable) blurred else blurred.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawColor(Color.argb((DARKEN * 255).roundToInt(), 0, 0, 0))
        cache.put(key, out)
        return out
    }

    /** [image] covering a [w] × [h] bitmap, centred. */
    private fun cover(image: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rect = CardGeometry.coverFit(
            image.width.toFloat(),
            image.height.toFloat(),
            CardBox(0f, 0f, w.toFloat(), h.toFloat()),
        )
        Canvas(out).drawBitmap(image, null, rect.toRectF(), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** A GPU blur through a [RenderNode] with a [RenderEffect], read back into a software bitmap. */
    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun blur(source: Bitmap, radius: Float): Bitmap? {
        val w = source.width
        val h = source.height
        val reader = ImageReader.newInstance(
            w,
            h,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("CardBackdrop")
        try {
            node.setPosition(0, 0, w, h)
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            val canvas = node.beginRecording()
            canvas.drawBitmap(source, 0f, 0f, null)
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: return null
            return image.use {
                val buffer = it.hardwareBuffer ?: return null
                buffer.use { hb ->
                    Bitmap.wrapHardwareBuffer(hb, null)?.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }
}
