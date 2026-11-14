package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.presentation.core.components.material.padding

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

@Composable
fun ReaderAppBars(
    visible: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    extraActions: List<AppBar.AppBarAction>,

    chapterNavigatorType: ChapterNavigatorType,
    verticalNavigatorHeight: Float,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    onPageIndexChangeFinished: () -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    onLongScreenshot: () -> Unit,
    onStyledScreenshot: () -> Unit,
    onPageAsIs: () -> Unit,
    onMakeCard: (() -> Unit)?,
    autoScrolling: Boolean?,
    onToggleAutoScroll: () -> Unit,
) {
    // The iOS reader chrome: floating dark glass panels over the page, dark in either theme.
    val backgroundColor = ReaderGlass
    val panelShape = RoundedCornerShape(24.dp)
    val panel = Modifier
        .shadow(12.dp, panelShape)
        .clip(panelShape)
        .background(backgroundColor)
        .border(0.5.dp, Color.White.copy(alpha = 0.12f), panelShape)

    ReaderGlassTheme {
        Column(modifier = Modifier.fillMaxHeight()) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(readerBarsSlideAnimationSpec) { -it } + fadeIn(readerBarsFadeAnimationSpec),
                exit = slideOutVertically(readerBarsSlideAnimationSpec) { -it } + fadeOut(readerBarsFadeAnimationSpec),
            ) {
                ReaderTopBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .then(panel)
                        .clickable(onClick = onClickTopAppBar),
                    mangaTitle = mangaTitle,
                    chapterTitle = chapterTitle,
                    navigateUp = navigateUp,
                    bookmarked = bookmarked,
                    onToggleBookmarked = onToggleBookmarked,
                    onOpenInWebView = onOpenInWebView,
                    onOpenInBrowser = onOpenInBrowser,
                    onShare = onShare,
                    extraActions = extraActions,
                )
            }

            if (!chapterNavigatorType.isHorizontal()) {
                val sliderOnLeft = chapterNavigatorType == ChapterNavigatorType.VERTICAL_LEFT
                val sideOffset: (Int) -> Int = { if (sliderOnLeft) -it else it }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (sliderOnLeft) LayoutDirection.Ltr else LayoutDirection.Rtl,
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInHorizontally(readerBarsSlideAnimationSpec) { sideOffset(it) } +
                                fadeIn(readerBarsFadeAnimationSpec),
                            exit = slideOutHorizontally(readerBarsSlideAnimationSpec) { sideOffset(it) } +
                                fadeOut(readerBarsFadeAnimationSpec),
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    ChapterNavigator(
                                        modifier = Modifier.fillMaxHeight(verticalNavigatorHeight),
                                        type = chapterNavigatorType,
                                        onNextChapter = onNextChapter,
                                        enabledNext = enabledNext,
                                        onPreviousChapter = onPreviousChapter,
                                        enabledPrevious = enabledPrevious,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        onPageIndexChange = onPageIndexChange,
                                        onPageIndexChangeFinished = onPageIndexChangeFinished,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(readerBarsSlideAnimationSpec) { it } + fadeIn(readerBarsFadeAnimationSpec),
                exit = slideOutVertically(readerBarsSlideAnimationSpec) { it } + fadeOut(readerBarsFadeAnimationSpec),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    if (chapterNavigatorType.isHorizontal()) {
                        ChapterNavigator(
                            type = chapterNavigatorType,
                            onNextChapter = onNextChapter,
                            enabledNext = enabledNext,
                            onPreviousChapter = onPreviousChapter,
                            enabledPrevious = enabledPrevious,
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageIndexChange = onPageIndexChange,
                            onPageIndexChangeFinished = onPageIndexChangeFinished,
                        )
                    }
                    ReaderBottomBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .then(panel)
                            .padding(horizontal = MaterialTheme.padding.small, vertical = 4.dp),
                        readingMode = readingMode,
                        onClickReadingMode = onClickReadingMode,
                        orientation = orientation,
                        onClickOrientation = onClickOrientation,
                        cropEnabled = cropEnabled,
                        onClickCropBorder = onClickCropBorder,
                        onClickSettings = onClickSettings,
                        onLongScreenshot = onLongScreenshot,
                        onStyledScreenshot = onStyledScreenshot,
                        onPageAsIs = onPageAsIs,
                        onMakeCard = onMakeCard,
                        autoScrolling = autoScrolling,
                        onToggleAutoScroll = onToggleAutoScroll,
                    )
                }
            }
        }
    }
}

/** Dark glass colours for the reader chrome, whatever the app theme. */
@Composable
private fun ReaderGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = ReaderGlassSurface,
            surfaceTint = Color.Transparent,
            surfaceContainer = ReaderGlassSurface,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            onBackground = Color.White,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

private val ReaderGlass = Color(0xFF1C1C1E).copy(alpha = 0.9f)
private val ReaderGlassSurface = Color(0xFF2C2C2E)
