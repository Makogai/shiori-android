package mihon.feature.friends.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

object AvatarImage {

    const val MAX_SIZE = 512
    private const val QUALITY = 85

    /** Decodes the picked image, crops its centre square and returns it as a JPEG of at most [MAX_SIZE] px. */
    suspend fun fromUri(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val decoded = decode(context, uri)
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val target = min(side, MAX_SIZE)
        val scaled = if (target == side) square else Bitmap.createScaledBitmap(square, target, target, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation; shrink large photos while decoding
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortSide = min(info.size.width, info.size.height)
                if (shortSide > MAX_SIZE * 2) {
                    val ratio = MAX_SIZE * 2f / shortSide
                    decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Couldn't read the image")
    }
}
