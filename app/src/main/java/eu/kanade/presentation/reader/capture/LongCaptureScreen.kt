package eu.kanade.presentation.reader.capture

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.capture.CaptureOutput
import eu.kanade.tachiyomi.ui.reader.capture.CapturePages
import eu.kanade.tachiyomi.ui.reader.capture.LongCaptureRequest
import eu.kanade.tachiyomi.ui.reader.capture.StitchPlan
import eu.kanade.tachiyomi.ui.reader.capture.StripAnchor
import eu.kanade.tachiyomi.ui.reader.capture.StripLayout
import eu.kanade.tachiyomi.ui.reader.capture.StripSelection
import eu.kanade.tachiyomi.ui.reader.capture.autoscrollSpeed
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ScreenshotFrame
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The long screenshot screen: the chapter's strip without the reader's controls and a selection with handles to
 * drag over it. The strip still scrolls, and holding a handle near an edge scrolls it along. The selected parts of
 * the page images are then stitched at full resolution, to share, save, style or make a card of.
 */
@Composable
fun LongCaptureScreen(
    request: LongCaptureRequest,
    working: Boolean,
    canMakeCard: Boolean,
    onClose: () -> Unit,
    onOutput: (CapturePages, StripSelection, CaptureOutput) -> Unit,
) {
    BackHandler(onBack = onClose)
    val pages = remember(request) { CapturePages(request.pages) }
    var selection by remember(request) { mutableStateOf(request.selection) }
    // Each tap on "Select the screen" asks the strip for what is on screen between the bars
    var selectScreen by remember(request) { mutableIntStateOf(0) }

    LaunchedEffect(pages) {
        // Real sizes for the pages around the selection, before the strip settles
        val around = request.selection.pages
        pages.load(max(around.first - 2, 0)..min(around.last + 2, request.pages.lastIndex))
    }
    LaunchedEffect(pages, selection.pages) { pages.load(selection.pages) }

    val plan by remember(pages) { derivedStateOf { StitchPlan.make(selection, pages.sizeList()) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Strip(
            pages = pages,
            request = request,
            selection = selection,
            onSelectionChange = { selection = it },
            truncatedAt = plan?.truncatedAt,
            selectScreen = selectScreen,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                onClose = onClose,
                onSelectScreen = { selectScreen++ },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            Spacer(modifier = Modifier.weight(1f))
            BottomBar(
                plan = plan,
                pageNumber = { request.pages.getOrNull(it)?.number },
                enabled = !working && plan != null,
                canMakeCard = canMakeCard,
                onOutput = { onOutput(pages, selection, it) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }

        if (working) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Strip(
    pages: CapturePages,
    request: LongCaptureRequest,
    selection: StripSelection,
    onSelectionChange: (StripSelection) -> Unit,
    truncatedAt: StripAnchor?,
    selectScreen: Int,
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val minHeight = with(density) { MIN_SELECTION_HEIGHT.toPx() }
    val handleReach = with(density) { 28.dp.toPx() }
    val autoscrollMax = with(density) { 22.dp.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val viewport = constraints.maxHeight.toFloat()

        val layout by remember(pages, width) {
            derivedStateOf {
                StripLayout(
                    request.pages.indices.map { index ->
                        pages.sizes[index]?.let { width * it.height / it.width } ?: (width * ESTIMATED_ASPECT)
                    },
                )
            }
        }
        val origin by remember(viewport) { derivedStateOf { max(0f, (viewport - layout.totalHeight) / 2) } }
        val maxOffset by remember(viewport) { derivedStateOf { max(0f, layout.totalHeight + 2 * origin - viewport) } }

        // What sits at the screen's top, so the strip stays put while pages load and change height
        var viewTop by remember(request) {
            mutableStateOf(layout.anchor(layout.y(request.selection.top) - viewport * BAR_TOP))
        }
        val offset by remember(viewport, width) {
            derivedStateOf { (layout.y(viewTop) + origin).coerceIn(0f, maxOffset) }
        }

        val currentSelection by rememberUpdatedState(selection)
        var dragging by remember { mutableStateOf<Handle?>(null) }
        var fingerY by remember { mutableFloatStateOf(0f) }

        fun scrollTo(newOffset: Float) {
            viewTop = layout.anchor(newOffset.coerceIn(0f, maxOffset) - origin)
        }

        fun move() {
            val handle = dragging ?: return
            val range = layout.range(currentSelection)
            val y = offset + fingerY - origin
            onSelectionChange(
                when (handle) {
                    Handle.TOP -> currentSelection.copy(
                        top = layout.anchor(min(max(y, 0f), range.endInclusive - minHeight)),
                    )
                    Handle.BOTTOM -> currentSelection.copy(
                        bottom = layout.anchor(max(min(y, layout.totalHeight), range.start + minHeight)),
                    )
                },
            )
        }

        LaunchedEffect(request) {
            // Both handles start on screen, between the bars, whatever the selection asked for
            val range = layout.range(request.selection)
            onSelectionChange(
                layout.selection(
                    max(range.start, offset - origin + viewport * BAR_TOP),
                    min(range.endInclusive, offset - origin + viewport * BAR_BOTTOM),
                    minHeight,
                ),
            )
        }

        LaunchedEffect(dragging) {
            while (dragging != null) {
                val speed = autoscrollSpeed(fingerY, viewport, autoscrollMax)
                if (speed != 0f) {
                    scrollTo(offset + speed)
                    move()
                }
                withFrameNanos {}
            }
        }

        val scrollState = rememberScrollableState { delta ->
            val old = offset
            scrollTo(old - delta)
            old - offset
        }

        val visible by remember(viewport, width) {
            derivedStateOf {
                request.pages.indices.filter { index ->
                    val top = origin + layout.top(index) - offset
                    top < viewport * 2 && top + layout.heights[index] > -viewport
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewport, width) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val range = layout.range(currentSelection)
                        val topY = origin + range.start - offset
                        val bottomY = origin + range.endInclusive - offset
                        val handle = when {
                            abs(down.position.y - bottomY) < handleReach -> Handle.BOTTOM
                            abs(down.position.y - topY) < handleReach -> Handle.TOP
                            else -> return@awaitEachGesture
                        }
                        down.consume()
                        fingerY = down.position.y
                        dragging = handle
                        move()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            change.consume()
                            fingerY = change.position.y
                            move()
                        }
                        dragging = null
                    }
                }
                .scrollable(scrollState, Orientation.Vertical),
        ) {
            visible.forEach { index ->
                key(index) {
                    CapturePage(
                        pages = pages,
                        index = index,
                        width = width.roundToInt(),
                        modifier = Modifier
                            .offset { IntOffset(0, (origin + layout.top(index) - offset).roundToInt()) }
                            .size(
                                with(density) { width.toDp() },
                                with(density) { layout.heights[index].toDp() },
                            ),
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val range = layout.range(selection)
                val top = origin + range.start - offset
                val bottom = origin + range.endInclusive - offset
                val stripBottom = origin + layout.totalHeight - offset
                val dim = Color.Black.copy(alpha = 0.62f)
                drawRect(dim, Offset(0f, origin - offset), Size(size.width, max(top - (origin - offset), 0f)))
                drawRect(dim, Offset(0f, bottom), Size(size.width, max(stripBottom - bottom, 0f)))
                truncatedAt?.let { anchor ->
                    val cut = origin + layout.y(anchor) - offset
                    if (cut < bottom) {
                        drawRect(TRUNCATED.copy(alpha = 0.28f), Offset(0f, cut), Size(size.width, bottom - cut))
                        drawLine(
                            TRUNCATED,
                            Offset(0f, cut),
                            Offset(size.width, cut),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        )
                    }
                }
                val stroke = 2.dp.toPx()
                drawRect(
                    accent,
                    Offset(stroke / 2, top + stroke / 2),
                    Size(size.width - stroke, max(bottom - top - stroke, 0f)),
                    style = Stroke(stroke),
                )
                listOf(Handle.TOP to top, Handle.BOTTOM to bottom).forEach { (handle, y) ->
                    val handleWidth = (if (dragging == handle) 64 else 54).dp.toPx()
                    val handleHeight = 22.dp.toPx()
                    val left = (size.width - handleWidth) / 2
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.35f),
                        Offset(left, y - handleHeight / 2 + 2.dp.toPx()),
                        Size(handleWidth, handleHeight),
                        CornerRadius(handleHeight / 2),
                    )
                    drawRoundRect(
                        accent,
                        Offset(left, y - handleHeight / 2),
                        Size(handleWidth, handleHeight),
                        CornerRadius(handleHeight / 2),
                    )
                    for (line in -1..1) {
                        val lineY = y + line * 3.dp.toPx()
                        drawLine(
                            onAccent,
                            Offset(size.width / 2 - 6.dp.toPx(), lineY),
                            Offset(size.width / 2 + 6.dp.toPx(), lineY),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                }
            }
        }

        LaunchedEffect(selectScreen) {
            if (selectScreen == 0) return@LaunchedEffect
            onSelectionChange(
                layout.selection(
                    offset - origin + viewport * BAR_TOP,
                    offset - origin + viewport * BAR_BOTTOM,
                    minHeight,
                ),
            )
        }
    }
}

@Composable
private fun CapturePage(pages: CapturePages, index: Int, width: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, pages, index, width) {
        value = pages.displayBitmap(index, width)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = pages.pages.getOrNull(index)?.number?.toString().orEmpty(),
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    onClose: () -> Unit,
    onSelectScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BAR_COLOR)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(MaterialSymbols.Rounded.Close, stringResource(MR.strings.action_close), tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(MR.strings.screenshot_long),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(MR.strings.screenshot_long_hint),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onSelectScreen) {
            Icon(
                CustomMaterialSymbols.Rounded.ScreenshotFrame,
                stringResource(MR.strings.screenshot_select_screen),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun BottomBar(
    plan: StitchPlan?,
    pageNumber: (Int) -> Int?,
    enabled: Boolean,
    canMakeCard: Boolean,
    onOutput: (CaptureOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BAR_COLOR)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Readout(plan, pageNumber)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CaptureButton(
                title = stringResource(MR.strings.screenshot_style),
                icon = MaterialSymbols.Rounded.Palette,
                prominent = false,
                enabled = enabled,
                onClick = { onOutput(CaptureOutput.STYLE) },
            )
            if (canMakeCard) {
                CaptureButton(
                    title = stringResource(MR.strings.screenshot_make_card),
                    icon = MaterialSymbols.Rounded.Photo,
                    prominent = false,
                    enabled = enabled,
                    onClick = { onOutput(CaptureOutput.CARD) },
                )
            }
            CaptureButton(
                title = stringResource(MR.strings.action_save),
                icon = MaterialSymbols.Rounded.Save,
                prominent = false,
                enabled = enabled,
                onClick = { onOutput(CaptureOutput.SAVE) },
            )
            CaptureButton(
                title = stringResource(MR.strings.action_share),
                icon = MaterialSymbols.Rounded.Share,
                prominent = true,
                enabled = enabled,
                onClick = { onOutput(CaptureOutput.SHARE) },
            )
        }
    }
}

@Composable
private fun Readout(plan: StitchPlan?, pageNumber: (Int) -> Int?) {
    val style = MaterialTheme.typography.labelMedium
    when {
        plan == null -> Text(
            text = stringResource(MR.strings.screenshot_waiting),
            color = Color.White.copy(alpha = 0.7f),
            style = style,
        )
        plan.truncated -> Text(
            text = stringResource(MR.strings.screenshot_too_long, StitchPlan.MAX_HEIGHT),
            color = TRUNCATED,
            style = style,
            fontWeight = FontWeight.SemiBold,
        )
        else -> {
            val numbers = plan.slices.mapNotNull { pageNumber(it.page) }
            val first = numbers.firstOrNull()
            val last = numbers.lastOrNull()
            val pagesText = when {
                first == null || last == null -> ""
                first == last -> stringResource(MR.strings.screenshot_page, first)
                else -> stringResource(MR.strings.screenshot_pages, first, last)
            }
            Text(
                text = stringResource(MR.strings.screenshot_size, plan.width, plan.height, pagesText),
                color = Color.White.copy(alpha = 0.8f),
                style = style,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Icon over caption, the main one in the accent. */
@Composable
private fun RowScope.CaptureButton(
    title: String,
    icon: ImageVector,
    prominent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (prominent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)
    val content = if (prominent) MaterialTheme.colorScheme.onPrimary else Color.White
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(background.copy(alpha = background.alpha * if (enabled) 1f else 0.45f))
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text = title, color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private enum class Handle { TOP, BOTTOM }

private val MIN_SELECTION_HEIGHT = 48.dp

/** Until a page's size is known, it is taken to be this much taller than wide. */
private const val ESTIMATED_ASPECT = 1.4f

/** Where the bars end and start, as fractions of the screen's height. */
private const val BAR_TOP = 0.18f
private const val BAR_BOTTOM = 0.8f

private val BAR_COLOR = Color(0xE6202124)
private val TRUNCATED = Color(0xFFFF9800)
