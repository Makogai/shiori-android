package mihon.feature.friends.cards

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Where an animated effect is at a moment, in master pixels. [wrap]: a second copy this far away (rise, fall). */
data class CardEffectPose(
    val opacity: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val wrap: Float? = null,
)

/** The effects' motion, shared with iOS (cards/ANDROID_CARDS.md §7); time in seconds. */
object CardMotion {
    private const val RISE_SPEED = 60.0
    private const val FALL_SPEED = 50.0
    private const val CANVAS_HEIGHT = 2100f

    /** The point `rotate` turns about, the same on every layout. */
    const val ROTATE_PIVOT_X = 750f
    const val ROTATE_PIVOT_Y = 925f

    fun pose(animation: CardAnimation, opacity: Float, seconds: Double, tilt: CardTilt, travel: Float): CardEffectPose =
        when (animation) {
            CardAnimation.NONE -> CardEffectPose(opacity)
            CardAnimation.TILT -> CardEffectPose(opacity, tilt.x * travel, tilt.y * travel)
            CardAnimation.TWINKLE -> CardEffectPose(opacity * (0.55f + 0.45f * wave(seconds, 1.6)))
            CardAnimation.DRIFT -> CardEffectPose(opacity, 18f * wave(seconds, 8.0), 10f * wave(seconds, 11.0))
            CardAnimation.RISE -> CardEffectPose(
                opacity,
                offsetY = -((seconds * RISE_SPEED) % CANVAS_HEIGHT).toFloat(),
                wrap = CANVAS_HEIGHT,
            )
            CardAnimation.FALL -> CardEffectPose(
                opacity,
                offsetY = ((seconds * FALL_SPEED) % CANVAS_HEIGHT).toFloat(),
                wrap = -CANVAS_HEIGHT,
            )
            CardAnimation.FLICKER -> CardEffectPose(opacity * (0.3f + 0.7f * noise(floor(seconds / 0.08)).toFloat()))
            CardAnimation.PULSE -> {
                val w = wave(seconds, 2.4)
                CardEffectPose(opacity * (0.6f + 0.4f * w), scale = 1f + 0.01f * w)
            }
            CardAnimation.ROTATE -> CardEffectPose(opacity, rotation = 6f * wave(seconds, 12.0))
            CardAnimation.JITTER -> {
                val tick = floor(seconds / 0.12)
                val shift = JITTER[(noise(tick) * 2.999).toInt()]
                CardEffectPose(opacity * (0.8f + 0.2f * noise(tick + 7).toFloat()), offsetX = shift)
            }
        }

    /** A repeatable 0…1 value per integer step. */
    fun noise(step: Double): Double {
        val value = sin(step * 12.9898) * 43758.5453
        return value - floor(value)
    }

    private fun wave(seconds: Double, period: Double): Float = sin(2 * PI * seconds / period).toFloat()

    private val JITTER = floatArrayOf(-6f, 0f, 6f)
}
