package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.ZoneOffset
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * Debug only: Shiori Cards v1.2 fronts and backs for a few ranks from synthetic art, through the real renderers (no
 * sign-in, no server). Extras: `grades` (stored codes, e.g. "C,E,L,S"), `side` (front | back | both), `legacy`
 * (2:3 art for the fronts), `template` (pack-frame cards instead of AI cards). Also writes 1500 px PNGs to the app's external files dir, `cards-preview/`.
 *
 * adb shell am start -n app.mihon.friends/mihon.feature.friends.cards.CardsPreviewActivity --es grades C,E,L,S
 *
 * `viewer` (a stored grade code) opens that card in the 3-D viewer instead, at rest at `yaw` / `pitch` degrees
 * (floats, default 0) so screenshots can catch it turned:
 *
 * adb shell am start -n app.mihon.friends/mihon.feature.friends.cards.CardsPreviewActivity --es viewer S --ef yaw 35
 */
class CardsPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grades = (intent.getStringExtra("grades") ?: "C,E,L,S").split(',').map { CardGrade.of(it.trim()) }
        val side = intent.getStringExtra("side") ?: "both"
        val legacy = intent.getBooleanExtra("legacy", false)
        val template = intent.getBooleanExtra("template", false)
        val pack = CardPack(applicationContext)
        val renderer = CardRenderer(pack)
        val fonts = CardBackFonts(pack.supportTypeface)
        val out = File(getExternalFilesDir(null), "cards-preview").apply { mkdirs() }

        val fronts = grades.associateWith { grade ->
            val art = art(grade, if (legacy) 1024 else 1000, if (legacy) 1536 else 1400)
            val face = face(grade, serial(grade), template)
            val suffix = if (template) {
                "-template"
            } else if (legacy) {
                "-legacy"
            } else {
                ""
            }
            // The export (number as its last layer) to a file; on screen, as the app: number drawn over the shine
            val (artIn, frameIn) = if (template) art to null else null to art
            renderer.render(face, artIn, frameIn, 1500)?.let { png(it, File(out, "front-${grade.rank}$suffix.png")) }
            renderer.render(face, artIn, frameIn, 1080) { it != CardLayer.Metadata }
        }
        grades.forEach {
            png(
                CardBackRenderer.render(CardBackFace(it, SERIES[it.ordinal], serial(it)), fonts),
                File(out, "back-${it.rank}.png"),
            )
        }

        intent.getStringExtra("viewer")?.let { code ->
            val grade = CardGrade.of(code)
            val face = face(grade, serial(grade), template = false)
            val front = renderer.render(face, null, art(grade, 1000, 1400), 1080) { it != CardLayer.Metadata }
            val back = CardBackRenderer.render(CardBackFace(grade, SERIES[grade.ordinal], serial(grade)), fonts, 1080)
            val faces = CardViewerFaces(
                front = front?.let { CardTexture.bake(it, CardSpec.cardIdText(serial(grade)), pack.metadataTypeface) },
                back = CardTexture.bake(back, null, null),
            )
            val yaw = intent.getFloatExtra("yaw", 0f)
            val pitch = intent.getFloatExtra("pitch", 0f)
            setContent {
                CardViewer3D(
                    front = faces.front,
                    back = faces.back,
                    grade = grade,
                    name = "Preview ${grade.rank}",
                    startOnBack = false,
                    onDismiss = ::finish,
                    restYaw = yaw,
                    restPitch = pitch,
                )
            }
            return
        }

        setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101014))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (side != "back") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        grades.forEach { grade ->
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.aspectRatio(CARD_ASPECT)) {
                                    fronts[grade]?.let {
                                        Image(
                                            it.asImageBitmap(),
                                            null,
                                            Modifier.fillMaxSize(),
                                            contentScale = ContentScale.FillBounds,
                                        )
                                    }
                                    ComposeCanvas(Modifier.fillMaxSize()) {
                                        drawCardShine(CardShine.of(grade), CardTilt.EXPORT, 0.0)
                                    }
                                    ComposeCanvas(Modifier.fillMaxSize()) {
                                        val canvas = drawContext.canvas.nativeCanvas
                                        canvas.save()
                                        val k = CardSpec.scale(size.width)
                                        canvas.scale(k, k)
                                        CardNumber.draw(
                                            canvas,
                                            CardSpec.cardIdText(serial(grade))!!,
                                            pack.metadataTypeface,
                                        )
                                        canvas.restore()
                                    }
                                }
                                Text(grade.label, color = Color(grade.rankColor), fontSize = 11.sp)
                            }
                        }
                    }
                }
                if (side != "front") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        grades.forEach { grade ->
                            val face = CardBackFace(grade, SERIES[grade.ordinal], serial(grade))
                            // The same live path as CardInfoBack: a Compose Canvas drawing the 750 × 1050 design
                            ComposeCanvas(Modifier.weight(1f).aspectRatio(CARD_ASPECT)) {
                                val canvas = drawContext.canvas.nativeCanvas
                                canvas.save()
                                val k = size.width / CardBackLayout.WIDTH
                                canvas.scale(k, k)
                                CardBackRenderer.draw(canvas, face, fonts)
                                canvas.restore()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun serial(grade: CardGrade): Long = listOf(21L, 17L, 2L, 8L, 13L, 4L, 1L)[grade.ordinal]

    private fun face(grade: CardGrade, serial: Long, template: Boolean): CardFace {
        val card = ShioriCard(
            id = "preview-${grade.code}",
            owner = "preview",
            serial = serial,
            gradeCode = grade.code,
            cardName = if (template) "Kim Dokja" else "Preview",
            seriesTitle = SERIES[grade.ordinal],
            effects = listOf("holo_rainbow", "foil_noise", "sparkle_burst"),
            createdAt = "2026-11-13T10:00:00Z",
            frameStatusCode = if (template) "none" else "ready",
            framePath = if (template) null else "preview.png",
            frameStyleCode = if (template) null else "edit",
        )
        return CardFace(card, ZoneOffset.UTC)
    }

    /** Synthetic art: a coloured scene with a bright "figure", so the backdrop blur and the number's contrast show. */
    private fun art(grade: CardGrade, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val accent = grade.rankColor
        c.drawPaint(
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        w.toFloat(),
                        h.toFloat(),
                        intArrayOf(0xFF1B2A4A.toInt(), accent, 0xFF2A1238.toInt()),
                        null,
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 9) {
            stripe.color = listOf(0x55FF6B6B, 0x55FFD93D, 0x556BCB77, 0x554D96FF, 0x55C77DFF)[i % 5]
            c.drawRect(i * w / 9f, 0f, i * w / 9f + w / 18f, h.toFloat(), stripe)
        }
        c.drawCircle(
            w * 0.5f,
            h * 0.42f,
            w * 0.3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        w * 0.45f,
                        h * 0.38f,
                        w * 0.32f,
                        intArrayOf(-0x1, accent, 0xFF000000.toInt()),
                        null,
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        // A soft dark fade at the bottom, like a generated card's natural clear space
        c.drawRect(
            0f,
            h * 0.78f,
            w.toFloat(),
            h.toFloat(),
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        h * 0.78f,
                        0f,
                        h.toFloat(),
                        0x00000000,
                        0xB0000000.toInt(),
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        return bitmap
    }

    private fun png(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val SERIES = listOf(
            "Omniscient Reader’s Viewpoint",
            "Eleceed",
            "Solo Leveling: Ragnarok",
            "Swordmaster’s Youngest Son",
            "The Greatest Estate Developer",
            "Return of the Mount Hua Sect",
            "Nano Machine",
        )
    }
}
