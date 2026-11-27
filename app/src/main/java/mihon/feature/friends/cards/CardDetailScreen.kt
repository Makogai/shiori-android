package mihon.feature.friends.cards

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.MenuIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Print
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.theme.separator
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The card detail carousel (CARD_SPEC §14): opened from the album it walks the album list ([ids], the current scope,
 * filter and sort) starting at [cardId]; elsewhere the list is just that card. The carousel on top (neighbours
 * peeking in), "3 of 15 · Filip" under it, then the centred card's actions: Flip, Save front, Save back, Print sheet,
 * Share, and favourite and delete for own cards, and its details. Each card keeps its own flip state. Tapping the
 * centred card opens it in the 3-D viewer ([CardViewerDialog]), on the side it shows.
 */
class CardDetailScreen(private val cardId: String, private val ids: List<String> = emptyList()) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<CardsViewModel>()
        val repository = viewModel.repository
        val album by repository.album.collectAsStateWithLifecycle()
        val feed by repository.feed.collectAsStateWithLifecycle()
        val loaded by repository.loaded.collectAsStateWithLifecycle()
        val cards = remember(album, feed) {
            val byId = (feed + album).associateBy { it.id }
            ids.ifEmpty { listOf(cardId) }.mapNotNull(byId::get)
        }
        val pager = remember(cards) { CardPager(cards.map { it.id }) }
        val pagerState = rememberPagerState(initialPage = pager.index(cardId)) { cards.size }
        val snackbarHostState = remember { SnackbarHostState() }
        val flipped = remember { mutableStateMapOf<String, Boolean>() }
        var confirmDelete by remember { mutableStateOf<ShioriCard?>(null) }
        // The original panel open full screen, with the card it belongs to
        var original by remember { mutableStateOf<Pair<ShioriCard, Bitmap>?>(null) }
        // The card open in the 3-D viewer (a tap on the centred card)
        var viewing by remember { mutableStateOf<ShioriCard?>(null) }
        // The list may arrive after the first frame (cold start): go to the tapped card once it does
        var positioned by rememberSaveable { mutableStateOf(cards.isNotEmpty()) }
        LaunchedEffect(cards.isNotEmpty()) {
            if (!positioned && cards.isNotEmpty()) {
                pagerState.scrollToPage(pager.index(cardId))
                positioned = true
            }
        }

        LaunchedEffect(Unit) {
            viewModel.load()
            viewModel.events.collect { event ->
                when (event) {
                    CardsViewModel.Event.Saved -> context.toast(MR.strings.picture_saved)
                    is CardsViewModel.Event.Share ->
                        context.startActivity(event.uri.toShareIntent(context, type = event.type, message = event.text))
                    is CardsViewModel.Event.Error ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.friends_error, event.message))
                }
            }
        }
        val currentIndex = pagerState.currentPage.coerceAtMost(cards.size - 1).coerceAtLeast(0)
        val current = cards.getOrNull(currentIndex)
        val view = LocalView.current
        val flip = { card: ShioriCard ->
            flipped[card.id] = flipped[card.id] != true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            Unit
        }
        // Full images for the cards around the current one, so a swipe finds them ready
        val scope = rememberCoroutineScope()
        val prefetcher = remember(repository) { CardImagePrefetcher(scope) { repository.images(it) } }
        LaunchedEffect(cards, currentIndex) { prefetcher.focus(cards, currentIndex) }
        LifecycleResumeEffect(current?.id, current?.frameStatus) {
            current?.let {
                repository.watchFrame(it)
                repository.reviveFrame(it)
            }
            onPauseOrDispose {}
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = current?.serialText ?: stringResource(MR.strings.cards_card),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            if (cards.isEmpty() || current == null) {
                if (loaded) EmptyScreen(MR.strings.cards_not_found, modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }
            val pulledBy = cardOwnerName(repository, current.owner)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CardCarouselPager(
                    cards = cards,
                    state = pagerState,
                    flipped = { flipped[it.id] == true },
                    onFlip = { flip(it) },
                    onOpen = { viewing = it },
                )
                CardCaption(pager.caption(currentIndex, pulledBy))
                CardActions(
                    card = current,
                    own = current.owner == repository.me,
                    repository = repository,
                    onFlip = { flip(current) },
                    onSaveFront = { viewModel.saveFront(current) },
                    onSaveBack = { viewModel.saveBack(current) },
                    onPrint = { viewModel.printSheet(current) },
                    onShare = { viewModel.share(current) },
                    onFavorite = { viewModel.toggleFavorite(current) },
                    onDelete = { confirmDelete = current },
                    onOriginal = { bitmap -> original = bitmap?.let { current to it } },
                )
            }
        }

        original?.let { (card, bitmap) ->
            OriginalPanel(bitmap, onShare = { viewModel.shareOriginal(card, bitmap) }) { original = null }
        }

        viewing?.let { card ->
            CardViewerDialog(
                card = card,
                startOnBack = flipped[card.id] == true,
                onSave = { viewModel.saveFront(card) },
                onDismiss = { viewing = null },
            )
        }

        confirmDelete?.let { card ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(stringResource(MR.strings.cards_delete_title)) },
                text = { Text(stringResource(MR.strings.cards_delete_summary)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = null
                            val last = cards.size <= 1
                            // The carousel then shows the next card (or the previous at the end); empty closes it
                            viewModel.delete(card) { if (last) navigator.pop() }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
    }
}

/** "3 of 15 · Filip" in a capsule. */
@Composable
private fun CardCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The current card's "Forging…" line, actions and details. As on iOS: Flip, Save (front or back) and Print in one
 * row; Share beside them for others' cards, or Share, Favourite and Delete, titled, in a second row for my own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardActions(
    card: ShioriCard,
    own: Boolean,
    repository: CardRepository,
    onFlip: () -> Unit,
    onSaveFront: () -> Unit,
    onSaveBack: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onOriginal: (Bitmap?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saveMenu by remember { mutableStateOf(false) }
    val share = stringResource(MR.strings.action_share)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (card.frameStatus == CardFrameStatus.PENDING && !CardAlbum.isStalePending(card, Instant.now())) {
            Text(
                text = stringResource(MR.strings.cards_forging),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardBorderedButton(
                    title = stringResource(MR.strings.cards_flip),
                    icon = MaterialSymbols.Rounded.FlipToBack,
                    onClick = onFlip,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.weight(1f)) {
                    CardBorderedButton(
                        title = stringResource(MR.strings.action_save),
                        icon = MaterialSymbols.Rounded.Download,
                        onClick = { saveMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.cards_save_front)) },
                            leadingIcon = { MenuIcon(MaterialSymbols.Rounded.Photo) },
                            onClick = {
                                saveMenu = false
                                onSaveFront()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.cards_save_back)) },
                            leadingIcon = { MenuIcon(MaterialSymbols.Rounded.Save) },
                            onClick = {
                                saveMenu = false
                                onSaveBack()
                            },
                        )
                    }
                }
                CardBorderedButton(
                    title = stringResource(MR.strings.cards_print),
                    icon = MaterialSymbols.Rounded.Print,
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                )
                if (!own) {
                    CardBorderedButton(
                        title = null,
                        icon = MaterialSymbols.Rounded.Share,
                        onClick = onShare,
                        label = share,
                    )
                }
            }
            if (own) {
                // Wraps on narrow screens, so "Unfavourite" is never cut
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardBorderedButton(title = share, icon = MaterialSymbols.Rounded.Share, onClick = onShare)
                    CardBorderedButton(
                        title = stringResource(
                            if (card.favorite) MR.strings.cards_unfavourite else MR.strings.cards_favourite,
                        ),
                        icon = if (card.favorite) {
                            MaterialSymbols.RoundedFilled.Favorite
                        } else {
                            MaterialSymbols.Rounded.Favorite
                        },
                        onClick = onFavorite,
                    )
                    CardBorderedButton(
                        title = stringResource(MR.strings.action_delete),
                        icon = MaterialSymbols.Rounded.Delete,
                        onClick = onDelete,
                        destructive = true,
                    )
                }
            }
        }
        // The player's screenshot the AI card was drawn from (AI cards redraw the scene).
        if (card.isCardEdit) {
            CardBorderedButton(
                title = stringResource(MR.strings.cards_original_panel),
                icon = MaterialSymbols.Rounded.Photo,
                onClick = {
                    scope.launch { onOriginal(repository.images(card).art?.withoutArtBars(card.scores?.artFill)) }
                },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
        }
        Details(card, repository)
    }
}

/**
 * The original panel full screen with a Share button; pinch to zoom, double tap to zoom in or out, tap outside or
 * back to close.
 */
@Composable
private fun OriginalPanel(bitmap: Bitmap, onShare: () -> Unit, onDismiss: () -> Unit) {
    var zoom by remember { mutableStateOf(1f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(MR.strings.cards_original_panel),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 5f) }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f },
                            onTap = { if (zoom == 1f) onDismiss() },
                        )
                    }
                    .graphicsLayer(scaleX = zoom, scaleY = zoom),
            )
            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Share,
                    contentDescription = stringResource(MR.strings.action_share),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Details(card: ShioriCard, repository: CardRepository) {
    val zone = repository.zone
    val pulled = remember(card.createdAt, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(card.createdInstant.atZone(zone))
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow(stringResource(MR.strings.cards_grade), card.grade.label)
            DetailRow(stringResource(MR.strings.cards_theme), card.theme.title)
            DetailRow(stringResource(MR.strings.cards_series), card.seriesTitle)
            if (card.chapterText.isNotEmpty()) DetailRow(stringResource(MR.strings.cards_chapter), card.chapterText)
            card.sourceName?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(MR.strings.cards_source), it) }
            DetailRow(stringResource(MR.strings.cards_pulled_by), cardOwnerName(repository, card.owner))
            DetailRow(stringResource(MR.strings.cards_pulled_at), pulled)
            DetailRow(stringResource(MR.strings.cards_serial), "${card.serialText} · ${card.seasonText(zone)}")
            DetailRow(
                stringResource(MR.strings.cards_stats),
                "${stringResource(MR.strings.cards_stat_power)} ${card.stats.power} · " +
                    "${stringResource(MR.strings.cards_stat_aura)} ${card.stats.aura} · " +
                    "${stringResource(MR.strings.cards_stat_drama)} ${card.stats.drama}",
            )
            CardRankProgress.of(card)?.let { rank ->
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.separator)
                RankProgressSection(rank, repository.accent(rank.grade))
            }
            card.scores?.let { scores ->
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.separator)
                Text(
                    text = stringResource(MR.strings.cards_judge_scores),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                ScoreBar(stringResource(MR.strings.cards_score_iconic), scores.iconic)
                ScoreBar(stringResource(MR.strings.cards_score_emotion), scores.emotion)
                ScoreBar(stringResource(MR.strings.cards_score_composition), scores.composition)
                ScoreBar(stringResource(MR.strings.cards_score_action), scores.action)
                ScoreBar(stringResource(MR.strings.cards_score_art), scores.artQuality)
                ScoreBar(stringResource(MR.strings.cards_score_story), scores.story)
            }
        }
    }
}

/** A v3 card's rank score: its rank, "68.4 / 100", progress through the rank's band and what's next. */
@Composable
private fun RankProgressSection(rank: CardRankProgress, color: Color) {
    val locale = Locale.getDefault()
    val score = CardRankProgress.format(rank.score, locale)
    val status = when (val status = rank.status) {
        is CardRankStatus.Next -> stringResource(
            MR.strings.cards_rank_points_to,
            CardRankProgress.format(status.points, locale),
            status.next.rank,
        )
        CardRankStatus.Top -> stringResource(MR.strings.cards_rank_top)
        is CardRankStatus.Held -> stringResource(MR.strings.cards_rank_held, status.rank.rank)
    }
    val description = stringResource(MR.strings.cards_rank_progress_description, rank.grade.rank, score, status)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(MR.strings.cards_rank_progress),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The rank badge: dark text on the rank's colour reads in light and dark mode alike
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .widthIn(min = 36.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = rank.grade.rank,
                    color = Color.Black.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = stringResource(MR.strings.cards_rank_score, score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { rank.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ScoreBar(title: String, value: Double?) {
    value ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
        // A thin rounded bar, as iOS's ProgressView
        LinearProgressIndicator(
            progress = { (value.coerceIn(0.0, 10.0) / 10).toFloat() },
            modifier = Modifier.weight(1f).height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            text = String.format(Locale.getDefault(), "%.1f", value),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

private val FAVORITE = androidx.compose.ui.graphics.Color(0xFFFF4D8D)
