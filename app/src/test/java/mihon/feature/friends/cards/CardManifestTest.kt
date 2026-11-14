package mihon.feature.friends.cards

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.io.File

/** The bundled pack's manifest (cards/CODEX_ASSET_PROMPT.md) as the renderer reads it. */
class CardManifestTest {

    private val root = File("src/main/assets/shiori_cards")
    private val manifest = CardManifest.parse(File(root, "manifest.json").readText())

    @Test
    fun `parses canvas, regions, grades, themes and effects`() {
        manifest.canvas shouldBe CardManifest.Canvas(1500f, 2100f, 36f)
        manifest.regions.art shouldBe CardManifest.Rect(36f, 36f, 1428f, 2028f, 12f)
        manifest.regions.badge shouldBe CardManifest.Circle(1372f, 128f, 150f)
        manifest.regions.emblem shouldBe CardManifest.Circle(120f, 120f, 96f)
        manifest.regions.banner shouldBe CardManifest.Rect(36f, 1790f, 1428f, 274f)
        manifest.grades.keys shouldBe CardGrade.entries.map { it.code }.toSet()
        manifest.themes.keys shouldContainAll CardTheme.entries.map { it.code }
        manifest.effects.keys shouldContainAll listOf("holo_rainbow", "foil_noise", "aura_glow", "sparkle_burst")
    }

    @Test
    fun `reads effect placement, blend, animation and flags`() {
        val holo = manifest.effects.getValue("holo_rainbow")
        holo.tile shouldBe true
        holo.blend shouldBe "screen"
        holo.motion shouldBe CardAnimation.TILT
        val foil = manifest.effects.getValue("foil_noise")
        foil.placement shouldBe EffectLayer.FRAME
        foil.blend shouldBe "overlay"
        foil.opacity shouldBe 0.4f
        val aura = manifest.effects.getValue("aura_glow")
        aura.tintWithAccent shouldBe true
        aura.motion shouldBe CardAnimation.PULSE
        manifest.effects.getValue("ember_particles").placement shouldBe EffectLayer.ART
    }

    @Test
    fun `every referenced file is in the pack`() {
        val paths = buildList {
            add(manifest.back)
            add(manifest.masks.art)
            add(manifest.masks.card)
            manifest.grades.values.forEach { addAll(listOf(it.base, it.frame, it.badge, it.glow, it.wordmark)) }
            manifest.themes.values.forEach { addAll(listOfNotNull(it.ornament, it.emblem)) }
            manifest.effects.values.forEach { add(it.file) }
        }
        paths.filterNot { File(root, it).isFile }.shouldBeEmpty()
    }

    @Test
    fun `unknown themes draw as neutral and unknown codes fall back`() {
        manifest.theme(CardTheme.of("vampire")) shouldBe manifest.themes.getValue("neutral")
        CardGrade.of("Z") shouldBe CardGrade.COMMON
        CardTheme.of("slice_of_life") shouldBe CardTheme.SLICE_OF_LIFE
        manifest.grade(CardGrade.EPIC) shouldNotBe null
        manifest.grade(CardGrade.EPIC)!!.wordmarkTint shouldBe "#F5C542"
    }

    @Test
    fun `tolerates optional fields and unknown keys`() {
        val text = """
            {"version": 2, "future": true, "regions": {"art": {"x": 0, "y": 0, "w": 10, "h": 10},
             "badge": {"cx": 1, "cy": 1, "size": 2}, "emblem": {"cx": 1, "cy": 1, "size": 2},
             "banner": {"x": 0, "y": 8, "w": 10, "h": 2}},
             "grades": {}, "themes": {}, "back": "b.png", "masks": {"art": "a.png", "card": "c.png"},
             "effects": {"x": {"file": "x.png", "layer": "sideways", "blend": "dodge", "animation": "spin"}}}
        """.trimIndent()
        val parsed = CardManifest.parse(text)
        parsed.canvas shouldBe CardManifest.Canvas()
        parsed.fonts shouldBe null
        val effect = parsed.effects.getValue("x")
        effect.placement shouldBe EffectLayer.OVER
        effect.motion shouldBe CardAnimation.NONE
        effect.opacity shouldBe 1f
        CardBlend.of(effect.blend, 34).op shouldBe CardBlend.Op.SRC_OVER
    }

    @Test
    fun `themes without an ornament parse and skip the ornament layer`() {
        val text = """
            {"version": 1, "regions": {"art": {"x": 0, "y": 0, "w": 10, "h": 10},
             "badge": {"cx": 1, "cy": 1, "size": 2}, "emblem": {"cx": 1, "cy": 1, "size": 2},
             "banner": {"x": 0, "y": 8, "w": 10, "h": 2}},
             "grades": {}, "themes": {"action": {"emblem": "e.png", "accent": "#F97316"},
                                      "neutral": {"ornament": "o.png", "emblem": "n.png", "accent": "#FFFFFF"}},
             "back": "b.png", "masks": {"art": "a.png", "card": "c.png"}, "effects": {}}
        """.trimIndent()
        val parsed = CardManifest.parse(text)
        parsed.theme(CardTheme.ACTION)!!.ornament shouldBe null
        parsed.theme(CardTheme.ACTION)!!.emblem shouldBe "e.png"
        CardLayerPlan.layers(emptyList(), parsed, theme = CardTheme.ACTION).contains(CardLayer.Ornament) shouldBe
            false
        CardLayerPlan.layers(emptyList(), parsed, theme = CardTheme.NEUTRAL).contains(CardLayer.Ornament) shouldBe
            true
        // Without a theme (the live view's effect list) the plan is unchanged
        CardLayerPlan.layers(emptyList(), parsed).contains(CardLayer.Ornament) shouldBe true
    }
}
