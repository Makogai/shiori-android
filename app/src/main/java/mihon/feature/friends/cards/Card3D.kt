package mihon.feature.friends.cards

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 3-D card viewer's pure math (no Android): the card is a thin rounded slab (5:7, [CardSpec.RADIUS_SHARE]
 * corners, [THICKNESS] of its width thick) centred on the origin, turned by a [Pose] (yaw about the card's vertical
 * axis, then pitch about the screen's horizontal one) and seen in perspective from [CAMERA] card widths away.
 *
 * Space: x right, y down (screen), z away from the viewer; the eye sits at z = −D. The front faces −z at rest.
 * Angles are degrees. Yaw is free (it winds past 360 with spins and flips); pitch stays under 90° ([rubberPitch]), so
 * the side showing depends on yaw and pitch alone ([face]).
 */
object Card3D {

    /** The slab's thickness as a share of its width: a little thicker than real card stock, so the edge reads. */
    const val THICKNESS = 0.018f

    /** The camera's distance in card widths: the near edge grows a little when turned, never fish-eyed. */
    const val CAMERA = 2.8f

    /** How far a drag turns the card: a drag across the whole card turns it this many degrees. */
    const val DEGREES_PER_CARD_WIDTH = 170f

    /** Pitch follows the finger freely up to [MAX_PITCH], then stretches toward [MAX_PITCH] + [PITCH_STRETCH]. */
    const val MAX_PITCH = 50f
    const val PITCH_STRETCH = 20f

    /** Pinch zoom limits; a release below [REST_MIN_ZOOM] springs back to it. */
    const val MIN_ZOOM = 0.8f
    const val MAX_ZOOM = 2.4f
    const val REST_MIN_ZOOM = 1f

    /** Fling friction (1/s): a release at v °/s coasts about v / FRICTION degrees before settling. */
    const val FRICTION = 3.5f

    /** One fling spins at most this far. */
    const val MAX_SPIN = 720f

    /** The gyroscope's parallax at full tilt (CardTilt ±1, about ±45° of device turn). */
    const val PARALLAX_YAW = 9f
    const val PARALLAX_PITCH = 7f

    /** The light: up-left of the card and in front of it (toward the viewer), unit length. */
    val LIGHT: Vec = Vec(-0.35f, -0.5f, -1f).normalized()

    /** Toward the viewer. */
    val VIEW: Vec = Vec(0f, 0f, -1f)

    /** Halfway between the light and the viewer: a mirror facing this shows the light's reflection. */
    val HALF: Vec = (LIGHT + VIEW).normalized()

    enum class Face { FRONT, BACK }

    data class Vec(val x: Float, val y: Float, val z: Float) {
        operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
        infix fun dot(o: Vec) = x * o.x + y * o.y + z * o.z
        val length: Float get() = sqrt(this dot this)
        fun normalized(): Vec = length.let { if (it == 0f) this else Vec(x / it, y / it, z / it) }
    }

    /** A turned card: yaw about the card's own vertical axis, then pitch about the screen's horizontal axis. */
    class Pose(val yaw: Float, val pitch: Float) {
        private val sy = sin(yaw.rad)
        private val cy = cos(yaw.rad)
        private val sp = sin(pitch.rad)
        private val cp = cos(pitch.rad)

        /** Card space to camera space (centred on the card; add the camera distance for depth). */
        fun rotate(v: Vec): Vec {
            // Yaw: dragging right brings the left edge toward the viewer
            val x1 = v.x * cy - v.z * sy
            val z1 = v.x * sy + v.z * cy
            // Pitch: dragging down brings the top edge toward the viewer
            val y2 = v.y * cp - z1 * sp
            val z2 = v.y * sp + z1 * cp
            return Vec(x1, y2, z2)
        }

        /** Camera space back to card space (the inverse rotation). */
        fun unrotate(v: Vec): Vec {
            val y1 = v.y * cp + v.z * sp
            val z1 = -v.y * sp + v.z * cp
            return Vec(v.x * cy + z1 * sy, y1, -v.x * sy + z1 * cy)
        }

        val face: Face get() = face(yaw, pitch)
    }

    /** The side facing the viewer: the front's normal (0, 0, −1) turned has z = −cos(yaw)·cos(pitch). */
    fun face(yaw: Float, pitch: Float): Face =
        if (cos(yaw.rad) * cos(pitch.rad) >= 0f) Face.FRONT else Face.BACK

    /** [degrees] in (−180, 180]. */
    fun normalize(degrees: Float): Float {
        var d = degrees % 360f
        if (d <= -180f) d += 360f
        if (d > 180f) d -= 360f
        return d
    }

    /** The nearest yaw at which a face looks straight at the viewer (a multiple of 180°). */
    fun nearestRest(yaw: Float): Float = (yaw / 180f).roundToInt() * 180f

    /** Where an exponential decay from [value] at [velocity] (per second) comes to rest. */
    fun decayTarget(value: Float, velocity: Float, friction: Float = FRICTION): Float = value + velocity / friction

    /**
     * Where a released card settles: the face nearest to where its fling would coast ([decayTarget]), never more than
     * [MAX_SPIN] away. Without [inertia] (reduced motion), simply the nearest face.
     */
    fun settleYaw(yaw: Float, velocity: Float, inertia: Boolean = true): Float {
        if (!inertia) return nearestRest(yaw)
        val coast = decayTarget(yaw, velocity)
        val target = nearestRest(coast)
        val base = nearestRest(yaw)
        return target.coerceIn(base - MAX_SPIN, base + MAX_SPIN)
    }

    /** A double tap's target: half a turn on from the face nearest now. */
    fun flipTarget(yaw: Float): Float = nearestRest(yaw) + 180f

    /**
     * Pitch for a finger's raw pitch: 1:1 up to [MAX_PITCH], then resisting, approaching but never reaching
     * [MAX_PITCH] + [PITCH_STRETCH] (well under 90°, so the face test stays a yaw one).
     */
    fun rubberPitch(raw: Float): Float {
        val over = abs(raw) - MAX_PITCH
        if (over <= 0f) return raw
        return sign(raw) * (MAX_PITCH + PITCH_STRETCH * (1f - 1f / (1f + over / PITCH_STRETCH)))
    }

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Where zoom springs to on release. */
    fun restZoom(zoom: Float): Float = zoom.coerceIn(REST_MIN_ZOOM, MAX_ZOOM)

    /** The card's unzoomed on-screen width in a [width] × [height] viewer: most of the width, short of the height. */
    fun cardWidth(width: Float, height: Float): Float = minOf(width * 0.78f, height * 0.62f * CardSpec.ASPECT)

    /**
     * The face images' width for a card [cardWidth] pixels wide: a quarter more for zooming in, a multiple of 5 (so
     * 5:7 is whole), between 300 and the 1500 master.
     */
    fun textureWidth(cardWidth: Float): Int {
        val wanted = (cardWidth * 1.25f).coerceIn(300f, CardSpec.WIDTH)
        return (wanted / 5f).roundToInt() * 5
    }

    /** Degrees per pixel of drag on a card [cardWidth] pixels wide. */
    fun degreesPerPixel(cardWidth: Float): Float = if (cardWidth <= 0f) 0f else DEGREES_PER_CARD_WIDTH / cardWidth

    /** Perspective scale of a point [z] deep (camera space, card centre at 0) seen from [distance]. */
    fun perspective(z: Float, distance: Float): Float = distance / (distance + z)

    /**
     * The card's outline as (x, y) pairs, centred on the origin, clockwise on screen (y down), with each corner
     * of radius [radius] in [cornerSteps] segments.
     */
    fun outline(width: Float, height: Float, radius: Float, cornerSteps: Int = 4): FloatArray {
        val a = width / 2
        val b = height / 2
        val r = radius.coerceIn(0f, minOf(a, b))
        // Corner centres clockwise from top-right, and each corner's starting angle (y down: −90° is up)
        val centres = listOf(a - r to -b + r, a - r to b - r, -a + r to b - r, -a + r to -b + r)
        val out = FloatArray(centres.size * (cornerSteps + 1) * 2)
        var i = 0
        centres.forEachIndexed { k, (cx, cy) ->
            val start = -90f + 90f * k
            for (s in 0..cornerSteps) {
                val angle = (start + 90f * s / cornerSteps).rad
                out[i++] = cx + r * cos(angle)
                out[i++] = cy + r * sin(angle)
            }
        }
        return out
    }

    /**
     * Whether a side wall whose outward normal is [normal] (card space) with its middle at [middle] (card space)
     * faces the eye at [distance] in front of the card.
     */
    fun wallVisible(pose: Pose, middle: Vec, normal: Vec, distance: Float): Boolean {
        val p = pose.rotate(middle)
        val n = pose.rotate(normal)
        // From the eye (0, 0, −distance) to the point, against the normal
        return Vec(p.x, p.y, p.z + distance) dot n < 0f
    }

    /** How lit a surface with card-space [normal] is (0 dark … 1 facing the light). */
    fun diffuse(pose: Pose, normal: Vec): Float = (pose.rotate(normal) dot LIGHT).coerceAtLeast(0f)

    /** The showing face's outward normal in card space. */
    fun faceNormal(face: Face): Vec = if (face == Face.FRONT) Vec(0f, 0f, -1f) else Vec(0f, 0f, 1f)

    /**
     * The light's reflection on the showing face: [u], [v] where the highlight sits (0…1 across the face as it
     * reads, may run off it) and [strength] 0…1 (how squarely the face mirrors the light at the viewer).
     */
    data class Glare(val u: Float, val v: Float, val strength: Float)

    fun glare(pose: Pose): Glare {
        val h = pose.unrotate(HALF)
        val face = pose.face
        // In the face's own reading frame (the back reads mirrored in x)
        val hu = if (face == Face.FRONT) h.x else -h.x
        val facing = if (face == Face.FRONT) -h.z else h.z
        return Glare(
            u = 0.5f + hu * GLARE_TRAVEL,
            v = 0.5f + h.y * GLARE_TRAVEL,
            strength = smoothstep(GLARE_FROM, 1f, facing),
        )
    }

    /**
     * The flat view's tilt for the angle the face is seen at relative to the light, so [CardShine]'s glare band and
     * foil roll with it: zero facing the viewer, ±1 about 45° off.
     */
    fun shineTilt(pose: Pose): CardTilt {
        val h = pose.unrotate(HALF)
        val hu = if (pose.face == Face.FRONT) h.x else -h.x
        return CardTilt(
            ((hu - HALF.x) * TILT_GAIN).coerceIn(-1f, 1f),
            ((h.y - HALF.y) * TILT_GAIN).coerceIn(-1f, 1f),
        )
    }

    /** How dark the face gets turned away from the light (0 at rest, at most [MAX_SHADE]). */
    fun shade(pose: Pose): Float {
        val rest = Vec(0f, 0f, -1f) dot LIGHT
        val now = diffuse(pose, faceNormal(pose.face))
        return ((rest - now) / rest * SHADE_GAIN).coerceIn(0f, MAX_SHADE)
    }

    /**
     * The specular spot's peak alpha by grade: a faint sheen on low ranks, a little more from Rank A up; always
     * well under [CardShine.MAX_ALPHA] so the art reads through it.
     */
    fun specular(grade: CardGrade): Float = when (grade) {
        CardGrade.COMMON -> 0.05f
        CardGrade.UNCOMMON -> 0.06f
        CardGrade.RARE -> 0.08f
        CardGrade.EPIC -> 0.09f
        CardGrade.LEGENDARY -> 0.11f
        CardGrade.MYTHIC -> 0.12f
        CardGrade.SECRET -> 0.13f
    }

    /** The back's spot, the same for every grade: a laminated card back, not foil. */
    const val BACK_SPECULAR = 0.06f

    const val GLARE_TRAVEL = 1.5f
    const val GLARE_FROM = 0.72f
    const val TILT_GAIN = 1.4f
    const val SHADE_GAIN = 0.5f
    const val MAX_SHADE = 0.35f

    fun smoothstep(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private val Float.rad: Float get() = (this * PI / 180.0).toFloat()
}
