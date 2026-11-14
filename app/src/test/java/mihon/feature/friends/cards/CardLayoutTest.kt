package mihon.feature.friends.cards

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/** Geometry, crop maths, layer order and blend mapping (cards/ANDROID_CARDS.md §3–§5). */
class CardLayoutTest {

    private val manifest = CardManifest.parse(File("src/main/assets/shiori_cards/manifest.json").readText())

    @Test
    fun `cover-fit fills the rect, centred, keeping the aspect`() {
        val window = CardBox(36f, 36f, 1428f, 2028f)
        // A wide image is scaled to the window's height and overflows sideways
        val wide = CardGeometry.coverFit(2000f, 1000f, window)
        wide.height shouldBe (2028f plusOrMinus 0.01f)
        wide.width shouldBe (4056f plusOrMinus 0.01f)
        wide.centerX shouldBe (750f plusOrMinus 0.01f)
        wide.centerY shouldBe (1050f plusOrMinus 0.01f)
        // A tall image is scaled to the window's width
        val tall = CardGeometry.coverFit(1000f, 4000f, window)
        tall.width shouldBe (1428f plusOrMinus 0.01f)
        tall.top shouldBe (1050f - 2856f plusOrMinus 0.01f)
        CardGeometry.coverFit(0f, 10f, window) shouldBe window
    }

    @Test
    fun `fit scale and decode sample size`() {
        CardGeometry.fitScale(1500f, 2100f, 300f, 2100f) shouldBe 0.2f
        CardGeometry.sampleSize(1f) shouldBe 1
        CardGeometry.sampleSize(0.6f) shouldBe 1
        CardGeometry.sampleSize(0.5f) shouldBe 2
        CardGeometry.sampleSize(0.2f) shouldBe 4
        CardGeometry.sampleSize(0.01f) shouldBe 8
    }

    @Test
    fun `crop frame is the largest 1428 by 2028 rect inside the inset view`() {
        val portrait = CardCrop.frame(400f, 1000f, 24f)
        portrait.width shouldBe (352f plusOrMinus 0.01f)
        portrait.height shouldBe (352f / CardCrop.ASPECT plusOrMinus 0.01f)
        portrait.centerX shouldBe (200f plusOrMinus 0.01f)
        portrait.centerY shouldBe (500f plusOrMinus 0.01f)
        val landscape = CardCrop.frame(2000f, 1048f, 24f)
        landscape.height shouldBe (1000f plusOrMinus 0.01f)
        landscape.width shouldBe (1000f * CardCrop.ASPECT plusOrMinus 0.01f)
    }

    @Test
    fun `offsets are clamped so the image keeps covering the frame`() {
        // 1000 x 1000 image under a 100 x 142 frame: base scale 0.142, wider than the frame
        val (x, y) = CardCrop.clamp(500f, 500f, 1000f, 1000f, 100f, 142f, zoom = 1f)
        x shouldBe (21f plusOrMinus 0.01f)
        y shouldBe 0f
        val (zx, zy) = CardCrop.clamp(-500f, 500f, 1000f, 1000f, 100f, 142f, zoom = 2f)
        zx shouldBe (-92f plusOrMinus 0.01f)
        zy shouldBe (71f plusOrMinus 0.01f)
    }

    @Test
    fun `crop rect in image pixels follows zoom and offset`() {
        // A tall capture exactly the frame's shape: zoom 1 takes all of it
        val all = CardCrop.cropRect(1428f, 2028f, 357f, 507f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        all.left shouldBe (0f plusOrMinus 0.01f)
        all.width shouldBe (1428f plusOrMinus 0.01f)
        all.height shouldBe (2028f plusOrMinus 0.01f)
        // Zoom 2, moved so the image's top-left shows: the top-left quarter
        val quarter = CardCrop.cropRect(1428f, 2028f, 357f, 507f, zoom = 2f, offsetX = 9999f, offsetY = 9999f)
        quarter.left shouldBe (0f plusOrMinus 0.01f)
        quarter.top shouldBe (0f plusOrMinus 0.01f)
        quarter.width shouldBe (714f plusOrMinus 0.01f)
        quarter.height shouldBe (1014f plusOrMinus 0.01f)
        // A long screenshot: the crop is a frame-shaped slice through the middle
        val long = CardCrop.cropRect(1440f, 14400f, 357f, 507f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        long.width shouldBe (1440f plusOrMinus 0.01f)
        long.height shouldBe (1440f / CardCrop.ASPECT plusOrMinus 0.1f)
        long.centerY shouldBe (7200f plusOrMinus 0.1f)
    }

    @Test
    fun `the upload is scaled down to fit 1428 by 2028, never up`() {
        CardCrop.outputSize(2856f, 4056f) shouldBe (1428 to 2028)
        CardCrop.outputSize(714f, 1014f) shouldBe (714 to 1014)
        CardCrop.outputSize(2000f, 2028f) shouldBe (1428 to 1448)
    }

    @Test
    fun `layer order follows the spec, effects grouped in list order`() {
        val effects = listOf("foil_noise", "sparkle_burst", "ember_particles", "unknown", "holo_rainbow", "ink_splash")
        val under = manifest.effects.filterValues { it.placement == EffectLayer.UNDER }.keys
        val layers = CardLayerPlan.layers(effects, manifest)
        val names = layers.map { (it as? CardLayer.Effect)?.id ?: it.toString() }
        val expected = listOf("Base", "Art") +
            effects.filter { it in under } +
            effects.filter { manifest.effects[it]?.placement == EffectLayer.ART } +
            listOf("Frame", "Ornament", "Emblem", "Badge", "Scrim", "Text", "Wordmark") +
            effects.filter { manifest.effects[it]?.placement == EffectLayer.OVER } +
            effects.filter { manifest.effects[it]?.placement == EffectLayer.FRAME } +
            "Metadata"
        names shouldBe expected
        names.contains("unknown") shouldBe false
    }

    @Test
    fun `a ready custom frame replaces base, art, under and art effects, frame and ornament`() {
        val effects = listOf("ember_particles", "sparkle_burst", "holo_rainbow", "foil_noise")
        val layers = CardLayerPlan.layers(effects, manifest, customFrame = true)
        layers.take(6) shouldBe listOf(
            CardLayer.CustomFrame,
            CardLayer.Emblem,
            CardLayer.Badge,
            CardLayer.Scrim,
            CardLayer.Text,
            CardLayer.Wordmark,
        )
        layers.last() shouldBe CardLayer.Metadata
        val rest = layers.drop(6).dropLast(1).map { (it as CardLayer.Effect).id }
        rest shouldBe effects.filter { manifest.effects.getValue(it).placement == EffectLayer.OVER } +
            effects.filter { manifest.effects.getValue(it).placement == EffectLayer.FRAME }
        rest.contains("ember_particles") shouldBe false
        CardLayerPlan.isBelowFrame(CardLayer.CustomFrame, manifest) shouldBe true
        CardLayerPlan.isBelowFrame(CardLayer.Effect("ember_particles"), manifest) shouldBe true
        CardLayerPlan.isBelowFrame(CardLayer.Effect("foil_noise"), manifest) shouldBe false
        CardLayerPlan.isBelowFrame(CardLayer.Wordmark, manifest) shouldBe false
    }

    @Test
    fun `effect clips by layer`() {
        CardLayerPlan.clip(EffectLayer.UNDER) shouldBe CardClip.CARD
        CardLayerPlan.clip(EffectLayer.OVER) shouldBe CardClip.CARD
        CardLayerPlan.clip(EffectLayer.ART) shouldBe CardClip.ART
        CardLayerPlan.clip(EffectLayer.FRAME) shouldBe CardClip.FRAME_ONLY
    }

    @Test
    fun `blend mapping with the soft light fallback before API 29`() {
        CardBlend.of("normal", 34) shouldBe CardBlend(CardBlend.Op.SRC_OVER)
        CardBlend.of("screen", 34) shouldBe CardBlend(CardBlend.Op.SCREEN)
        CardBlend.of("multiply", 26) shouldBe CardBlend(CardBlend.Op.MULTIPLY)
        CardBlend.of("overlay", 26) shouldBe CardBlend(CardBlend.Op.OVERLAY)
        CardBlend.of("add", 34) shouldBe CardBlend(CardBlend.Op.PLUS)
        CardBlend.of("softLight", 29) shouldBe CardBlend(CardBlend.Op.SOFT_LIGHT)
        CardBlend.of("softLight", 28) shouldBe CardBlend(CardBlend.Op.OVERLAY, 0.7f)
    }

    @Test
    fun `tiled textures start at the wrapped tilt offset`() {
        val tilt = CardTilt.EXPORT
        CardTilt.wrap(tilt.x * CardTilt.travel("holo_rainbow"), 1024f) shouldBe (-624f plusOrMinus 0.01f)
        CardTilt.wrap(tilt.x * CardTilt.travel("foil_noise"), 1024f) shouldBe (-864f plusOrMinus 0.01f)
        CardTilt.wrap(0f, 1024f) shouldBe 0f
        CardTilt.wrap(-300f, 1024f) shouldBe -300f
        CardTilt.wrap(-1500f, 1024f) shouldBe -476f
        CardTilt.wrap(2048f, 1024f) shouldBe 0f
    }

    @Test
    fun `tilt from the sensor and from drags is clamped to a 45 degree swing`() {
        CardTilt.fromAttitude((Math.PI / 8).toFloat(), (-Math.PI).toFloat()) shouldBe CardTilt(0.5f, -1f)
        CardTilt.fromDrag(50f, -500f, 200f, 280f) shouldBe CardTilt(0.5f, -1f)
        CardTilt.fromDrag(10f, 10f, 0f, 0f) shouldBe CardTilt()
    }

    @Test
    fun `name size shrinks by 2 to fit, down to 44`() {
        CardTextLayout.fittingSize(64f, 44f, 1316f) { it * 10 } shouldBe 64f
        CardTextLayout.fittingSize(64f, 44f, 1316f) { it * 25 } shouldBe 52f
        CardTextLayout.fittingSize(64f, 44f, 1316f) { it * 100 } shouldBe 44f
        val wordmark = CardTextLayout.wordmarkRect(520f, 70f, 1500f)
        wordmark.height shouldBe 28f
        wordmark.width shouldBe (208f plusOrMinus 0.01f)
        wordmark.centerX shouldBe (750f plusOrMinus 0.01f)
        wordmark.centerY shouldBe (2027f plusOrMinus 0.01f)
    }

    @Test
    fun `effect poses`() {
        val tilt = CardTilt(0.5f, -0.25f)
        CardMotion.pose(CardAnimation.TILT, 0.5f, 3.0, tilt, 600f) shouldBe CardEffectPose(0.5f, 300f, -150f)
        CardMotion.pose(CardAnimation.NONE, 0.8f, 3.0, tilt, 600f) shouldBe CardEffectPose(0.8f)
        CardMotion.pose(CardAnimation.TWINKLE, 1f, 0.4, tilt, 600f).opacity shouldBe (1f plusOrMinus 0.001f)
        CardMotion.pose(CardAnimation.RISE, 1f, 40.0, tilt, 600f).let {
            it.offsetY shouldBe (-300f plusOrMinus 0.01f)
            it.wrap shouldBe 2100f
        }
        CardMotion.pose(CardAnimation.FALL, 1f, 50.0, tilt, 600f).let {
            it.offsetY shouldBe (400f plusOrMinus 0.01f)
            it.wrap shouldBe -2100f
        }
        CardMotion.pose(CardAnimation.ROTATE, 1f, 3.0, tilt, 600f).rotation shouldBe (6f plusOrMinus 0.001f)
        CardMotion.pose(CardAnimation.PULSE, 1f, 0.6, tilt, 600f).let {
            it.opacity shouldBe (1f plusOrMinus 0.001f)
            it.scale shouldBe (1.01f plusOrMinus 0.0001f)
        }
        val jitter = CardMotion.pose(CardAnimation.JITTER, 1f, 1.0, tilt, 600f)
        (jitter.offsetX in listOf(-6f, 0f, 6f)) shouldBe true
        (CardMotion.noise(3.0) in 0.0..1.0) shouldBe true
        CardMotion.noise(3.0) shouldBe CardMotion.noise(3.0)
    }
}
