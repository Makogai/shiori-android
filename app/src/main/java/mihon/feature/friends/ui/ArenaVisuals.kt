package mihon.feature.friends.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mihon.feature.friends.domain.QuestProgress
import mihon.feature.friends.domain.Tier
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/** Shared with iOS (backend README, "UI alignment"): you in the theme colour, your rival in coral. */
object ArenaColors {
    /** "You" follows the theme's primary colour, as on iOS. */
    val Me: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    val MeGlow: Color
        @Composable @ReadOnlyComposable
        get() = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.45f)
    val Rival = Color(0xFFFF6B6B)
    val RivalGlow = Color(0xFFFFB199)
    val Live = Color(0xFFFF3B5C)
    val Xp = Color(0xFF8B5CF6)
    val Quest = Color(0xFF41E2A0)
    val OnHero = Color.White
    val OnHeroMuted = Color.White.copy(alpha = 0.72f)
    val LegendStart = Color(0xFF8A3FFC)
    val LegendEnd = Color(0xFFFF4FB4)
}

val Tier.composeColor: Color get() = Color(color)

/** Deep "arena at night" background tinted with the theme's primary colour, so it fits every Mihon theme. */
@Composable
fun heroBrush(accent: Color): Brush {
    val base = lerp(Color(0xFF120A2A), MaterialTheme.colorScheme.primary, 0.22f)
    return Brush.linearGradient(
        colors = listOf(lerp(base, accent, 0.35f), base, lerp(base, Color.Black, 0.35f)),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
}

/** Animation spec that snaps when the system turned animations off. */
@Composable
fun <T> motion(spec: AnimationSpec<T>): AnimationSpec<T> = if (rememberReducedMotion()) snap() else spec

fun <T> bouncy(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow,
)

/** Phase 0..1 looping forever, or a constant with reduced motion. */
@Composable
fun rememberLoop(durationMillis: Int, reverse: Boolean = false, constant: Float = 0.5f): State<Float> {
    if (rememberReducedMotion()) return remember { mutableFloatStateOf(constant) }
    val transition = rememberInfiniteTransition(label = "loop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, easing = if (reverse) FastOutSlowInEasing else LinearEasing),
            if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "loop",
    )
}

/** Fill for a tier: flat colour gradients, and a moving purple→pink sheen for Legend. */
@Composable
fun tierBrush(tier: Tier): Brush {
    if (tier != Tier.LEGEND) {
        val color = tier.composeColor
        return Brush.linearGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.25f)))
    }
    val phase by rememberLoop(2_400)
    val shift = phase * 400f
    return Brush.linearGradient(
        colors = listOf(ArenaColors.LegendStart, ArenaColors.LegendEnd, Color(0xFFFFC2E8), ArenaColors.LegendStart),
        start = Offset(shift - 400f, 0f),
        end = Offset(shift, 200f),
    )
}

/** A card with depth: coloured glow shadow, bold gradient and a soft highlight across the top. */
@Composable
fun GlowCard(
    brush: Brush,
    glow: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .background(brush)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        endY = size.height * 0.45f,
                    ),
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(glow.copy(alpha = 0.7f), Color.Transparent)), shape),
        content = content,
    )
}

/** The bold uppercase title above an Arena section, as on iOS. */
@Composable
fun ArenaSectionTitle(
    title: String,
    emoji: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(text = emoji, fontSize = 14.sp)
        Text(
            text = title.uppercase(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Small grey text at the end of a section title ("2/3 done", "This month"). */
@Composable
fun SectionTrailing(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The plain rounded card iOS calls a themed card. */
fun Modifier.arenaCard(cornerRadius: Dp = 18.dp, padding: Dp = 14.dp): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(padding)
}

/**
 * An Arena section: the uppercase title, then its content in a plain card (iOS's LeagueCard), or as it is when
 * [card] is false (the game sections, which bring their own panels).
 */
@Composable
fun ArenaSection(
    title: String,
    emoji: String,
    modifier: Modifier = Modifier,
    card: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (card) 8.dp else 12.dp),
    ) {
        ArenaSectionTitle(title = title, emoji = emoji, trailing = trailing)
        Column(
            modifier = if (card) Modifier.arenaCard() else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** The Arena's dark lobby panel: a deep gradient with each side lit in a player's colour (iOS ArenaPanelBackground). */
@Composable
fun ArenaPanel(
    left: Color,
    right: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val base = lerp(PanelBase, MaterialTheme.colorScheme.primary, 0.12f)
    Box(
        modifier = modifier
            .shadow(14.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(lerp(base, Color.White, 0.06f), base, lerp(base, Color.Black, 0.35f)),
                    ),
                )
                val radius = size.width * 0.55f
                listOf(left.copy(alpha = 0.55f) to 0.12f, right.copy(alpha = 0.5f) to 0.88f).forEach { (color, x) ->
                    val center = Offset(size.width * x, size.height * 0.3f)
                    drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center, radius))
                }
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                        start = Offset(size.width * 0.3f, 0f),
                        end = Offset(size.width * 0.7f, size.height),
                    ),
                )
            }
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))),
                shape,
            ),
        content = content,
    )
}

/** The dark base colour of the Arena's hero panels. */
val PanelBase = Color(0xFF14112A)

/** Slowly turning rays of light behind a badge. */
@Composable
fun LightRays(color: Color, modifier: Modifier = Modifier) {
    val turn by rememberLoop(24_000, constant = 0f)
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val brush = Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)), center, radius)
        for (index in 0..<12) {
            val angle = (index / 12f + turn) * 360f
            drawArc(
                brush,
                startAngle = angle - 5.2f,
                sweepAngle = 10.4f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
            )
        }
    }
}

/** Counts up from 0 on first show, then animates to every new value. */
@Composable
fun rememberCountUp(target: Long, durationMillis: Int = 1_100): Long {
    val reducedMotion = rememberReducedMotion()
    val value = remember { Animatable(if (reducedMotion) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (reducedMotion) {
            value.snapTo(target.toFloat())
        } else {
            value.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
        }
    }
    return value.value.toLong()
}

/** A number that rolls up (or down) when it changes. */
@Composable
fun RollingNumber(value: Long, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val transform = if (reducedMotion) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else if (targetState > initialState) {
                (slideInVertically(bouncy()) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            } else {
                (slideInVertically(bouncy()) { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            }
            transform using SizeTransform(clip = false)
        },
        modifier = modifier,
        label = "rolling",
    ) { number ->
        Text(text = formatNumber(number), style = style, color = color)
    }
}

fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance().format(value)

/** Ring with a glowing gradient arc; [content] sits in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    colors: List<Color>,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.14f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), motion(tween(1_000, easing = FastOutSlowInEasing)))
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width * 1.5f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
            if (animated <= 0f) return@Canvas
            val brush = Brush.sweepGradient(colors + colors.first(), center)
            // Fake glow: wider, fainter copies of the arc underneath
            listOf(3.2f to 0.10f, 2.2f to 0.18f).forEach { (scale, alpha) ->
                drawArc(
                    colors.last().copy(alpha = alpha),
                    -90f,
                    360f * animated,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(width * scale, cap = StrokeCap.Round),
                )
            }
            drawArc(brush, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Avatar inside a level ring, with the level underneath and the tier badge on the shoulder. */
@Composable
fun LevelAvatar(
    name: String,
    url: String?,
    level: Int,
    levelProgress: Float,
    tier: Tier,
    size: Dp,
    team: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
        ProgressRing(
            fraction = levelProgress,
            colors = listOf(team, lerp(team, Color.White, 0.55f)),
            size = size + 8.dp,
            stroke = (size.value / 20f).coerceIn(3f, 6f).dp,
        ) {
            FriendAvatar(name = name, url = url, size = size * 0.76f)
        }
        LevelChip(
            level = level,
            color = team,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 6.dp),
        )
        TierBadge(
            tier = tier,
            size = (size.value * 0.36f).coerceAtLeast(18f).dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp),
        )
    }
}

@Composable
fun LevelChip(level: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(4.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(lerp(color, Color.Black, 0.2f), color)))
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            text = stringResource(MR.strings.friends_level_short, level),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** A season tier badge, as on iOS: a minted hexagon with a bright inner rim and a star; Legend shimmers. */
@Composable
fun TierBadge(tier: Tier, size: Dp, modifier: Modifier = Modifier, glow: Boolean = true) {
    val brush = tierBrush(tier)
    val color = tier.composeColor
    Canvas(modifier = modifier.size(width = size, height = size * 1.06f)) {
        val radius = this.size.minDimension / 2
        if (glow) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center, radius * 1.4f),
                radius * 1.4f,
            )
        }
        drawPath(hexagonPath(center, radius), brush)
        val rim = radius * 0.09f * 2
        drawPath(
            hexagonPath(center, radius - rim),
            Color.White.copy(alpha = 0.55f),
            style = Stroke((radius / 11f).coerceAtLeast(1f)),
        )
        drawPath(
            hexagonPath(center, radius),
            Color.Black.copy(alpha = 0.18f),
            style = Stroke((radius / 20f).coerceAtLeast(0.5f)),
        )
        drawStar(center, radius * 0.42f, Color.White)
    }
}

private fun hexagonPath(center: Offset, radius: Float): Path = Path().apply {
    for (index in 0..<6) {
        val angle = Math.toRadians(index * 60.0 - 90.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0..<10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val point = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

/** Tier pill: badge + name, on the tier gradient. */
@Composable
fun TierPill(tier: Tier, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tierBrush(tier))
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TierBadge(tier, 18.dp)
        Text(
            text = tier.label.uppercase(),
            color = if (tier == Tier.SILVER || tier == Tier.GOLD || tier == Tier.PLATINUM) {
                Color(0xFF1A1300)
            } else {
                Color.White
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * Today's tug-of-war: my share fills from the left in blue, the rival's from the right in magenta, with a glowing
 * knot at the split and a light sweep running across the leader's side.
 */
@Composable
fun TugOfWarBar(share: Float, height: Dp, modifier: Modifier = Modifier, leftLeads: Boolean?) {
    val animated by animateFloatAsState(
        targetValue = share.coerceIn(0.04f, 0.96f),
        animationSpec = motion(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)),
        label = "tug",
    )
    val me = ArenaColors.Me
    val meGlow = ArenaColors.MeGlow
    val sweep by rememberLoop(1_800, constant = -1f)
    val pulse by rememberLoop(900, reverse = true, constant = 0.5f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val h = size.height
        val barTop = h * 0.2f
        val barHeight = h * 0.6f
        val radius = CornerRadius(barHeight / 2)
        val split = size.width * animated
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(0f, barTop), Size(size.width, barHeight), radius)
        clipRect(right = split) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(me, meGlow), endX = split),
                Offset(0f, barTop),
                Size(size.width, barHeight),
                radius,
            )
        }
        clipRect(left = split) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(ArenaColors.RivalGlow, ArenaColors.Rival), startX = split),
                Offset(0f, barTop),
                Size(size.width, barHeight),
                radius,
            )
        }
        // Light sweep across the leader's side
        if (leftLeads != null && sweep >= 0f) {
            val (from, to) = if (leftLeads) 0f to split else split to size.width
            val x = from + (to - from) * sweep
            clipRect(left = from, right = to) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x - 40.dp.toPx(),
                        endX = x + 40.dp.toPx(),
                    ),
                    Offset(0f, barTop),
                    Size(size.width, barHeight),
                )
            }
        }
        // Glowing knot where the two sides meet
        val glow = when (leftLeads) {
            true -> meGlow
            false -> ArenaColors.RivalGlow
            null -> Color.White
        }
        val knot = Offset(split, h / 2)
        drawCircle(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent),
                center = knot,
                radius = h * (0.9f + 0.2f * pulse),
            ),
            radius = h * (0.9f + 0.2f * pulse),
            center = knot,
        )
        drawCircle(Color.White, radius = h * 0.26f, center = knot)
        drawCircle(glow, radius = h * 0.17f, center = knot)
    }
}

/** Pulsing red dot with "LIVE". */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val pulse by rememberLoop(1_100, constant = 0f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(ArenaColors.Live.copy(alpha = 0.16f))
            .border(1.dp, ArenaColors.Live.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(
                ArenaColors.Live.copy(alpha = (1f - pulse) * 0.6f),
                radius = size.minDimension / 2 * (1f + pulse),
            )
            drawCircle(ArenaColors.Live, radius = size.minDimension / 2.6f)
        }
        Text(
            text = stringResource(MR.strings.friends_live),
            color = ArenaColors.Live,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
    }
}

/** The current time, ticking every second while shown. */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1_000)
            value = Instant.now()
        }
    }
    return now
}

/** "2d 4h", "3h 12m" or "12m 5s" until [target], ticking every second. */
@Composable
fun countdownText(target: Instant): String {
    val context = LocalContext.current
    return formatCountdown(context, Duration.between(rememberNow(), target))
}

fun formatCountdown(context: Context, duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0)
    val days = (total / 86_400).toInt()
    val hours = (total % 86_400 / 3_600).toInt()
    val minutes = (total % 3_600 / 60).toInt()
    val seconds = (total % 60).toInt()
    return when {
        days > 0 -> context.stringResource(MR.strings.friends_duration_days, days, hours)
        hours > 0 -> context.stringResource(MR.strings.friends_duration_hours, hours, minutes)
        else -> context.stringResource(MR.strings.friends_duration_minutes, minutes, seconds)
    }
}

/** Compact quest pill: mini progress ring with the emoji, value/target, and a check when done. */
@Composable
fun QuestPill(progress: QuestProgress, modifier: Modifier = Modifier, onHero: Boolean = true) {
    val done = progress.done
    val content = if (onHero) ArenaColors.OnHero else MaterialTheme.colorScheme.onSurface
    val scale by animateFloatAsState(if (done) 1f else 0.94f, motion(bouncy()), label = "questScale")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (done) {
                    Brush.horizontalGradient(listOf(ArenaColors.Quest.copy(alpha = 0.85f), Color(0xFF1FB57A)))
                } else {
                    Brush.horizontalGradient(listOf(content.copy(alpha = 0.10f), content.copy(alpha = 0.06f)))
                },
            )
            .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ProgressRing(
            fraction = progress.fraction,
            colors = listOf(ArenaColors.Quest, Color(0xFFB6FFDF)),
            size = 26.dp,
            stroke = 2.5.dp,
            track = content.copy(alpha = 0.15f),
        ) {
            Text(text = progress.quest.emoji, fontSize = 11.sp)
        }
        Text(
            text = if (done) {
                "✓"
            } else {
                "${progress.value.coerceAtMost(progress.quest.target)}/${progress.quest.target}"
            },
            color = if (done) Color.White else content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Thin glowing XP bar. */
@Composable
fun XpBar(progress: Float, brush: Brush, glow: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), motion(tween(1_200, easing = FastOutSlowInEasing)))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = radius)
        if (animated <= 0f) return@Canvas
        val width = (size.width * animated).coerceAtLeast(size.height)
        drawRoundRect(glow.copy(alpha = 0.35f), Offset(0f, -2f), Size(width, size.height + 4f), radius)
        drawRoundRect(brush, size = Size(width, size.height), cornerRadius = radius)
        drawCircle(
            Color.White.copy(alpha = 0.9f),
            radius = size.height * 0.28f,
            center = Offset(width - size.height / 2, size.height / 2),
        )
    }
}
