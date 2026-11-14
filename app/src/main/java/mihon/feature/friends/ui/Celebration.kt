package mihon.feature.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.feature.friends.domain.Tier
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Whether the user turned animations off in the system settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { context.animatorDurationScale == 0f }
}

private class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val spin: Float,
    val color: Color,
    /** Burst particles fly out from the centre; the rest rain down from the top. */
    val burst: Boolean,
    val x: Float,
    val delay: Float,
)

/** How long a celebration stays up unless dismissed sooner (shared with iOS). */
const val CELEBRATION_VISIBLE_MS = 3_500L

/**
 * Full-screen burst of particles and confetti around a big emoji and message; text only with reduced motion. Goes
 * away by itself after [CELEBRATION_VISIBLE_MS], or at once on a tap, a swipe or back. [onFinished] runs exactly once;
 * callers key the overlay by the celebration so each one gets its own timer.
 */
@Composable
fun CelebrationOverlay(celebration: Celebration, onFinished: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val finish by rememberUpdatedState(onFinished)
    var done by remember(celebration) { mutableStateOf(false) }
    val dismiss = {
        if (!done) {
            done = true
            finish()
        }
    }
    BackHandler(enabled = !done) { dismiss() }
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(if (reducedMotion) 1f else 0.2f) }
    val (emoji, message, accent) = celebrationContent(celebration)
    val particles = remember(accent) {
        val palette = listOf(accent) + CONFETTI
        List(PARTICLES) { index ->
            val burst = index < PARTICLES / 2
            Particle(
                angle = Random.nextFloat() * 360f,
                speed = 0.35f + Random.nextFloat() * 0.65f,
                size = 6f + Random.nextFloat() * 10f,
                spin = Random.nextFloat() * 900f - 450f,
                color = palette[index % palette.size],
                burst = burst,
                x = Random.nextFloat(),
                delay = if (burst) 0f else Random.nextFloat() * 0.35f,
            )
        }
    }
    LaunchedEffect(celebration) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!reducedMotion) {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
            launch { progress.animateTo(1f, tween(durationMillis = 2_800, easing = LinearEasing)) }
        }
        // A plain delay, not the animation: animator scale or a paused frame clock can't keep it up
        delay(CELEBRATION_VISIBLE_MS)
        dismiss()
    }
    if (done) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(celebration) { detectTapGestures { dismiss() } }
            .pointerInput(celebration) {
                detectDragGestures { change, _ ->
                    change.consume()
                    dismiss()
                }
            }
            .background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.35f * (1f - progress.value * 0.6f)), Color.Transparent),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!reducedMotion) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = progress.value
                val origin = Offset(size.width / 2, size.height * 0.45f)
                val reach = size.minDimension * 0.75f
                particles.forEach { p ->
                    val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) return@forEach
                    val fade = (1f - local).coerceIn(0f, 1f)
                    val position = if (p.burst) {
                        // Fast out, slowing down, pulled by gravity
                        val travel = (1f - (1f - local) * (1f - local)) * reach * p.speed
                        val rad = Math.toRadians(p.angle.toDouble())
                        Offset(
                            origin.x + (cos(rad) * travel).toFloat(),
                            origin.y + (sin(rad) * travel).toFloat() + local * local * size.height * 0.35f,
                        )
                    } else {
                        Offset(
                            p.x * size.width + sin(local * 12f + p.angle) * 24f,
                            -20f + local * p.speed * (size.height + 60f),
                        )
                    }
                    rotate(p.spin * local, position) {
                        if (p.burst && p.size < 10f) {
                            drawCircle(p.color.copy(alpha = fade), radius = p.size / 2, center = position)
                        } else {
                            drawRect(
                                color = p.color.copy(alpha = if (p.burst) fade else 1f),
                                topLeft = Offset(position.x - p.size / 2, position.y - p.size),
                                size = Size(p.size, p.size * 1.8f),
                            )
                        }
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(text = emoji, fontSize = 84.sp)
            Text(
                text = message,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1B1036), accent.copy(alpha = 0.9f))),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun celebrationContent(celebration: Celebration): Triple<String, String, Color> = when (celebration) {
    is Celebration.ChallengeWon -> Triple(
        "🏆",
        stringResource(MR.strings.friends_celebrate_challenge, "${celebration.emoji} ${celebration.title}"),
        GoldColor,
    )
    is Celebration.TierUp -> Triple(
        "🛡️",
        stringResource(MR.strings.friends_celebrate_tier, celebration.tier.label),
        if (celebration.tier == Tier.LEGEND) ArenaColors.LegendEnd else celebration.tier.composeColor,
    )
    is Celebration.LevelUp -> Triple(
        "⬆️",
        stringResource(MR.strings.friends_celebrate_level, celebration.level, celebration.name),
        ArenaColors.Me,
    )
    is Celebration.AchievementUnlocked -> Triple(
        celebration.emoji,
        stringResource(MR.strings.friends_celebrate_achievement, celebration.label),
        Color(0xFF9B6CFF),
    )
    Celebration.PerfectDay -> Triple("🌟", stringResource(MR.strings.friends_celebrate_perfect), ArenaColors.Xp)
    is Celebration.QuestDone -> Triple(
        celebration.emoji,
        stringResource(MR.strings.friends_celebrate_quest),
        ArenaColors.Quest,
    )
    Celebration.TookTheLead -> Triple("🔥", stringResource(MR.strings.friends_celebrate_lead), ArenaColors.Rival)
}

private const val PARTICLES = 140

private val CONFETTI = listOf(GoldColor, SilverColor, BronzeColor, FlameColor, ArenaColors.Rival, ArenaColors.Xp) +
    listOf(0xFF3D8BFF, 0xFF2EC4B6, 0xFF41E2A0).map { Color(it) }
