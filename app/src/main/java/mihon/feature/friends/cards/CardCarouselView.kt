package mihon.feature.friends.cards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The detail carousel (CARD_SPEC §14): the centred card [CardCarousel.cardWidth] wide, its neighbours peeking in at
 * the sides, smaller and dimmed by their distance from the centre; one card per swipe with a soft spring. Double tap
 * flips a card; a single tap opens the centred card in the 3-D viewer ([onOpen]) or brings a neighbour to the
 * centre. Only the settled card runs the live view; every card shows its album thumbnail until the full card is
 * rendered.
 */
@Composable
fun CardCarouselPager(
    cards: List<ShioriCard>,
    state: PagerState,
    flipped: (ShioriCard) -> Boolean,
    onFlip: (ShioriCard) -> Unit,
    onOpen: (ShioriCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val openLabel = stringResource(MR.strings.cards_viewer_open)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth.value
        val cardWidth = CardCarousel.cardWidth(available).dp
        val side = CardCarousel.sidePadding(available).dp
        HorizontalPager(
            state = state,
            key = { cards[it].id },
            // Room above and below: the pager clips its bounds, and a card tilting toward the viewer grows past its own
            contentPadding = PaddingValues(horizontal = side, vertical = CardCarousel.SWING_ROOM.dp),
            beyondViewportPageCount = 2,
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = spring(
                    dampingRatio = CardCarousel.SNAP_DAMPING,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            verticalAlignment = Alignment.Top,
            // The cards' height (a pager in a vertical scroll needs one) plus the swing room
            modifier = Modifier
                .fillMaxWidth()
                .height(cardWidth / CARD_ASPECT + CardCarousel.SWING_ROOM.dp * 2)
                .clipToBounds(),
        ) { page ->
            val card = cards[page]
            FlipCard(
                flipped = flipped(card),
                front = {
                    if (page == state.settledPage) {
                        CardForgingFrame(
                            card = card,
                            // With several cards a sideways drag pages; the tilt then follows the device only
                            draggable = cards.size == 1,
                            showCaption = false,
                            thumbnailPlaceholder = true,
                        )
                    } else {
                        ShioriCardView(card = card, animated = false, interactive = false, thumbnailPlaceholder = true)
                    }
                },
                back = { CardInfoBack(card = card) },
                modifier = Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        // Read here, in the draw phase: the drag doesn't recompose the pages
                        val offset = (page - state.currentPage) - state.currentPageOffsetFraction
                        val scale = CardCarousel.scale(offset)
                        scaleX = scale
                        scaleY = scale
                        alpha = CardCarousel.alpha(offset)
                        // Fade without an offscreen buffer: one is sized to the page and would clip the tilted card
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        translationX = CardCarousel.pull(offset) * density
                    }
                    .semantics {
                        onClick(label = openLabel) {
                            onOpen(card)
                            true
                        }
                    }
                    .pointerInput(card.id) {
                        detectTapGestures(
                            onDoubleTap = { onFlip(card) },
                            onTap = {
                                if (page == state.currentPage) {
                                    onOpen(card)
                                } else {
                                    scope.launch { state.animateScrollToPage(page) }
                                }
                            },
                        )
                    },
            )
        }
    }
}

/**
 * Holds the full images (art, custom frame) of the cards around the current page so swiping finds them ready: the
 * ±[CardPager.PREFETCH_RADIUS] window loads current card first, at most [CardPager.HELD] cards are kept (least
 * recently used dropped), and loads for cards that left the window are cancelled. Main thread only.
 */
class CardImagePrefetcher(
    private val scope: CoroutineScope,
    private val load: suspend (ShioriCard) -> CardImages,
) {
    private val held = CardPrefetchCache<CardImages>()
    private val loading = mutableMapOf<String, Job>()

    /** The current page moved to [index] of [cards]. */
    fun focus(cards: List<ShioriCard>, index: Int) {
        val pager = CardPager(cards.map { it.id })
        val window = pager.windowIds(index)
        val plan = CardPrefetchPlan.of(window, held.ids, loading.keys)
        plan.cancel.forEach { loading.remove(it)?.cancel() }
        // Touch the held ones in reverse window order so the current card is the most recently used
        window.asReversed().forEach { held[it] }
        val byId = cards.associateBy { it.id }
        plan.start.forEach { id ->
            val card = byId[id] ?: return@forEach
            loading[id] = scope.launch {
                try {
                    held.put(id, load(card))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.WARN, e) { "Prefetching card $id failed" }
                } finally {
                    if (loading[id] == coroutineContext[Job]) loading.remove(id)
                }
            }
        }
    }
}
