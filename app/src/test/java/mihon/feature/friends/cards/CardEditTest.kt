package mihon.feature.friends.cards

import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.io.File
import java.time.ZoneOffset

/**
 * AI card edits (frame_style "edit", CARD_SPEC §12, v1.2): the image is the whole card, over its backdrop when it's a
 * legacy shape; the app draws only the effects and the card number over it.
 */
class CardEditTest {

    private val manifest = CardManifest.parse(File("src/main/assets/shiori_cards/manifest.json").readText())
    private val requests = CardRequests(FriendsJson)
    private val effects = listOf("ember_particles", "aura_glow", "holo_rainbow", "foil_noise")

    private fun decode(style: String?): ShioriCard {
        val extra = style?.let { ""","frame_style":"$it"""" } ?: ""
        val json = """[{"id":"c1","owner":"u1","serial":42,"grade":"S","card_name":"N","frame_status":"ready",""" +
            """"frame_path":"c1.png"$extra}]"""
        return requests.decodeCards(json).single()
    }

    private fun card(grade: CardGrade, style: String?, status: String = "ready") = ShioriCard(
        id = "c1",
        owner = "u1",
        serial = 42,
        gradeCode = grade.code,
        effects = effects,
        frameStatusCode = status,
        framePath = "c1.png",
        frameStyleCode = style,
    )

    /** The picked effects that exist in the test pack, then the holo and foil. */
    private fun editLayers(picked: List<String>) =
        (picked + listOf("holo_rainbow", "foil_noise")).filter { it in manifest.effects }.map(CardLayer::Effect)

    /** An edit's plan: backdrop, the image, [effects]; no card number (that is on the back). */
    private fun edit(effects: List<CardLayer>) =
        listOf(CardLayer.Backdrop, CardLayer.CustomFrame) + effects

    private fun face(card: ShioriCard) = CardFace(card, ZoneOffset.UTC)

    @Test
    fun `frame_style decodes and tolerates missing or unknown values`() {
        decode("edit").frameStyle shouldBe CardFrameStyle.EDIT
        decode("edit").isCardEdit shouldBe true
        decode("border").frameStyle shouldBe CardFrameStyle.BORDER
        decode(null).frameStyle shouldBe CardFrameStyle.BORDER
        decode(null).frameStyleCode shouldBe null
        decode("sparkly").frameStyle shouldBe CardFrameStyle.BORDER
        decode("sparkly").isCardEdit shouldBe false
        card(CardGrade.SECRET, "edit", status = "pending").isCardEdit shouldBe false
        face(decode("edit")).frameStyle shouldBe CardFrameStyle.EDIT
    }

    @Test
    fun `an edit draws its image, with effects that grow with the grade`() {
        // Mythic and Secret: their own picked effects, then the holo and foil (never doubled)
        for (grade in listOf(CardGrade.MYTHIC, CardGrade.SECRET)) {
            CardLayerPlan.layers(face(card(grade, "edit")), manifest, customFrame = true) shouldBe
                edit(editLayers(listOf("ember_particles", "aura_glow")))
        }
        // Legendary: the holo and foil, no picked effects
        CardLayerPlan.layers(face(card(CardGrade.LEGENDARY, "edit")), manifest, customFrame = true) shouldBe
            edit(listOf(CardLayer.Effect("holo_rainbow"), CardLayer.Effect("foil_noise")))
        // Epic: the holo only (dimmed to 0.4, below)
        CardLayerPlan.layers(face(card(CardGrade.EPIC, "edit")), manifest, customFrame = true) shouldBe
            edit(listOf(CardLayer.Effect("holo_rainbow")))
        // Common, Uncommon and Rare: the AI image alone
        for (grade in listOf(CardGrade.COMMON, CardGrade.UNCOMMON, CardGrade.RARE)) {
            CardLayerPlan.layers(face(card(grade, "edit")), manifest, customFrame = true) shouldBe edit(emptyList())
        }
        // Animated: backdrop and the GIF; no effects, no number
        CardLayerPlan.layers(
            face(card(CardGrade.SECRET, "edit")),
            manifest,
            customFrame = true,
            animation = true,
        ) shouldBe
            listOf(CardLayer.Backdrop, CardLayer.AnimatedFrame)
        // The backdrop sits under the frame (the live view's cached "below")
        CardLayerPlan.isBelowFrame(CardLayer.Backdrop, manifest) shouldBe true
    }

    @Test
    fun `the face carries the season, no rank symbol`() {
        val face = face(card(CardGrade.LEGENDARY, "edit").copy(createdAt = "2026-11-12T10:00:00Z"))
        face.season shouldBe "S1·2026"
    }

    @Test
    fun `no front plan draws the card number, it is only on the back`() {
        for (grade in CardGrade.entries) {
            val plans = listOf(
                // Pack template (no custom frame), custom border, still AI edit, animated AI edit
                CardLayerPlan.layers(face(card(grade, null)), manifest, customFrame = false),
                CardLayerPlan.layers(face(card(grade, "border")), manifest, customFrame = true),
                CardLayerPlan.layers(face(card(grade, "edit")), manifest, customFrame = true),
                CardLayerPlan.layers(face(card(grade, "edit")), manifest, customFrame = true, animation = true),
                CardRenderer.plan(face(card(grade, "edit")), manifest, customFrame = true),
            )
            plans.forEach { plan ->
                plan.map { it.toString() }.none { "Metadata" in it || "Number" in it } shouldBe true
                // Every plan ends on the card art, its text or an effect: nothing is drawn after them
                (plan.last() is CardLayer.Effect || plan.last() in frontTops) shouldBe true
            }
        }
        // The number is on the back instead ("№ 0042 · RANK A")
        CardBackLayout.numberText(42, CardGrade.LEGENDARY) shouldBe "№ 0042 · RANK A"
    }

    private val frontTops = setOf(CardLayer.Wordmark, CardLayer.CustomFrame, CardLayer.AnimatedFrame)

    @Test
    fun `only Epic's holo is dimmed over an AI card`() {
        CardLayerPlan.editEffectOpacity(CardGrade.EPIC, "holo_rainbow") shouldBe 0.4f
        CardLayerPlan.editEffectOpacity(CardGrade.EPIC, "foil_noise") shouldBe 1f
        for (grade in CardGrade.entries - CardGrade.EPIC) {
            CardLayerPlan.editEffectOpacity(grade, "holo_rainbow") shouldBe 1f
            CardLayerPlan.editEffectOpacity(grade, "sparkle_burst") shouldBe 1f
        }
    }

    @Test
    fun `borders and edits without their image keep the usual plans`() {
        CardLayerPlan.layers(face(card(CardGrade.SECRET, "border")), manifest, customFrame = true) shouldBe listOf(
            CardLayer.CustomFrame,
            CardLayer.Emblem,
            CardLayer.Badge,
            CardLayer.Scrim,
            CardLayer.Text,
            CardLayer.Wordmark,
            CardLayer.Effect("aura_glow"),
            CardLayer.Effect("holo_rainbow"),
            CardLayer.Effect("foil_noise"),
        )
        // Pending, or the image didn't download: the pack frame
        val pending = CardLayerPlan.layers(face(card(CardGrade.SECRET, "edit")), manifest, customFrame = false)
        pending.first() shouldBe CardLayer.Base
        pending.contains(CardLayer.Text) shouldBe true
        pending.contains(CardLayer.Frame) shouldBe true
        // Template cards end with the overlays, no card number
        pending.last() shouldBe CardLayer.Effect("foil_noise")
    }

    @Test
    fun `the render upload's face draws the edit plan`() {
        // uploadRender draws CardFace(card, zone) with CardRenderer.plan, as the export and the live view do
        val upload = CardRenderer.plan(face(card(CardGrade.MYTHIC, "edit")), manifest, customFrame = true)
        upload shouldBe edit(editLayers(listOf("ember_particles", "aura_glow")))
        CardRenderer.plan(face(card(CardGrade.LEGENDARY, null)), manifest, customFrame = true)
            .contains(CardLayer.Text) shouldBe true
    }
}
