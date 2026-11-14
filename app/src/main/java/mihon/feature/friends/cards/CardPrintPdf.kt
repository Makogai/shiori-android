package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.roundToInt
import mihon.feature.friends.cards.CardPrintSheet as S

/** Writes the one-page print sheet PDF (CARD_SPEC §15) from the 1500 × 2100 front and back. */
object CardPrintPdf {

    fun write(front: Bitmap, back: Bitmap, caption: String, output: OutputStream) {
        val document = PdfDocument()
        try {
            // PdfDocument takes whole points: 442 × 306 (the sheet is 442.08 pt; the 0.08 pt is right margin)
            val info = PdfDocument.PageInfo.Builder(S.PAGE_W_PT.roundToInt(), S.PAGE_H_PT.roundToInt(), 1).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            canvas.scale((1 / S.PX_PER_PT).toFloat(), (1 / S.PX_PER_PT).toFloat())
            canvas.drawColor(Color.WHITE)

            val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            listOf(front to S.FRONT, back to S.BACK).forEach { (image, trim) ->
                // Embedded at 300 dpi, scaled uniformly (5:7) and centred in the trim
                val box = S.fit(trim)
                val scaled = Bitmap.createScaledBitmap(image, box.width.roundToInt(), box.height.roundToInt(), true)
                canvas.drawBitmap(scaled, null, RectF(box.left, box.top, box.right, box.bottom), filter)
                if (scaled !== image) scaled.recycle()
            }

            val marks = Paint().apply {
                color = Color.BLACK
                strokeWidth = S.MARK_WIDTH
                strokeCap = Paint.Cap.BUTT
                style = Paint.Style.STROKE
            }
            S.MARKS.forEach { canvas.drawLine(it.x1, it.y1, it.x2, it.y2, marks) }

            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = S.CAPTION_COLOR.toInt()
                textSize = S.CAPTION_SIZE
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(caption, S.PAGE_W / 2f, S.CAPTION_BASELINE, text)

            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}
