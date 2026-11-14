package mihon.feature.friends.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.feature.friends.ui.rememberReducedMotion
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.FlipToBack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * A card full screen as a 3-D object: drag turns it on both axes (it coasts on a fling and settles on the nearest
 * face), double tap flips it, pinch zooms, and at rest the gyroscope sways it a little. System back, the close
 * button or a swipe down from outside the card closes it. Loads the card's front (the on-screen composite with the
 * card number) and back sized for the screen, and releases them when closed.
 */
@Composable
fun CardViewerDialog(card: ShioriCard, startOnBack: Boolean, onDismiss: () -> Unit) {
    val repository = metroViewModel<CardsViewModel>().repository
    val context = LocalContext.current
    val textureWidth = remember(context) {
        val metrics = context.resources.displayMetrics
        Card3D.textureWidth(Card3D.cardWidth(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()))
    }
    val placeholder = remember(card.id) { repository.cachedThumbnail(card)?.let { CardTexture(it, 0, owned = false) } }
    val faces by produceState<CardViewerFaces?>(null, card.id, card.frameImagePath, textureWidth) {
        val front = repository.still(card, textureWidth, animated = true)
        val number = CardSpec.cardIdText(card.serial)
        val typeface = repository.pack.metadataTypeface
        val back = repository.back(card, textureWidth)
        value = withContext(Dispatchers.Default) {
            CardViewerFaces(
                front = front?.let { CardTexture.bake(it, number, typeface) },
                back = CardTexture.bake(back, null, null).also { back.recycle() },
            )
        }
        awaitDispose { value?.release() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CardViewer3D(
            front = faces?.front ?: placeholder,
            back = faces?.back,
            grade = card.grade,
            name = card.cardName,
            startOnBack = startOnBack,
            onDismiss = onDismiss,
        )
    }
}

/** A face's image: the card fills it but for [pad] transparent pixels on every side. [owned] ones are recycled. */
class CardTexture(val bitmap: Bitmap, val pad: Int, val owned: Boolean) {
    companion object {
        /** Transparent margin so the straight edges come out smooth under any turn (bilinear filtering does it). */
        private const val PAD = 2

        /**
         * [source] clipped to the card's rounded corners with a clear margin, the card [number] drawn last (as the
         * on-screen card does, over everything). The source is not changed.
         */
        fun bake(source: Bitmap, number: String?, typeface: Typeface?): CardTexture {
            val w = source.width
            val h = source.height
            val out = Bitmap.createBitmap(w + PAD * 2, h + PAD * 2, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val r = CardSpec.cornerRadius(w.toFloat())
            val box = RectF(PAD.toFloat(), PAD.toFloat(), (PAD + w).toFloat(), (PAD + h).toFloat())
            val clip = android.graphics.Path().apply { addRoundRect(box, r, r, android.graphics.Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(source, PAD.toFloat(), PAD.toFloat(), paint)
            if (number != null && typeface != null) {
                canvas.translate(PAD.toFloat(), PAD.toFloat())
                val k = CardSpec.scale(w.toFloat())
                canvas.scale(k, k)
                CardNumber.draw(canvas, number, typeface)
            }
            canvas.restore()
            return CardTexture(out, PAD, owned = true)
        }
    }
}

class CardViewerFaces(val front: CardTexture?, val back: CardTexture?) {
    fun release() {
        listOfNotNull(front, back).filter { it.owned }.forEach { it.bitmap.recycle() }
    }
}

/** The viewer itself (also the debug preview's): [front] / [back] null draw a plain face until they load. */
@Composable
fun CardViewer3D(
    front: CardTexture?,
    back: CardTexture?,
    grade: CardGrade,
    name: String,
    startOnBack: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    restYaw: Float = if (startOnBack) 180f else 0f,
    restPitch: Float = 0f,
) {
    val reduced = rememberReducedMotion()
    val motion = !reduced
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val state = remember {
        CardViewerState(
            scope = scope,
            motion = motion,
            yaw = if (motion) restYaw - ENTRANCE_YAW else restYaw,
            pitch = restPitch,
            zoom = if (motion) ENTRANCE_ZOOM else 1f,
            view = view,
            onDismiss = { dismiss() },
        )
    }
    LaunchedEffect(Unit) { state.enter(restYaw) }
    // A tick each time the other side comes round
    LaunchedEffect(state) {
        snapshotFlow { Card3D.face(state.yaw, state.pitch) }.drop(1).collect { CardViewerHaptics.flip(view) }
    }
    val shine = remember(grade) { CardLayerPlan.shine(grade) }
    val sway = rememberGyroSway(enabled = motion, touching = { state.touching })
    val seconds = rememberViewerClock(running = motion && shine.sparkles > 0)
    val accent = Color(grade.rankColor)
    val painter = remember { CardSlabPainter() }
    val showing = if (Card3D.face(state.yaw, state.pitch) == Card3D.Face.FRONT) {
        stringResource(MR.strings.cards_viewer_front)
    } else {
        stringResource(MR.strings.cards_viewer_back)
    }
    val cardLabel = stringResource(MR.strings.cards_viewer_card, name, grade.label, showing)
    val flipLabel = stringResource(MR.strings.cards_flip)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.backdropAlpha }
            .drawBehind {
                // Dark, tinted by the rank's colour around the card
                drawRect(BACKDROP)
                drawRect(
                    Brush.radialGradient(
                        0f to lerp(BACKDROP, accent, 0.24f),
                        0.5f to lerp(BACKDROP, accent, 0.07f),
                        1f to BACKDROP,
                        center = Offset(size.width / 2, size.height * (0.5f - CARD_RAISE)),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
            },
    ) {
        val density = LocalDensity.current
        val cardWidth = with(density) { Card3D.cardWidth(maxWidth.toPx(), maxHeight.toPx()) }
        state.cardWidth = cardWidth
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = cardLabel
                    customActions = listOf(
                        CustomAccessibilityAction(flipLabel) {
                            state.flip()
                            true
                        },
                    )
                }
                .pointerInput(state) { detectTapGestures(onDoubleTap = { state.flip() }) }
                .pointerInput(state) { viewerGestures(state) },
        ) {
            state.center = Offset(size.width / 2, size.height / 2 - size.height * CARD_RAISE)
            val (swayYaw, swayPitch) = sway.value
            painter.draw(
                scope = this,
                pose = Card3D.Pose(state.yaw + swayYaw, state.pitch + swayPitch),
                cardWidth = cardWidth,
                center = state.center + Offset(0f, state.drop),
                zoom = state.zoom,
                front = front,
                back = back,
                shine = shine,
                specular = Card3D.specular(grade),
                accent = accent,
                seconds = seconds.value,
            )
        }

        // Name and rank, small, at the top; close on the right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(start = 20.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(
                    text = name,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = grade.label,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            IconButton(onClick = { state.close() }) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Close,
                    contentDescription = stringResource(MR.strings.action_close),
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = { state.flip() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Icon(MaterialSymbols.Rounded.FlipToBack, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(flipLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = stringResource(MR.strings.cards_viewer_hint),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * The viewer's motion: the finger's yaw and pitch, zoom, the swipe-down drop and the animations that settle them.
 * Values are read in the draw phase, so turning never recomposes the screen.
 */
@Stable
private class CardViewerState(
    private val scope: CoroutineScope,
    private val motion: Boolean,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    private val view: View,
    private val onDismiss: () -> Unit,
) {
    var yaw by mutableFloatStateOf(yaw)
    private var rawPitch by mutableFloatStateOf(pitch)
    val pitch: Float get() = Card3D.rubberPitch(rawPitch)
    var zoom by mutableFloatStateOf(zoom)
    var drop by mutableFloatStateOf(0f)
    var touching by mutableStateOf(false)
    var backdropAlpha by mutableFloatStateOf(if (motion) 0f else 1f)
    var cardWidth = 0f
    var center = Offset.Zero
    var height = 0f
    private var turning: Job? = null
    private var zooming: Job? = null
    private var dropping: Job? = null
    private var closing = false

    suspend fun enter(restYaw: Float) {
        if (!motion) return
        coroutineScope {
            launch { animate(0f, 1f, animationSpec = tween(ENTER_MS)) { v, _ -> backdropAlpha = v } }
            launch { animate(zoom, 1f, animationSpec = spring(0.7f, 260f)) { v, _ -> zoom = v } }
            launch { animate(yaw, restYaw, animationSpec = spring(0.55f, 120f)) { v, _ -> yaw = v } }
        }
    }

    /** Whether [position] is on the card (its unturned box at the current zoom, a little generous). */
    fun hits(position: Offset): Boolean {
        val halfW = cardWidth / 2 * zoom * 1.08f
        val halfH = halfW / CardSpec.ASPECT
        return abs(position.x - center.x) <= halfW && abs(position.y - center.y) <= halfH
    }

    fun touchStart() {
        turning?.cancel()
        zooming?.cancel()
        touching = true
    }

    fun turn(pan: Offset) {
        val k = Card3D.degreesPerPixel(cardWidth * zoom)
        yaw += pan.x * k
        rawPitch += pan.y * k
    }

    fun zoomBy(factor: Float) {
        zoom = Card3D.clampZoom(zoom * factor)
    }

    fun dropBy(dy: Float) {
        drop = max(0f, drop + dy)
        backdropAlpha = 1f - (drop / max(1f, height * 0.6f)).coerceIn(0f, 0.6f)
    }

    /** The finger(s) lifted: coast and settle on the nearest face, pitch back level, zoom back in range. */
    fun release(velocity: Offset, dropping: Boolean) {
        touching = false
        if (dropping) {
            releaseDrop(velocity.y)
            return
        }
        val k = Card3D.degreesPerPixel(cardWidth * zoom)
        settle(velocity.x * k, velocity.y * k)
        val rest = Card3D.restZoom(zoom)
        if (rest != zoom) {
            zooming = scope.launch {
                if (motion) {
                    animate(zoom, rest, animationSpec = spring(0.8f, 300f)) { v, _ -> zoom = v }
                } else {
                    zoom = rest
                }
            }
        }
    }

    private fun settle(yawVelocity: Float, pitchVelocity: Float) {
        val target = Card3D.settleYaw(yaw, yawVelocity, inertia = motion)
        val from = yaw
        turning?.cancel()
        turning = scope.launch {
            if (!motion) {
                yaw = target
                rawPitch = 0f
                return@launch
            }
            coroutineScope {
                launch {
                    animate(yaw, target, yawVelocity, spring(SETTLE_DAMPING, SETTLE_STIFFNESS)) { v, _ -> yaw = v }
                    if (abs(target - from) >= SNAP_TICK_DEGREES) CardViewerHaptics.snap(view)
                }
                launch {
                    animate(rawPitch, 0f, pitchVelocity, spring(0.55f, 180f)) { v, _ -> rawPitch = v }
                }
            }
        }
    }

    /** Half a turn with a spring (or at once with reduced motion). */
    fun flip() {
        val target = Card3D.flipTarget(yaw)
        turning?.cancel()
        turning = scope.launch {
            if (!motion) {
                yaw = target
                rawPitch = 0f
                return@launch
            }
            coroutineScope {
                launch { animate(yaw, target, animationSpec = spring(0.62f, 170f)) { v, _ -> yaw = v } }
                launch { animate(rawPitch, 0f, animationSpec = spring(0.7f, 200f)) { v, _ -> rawPitch = v } }
            }
        }
    }

    private fun releaseDrop(velocity: Float) {
        dropping?.cancel()
        if (drop > height * DISMISS_SHARE || velocity > DISMISS_VELOCITY) {
            close()
            return
        }
        dropping = scope.launch {
            animate(drop, 0f, velocity, spring(0.8f, 400f)) { v, _ -> dropBy(v - drop) }
        }
    }

    /** Slide away (unless reduced motion) and close. */
    fun close() {
        if (closing) return
        closing = true
        dropping?.cancel()
        if (!motion) {
            onDismiss()
            return
        }
        dropping = scope.launch {
            animate(drop, drop + height * 0.5f, animationSpec = tween(EXIT_MS)) { v, _ -> dropBy(v - drop) }
            onDismiss()
        }
    }
}

/**
 * One finger turns the card (from anywhere, except a downward swipe that starts off the card, which drops the
 * viewer to close it); two pinch to zoom.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.viewerGestures(state: CardViewerState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.height = size.height.toFloat()
        state.touchStart()
        val onCard = state.hits(down.position)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var mode = GestureMode.PENDING
        var travel = Offset.Zero
        var zoomed = false
        var tracked = down.id
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            val pan = event.calculatePan()
            if (pressed.size > 1) {
                zoomed = true
                if (mode == GestureMode.PENDING) mode = GestureMode.TURN
            }
            if (mode == GestureMode.PENDING) {
                travel += pan
                if (travel.getDistance() > viewConfiguration.touchSlop) {
                    mode = if (!onCard && travel.y > abs(travel.x)) GestureMode.DROP else GestureMode.TURN
                }
            }
            when (mode) {
                GestureMode.TURN -> {
                    state.turn(pan)
                    if (pressed.size > 1) state.zoomBy(event.calculateZoom())
                }
                GestureMode.DROP -> state.dropBy(pan.y)
                GestureMode.PENDING -> Unit
            }
            if (mode != GestureMode.PENDING) event.changes.forEach { if (it.positionChanged()) it.consume() }
            // Velocity follows one finger; a new one after it lifts starts a fresh track
            val finger = pressed.firstOrNull { it.id == tracked } ?: pressed.first().also {
                tracked = it.id
                tracker.resetTracking()
            }
            tracker.addPosition(finger.uptimeMillis, finger.position)
        }
        val velocity = if (zoomed || mode == GestureMode.PENDING) {
            Offset.Zero
        } else {
            tracker.calculateVelocity().let { Offset(it.x, it.y) }
        }
        state.release(velocity, dropping = mode == GestureMode.DROP)
    }
}

private enum class GestureMode { PENDING, TURN, DROP }

private object CardViewerHaptics {
    fun flip(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun snap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/**
 * The gyroscope's sway (yaw, pitch degrees) from where the device was when the viewer opened, eased in while the
 * card isn't touched and out while it is; zero when not [enabled] (reduced motion) or without a sensor. Updated per
 * frame in a state read only by the draw phase.
 */
@Composable
private fun rememberGyroSway(enabled: Boolean, touching: () -> Boolean): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val sway = remember { mutableStateOf(0f to 0f) }
    val reading = remember { FloatArray(2) }
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
                val start = reference ?: (angles[2] to angles[1]).also { reference = it }
                val tilt = CardTilt.fromAttitude(angles[2] - start.first, angles[1] - start.second)
                reading[0] = tilt.x
                reading[1] = tilt.y
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SENSOR_PERIOD_US)
        onDispose { manager.unregisterListener(listener) }
    }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var weight = 1f
        var x = 0f
        var y = 0f
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                weight += ((if (touching()) 0f else 1f) - weight) * min(1f, dt * SWAY_WEIGHT_RATE)
                val ease = 1f - exp(-dt * SWAY_EASE_RATE)
                x += (reading[0] * Card3D.PARALLAX_YAW * weight - x) * ease
                y += (-reading[1] * Card3D.PARALLAX_PITCH * weight - y) * ease
                val (px, py) = sway.value
                if (abs(px - x) > 0.01f || abs(py - y) > 0.01f) sway.value = x to y
            }
        }
    }
    return sway
}

/** Seconds since the viewer opened, every frame while [running] (the glints' clock); 0 otherwise. */
@Composable
private fun rememberViewerClock(running: Boolean): State<Double> {
    val seconds = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { seconds.doubleValue = (it - start) / 1e9 }
    }
    return seconds
}

/**
 * Draws the slab: its soft shadow, the side walls that face the eye (each lit by its angle to the light), then the
 * showing face mapped in perspective with the grade's shine, the specular spot and the shade, all clipped to the face.
 * Reuses its paths and matrices, so a frame allocates next to nothing.
 */
private class CardSlabPainter {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wallPath = android.graphics.Path()
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val quad = FloatArray(8)
    private val effects = CanvasDrawScope()
    private var outline = FloatArray(0)
    private var outlineFor = -1f
    private val facePath = Path()

    fun draw(
        scope: DrawScope,
        pose: Card3D.Pose,
        cardWidth: Float,
        center: Offset,
        zoom: Float,
        front: CardTexture?,
        back: CardTexture?,
        shine: CardShine,
        specular: Float,
        accent: Color,
        seconds: Double,
    ) = with(scope) {
        if (cardWidth <= 0f) return@with
        val w = cardWidth
        val h = w / CardSpec.ASPECT
        val a = w / 2
        val b = h / 2
        val t = w * Card3D.THICKNESS
        val distance = w * Card3D.CAMERA
        val face = pose.face
        val faceZ = if (face == Card3D.Face.FRONT) -t / 2 else t / 2

        fun project(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
            val p = pose.rotate(Card3D.Vec(x, y, z))
            val s = Card3D.perspective(p.z, distance) * zoom
            out[at] = center.x + p.x * s
            out[at + 1] = center.y + p.y * s
        }

        // The face's corners as it reads: top-left, top-right, bottom-right, bottom-left (the back mirrored in x)
        val sx = if (face == Card3D.Face.FRONT) 1f else -1f
        project(-a * sx, -b, faceZ, dst, 0)
        project(a * sx, -b, faceZ, dst, 2)
        project(a * sx, b, faceZ, dst, 4)
        project(-a * sx, b, faceZ, dst, 6)

        drawShadow(dst, center, w, h, zoom)
        drawWalls(pose, w, h, t, distance, zoom, center, accent)

        val canvas = drawContext.canvas.nativeCanvas
        val texture = if (face == Card3D.Face.FRONT) front else back
        if (texture != null && !texture.bitmap.isRecycled) {
            val bw = texture.bitmap.width.toFloat()
            val bh = texture.bitmap.height.toFloat()
            val p = texture.pad.toFloat()
            src[0] = p
            src[1] = p
            src[2] = bw - p
            src[3] = p
            src[4] = bw - p
            src[5] = bh - p
            src[6] = p
            src[7] = bh - p
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            canvas.save()
            canvas.concat(matrix)
            canvas.drawBitmap(texture.bitmap, 0f, 0f, bitmapPaint)
            canvas.restore()
        }

        // The face's own space: w × h, mapped onto the turned face
        src[0] = 0f
        src[1] = 0f
        src[2] = w
        src[3] = 0f
        src[4] = w
        src[5] = h
        src[6] = 0f
        src[7] = h
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        canvas.save()
        canvas.concat(matrix)
        effects.draw(this, layoutDirection, drawContext.canvas, Size(w, h)) {
            if (texture == null) {
                drawRoundRect(
                    color = lerp(Color(0xFF15151C), accent, 0.18f),
                    cornerRadius = CornerRadius(CardSpec.cornerRadius(w)),
                )
            }
            drawFaceLight(pose, face, shine, specular, seconds)
        }
        canvas.restore()
    }

    private fun DrawScope.drawFaceLight(
        pose: Card3D.Pose,
        face: Card3D.Face,
        shine: CardShine,
        specular: Float,
        seconds: Double,
    ) {
        if (face == Card3D.Face.FRONT) drawCardShine(shine, Card3D.shineTilt(pose), seconds)
        val w = size.width
        val r = CardSpec.cornerRadius(w)
        if (outlineFor != w) {
            facePath.reset()
            facePath.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, size.height, CornerRadius(r)))
            outlineFor = w
        }
        clipPath(facePath) {
            val glare = Card3D.glare(pose)
            val peak = (if (face == Card3D.Face.FRONT) specular else Card3D.BACK_SPECULAR) * glare.strength
            if (peak > 0.002f) {
                val c = Offset(glare.u * w, glare.v * size.height)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = peak),
                        0.45f to Color.White.copy(alpha = peak * 0.35f),
                        1f to Color.Transparent,
                        center = c,
                        radius = w * 0.8f,
                    ),
                    radius = w * 0.8f,
                    center = c,
                    blendMode = BlendMode.Screen,
                )
            }
            val shade = Card3D.shade(pose)
            if (shade > 0.003f) drawRect(Color.Black.copy(alpha = shade))
        }
    }

    /** A soft contact shadow under the card, as wide as the turned card. */
    private fun DrawScope.drawShadow(corners: FloatArray, center: Offset, w: Float, h: Float, zoom: Float) {
        val left = min(min(corners[0], corners[2]), min(corners[4], corners[6]))
        val right = max(max(corners[0], corners[2]), max(corners[4], corners[6]))
        val width = max(right - left, w * 0.2f * zoom)
        val c = Offset((left + right) / 2, center.y + (h / 2 + w * 0.09f) * zoom)
        val radius = width * 0.58f
        withTransform({ scale(1f, SHADOW_FLATTEN, c) }) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.6f to Color.Black.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = c,
                    radius = radius,
                ),
                radius = radius,
                center = c,
            )
        }
    }

    /** The facing side walls, quad by quad, each a touch longer than its segment so no seams show. */
    private fun DrawScope.drawWalls(
        pose: Card3D.Pose,
        w: Float,
        h: Float,
        t: Float,
        distance: Float,
        zoom: Float,
        center: Offset,
        accent: Color,
    ) {
        if (outline.isEmpty() || outline.size != OUTLINE_SIZE || outlineW != w) {
            outline = Card3D.outline(w, h, CardSpec.cornerRadius(w), CORNER_STEPS)
            outlineW = w
        }
        val base = lerp(EDGE_PAPER, accent, EDGE_TINT)
        val canvas = drawContext.canvas.nativeCanvas
        val n = outline.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x0 = outline[i * 2]
            val y0 = outline[i * 2 + 1]
            val x1 = outline[j * 2]
            val y1 = outline[j * 2 + 1]
            val dx = x1 - x0
            val dy = y1 - y0
            val normal = Card3D.Vec(dy, -dx, 0f).normalized()
            val middle = Card3D.Vec((x0 + x1) / 2, (y0 + y1) / 2, 0f)
            if (!Card3D.wallVisible(pose, middle, normal, distance)) continue
            fun project(x: Float, y: Float, z: Float, at: Int) {
                val p = pose.rotate(Card3D.Vec(x, y, z))
                val s = Card3D.perspective(p.z, distance) * zoom
                quad[at] = center.x + p.x * s
                quad[at + 1] = center.y + p.y * s
            }
            project(x0, y0, -t / 2, 0)
            project(x1, y1, -t / 2, 2)
            project(x1, y1, t / 2, 4)
            project(x0, y0, t / 2, 6)
            extend(0, 2)
            extend(6, 4)
            val light = EDGE_AMBIENT + (1f - EDGE_AMBIENT) * Card3D.diffuse(pose, normal)
            wallPaint.color = Color(base.red * light, base.green * light, base.blue * light).toArgb()
            wallPath.rewind()
            wallPath.moveTo(quad[0], quad[1])
            wallPath.lineTo(quad[2], quad[3])
            wallPath.lineTo(quad[4], quad[5])
            wallPath.lineTo(quad[6], quad[7])
            wallPath.close()
            canvas.drawPath(wallPath, wallPaint)
        }
    }

    private var outlineW = -1f

    /** Lengthens the projected segment [from] → [to] in [quad] by [SEAM] pixels at both ends. */
    private fun extend(from: Int, to: Int) {
        val dx = quad[to] - quad[from]
        val dy = quad[to + 1] - quad[from + 1]
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) return
        val ex = dx / len * SEAM
        val ey = dy / len * SEAM
        quad[from] -= ex
        quad[from + 1] -= ey
        quad[to] += ex
        quad[to + 1] += ey
    }

    private companion object {
        const val CORNER_STEPS = 4
        const val OUTLINE_SIZE = 4 * (CORNER_STEPS + 1) * 2
        const val SEAM = 0.6f
        const val SHADOW_FLATTEN = 0.16f
        const val EDGE_AMBIENT = 0.45f
        const val EDGE_TINT = 0.35f
        val EDGE_PAPER = Color(0xFFE9E4DA)
    }
}

/** Where the card sits: a little above the middle, leaving room for the shadow and the flip button. */
private const val CARD_RAISE = 0.03f
private val BACKDROP = Color(0xFF07070A)
private const val ENTRANCE_YAW = 28f
private const val ENTRANCE_ZOOM = 0.88f
private const val ENTER_MS = 220
private const val EXIT_MS = 200
private const val SETTLE_DAMPING = 0.72f
private const val SETTLE_STIFFNESS = 140f
private const val SNAP_TICK_DEGREES = 12f
private const val DISMISS_SHARE = 0.18f
private const val DISMISS_VELOCITY = 1800f
private const val SENSOR_PERIOD_US = 16_667
private const val SWAY_WEIGHT_RATE = 3f
private const val SWAY_EASE_RATE = 8f
