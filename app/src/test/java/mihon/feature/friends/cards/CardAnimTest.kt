package mihon.feature.friends.cards

import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.io.File
import java.time.ZoneOffset

/**
 * Animated card edits (`frame_anim`): only the live view plays the GIF, as the whole card; exports, uploads and
 * thumbnails draw the PNG, else the GIF's frame 0.
 */
class CardAnimTest {

    private val manifest = CardManifest.parse(File("src/main/assets/shiori_cards/manifest.json").readText())
    private val requests = CardRequests(FriendsJson)

    private fun decode(anim: String?, style: String = "edit", framePath: String? = "c1.png"): ShioriCard {
        var extra = ""
        if (anim != null) extra += ""","frame_anim":"$anim""""
        if (framePath != null) extra += ""","frame_path":"$framePath""""
        // frame_anim_transform is gone from the contract; a stray one is ignored
        val json = """[{"id":"c1","owner":"u1","serial":42,"grade":"S","card_name":"N","frame_status":"ready",""" +
            """"frame_style":"$style","frame_anim_transform":{"sx":1},"effects":["holo_rainbow"]$extra}]"""
        return requests.decodeCards(json).single()
    }

    private fun face(card: ShioriCard) = CardFace(card, ZoneOffset.UTC)

    @Test
    fun `frame_anim decodes`() {
        val card = decode("card-frames/c1.gif")
        card.frameAnimPath shouldBe "card-frames/c1.gif"
        card.frameAnimationPath shouldBe "c1.gif"
        card.frameImagePath shouldBe "c1.png"
        // GIF only: stills use its frame 0
        val gifOnly = decode("c1.gif", framePath = null)
        gifOnly.frameImagePath shouldBe "c1.gif"
        gifOnly.isCardEdit shouldBe true
        // The server's shape: frame_path and frame_anim name the same GIF; a GIF frame_path alone is detected too
        val same = decode("c1.gif", framePath = "c1.gif")
        same.frameImagePath shouldBe "c1.gif"
        same.frameAnimationPath shouldBe "c1.gif"
        decode(null, framePath = "c1.GIF").frameAnimationPath shouldBe "c1.GIF"
    }

    @Test
    fun `missing animations fall back to the static edit`() {
        val none = decode(null)
        none.frameAnimationPath shouldBe null
        none.isCardEdit shouldBe true
        decode("").frameAnimationPath shouldBe null
        decode("c1.gif", style = "border").frameAnimationPath shouldBe null
        val pending = decode("c1.gif").copy(frameStatusCode = "pending")
        pending.frameAnimationPath shouldBe null
        pending.frameImagePath shouldBe null
    }

    @Test
    fun `only real GIFs on API 28+ play`() {
        CardFrameAnimation.looksLikeGif("GIF89a...".toByteArray()) shouldBe true
        CardFrameAnimation.looksLikeGif("GIF87a".toByteArray()) shouldBe true
        CardFrameAnimation.looksLikeGif("\u0089PNG\r\n".toByteArray()) shouldBe false
        CardFrameAnimation.looksLikeGif(null) shouldBe false
        CardFrameAnimation.looksLikeGif(ByteArray(2)) shouldBe false
        CardFrameAnimation.canPlay(27) shouldBe false
        CardFrameAnimation.canPlay(28) shouldBe true
    }

    @Test
    fun `animated plan versus static`() {
        val face = face(decode("c1.gif"))
        // Playing: the GIF is the whole card (over its backdrop when legacy), nothing over it
        CardLayerPlan.layers(face, manifest, customFrame = true, animation = true) shouldBe
            listOf(CardLayer.Backdrop, CardLayer.AnimatedFrame)
        // No GIF decoded: the static edit, with the S card's effects (its holo, plus the foil) over it
        CardLayerPlan.layers(face, manifest, customFrame = true) shouldBe listOf(
            CardLayer.Backdrop,
            CardLayer.CustomFrame,
            CardLayer.Effect("holo_rainbow"),
            CardLayer.Effect("foil_noise"),
        )
        CardLayerPlan.isBelowFrame(CardLayer.AnimatedFrame, manifest) shouldBe true
        CardLayerPlan.isAnimated(face.copy(frameStyle = CardFrameStyle.BORDER), animation = true) shouldBe false
    }

    @Test
    fun `exports and the render upload stay static`() {
        // export(), renderPng() and still() without animated pass no animation frame
        CardRenderer.plan(face(decode("c1.gif", framePath = null)), manifest, customFrame = true) shouldBe listOf(
            CardLayer.Backdrop,
            CardLayer.CustomFrame,
            CardLayer.Effect("holo_rainbow"),
            CardLayer.Effect("foil_noise"),
        )
    }
}
