package eu.kanade.tachiyomi.ui.reader.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.graphics.createBitmap
import ca.mpreg.imagedecoder.ImageDecoder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

/**
 * The page images a screenshot is made from: loads pages through the chapter's loader, reads their pixel sizes and
 * decodes them, whole for the screen or a crop at a time for stitching.
 */
class CapturePages(val pages: List<ReaderPage>) {

    /** Pixel sizes known so far, by index in [pages]. */
    val sizes = mutableStateMapOf<Int, PixelSize>()

    fun sizeList(): List<PixelSize?> = pages.indices.map { sizes[it] }

    /** The page's image, loading it first when needed. Null when it fails or takes too long. */
    suspend fun stream(index: Int): (() -> InputStream)? {
        val page = pages.getOrNull(index) ?: return null
        if (page.status != Page.State.Ready) {
            val loader = page.chapter.pageLoader ?: return null
            coroutineScope {
                // The loader doesn't always return, so it only runs until the page is ready
                val job = launch(Dispatchers.IO) { loader.loadPage(page) }
                withTimeoutOrNull(LOAD_TIMEOUT) {
                    page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                }
                job.cancel()
            }
        }
        if (page.status != Page.State.Ready) return null
        val stream = page.stream ?: return null
        if (index !in sizes) {
            withContext(Dispatchers.IO) { readSize(stream) }?.let { sizes[index] = it }
        }
        return stream
    }

    /**
     * Loads the pages at [indices] and their sizes, in order: an online loader keeps downloading only around the
     * page last asked for, so pages asked for together would cancel each other.
     */
    suspend fun load(indices: Iterable<Int>) {
        indices.filter { it !in sizes }.forEach { stream(it) }
    }

    /** The page for the screen: about [width] wide and never taller than a texture can be. */
    suspend fun displayBitmap(index: Int, width: Int): Bitmap? {
        val stream = stream(index) ?: return null
        return withContext(Dispatchers.IO) {
            decodes.withPermit {
                try {
                    stream().use { input ->
                        ImageDecoder.open(input).use { decoder ->
                            val sample = max(
                                sampleSizeFor(decoder.width, width),
                                ceil(decoder.height / MAX_DISPLAY_HEIGHT.toDouble()).toInt(),
                            )
                            decoder.decodeToBitmap(null, sample)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e)
                    null
                }
            }
        }
    }

    /**
     * Draws [plan] into one bitmap, a page at a time, so each page is released as soon as it is drawn. Null when
     * the bitmap can't be made or no page could be read.
     */
    suspend fun stitch(plan: StitchPlan): Bitmap? {
        val output = try {
            createBitmap(plan.width, plan.height)
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.ERROR, e)
            return null
        }
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var drawn = 0
        for (slice in plan.slices) {
            val stream = stream(slice.page) ?: continue
            val size = sizes[slice.page] ?: continue
            val page = withContext(Dispatchers.IO) {
                decodes.withPermit {
                    try {
                        stream().use { input ->
                            ImageDecoder.open(input).use { decoder ->
                                decoder.decodeToBitmap(null, sampleSizeFor(size.width, slice.destination.width))
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e)
                        null
                    }
                }
            } ?: continue
            // A sample size shrinks the page; the crop is in its full-size pixels
            val scale = page.width.toFloat() / size.width
            val crop = slice.crop
            val source = Rect(
                (crop.left * scale).toInt(),
                (crop.top * scale).toInt(),
                ((crop.left + crop.width) * scale).toInt().coerceAtMost(page.width),
                (crop.bottom * scale).toInt().coerceAtMost(page.height),
            )
            val destination = slice.destination
            canvas.drawBitmap(
                page,
                source,
                Rect(destination.left, destination.top, destination.left + destination.width, destination.bottom),
                paint,
            )
            page.recycle()
            drawn++
        }
        if (drawn == 0) {
            output.recycle()
            return null
        }
        return output
    }

    private fun readSize(stream: () -> InputStream): PixelSize? = try {
        stream().use { input -> ImageDecoder.open(input).use { PixelSize(it.width, it.height) } }
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        null
    }
}

private val LOAD_TIMEOUT = 60.seconds

/** Taller bitmaps may not draw: GPUs cap the size of a texture. */
private const val MAX_DISPLAY_HEIGHT = 4096

// Whole pages decode into native memory first; two at a time keep a long strip from piling them up
private val decodes = Semaphore(2)
