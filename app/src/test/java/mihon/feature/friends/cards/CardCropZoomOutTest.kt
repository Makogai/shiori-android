package mihon.feature.friends.cards

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.time.ZoneId

/** Zooming the crop out past aspect-fill: the grey bars, `art_fill`, and card fronts drawing only the panel. */
class CardCropZoomOutTest {

    // 1000 x 1000 image under a 100 x 142 frame: cover scale 0.142, fit scale 0.1
    private val side = 1000f
    private val fw = 100f
    private val fh = 142f

    @Test
    fun `the smallest zoom fits the whole image inside the frame`() {
        CardCrop.minZoom(side, side, fw, fh) shouldBe (0.1f / 0.142f plusOrMinus 0.0001f)
        // A capture of the frame's shape can't zoom out at all
        CardCrop.minZoom(1428f, 2028f, 357f, 507f) shouldBe (1f plusOrMinus 0.0001f)
        // A long screenshot: fit by the height
        CardCrop.minZoom(1440f, 14400f, 357f, 507f) shouldBe ((507f / 14400f) / (357f / 1440f) plusOrMinus 0.0001f)
        CardCrop.minZoom(0f, 10f, fw, fh) shouldBe 1f
    }

    @Test
    fun `fully zoomed out, the smaller axis moves freely inside the frame and the other stays covering`() {
        val min = CardCrop.minZoom(side, side, fw, fh)
        // 100 x 100 shown in the 100 x 142 frame: no room sideways, 21 up or down
        val (x, y) = CardCrop.clamp(500f, 500f, side, side, fw, fh, min)
        x shouldBe (0f plusOrMinus 0.01f)
        y shouldBe (21f plusOrMinus 0.01f)
        val (_, up) = CardCrop.clamp(0f, -500f, side, side, fw, fh, min)
        up shouldBe (-21f plusOrMinus 0.01f)
        val (_, inside) = CardCrop.clamp(0f, 7f, side, side, fw, fh, min)
        inside shouldBe (7f plusOrMinus 0.01f)
        // Below the minimum behaves as the minimum
        CardCrop.clamp(500f, 500f, side, side, fw, fh, zoom = 0.1f) shouldBe CardCrop.clamp(
            500f,
            500f,
            side,
            side,
            fw,
            fh,
            min,
        )
    }

    @Test
    fun `between fit and fill each axis keeps its own rule`() {
        // Zoom 0.85: 120.7 x 120.7 shown, wider than the frame (covers it) and shorter (stays inside it)
        val shown = side * 0.142f * 0.85f
        val (x, y) = CardCrop.clamp(500f, 500f, side, side, fw, fh, 0.85f)
        x shouldBe ((shown - fw) / 2 plusOrMinus 0.01f)
        y shouldBe ((fh - shown) / 2 plusOrMinus 0.01f)
        val (nx, ny) = CardCrop.clamp(-500f, -500f, side, side, fw, fh, 0.85f)
        nx shouldBe (-(shown - fw) / 2 plusOrMinus 0.01f)
        ny shouldBe (-(fh - shown) / 2 plusOrMinus 0.01f)
    }

    @Test
    fun `the export draws the image unstretched where the frame shows it`() {
        val min = CardCrop.minZoom(side, side, fw, fh)
        val frame = CardCrop.frameRect(side, side, fw, fh, min, 0f, 0f)
        frame.width shouldBe (1000f plusOrMinus 0.1f)
        frame.height shouldBe (1420f plusOrMinus 0.1f)
        frame.top shouldBe (-210f plusOrMinus 0.1f)
        val export = CardCrop.export(side, side, frame)
        export.width shouldBe 1000
        export.height shouldBe 1420
        export.image.left shouldBe (0f plusOrMinus 0.1f)
        export.image.top shouldBe (210f plusOrMinus 0.1f)
        export.image.width shouldBe (1000f plusOrMinus 0.1f)
        export.image.height shouldBe (1000f plusOrMinus 0.1f)
        // The cut part of the image is still what cropRect reports
        CardCrop.cropRect(side, side, fw, fh, min, 0f, 0f).height shouldBe (1000f plusOrMinus 0.1f)
    }

    @Test
    fun `the export keeps the size limit and the image's aspect`() {
        val min = CardCrop.minZoom(4000f, 3000f, fw, fh)
        val export = CardCrop.export(4000f, 3000f, CardCrop.frameRect(4000f, 3000f, fw, fh, min, 0f, 0f))
        export.width shouldBe 1428
        export.height shouldBe 2028
        (export.image.width / export.image.height) shouldBe (4000f / 3000f plusOrMinus 0.001f)
        export.image.width shouldBe (1428f plusOrMinus 0.5f)
    }

    @Test
    fun `art_fill has both bars when centred, one after moving to an edge, none when filled`() {
        val min = CardCrop.minZoom(side, side, fw, fh)
        fun fill(zoom: Float, offsetY: Float) =
            CardCrop.export(side, side, CardCrop.frameRect(side, side, fw, fh, zoom, 0f, offsetY)).fill

        fill(min, 0f) shouldBe CardArtFill(top = 0.148, bottom = 0.148)
        // Moved down to the frame's bottom edge: the whole bar is on top
        fill(min, 500f) shouldBe CardArtFill(top = 0.296)
        fill(min, -500f) shouldBe CardArtFill(bottom = 0.296)
        fill(1f, 0f) shouldBe CardArtFill()
        fill(1f, 0f).isEmpty shouldBe true
        fill(2f, 30f).isEmpty shouldBe true
    }

    @Test
    fun `art_fill fractions are 0 to 0_5, three decimals, tiny bars are none`() {
        CardArtFill.fraction(3f, 1000f) shouldBe 0.0
        CardArtFill.fraction(4.4f, 1000f) shouldBe 0.0
        CardArtFill.fraction(4.6f, 1000f) shouldBe 0.005
        CardArtFill.fraction(5f, 1000f) shouldBe 0.005
        CardArtFill.fraction(123.456f, 1000f) shouldBe 0.123
        CardArtFill.fraction(800f, 1000f) shouldBe 0.5
        CardArtFill.fraction(-4f, 1000f) shouldBe 0.0
        CardArtFill.of(CardBox(0.2f, -1f, 1000f, 1421f), 1000, 1420) shouldBe CardArtFill()
    }

    @Test
    fun `the inner rect is the JPEG minus its bars, on whole pixels inside them`() {
        val inner = CardArtFill(top = 0.148, bottom = 0.148).innerRect(1000f, 1420f)
        inner.left shouldBe 0f
        inner.width shouldBe 1000f
        inner.top shouldBe 211f
        inner.bottom shouldBe 1209f
        val sides = CardArtFill(left = 0.25, right = 0.1).innerRect(1428f, 2028f)
        sides.left shouldBe 357f
        sides.right shouldBe 1285f
        sides.height shouldBe 2028f
        CardArtFill().innerRect(1428f, 2028f) shouldBe CardBox(0f, 0f, 1428f, 2028f)
        // Nothing left: the whole JPEG rather than an empty rect
        CardArtFill(left = 0.5, right = 0.5).innerRect(100f, 140f) shouldBe CardBox(0f, 0f, 100f, 140f)
    }

    private val context = CardSeriesContext("S", "Src", "Ch 1", 1.0, emptyList(), "a".repeat(32))

    @Test
    fun `the pull sends art_fill only when there are bars`() {
        val requests = CardRequests(FriendsJson)
        fun body(fill: CardArtFill?) =
            FriendsJson.parseToJsonElement(requests.pull(CardPullRequest("u1/x.jpg", context, null, fill)).body!!)
                .jsonObject

        body(null).containsKey("art_fill") shouldBe false
        body(CardArtFill()).containsKey("art_fill") shouldBe false
        val sent = body(CardArtFill(top = 0.148, bottom = 0.148))["art_fill"]!!.jsonObject
        sent.keys shouldBe setOf("left", "top", "right", "bottom")
        sent["top"]!!.jsonPrimitive.content shouldBe "0.148"
        sent["left"]!!.jsonPrimitive.content shouldBe "0.0"
        sent["bottom"]!!.jsonPrimitive.content shouldBe "0.148"
    }

    @Test
    fun `scores decode art_fill, and cards without it still decode`() {
        val requests = CardRequests(FriendsJson)
        fun card(scores: String) = requests.decodeCards(
            """[{"id": "c1", "owner": "u1", "art_path": "u1/a.jpg", "scores": $scores}]""",
        ).single()

        val zoomed = card("""{"rank_score": 70.2, "art_fill": {"left": 0, "top": 0.148, "right": 0, "bottom": 0.1}}""")
        zoomed.scores!!.artFill shouldBe CardArtFill(top = 0.148, bottom = 0.1)
        CardFace(zoomed, ZoneId.of("UTC")).artFill shouldBe CardArtFill(top = 0.148, bottom = 0.1)

        val old = card("""{"rank_score": 70.2}""")
        old.scores!!.artFill shouldBe null
        CardFace(old, ZoneId.of("UTC")).artFill shouldBe null
        card("null").scores shouldBe null
        // An all-zero fill draws like none
        CardFace(card("""{"art_fill": {"left": 0, "top": 0, "right": 0, "bottom": 0}}"""), ZoneId.of("UTC"))
            .artFill shouldBe null
    }
}
