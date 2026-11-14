package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.feature.friends.ui.rememberReducedMotion
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.sin

/** "Make a card" from a capture: the image and the series it came from. */
class CardMakeRequest(val image: Bitmap, val series: CardSeriesContext)

/** Holds the capture being made into a card across configuration changes; the pull itself lives in the repository. */
class CardMakerViewModel(val repository: CardRepository) : ViewModel() {

    val request: StateFlow<CardMakeRequest?>
        field = MutableStateFlow<CardMakeRequest?>(null)

    private val _started = MutableStateFlow(false)
    val started = _started.asStateFlow()

    /** The optional character name for this card only: never prefilled, forgotten on close. */
    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    fun setName(value: String) {
        _name.value = CardRules.limitedNameInput(value)
    }

    fun open(image: Bitmap, series: CardSeriesContext) {
        repository.resetPull()
        _started.value = false
        _name.value = ""
        request.value = CardMakeRequest(image, series)
        viewModelScope.launch { repository.me?.let { repository.loadPulledToday(it) } }
    }

    fun close() {
        request.value = null
        _started.value = false
        _name.value = ""
        repository.resetPull()
    }

    /** Back to the crop after a failed pull. */
    fun retry() {
        repository.resetPull()
        _started.value = false
    }

    fun summon(crop: CardBox) {
        val current = request.value ?: return
        val name = CardRules.normalizedName(_name.value)
        _started.value = true
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { cropJpeg(current.image, crop) }
            repository.pullCard(jpeg, current.series, name)
        }
    }

    companion object {
        /** The crop's pixels, at most 1428 × 2028, JPEG quality 85. */
        fun cropJpeg(image: Bitmap, crop: CardBox): ByteArray {
            val (width, height) = CardCrop.outputSize(crop.width, crop.height)
            val output = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val factor = width / crop.width
            Canvas(output).apply {
                drawColor(android.graphics.Color.BLACK)
                drawBitmap(
                    image,
                    null,
                    RectF(
                        -crop.left * factor,
                        -crop.top * factor,
                        (image.width - crop.left) * factor,
                        (image.height - crop.top) * factor,
                    ),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }
            return ByteArrayOutputStream().use {
                output.compress(Bitmap.CompressFormat.JPEG, CardRules.JPEG_QUALITY, it)
                it.toByteArray()
            }
        }
    }
}

/** The crop, then the summoning and the reveal, full screen over the reader. */
@Composable
fun CardMakerDialog(viewModel: CardMakerViewModel) {
    val request by viewModel.request.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val phase by viewModel.repository.pull.collectAsStateWithLifecycle()
    val pulled by viewModel.repository.pulledToday.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val current = request ?: return
    val busy = started && phase !is CardPullPhase.Revealed && phase !is CardPullPhase.Failed
    val pulledToday = pulled[viewModel.repository.me] ?: 0
    val accent = MaterialTheme.colorScheme.accentOnBlack
    Dialog(
        onDismissRequest = { if (!busy) viewModel.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                // safeDrawing already holds the IME; kept explicit so the keyboard never covers Summon.
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::close, enabled = !busy) {
                    Text(
                        text = stringResource(if (started) MR.strings.action_close else MR.strings.action_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (busy) Color.White.copy(alpha = 0.4f) else accent,
                    )
                }
                Text(
                    text = if (started) "" else stringResource(MR.strings.screenshot_make_card),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(72.dp))
            }
            if (started) {
                PullStage(phase, pulledToday, onRetry = viewModel::retry, onDone = viewModel::close)
            } else {
                CropStage(current.image, pulledToday, name, viewModel::setName, onSummon = viewModel::summon)
            }
        }
    }
}

/** A fixed 1428:2028 frame over the capture; pinch and drag move the image under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CropStage(
    image: Bitmap,
    pulledToday: Int,
    name: String,
    onName: (String) -> Unit,
    onSummon: (CardBox) -> Unit,
) {
    val view = LocalView.current
    val bitmap = remember(image) { image.asImageBitmap() }
    val imageWidth = image.width.toFloat()
    val imageHeight = image.height.toFloat()
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    // In frame widths, so the crop stays put when the keyboard (or a rotation) resizes the frame.
    var relativeOffset by remember(image) { mutableStateOf(Offset.Zero) }
    val imeVisible = WindowInsets.isImeVisible
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        val inset = with(LocalDensity.current) { 24.dp.toPx() }
        val corner = with(LocalDensity.current) { 14.dp.toPx() }
        val line = with(LocalDensity.current) { 2.dp.toPx() }
        val frame = CardCrop.frame(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), inset)
        fun clamped(value: Offset, z: Float): Offset {
            val (x, y) = CardCrop.clamp(value.x, value.y, imageWidth, imageHeight, frame.width, frame.height, z)
            return Offset(x, y)
        }
        val offset = relativeOffset * frame.width
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image, frame) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, CardCrop.MAX_ZOOM)
                        relativeOffset = clamped(relativeOffset * frame.width + pan, zoom) / frame.width
                    }
                },
        ) {
            val scale = CardCrop.baseScale(imageWidth, imageHeight, frame.width, frame.height) * zoom
            val shown = clamped(offset, zoom)
            val width = imageWidth * scale
            val height = imageHeight * scale
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    (frame.centerX + shown.x - width / 2).roundToInt(),
                    (frame.centerY + shown.y - height / 2).roundToInt(),
                ),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val hole = RoundRect(frame.left, frame.top, frame.right, frame.bottom, CornerRadius(corner))
            drawPath(
                path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(hole)
                },
                color = Color.Black.copy(alpha = 0.6f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(frame.left, frame.top),
                size = Size(frame.width, frame.height),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = line),
            )
        }
        CardProminentButton(
            title = "✨ " + stringResource(MR.strings.cards_summon),
            onClick = {
                CardHaptics.press(view)
                onSummon(
                    CardCrop.cropRect(imageWidth, imageHeight, frame.width, frame.height, zoom, offset.x, offset.y),
                )
            },
            enabled = pulledToday < CardRules.DAILY_LIMIT,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth(),
        )
    }
    CardNameField(
        value = name,
        onValueChange = onName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = if (imeVisible) 8.dp else 0.dp),
    )
    // With the keyboard up the crop already has little room: the hint and the count step aside.
    if (!imeVisible) {
        Text(
            text = stringResource(MR.strings.cards_crop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(MR.strings.cards_pulled_today, pulledToday, CardRules.DAILY_LIMIT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
    }
}

/** After "Summon card": the summoning, then the reveal or a friendly error. */
@Composable
private fun ColumnScope.PullStage(phase: CardPullPhase, pulledToday: Int, onRetry: () -> Unit, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            CardPullPhase.Idle, CardPullPhase.Uploading, CardPullPhase.Summoning ->
                Summoning(uploading = phase != CardPullPhase.Summoning)
            is CardPullPhase.Revealed -> Reveal(phase.card, pulledToday, onDone)
            is CardPullPhase.Failed -> PullFailed(phase, onRetry, onDone)
        }
    }
}

/** The card back swaying in a pulsing purple glow while the judge looks at the panel. */
@Composable
private fun Summoning(uploading: Boolean) {
    val reduced = rememberReducedMotion()
    val seconds by produceState(0.0, reduced) {
        if (reduced) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9 }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        val glow = (0.6 + 0.3 * sin(seconds * 3)).toFloat()
        CardBackView(
            glow = null,
            modifier = Modifier
                .width(210.dp)
                .graphicsLayer {
                    rotationY = (sin(seconds * 1.4) * 18).toFloat()
                    val pulse = 1f + 0.03f * sin(seconds * 2.2).toFloat()
                    scaleX = pulse
                    scaleY = pulse
                    cameraDistance = 12 * density
                }
                .shadow(
                    30.dp,
                    CardCornerShape,
                    spotColor = SUMMON_GLOW.copy(alpha = glow),
                    ambientColor = SUMMON_GLOW.copy(alpha = glow),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(if (uploading) MR.strings.cards_preparing else MR.strings.cards_summoning),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(MR.strings.cards_judge_studying),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        CircularProgressIndicator(color = Color.White)
    }
}

/**
 * The reveal: the card's back (its rank's halo) brightens and grows, then flips to the live card with haptics (and
 * confetti from rank A up). R+ cards then show their custom frame being forged.
 */
@Composable
private fun Reveal(card: ShioriCard, pulledToday: Int, onDone: () -> Unit) {
    val viewModel = metroViewModel<CardsViewModel>()
    val repository = viewModel.repository
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    var glowing by remember(card.id) { mutableStateOf(false) }
    var flipped by remember(card.id) { mutableStateOf(false) }
    var confetti by remember(card.id) { mutableIntStateOf(0) }
    val backScale = remember(card.id) { Animatable(0.9f) }
    val album by repository.album.collectAsStateWithLifecycle()
    val current = album.firstOrNull { it.id == card.id } ?: card
    LifecycleResumeEffect(card.id) {
        repository.resumeWatching()
        onPauseOrDispose {}
    }
    LaunchedEffect(card.id) {
        glowing = true
        launch { backScale.animateTo(1.04f, tween(if (reduced) 100 else 900)) }
        CardHaptics.press(view)
        delay(if (reduced) 200 else 1_100)
        flipped = true
        CardHaptics.success(view)
        if (card.grade.celebrates) confetti++
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            FlipCard(
                flipped = flipped,
                front = {
                    // The card's own back (its rank's halo and colours), growing before the flip
                    CardInfoBack(
                        card = card,
                        modifier = Modifier.graphicsLayer {
                            scaleX = backScale.value
                            scaleY = backScale.value
                            alpha = if (glowing) 1f else 0.85f
                        },
                    )
                },
                back = { CardForgingFrame(card = current, captionColor = Color.White.copy(alpha = 0.85f)) },
            )
            CardConfetti(
                trigger = confetti,
                accent = repository.accent(card.grade),
                modifier = Modifier.matchParentSize(),
            )
        }
        if (flipped) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.grade.label.uppercase(),
                    color = repository.accent(card.grade),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = card.cardName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(MR.strings.cards_pulled_today, pulledToday, CardRules.DAILY_LIMIT),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            CardProminentButton(title = stringResource(MR.strings.cards_done), onClick = onDone)
        }
    }
}

@Composable
private fun PullFailed(phase: CardPullPhase.Failed, onRetry: () -> Unit, onDone: () -> Unit) {
    val view = LocalView.current
    LaunchedEffect(phase) { CardHaptics.warning(view) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(text = if (phase.retry) "🪄" else "🌙", style = MaterialTheme.typography.displayMedium)
        Text(
            text = stringResource(if (phase.retry) MR.strings.cards_no_card else MR.strings.cards_out_of_pulls),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = phase.message,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (phase.retry) {
            CardProminentButton(
                title = stringResource(MR.strings.cards_try_another_crop),
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = onDone) {
            Text(
                text = stringResource(MR.strings.action_close),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.accentOnBlack,
            )
        }
    }
}

/** Two faces of a card turned around the Y axis with a spring: [front] up when not [flipped]. */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
        label = "flip",
    )
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12 * density
        },
    ) {
        if (rotation <= 90f) {
            front()
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
        }
    }
}

private val SUMMON_GLOW = Color(0xFF9B5CFF)
