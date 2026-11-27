package mihon.feature.friends.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.feature.friends.ui.rememberReducedMotion
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

const val CARD_ASPECT = 1500f / 2100f

/** The display rank's colour (BRIEF §6). */
@Suppress("UnusedReceiverParameter")
fun CardRepository.accent(grade: CardGrade): Color = Color(grade.rankColor)

/** "You", the player's Arena name, or "Someone". */
@Composable
fun cardOwnerName(repository: CardRepository, id: String): String = when (id) {
    repository.me -> stringResource(MR.strings.cards_you)
    else -> repository.playerName(id) ?: stringResource(MR.strings.cards_someone)
}

/** [cardOwnerName] as a plain lambda, for sorting by owner. */
@Composable
fun rememberCardOwnerNames(repository: CardRepository): (String) -> String {
    val you = stringResource(MR.strings.cards_you)
    val someone = stringResource(MR.strings.cards_someone)
    return remember(repository, you, someone) {
        { id -> if (id == repository.me) you else repository.playerName(id) ?: someone }
    }
}

private sealed interface Rendered {
    class Still(val bitmap: Bitmap?) : Rendered
    class Live(val layers: CardLiveLayers?) : Rendered
}

/**
 * A card as it lives on screen: the static layers cached, the animated effects and the holo drawn every frame with
 * the device's tilt (or a drag), the whole card swinging a little in 3-D. With animations off (or [animated] false)
 * it is one still image, like the export. A custom frame landing cross-fades in. With [thumbnailPlaceholder] (the
 * detail carousel) the album thumbnail shows at once and the full card cross-fades in over it when rendered.
 *
 * Everything the card shows (the thumbnail under a fading-in card, the placeholder, the full card, the grade's
 * [CardShine] and [overlay]) sits in one layer that swings with the tilt, so no still copy shows behind the
 * swinging card.
 */
@Composable
fun ShioriCardView(
    card: ShioriCard,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    interactive: Boolean = true,
    draggable: Boolean = interactive,
    thumbnailPlaceholder: Boolean = false,
    overlay: @Composable () -> Unit = {},
) {
    val repository = metroViewModel<CardsViewModel>().repository
    val manifest = repository.pack.manifest
    val moving = animated && !rememberReducedMotion()
    val swing = rememberCardSwing(sensor = moving && interactive)
    val shine = remember(card.grade) { CardLayerPlan.shine(card.grade) }
    // The live card's animated effects and the glints run on it, so it runs for every moving card
    val seconds = rememberCardClock(running = moving)
    BoxWithConstraints(modifier.aspectRatio(CARD_ASPECT)) {
        val width = constraints.maxWidth
        val rendered by produceState<Rendered?>(
            null,
            card.id,
            card.frameImagePath,
            card.frameAnimationPath,
            width,
            moving,
        ) {
            value =
                if (moving) {
                    Rendered.Live(
                        repository.live(card, width),
                    )
                } else {
                    Rendered.Still(repository.still(card, width, animated = animated))
                }
        }
        val full = @Composable {
            Crossfade(targetState = rendered, animationSpec = tween(CROSSFADE_MS), label = "card") { current ->
                when {
                    manifest == null || current == null -> CardPlaceholder()
                    current is Rendered.Still -> current.bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = card.cardName,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: CardPlaceholder()
                    current is Rendered.Live -> current.layers?.let {
                        LiveCard(card, it, repository, manifest, swing::tilt, seconds)
                    } ?: CardPlaceholder()
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read here, in the draw phase: the tilt doesn't recompose the card
                    val tilt = swing.tilt()
                    rotationY = tilt.x * SWING_DEGREES
                    rotationX = -tilt.y * SWING_DEGREES
                    cameraDistance = SWING_CAMERA_DISTANCE * density
                }
                .then(if (draggable && moving) swing.dragModifier else Modifier),
        ) {
            if (thumbnailPlaceholder) {
                ThumbnailFirst(card, repository, ready = manifest != null && rendered.isReady, full = full)
            } else {
                full()
            }
            if (!shine.isNone) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCardShine(shine, swing.tilt(), if (moving) seconds.value else 0.0)
                }
            }
            overlay()
        }
    }
}

private val Rendered?.isReady: Boolean
    get() = when (this) {
        is Rendered.Still -> bitmap != null
        is Rendered.Live -> layers != null
        null -> false
    }

/**
 * A card's tilt: the device's (when [sensor]), or a drag while one lasts, sprung. [tilt] is meant for the draw phase
 * (graphics layers, canvases) so the swing never recomposes.
 */
@Stable
private class CardSwing(private val x: State<Float>, private val y: State<Float>, val dragModifier: Modifier) {
    fun tilt(): CardTilt = CardTilt(x.value, y.value)
}

@Composable
private fun rememberCardSwing(sensor: Boolean): CardSwing {
    val sensorTilt = rememberDeviceTilt(enabled = sensor)
    var drag by remember { mutableStateOf<CardTilt?>(null) }
    val target = drag ?: sensorTilt
    val x = animateFloatAsState(target.x, spring(stiffness = Spring.StiffnessMedium), label = "tiltX")
    val y = animateFloatAsState(target.y, spring(stiffness = Spring.StiffnessMedium), label = "tiltY")
    val dragModifier = remember {
        Modifier.pointerInput(Unit) {
            var dragged = Offset.Zero
            detectDragGestures(
                onDragStart = { dragged = Offset.Zero },
                onDragEnd = { drag = null },
                onDragCancel = { drag = null },
            ) { change, amount ->
                change.consume()
                dragged += amount
                drag = CardTilt.fromDrag(dragged.x, dragged.y, size.width.toFloat(), size.height.toFloat())
            }
        }
    }
    return remember(x, y, dragModifier) { CardSwing(x, y, dragModifier) }
}

/** Seconds since the card appeared, every frame while [running]; stays 0 otherwise. */
@Composable
private fun rememberCardClock(running: Boolean): State<Double> = produceState(0.0, running) {
    if (!running) return@produceState
    val start = withFrameNanos { it }
    while (true) withFrameNanos { value = (it - start) / 1e9 }
}

/**
 * The album thumbnail (cached, else rendered) until the full card is [ready], then the full card fading in over it in
 * [CardCarousel.CROSSFADE_MS]: never a dark box while the full images load (CARD_SPEC §14).
 */
@Composable
private fun ThumbnailFirst(card: ShioriCard, repository: CardRepository, ready: Boolean, full: @Composable () -> Unit) {
    val thumbnail by produceState(repository.cachedThumbnail(card), repository.thumbnailKey(card)) {
        if (value == null) value = repository.thumbnail(card)
    }
    val stage = CardLoadStage.of(thumbnail = thumbnail != null, full = ready)
    val alpha by animateFloatAsState(
        targetValue = if (stage == CardLoadStage.FULL) 1f else 0f,
        animationSpec = tween(CardCarousel.CROSSFADE_MS),
        label = "full",
    )
    Box(Modifier.fillMaxSize()) {
        // The thumbnail stays under the fading card so nothing darkens mid-fade; it swings with it (same layer)
        if (alpha < 1f) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = card.cardName,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (stage == CardLoadStage.FULL) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        // No offscreen buffer: one would be clipped to this box
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            ) { full() }
        }
    }
}

@Composable
private fun CardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CardCornerShape)
            .background(Color.Black.copy(alpha = 0.25f)),
    )
}

/** The live card's layers and effects, drawn every frame at [tilt] and [seconds]; the swing is the caller's. */
@Composable
private fun LiveCard(
    card: ShioriCard,
    layers: CardLiveLayers,
    repository: CardRepository,
    manifest: CardManifest,
    tilt: () -> CardTilt,
    seconds: State<Double>,
) {
    val face = remember(card) { CardFace(card, repository.zone) }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    // An animated card edit's GIF loops while the card is on screen
    DisposableEffect(layers) {
        val gif = layers.animation?.drawable as? android.graphics.drawable.Animatable
        gif?.start()
        onDispose { gif?.stop() }
    }
    val full = remember(manifest) { RectF(0f, 0f, manifest.canvas.width, manifest.canvas.height) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tilt = tilt()
        val time = seconds.value
        val canvas = drawContext.canvas.nativeCanvas
        val scale = size.width / manifest.canvas.width
        canvas.save()
        canvas.scale(scale, scale)
        repository.renderer.clipCard(canvas, manifest)
        // Below holds a legacy GIF's blurred backdrop, so it goes first
        layers.below?.let { canvas.drawBitmap(it, null, full, paint) }
        layers.animation?.drawable?.let { gif ->
            val box = CardGeometry.editRect(
                gif.intrinsicWidth.toFloat(),
                gif.intrinsicHeight.toFloat(),
                manifest.canvas.width,
                manifest.canvas.height,
            )
            gif.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
            gif.draw(canvas)
        }
        fun effects(ids: List<String>) = ids.forEach { id ->
            val effect = manifest.effects[id] ?: return@forEach
            // Over an AI card Epic's holo is dimmed, as in the exports; on screen the holo and foil draw stronger
            val opacity = CardLayerPlan.liveEffectOpacity(
                id,
                effect.opacity * if (layers.editBox != null) CardLayerPlan.editEffectOpacity(face.grade, id) else 1f,
            )
            val pose = CardMotion.pose(effect.motion, opacity, time, tilt, CardTilt.travel(id))
            repository.renderer.drawEffect(canvas, face, id, pose, layers.sample, layers.editBox)
        }
        effects(layers.belowEffects)
        layers.top?.let { canvas.drawBitmap(it, null, full, paint) }
        effects(layers.topEffects)
        canvas.restore()
    }
}

/** Roll and pitch from where the device was when the card appeared, ~30 Hz. */
@Composable
private fun rememberDeviceTilt(enabled: Boolean): CardTilt {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(CardTilt()) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) return@DisposableEffect onDispose {}
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var reference: Pair<Float, Float>? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                val pitch = angles[1]
                val roll = angles[2]
                val start = reference ?: (roll to pitch).also { reference = it }
                tilt = CardTilt.fromAttitude(roll - start.first, pitch - start.second)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SENSOR_PERIOD_US)
        onDispose {
            manager.unregisterListener(listener)
            tilt = CardTilt()
        }
    }
    return tilt
}

/** The pack's card back, glowing in [glow]'s colour when given. */
@Composable
fun CardBackView(glow: CardGrade?, modifier: Modifier = Modifier) {
    val repository = metroViewModel<CardsViewModel>().repository
    val manifest = repository.pack.manifest
    val back by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) { repository.pack.image(manifest?.back, BACK_SAMPLE) }
    }
    val glowImage by produceState<Bitmap?>(null, glow) {
        value = glow?.let { grade ->
            withContext(Dispatchers.IO) { repository.pack.image(manifest?.grade(grade)?.glow, BACK_SAMPLE) }
        }
    }
    Box(modifier = modifier.aspectRatio(CARD_ASPECT)) {
        glowImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(GLOW_SCALE),
            )
        }
        back?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(MR.strings.cards_back),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardCornerShape)
                .background(Color(0xFF1F1C4A)),
        )
    }
}

/**
 * The card's back side (v1.2 "Pull back" + "Halo"): drawn live on a Compose [Canvas] from the 750 × 1050 design,
 * scaled uniformly. The same drawing ([CardBackRenderer]) is the "Save back" PNG and the print sheet's back.
 */
@Composable
fun CardInfoBack(card: ShioriCard, modifier: Modifier = Modifier) {
    val repository = metroViewModel<CardsViewModel>().repository
    val face = remember(card.id, card.gradeCode, card.serial, card.seriesTitle) { CardBackFace(card) }
    val fonts = remember(repository) { repository.backFonts }
    val description = stringResource(MR.strings.cards_back)
    Canvas(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .semantics { contentDescription = description },
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        val k = size.width / CardBackLayout.WIDTH
        canvas.scale(k, k)
        CardBackRenderer.draw(canvas, face, fonts)
        canvas.restore()
    }
}

/** Three card backs fanned out (−8°, 0°, +8°), for the empty album. */
@Composable
fun CardBackFan(modifier: Modifier = Modifier, width: Dp = 96.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        listOf(-8f, 8f, 0f).forEach { angle ->
            CardBackView(
                glow = null,
                modifier = Modifier
                    .width(width)
                    .graphicsLayer {
                        rotationZ = angle
                        translationX = angle * 4 * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
            )
        }
    }
}

/**
 * A card that may still be getting its custom frame: a band sweeping along the frame with "✨ Forging a custom
 * frame…" while pending, then a cross-fade (in [ShioriCardView]) and a burst when it lands.
 */
@Composable
fun CardForgingFrame(
    card: ShioriCard,
    modifier: Modifier = Modifier,
    captionColor: Color = Color.Unspecified,
    draggable: Boolean = true,
    showCaption: Boolean = true,
    thumbnailPlaceholder: Boolean = false,
) {
    val repository = metroViewModel<CardsViewModel>().repository
    val view = LocalView.current
    val forged by repository.forged.collectAsStateWithLifecycle()
    var burst by remember { mutableIntStateOf(0) }
    // Only a frame landing while the card is on screen bursts, not one that landed earlier
    var celebrated by remember(card.id) { mutableStateOf(card.id in forged) }
    val landed = card.id in forged
    LaunchedEffect(landed) {
        if (landed && !celebrated) {
            celebrated = true
            burst++
            CardHaptics.success(view)
        }
    }
    val pending = card.frameStatus == CardFrameStatus.PENDING && !CardAlbum.isStalePending(card, Instant.now())
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            val shimmer = pending && !rememberReducedMotion()
            ShioriCardView(
                card = card,
                draggable = draggable,
                thumbnailPlaceholder = thumbnailPlaceholder,
                modifier = Modifier.fillMaxWidth(),
                // Inside the swinging layer, so the band follows the tilted card
                overlay = { if (shimmer) ForgingShimmer(Modifier.fillMaxSize()) },
            )
            CardConfetti(trigger = burst, accent = repository.accent(card.grade), modifier = Modifier.matchParentSize())
        }
        if (pending && showCaption) {
            Text(
                text = stringResource(MR.strings.cards_forging),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = captionColor,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** A white 45 % band, half the card wide and turned 20°, sweeping every 2.2 s over the edge band (frame + 60 px). */
@Composable
private fun ForgingShimmer(modifier: Modifier) {
    val phase by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (((it - start) / 1e9) % SHIMMER_PERIOD / SHIMMER_PERIOD).toFloat() }
    }
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            blendMode = BlendMode.Plus
        },
    ) {
        val unit = size.width / 1500f
        val edge = 96f * unit
        val radius = 60f * unit
        val band = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            addRect(Rect(edge, edge, size.width - edge, size.height - edge))
        }
        val width = size.width * 0.5f
        val x = (phase * 2 - 0.5f) * size.width
        clipPath(band) {
            rotate(SHIMMER_ANGLE, Offset(x + width / 2, size.height / 2)) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x,
                        endX = x + width,
                    ),
                    topLeft = Offset(x, -size.height),
                    size = Size(width, size.height * 3),
                )
            }
        }
    }
}

private class Spark(val angle: Float, val speed: Float, val size: Float, val spin: Float, val color: Color)

/** A burst of confetti from the middle each time [trigger] goes up; nothing with animations off. */
@Composable
fun CardConfetti(trigger: Int, accent: Color, modifier: Modifier = Modifier) {
    if (trigger == 0 || rememberReducedMotion()) return
    val progress = remember(trigger) { Animatable(0f) }
    val sparks = remember(trigger, accent) {
        val palette = listOf(accent, Color(0xFFF5C542), Color.White, Color(0xFFF472B6), Color(0xFF22D3EE))
        List(SPARKS) {
            Spark(
                angle = Random.nextFloat() * 360f,
                speed = 0.4f + Random.nextFloat() * 0.6f,
                size = 5f + Random.nextFloat() * 8f,
                spin = Random.nextFloat() * 720f - 360f,
                color = palette[it % palette.size],
            )
        }
    }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing)) }
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val reach = size.minDimension * 0.9f
        val origin = Offset(size.width / 2, size.height / 2)
        sparks.forEach { spark ->
            val travel = (1f - (1f - t) * (1f - t)) * reach * spark.speed
            val rad = Math.toRadians(spark.angle.toDouble())
            val position = Offset(
                origin.x + (cos(rad) * travel).toFloat(),
                origin.y + (sin(rad) * travel).toFloat() + t * t * size.height * 0.3f,
            )
            rotate(spark.spin * t, position) {
                drawRect(
                    color = spark.color.copy(alpha = 1f - t),
                    topLeft = Offset(position.x - spark.size / 2, position.y - spark.size),
                    size = Size(spark.size, spark.size * 1.8f),
                )
            }
        }
    }
}

/**
 * An album thumbnail (CARD_SPEC §13): the [CardMiniature] with the ✨ Custom mark and ♥ for favourites, then the
 * name and [caption].
 */
@Composable
fun CardThumb(card: ShioriCard, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CardMiniature(card, Modifier.fillMaxWidth()) {
            if (card.frameImagePath != null) {
                Text(
                    text = stringResource(MR.strings.cards_custom),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (card.favorite) {
                Text(
                    text = "♥",
                    color = Color(0xFFFF4D8D),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = card.cardName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // In the rank's colour, as on iOS
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = Color(card.grade.rankColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A still card (CARD_SPEC §13): the 300 × 420 still from the cache (a placeholder in the grade colour until it's
 * rendered) with its shine and, with [glow], the grade glow; [overlay] draws over it.
 */
@Composable
fun CardMiniature(
    card: ShioriCard,
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val repository = metroViewModel<CardsViewModel>().repository
    val key = repository.thumbnailKey(card)
    val thumbShine = remember(card.grade) { CardLayerPlan.shine(card.grade).scaled(CardShine.THUMB_STRENGTH) }
    val thumbnail by produceState(repository.cachedThumbnail(card), key) {
        if (value == null) value = repository.thumbnail(card)
    }
    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .then(if (glow) Modifier.cardGlow(card.grade) else Modifier),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = card.cardName,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardCornerShape)
                .background(Color(card.grade.rankColor).copy(alpha = 0.15f)),
        )
        // The grade's light effects, still and a little softer than in the detail view
        if (thumbnail != null && !thumbShine.isNone) {
            Canvas(Modifier.fillMaxSize()) { drawCardShine(thumbShine, CardTilt.EXPORT, 0.0) }
        }
        overlay()
    }
}

/**
 * How many cards of each grade, as small coloured chips (C → S): the grade colour at 28 % behind a non-zero count,
 * 10 % and half-faded for zero. With [onToggle], tapping a chip toggles that grade; [selected] ones are 60 % and
 * outlined.
 */
@Composable
fun CardGradeCounts(
    cards: List<ShioriCard>,
    modifier: Modifier = Modifier,
    selected: Set<CardGrade> = emptySet(),
    onToggle: ((CardGrade) -> Unit)? = null,
) {
    val repository = metroViewModel<CardsViewModel>().repository
    val counts = remember(cards) { CardAlbum.counts(cards) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (grade, count) ->
            val color = repository.accent(grade)
            val chosen = grade in selected
            val fill = when {
                chosen -> 0.6f
                count > 0 -> 0.28f
                else -> 0.1f
            }
            Text(
                text = "${grade.rank} $count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer { alpha = if (count > 0 || chosen) 1f else 0.5f }
                    .clip(CircleShape)
                    .background(color.copy(alpha = fill))
                    .then(if (chosen) Modifier.border(1.5.dp, color, CircleShape) else Modifier)
                    .then(if (onToggle != null) Modifier.clickable { onToggle(grade) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(1.dp))
    }
}

object CardHaptics {
    /** A light tick (the reveal's rank roulette). */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun press(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun success(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            },
        )
    }

    fun warning(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            },
        )
    }
}

/** 36 of 1500 px (v1.2): the card's corner radius as a share of its width, in percent. */
const val CARD_CORNER_PERCENT = CardSpec.RADIUS_SHARE * 100

/** The card's rounded rectangle at any size: the same mask as the renderer's. */
val CardCornerShape = RoundedCornerShape(CornerSize(CARD_CORNER_PERCENT))

private const val SWING_DEGREES = 8f

/** The swing's camera distance (× density); far enough that the near edge grows only a little. */
private const val SWING_CAMERA_DISTANCE = 12f
private const val SENSOR_PERIOD_US = 33_333
private const val CROSSFADE_MS = 800
private const val BACK_SAMPLE = 2
private const val GLOW_SCALE = 1.12f
private const val SHIMMER_PERIOD = 2.2
private const val SHIMMER_ANGLE = 20f
private const val SPARKS = 90
private const val CONFETTI_MS = 2_200
