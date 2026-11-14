package mihon.feature.friends.cards

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** The 3-D viewer's math: which side shows, how a release settles, the clamps, and the light. */
class Card3DTest {

    @Test
    fun `the face swaps past 90 degrees of yaw, either way and after whole turns`() {
        Card3D.face(0f, 0f) shouldBe Card3D.Face.FRONT
        Card3D.face(89f, 0f) shouldBe Card3D.Face.FRONT
        Card3D.face(91f, 0f) shouldBe Card3D.Face.BACK
        Card3D.face(-91f, 0f) shouldBe Card3D.Face.BACK
        Card3D.face(180f, 0f) shouldBe Card3D.Face.BACK
        Card3D.face(269f, 0f) shouldBe Card3D.Face.BACK
        Card3D.face(271f, 0f) shouldBe Card3D.Face.FRONT
        Card3D.face(720f, 0f) shouldBe Card3D.Face.FRONT
        Card3D.face(-540f, 0f) shouldBe Card3D.Face.BACK
    }

    @Test
    fun `pitch within its range never changes the side`() {
        listOf(-69f, -30f, 0f, 30f, 69f).forEach { pitch ->
            Card3D.face(10f, pitch) shouldBe Card3D.Face.FRONT
            Card3D.face(170f, pitch) shouldBe Card3D.Face.BACK
        }
    }

    @Test
    fun `the pose agrees with the face test`() {
        listOf(0f, 45f, 95f, 180f, 260f, 300f).forEach { yaw ->
            val pose = Card3D.Pose(yaw, 20f)
            val normal = pose.rotate(Card3D.Vec(0f, 0f, -1f))
            (normal.z < 0f) shouldBe (pose.face == Card3D.Face.FRONT)
        }
    }

    @Test
    fun `unrotate undoes rotate`() {
        val pose = Card3D.Pose(37f, -22f)
        val v = Card3D.Vec(0.3f, -0.7f, 0.2f)
        val back = pose.unrotate(pose.rotate(v))
        back.x shouldBe (v.x plusOrMinus 1e-5f)
        back.y shouldBe (v.y plusOrMinus 1e-5f)
        back.z shouldBe (v.z plusOrMinus 1e-5f)
    }

    @Test
    fun `dragging right brings the left edge closer, dragging down brings the top edge closer`() {
        val yawed = Card3D.Pose(30f, 0f)
        yawed.rotate(Card3D.Vec(-1f, 0f, 0f)).z shouldBeLessThan 0f
        yawed.rotate(Card3D.Vec(1f, 0f, 0f)).z shouldBeGreaterThan 0f
        val pitched = Card3D.Pose(0f, 30f)
        pitched.rotate(Card3D.Vec(0f, -1f, 0f)).z shouldBeLessThan 0f
    }

    @Test
    fun `normalize keeps angles in -180 to 180`() {
        Card3D.normalize(0f) shouldBe 0f
        Card3D.normalize(190f) shouldBe (-170f plusOrMinus 1e-4f)
        Card3D.normalize(-190f) shouldBe (170f plusOrMinus 1e-4f)
        Card3D.normalize(540f) shouldBe (180f plusOrMinus 1e-4f)
        Card3D.normalize(-180f) shouldBe (180f plusOrMinus 1e-4f)
    }

    @Test
    fun `a release with no speed settles on the nearest face`() {
        Card3D.settleYaw(40f, 0f) shouldBe 0f
        Card3D.settleYaw(100f, 0f) shouldBe 180f
        Card3D.settleYaw(-100f, 0f) shouldBe -180f
        Card3D.settleYaw(350f, 0f) shouldBe 360f
    }

    @Test
    fun `a fling coasts by velocity over friction, then settles on a face`() {
        Card3D.decayTarget(10f, 350f) shouldBe (10f + 350f / Card3D.FRICTION plusOrMinus 1e-4f)
        // 400 °/s coasts ~114°: past 90, so the other side
        Card3D.settleYaw(0f, 400f) shouldBe 180f
        Card3D.settleYaw(0f, -400f) shouldBe -180f
        // A gentle push isn't enough to turn it over
        Card3D.settleYaw(0f, 200f) shouldBe 0f
        // The target is always a face
        listOf(-2000f, -700f, 123f, 999f, 3000f).forEach { v ->
            abs(Card3D.settleYaw(17f, v) % 180f) shouldBe 0f
        }
    }

    @Test
    fun `a huge fling spins at most two turns`() {
        Card3D.settleYaw(0f, 100_000f) shouldBe Card3D.MAX_SPIN
        Card3D.settleYaw(0f, -100_000f) shouldBe -Card3D.MAX_SPIN
    }

    @Test
    fun `without inertia (reduced motion) the velocity is ignored`() {
        Card3D.settleYaw(30f, 5_000f, inertia = false) shouldBe 0f
    }

    @Test
    fun `a flip goes half a turn on from the nearest face`() {
        Card3D.flipTarget(0f) shouldBe 180f
        Card3D.flipTarget(10f) shouldBe 180f
        Card3D.flipTarget(170f) shouldBe 360f
        Card3D.flipTarget(-20f) shouldBe 180f
        Card3D.face(Card3D.flipTarget(0f), 0f) shouldBe Card3D.Face.BACK
        Card3D.face(Card3D.flipTarget(180f), 0f) shouldBe Card3D.Face.FRONT
    }

    @Test
    fun `pitch follows the finger, then resists, and stays well under 90`() {
        Card3D.rubberPitch(30f) shouldBe 30f
        Card3D.rubberPitch(-Card3D.MAX_PITCH) shouldBe -Card3D.MAX_PITCH
        val over = Card3D.rubberPitch(Card3D.MAX_PITCH + 20f)
        over shouldBeGreaterThan Card3D.MAX_PITCH
        over shouldBeLessThan Card3D.MAX_PITCH + 20f
        Card3D.rubberPitch(10_000f) shouldBeLessThan Card3D.MAX_PITCH + Card3D.PITCH_STRETCH + 0.001f
        Card3D.rubberPitch(-10_000f) shouldBeGreaterThan -(Card3D.MAX_PITCH + Card3D.PITCH_STRETCH) - 0.001f
        (Card3D.MAX_PITCH + Card3D.PITCH_STRETCH) shouldBeLessThan 80f
        // Monotonic
        var last = Card3D.rubberPitch(0f)
        for (raw in 1..400) {
            val now = Card3D.rubberPitch(raw.toFloat())
            now shouldBeGreaterThan last
            last = now
        }
    }

    @Test
    fun `zoom is clamped, and rests at least at 1`() {
        Card3D.clampZoom(0.1f) shouldBe Card3D.MIN_ZOOM
        Card3D.clampZoom(9f) shouldBe Card3D.MAX_ZOOM
        Card3D.clampZoom(1.5f) shouldBe 1.5f
        Card3D.restZoom(0.85f) shouldBe 1f
        Card3D.restZoom(2f) shouldBe 2f
    }

    @Test
    fun `a drag across the card turns it by the set amount`() {
        val k = Card3D.degreesPerPixel(500f)
        (500f * k) shouldBe (Card3D.DEGREES_PER_CARD_WIDTH plusOrMinus 1e-3f)
        Card3D.degreesPerPixel(0f) shouldBe 0f
    }

    @Test
    fun `sizes - most of the width, 5 by 7 textures within the master`() {
        Card3D.cardWidth(1080f, 2400f) shouldBe (1080f * 0.78f plusOrMinus 0.01f)
        // A landscape window is limited by its height
        Card3D.cardWidth(2400f, 1080f) shouldBeLessThan 1080f * 0.62f
        val t = Card3D.textureWidth(842f)
        (t % 5) shouldBe 0
        t shouldBe 1055
        Card3D.textureWidth(5000f) shouldBe 1500
        Card3D.textureWidth(10f) shouldBe 300
    }

    @Test
    fun `the outline is closed, clockwise and inside the card`() {
        val o = Card3D.outline(100f, 140f, 2.4f, 4)
        o.size shouldBe 4 * 5 * 2
        var area = 0f
        val n = o.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            abs(o[i * 2]) shouldBeLessThan 50.001f
            abs(o[i * 2 + 1]) shouldBeLessThan 70.001f
            area += o[i * 2] * o[j * 2 + 1] - o[j * 2] * o[i * 2 + 1]
        }
        // Positive shoelace area with y down is clockwise on screen; about the card's area
        (area / 2) shouldBe (100f * 140f plusOrMinus 30f)
    }

    @Test
    fun `no side wall shows facing the viewer, the left one shows turned right`() {
        val distance = 100f * Card3D.CAMERA
        val left = Card3D.Vec(-50f, 0f, 0f)
        val leftNormal = Card3D.Vec(-1f, 0f, 0f)
        val right = Card3D.Vec(50f, 0f, 0f)
        val rightNormal = Card3D.Vec(1f, 0f, 0f)
        val top = Card3D.Vec(0f, -70f, 0f)
        val topNormal = Card3D.Vec(0f, -1f, 0f)
        val rest = Card3D.Pose(0f, 0f)
        Card3D.wallVisible(rest, left, leftNormal, distance).shouldBeFalse()
        Card3D.wallVisible(rest, right, rightNormal, distance).shouldBeFalse()
        Card3D.wallVisible(rest, top, topNormal, distance).shouldBeFalse()
        val turned = Card3D.Pose(40f, 0f)
        Card3D.wallVisible(turned, left, leftNormal, distance).shouldBeTrue()
        Card3D.wallVisible(turned, right, rightNormal, distance).shouldBeFalse()
        Card3D.wallVisible(Card3D.Pose(0f, 30f), top, topNormal, distance).shouldBeTrue()
    }

    @Test
    fun `perspective grows what is near`() {
        Card3D.perspective(0f, 280f) shouldBe 1f
        Card3D.perspective(-40f, 280f) shouldBeGreaterThan 1f
        Card3D.perspective(40f, 280f) shouldBeLessThan 1f
    }

    @Test
    fun `at rest the flat view's shine is unchanged and the face is unshaded`() {
        val rest = Card3D.Pose(0f, 0f)
        Card3D.shineTilt(rest).x shouldBe (0f plusOrMinus 1e-5f)
        Card3D.shineTilt(rest).y shouldBe (0f plusOrMinus 1e-5f)
        Card3D.shade(rest) shouldBe (0f plusOrMinus 1e-5f)
        // The back at rest reads the same
        Card3D.shineTilt(Card3D.Pose(180f, 0f)).x shouldBe (0f plusOrMinus 1e-4f)
    }

    @Test
    fun `turning the card rolls the shine, both ways, within -1 to 1`() {
        val right = Card3D.shineTilt(Card3D.Pose(30f, 0f)).x
        val left = Card3D.shineTilt(Card3D.Pose(-30f, 0f)).x
        abs(right) shouldBeGreaterThan 0.3f
        (right * left) shouldBeLessThan 0f
        listOf(-89f, 89f).forEach {
            val tilt = Card3D.shineTilt(Card3D.Pose(it, 60f))
            abs(tilt.x) shouldBeLessThan 1.0001f
            abs(tilt.y) shouldBeLessThan 1.0001f
        }
    }

    @Test
    fun `the glare is strongest facing the light, gone turned well away, and sits up-left at rest`() {
        val rest = Card3D.glare(Card3D.Pose(0f, 0f))
        rest.strength shouldBeGreaterThan 0.8f
        rest.u shouldBeLessThan 0.5f
        rest.v shouldBeLessThan 0.5f
        Card3D.glare(Card3D.Pose(60f, 0f)).strength shouldBe 0f
        // Facing straight at the half vector: full strength
        Card3D.glare(Card3D.Pose(-9f, -13f)).strength shouldBeGreaterThan rest.strength
    }

    @Test
    fun `turned away from the light the face darkens, but only a little`() {
        val away = Card3D.shade(Card3D.Pose(70f, 30f))
        away shouldBeGreaterThan 0.05f
        away shouldBeLessThan Card3D.MAX_SHADE + 1e-6f
    }

    @Test
    fun `the specular spot is faint, grows with the rank, and stays under the shine cap`() {
        var last = 0f
        CardGrade.entries.forEach {
            val s = Card3D.specular(it)
            s shouldBeGreaterThan last
            s shouldBeLessThan CardShine.MAX_ALPHA / 2
            last = s
        }
        Card3D.specular(CardGrade.COMMON) shouldBeLessThan 0.07f
    }
}
