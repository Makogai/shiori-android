package eu.kanade.tachiyomi.ui.reader

import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderNavigationOverlay
import eu.kanade.presentation.reader.ReaderNavigationOverlayState
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.capture.LongCaptureScreen
import eu.kanade.presentation.reader.capture.StyledShotDialog
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.capture.CaptureOutput
import eu.kanade.tachiyomi.ui.reader.capture.CapturePages
import eu.kanade.tachiyomi.ui.reader.capture.ReaderCaptureViewModel
import eu.kanade.tachiyomi.ui.reader.capture.ScreenshotCardMaker
import eu.kanade.tachiyomi.ui.reader.capture.ShotInfo
import eu.kanade.tachiyomi.ui.reader.capture.StripSelection
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.Action
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import mihon.feature.autoscroll.AutoPageInterval
import mihon.feature.autoscroll.AutoPageTarget
import mihon.feature.autoscroll.AutoScrollSpeed
import mihon.feature.autoscroll.AutoScrollTarget
import mihon.feature.autoscroll.ReaderAutoScroll
import mihon.feature.enhance.PageEnhance
import mihon.feature.friends.cards.CardMakerDialog
import mihon.feature.friends.cards.CardMakerViewModel
import mihon.feature.friends.cards.CardRepository
import mihon.feature.friends.cards.CardSeriesContext
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.LeagueHashing
import mihon.feature.habits.HabitsPreferences
import mihon.feature.habits.ReadingDays
import mihon.feature.habits.ReadingTimeTracker
import mihon.feature.readerextras.ReaderExtrasHost
import mihon.feature.readerextras.ReaderExtrasViewModel
import mihon.feature.readerextras.readerExtrasActions
import mihon.feature.readingpace.ReadingPaceViewModel
import mihon.feature.readingpace.timeLeftLabel
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

class ReaderActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    companion object {
        /** [page] opens the chapter at that page index instead of where it was left. */
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?, page: Int? = null): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                // Read by ReaderViewModel as the page to restore
                page?.let { putExtra("page_index", it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    @Inject private lateinit var readerPreferences: ReaderPreferences

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var friendsRepository: FriendsRepository

    @Inject private lateinit var imageSaver: ImageSaver

    @Inject private lateinit var cardRepository: CardRepository

    @Inject private lateinit var readingTimeTracker: ReadingTimeTracker

    @Inject private lateinit var habitsPreferences: HabitsPreferences

    val viewModel by viewModels<ReaderViewModel> { graph.viewModelFactory }
    val settingsViewModel by viewModels<ReaderSettingsViewModel> {
        viewModelFactory { initializer { ReaderSettingsViewModel(viewModel, readerPreferences, preferences) } }
    }
    val captureViewModel by viewModels<ReaderCaptureViewModel> {
        viewModelFactory {
            initializer { ReaderCaptureViewModel(application, viewModel, imageSaver, readerPreferences) }
        }
    }
    private val extrasViewModel by viewModels<ReaderExtrasViewModel> { graph.viewModelFactory }
    private val paceViewModel by viewModels<ReadingPaceViewModel> { graph.viewModelFactory }
    private val autoScroll by lazy { ReaderAutoScroll(resources.displayMetrics.density) }
    private val cardMakerViewModel by viewModels<CardMakerViewModel> {
        viewModelFactory { initializer { CardMakerViewModel(cardRepository) } }
    }
    private var assistUrl: String? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost by lazy { DisplayRefreshHost(readerPreferences) }
    private val navigationOverlayState = ReaderNavigationOverlayState()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    private var isLoadingViewer by mutableStateOf(false)

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    private val viewerFlow = MutableStateFlow<Viewer?>(null)
    private val viewer: Viewer? inline get() = viewerFlow.value

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        super.onCreate(savedInstanceState)

        if (cardRepository.isAvailable) {
            captureViewModel.cardMaker = ScreenshotCardMaker { bitmap, info ->
                cardContext(info)?.let { cardMakerViewModel.open(bitmap, it) }
            }
        }

        setComposeContent {
            val state by viewModel.state.collectAsState()
            val general by settingsViewModel.general.collectAsState()
            val showPageNumber by readerPreferences.showPageNumber.collectAsState()
            val flashOnPageChange by readerPreferences.flashOnPageChange.collectAsState()
            val colorOverlayEnabled by readerPreferences.colorFilter.collectAsState()
            val colorOverlay by readerPreferences.colorFilterValue.collectAsState()
            val colorOverlayMode by readerPreferences.colorFilterMode.collectAsState()
            val brightness by settingsViewModel.brightness.collectAsState()
            val navigation = settingsViewModel.navigation.collectAsState().value

            val backgroundColor = remember(general.theme) { Color(baseContext.readerBackgroundColor(general.theme)) }
            val colorOverlayBlendMode = remember(colorOverlayMode) {
                ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
            }

            LaunchedEffect(brightness) { setWindowBrightness(brightness) }

            val forceNavigationOverlay = remember { settingsViewModel.takeNavigationOverlayForNewUser() }
            LaunchedEffect(navigation) {
                if (navigation == null) return@LaunchedEffect
                val showOnStart = forceNavigationOverlay || settingsViewModel.general.value.navigationOverlayOnStart
                navigationOverlayState.setNavigation(navigation, showOnStart)
            }

            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .onPreviewKeyEvent(::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            navigationOverlayState.hide()
                        }
                    }
                    .then(if (general.keepScreenOn) Modifier.keepScreenOn() else Modifier),
            ) {
                ReaderViewer()

                if (isLoadingViewer) {
                    LoadingScreen()
                    return@Box
                }

                ReaderContentOverlay(
                    brightness = brightness,
                    color = colorOverlay.takeIf { colorOverlayEnabled },
                    colorBlendMode = colorOverlayBlendMode,
                )

                if (!state.menuVisible && showPageNumber) {
                    ReaderPageIndicator(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }

                if (flashOnPageChange) {
                    DisplayRefreshHost(hostState = displayRefreshHost)
                }

                ReaderNavigationOverlay(state = navigationOverlayState)

                AppBars(state = state)

                CaptureScreens()
            }

            ReaderExtrasHost(
                viewModel = extrasViewModel,
                state = state,
                onClose = ::finish,
                onOpenOffer = { offer ->
                    if (offer.chapter != null) {
                        startActivity(newIntent(this, offer.manga.id, offer.chapter.id))
                    } else {
                        openMangaScreen(offer.manga.id)
                    }
                    finish()
                },
            )

            if (state.isLoadingAdjacentChapter) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(MR.strings.loading))
                        }
                    },
                )
            }

            val onDismissRequest = viewModel::closeDialog
            when (val dialog = state.dialog) {
                is ReaderViewModel.Dialog.Settings -> {
                    ReaderSettingsDialog(
                        onDismissRequest = onDismissRequest,
                        onShowMenus = { setMenuVisibility(true) },
                        onHideMenus = { setMenuVisibility(false) },
                        viewModel = settingsViewModel,
                    )
                }
                is ReaderViewModel.Dialog.ReadingModeSelect -> {
                    ReadingModeSelectDialog(
                        onDismissRequest = onDismissRequest,
                        viewModel = settingsViewModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            if (!readerPreferences.showReadingMode.get()) {
                                menuToggleToast = toast(stringRes)
                            }
                        },
                    )
                }
                is ReaderViewModel.Dialog.OrientationModeSelect -> {
                    OrientationSelectDialog(
                        onDismissRequest = onDismissRequest,
                        viewModel = settingsViewModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            menuToggleToast = toast(stringRes)
                        },
                    )
                }
                is ReaderViewModel.Dialog.PageActions -> {
                    val page = dialog.page
                    val isLongStrip = isLongStrip()
                    ReaderPageActionsDialog(
                        onDismissRequest = onDismissRequest,
                        onSetAsCover = viewModel::setAsCover,
                        onShare = viewModel::shareImage,
                        onSave = viewModel::saveImage,
                        isLongStrip = isLongStrip,
                        onStyledScreenshot = { captureViewModel.styleVisible(page, null, isLongStrip) },
                        onLongScreenshot = { captureViewModel.startLongCapture(page, null, isLongStrip) },
                        onMakeCard = captureViewModel.cardMaker?.let { maker ->
                            {
                                val pages = CapturePages(listOf(page))
                                captureViewModel.stitchThen(pages, StripSelection.whole(0), maker::onMakeCard)
                            }
                        },
                    )
                }
                null -> {}
            }
        }

        if (!viewModel.hasValidArgs) {
            finish()
            return
        }

        NotificationReceiver.dismissNotification(
            this,
            viewModel.mangaId.hashCode(),
            Notifications.ID_NEW_CHAPTERS,
        )

        setMenuVisibility(viewModel.state.value.menuVisible)

        // Finish when incognito mode is disabled
        preferences.incognitoMode.changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.initError }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setInitialChapterError)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.PageChanged -> {
                        displayRefreshHost.flash()
                        readingTimeTracker.onActive()
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page)
                    }
                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                    is ReaderViewModel.Event.ChapterFinished -> {
                        onChapterFinished(event.today, event.goal)
                    }
                }
            }
            .launchIn(lifecycleScope)

        captureViewModel.events
            .onEach { event ->
                when (event) {
                    is ReaderCaptureViewModel.Event.Toast -> toast(event.resource)
                    is ReaderCaptureViewModel.Event.Share -> {
                        startActivity(event.uri.toShareIntent(applicationContext, message = event.text))
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        autoScroll.pause()
        viewModel.saveHistory()
        readingTimeTracker.onPause()
        if (isFinishing) friendsRepository.requestSync(FriendsRepository.READER_SYNC_DELAY)
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()
        readingTimeTracker.onActive()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        readingTimeTracker.onActive()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        readingTimeTracker.onActive()
        return super.dispatchKeyEvent(event)
    }

    /** "Chapter 7 today · 3 left to goal", with a success haptic when it reaches the goal. */
    private fun onChapterFinished(today: Int, goal: Int?) {
        val left = ReadingDays.goalLeft(today, goal)
        if (goal != null && today == goal) {
            window.decorView.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                },
            )
        }
        if (!habitsPreferences.chapterToast.get()) return
        val text = listOfNotNull(
            stringResource(MR.strings.habits_toast_today, today),
            when (left) {
                null -> null
                0 -> stringResource(MR.strings.habits_toast_goal_done)
                else -> stringResource(MR.strings.habits_toast_left, left)
            },
        )
            .joinToString(" · ")
        toast(text)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    @Composable
    private fun ReaderViewer() {
        val viewer by viewerFlow.collectAsState()
        val general by settingsViewModel.general.collectAsState()
        val insets = when {
            !general.fullscreen -> WindowInsets.systemBars
            !general.drawUnderCutout -> WindowInsets.displayCutout
            else -> WindowInsets(0)
        }
        val colorFilter = remember(general.grayscale, general.invertedColors, general.enhance) {
            viewerColorFilter(general.grayscale, general.invertedColors, general.enhance)
        }
        // Sharpening runs on what the viewer draws, after the pages are scaled to the screen.
        val sharpen = remember(general.enhance) { if (general.enhance) PageEnhance.sharpen() else null }

        // Each viewer filters its page images alone: transition, loading and error pages keep their colours.
        CompositionLocalProvider(LocalPageColorFilter provides colorFilter) {
            key(viewer) {
                viewer?.Content(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(insets)
                        .graphicsLayer { renderEffect = sharpen },
                )
            }
        }
    }

    /**
     * Handles a key [event] before anything else in the reader. Movement happens on key up.
     */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        val viewer = viewer ?: return false
        val navigation = settingsViewModel.navigation.value ?: return false
        val isUp = event.type == KeyEventType.KeyUp
        val action = when (event.key) {
            Key.VolumeDown, Key.VolumeUp -> {
                val general = settingsViewModel.general.value
                if (!general.volumeKeysEnabled || viewModel.state.value.menuVisible) return false
                if ((event.key == Key.VolumeDown) != general.volumeKeysInverted) Action.FORWARD else Action.BACKWARD
            }
            Key.DirectionRight -> if (event.isCtrlPressed) {
                Action.FORWARD
            } else {
                navigation.actionOf(
                    NavigationRegion.RIGHT,
                )
            }
            Key.DirectionLeft -> if (event.isCtrlPressed) {
                Action.BACKWARD
            } else {
                navigation.actionOf(
                    NavigationRegion.LEFT,
                )
            }
            Key.DirectionDown, Key.PageDown -> Action.FORWARD
            Key.DirectionUp, Key.PageUp -> Action.BACKWARD
            Key.Menu -> Action.MENU
            Key.N -> {
                if (isUp) loadNextChapter()
                return true
            }
            Key.P -> {
                if (isUp) loadPreviousChapter()
                return true
            }
            else -> return false
        }
        if (isUp) {
            when (action) {
                Action.MENU -> toggleMenu()
                Action.FORWARD -> viewer.moveForward()
                Action.BACKWARD -> viewer.moveBackward()
            }
        }
        return true
    }

    /**
     * Called from the viewer when it's tapped at [pos], given as a fraction of its size.
     */
    fun onViewerTap(pos: Offset) {
        val action = settingsViewModel.navigation.value?.getAction(pos) ?: Action.MENU
        when (action) {
            Action.MENU -> toggleMenu()
            Action.FORWARD -> viewer?.moveForward()
            Action.BACKWARD -> viewer?.moveBackward()
        }
    }

    @Composable
    fun AppBars(state: ReaderViewModel.State) {
        val isHttpSource = state.source is HttpSource
        val readingMode = ReadingMode.fromPreference(viewModel.getMangaReadingMode())

        val cropBorderPaged by readerPreferences.cropBorders.collectAsState()
        val cropBorderWebtoon by readerPreferences.cropBordersWebtoon.collectAsState()
        val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
        val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon

        val verticalNavigatorModes by readerPreferences.verticalNavigator.collectAsState()
        val verticalNavigator = verticalNavigatorModes.contains(readingMode)
        val verticalNavigatorOnLeft by readerPreferences.verticalNavigatorOnLeft.collectAsState()
        val verticalNavigatorHeight by readerPreferences.verticalNavigatorHeight.collectAsState()

        val pace by paceViewModel.pace.collectAsState()
        val autoScrolling by autoScroll.running.collectAsState()
        val viewer by viewerFlow.collectAsState()
        val timeLeft = pace?.let { timeLeftLabel(it, state.totalPages - state.currentPage + 1) }

        ReaderAppBars(
            visible = state.menuVisible,

            mangaTitle = state.manga?.title,
            chapterTitle = listOfNotNull(state.currentChapter?.chapter?.name, timeLeft).joinToString(" · "),
            navigateUp = onBackPressedDispatcher::onBackPressed,
            onClickTopAppBar = ::openMangaScreen,
            bookmarked = state.bookmarked,
            onToggleBookmarked = viewModel::toggleChapterBookmark,
            onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
            onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
            onShare = ::shareChapter.takeIf { isHttpSource },
            extraActions = readerExtrasActions(extrasViewModel, state),

            chapterNavigatorType = if (!verticalNavigator) {
                if (readingMode == ReadingMode.RIGHT_TO_LEFT) {
                    ChapterNavigatorType.HORIZONTAL_RTL
                } else {
                    ChapterNavigatorType.HORIZONTAL_LTR
                }
            } else {
                if (verticalNavigatorOnLeft) {
                    ChapterNavigatorType.VERTICAL_LEFT
                } else {
                    ChapterNavigatorType.VERTICAL_RIGHT
                }
            },
            verticalNavigatorHeight = verticalNavigatorHeight / 100f,
            onNextChapter = ::loadNextChapter,
            enabledNext = state.viewerChapters?.nextChapter != null,
            onPreviousChapter = ::loadPreviousChapter,
            enabledPrevious = state.viewerChapters?.prevChapter != null,
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageIndexChange = {
                isScrollingThroughPages = true
                moveToPageIndex(it)
            },
            onPageIndexChangeFinished = {
                isScrollingThroughPages = false
            },

            readingMode = ReadingMode.fromPreference(
                viewModel.getMangaReadingMode(resolveDefault = false),
            ),
            onClickReadingMode = viewModel::openReadingModeSelectDialog,
            orientation = ReaderOrientation.fromPreference(
                viewModel.getMangaOrientation(resolveDefault = false),
            ),
            onClickOrientation = viewModel::openOrientationModeSelectDialog,
            cropEnabled = cropEnabled,
            onClickCropBorder = {
                val enabled = viewModel.toggleCropBorders()
                menuToggleToast?.cancel()
                menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
            },
            onClickSettings = viewModel::openSettingsDialog,
            onLongScreenshot = {
                hideMenu()
                captureViewModel.startLongCapture(null, viewer?.visibleSelection(), isLongStrip())
            },
            onStyledScreenshot = {
                hideMenu()
                captureViewModel.styleVisible(null, viewer?.visibleSelection(), isLongStrip())
            },
            onPageAsIs = {
                val page = state.currentChapter?.pages?.getOrNull(state.currentPage - 1)
                if (page != null) viewModel.openPageDialog(page) else toast(MR.strings.screenshot_no_page)
            },
            autoScrolling = autoScrolling.takeIf { viewer is AutoScrollTarget || viewer is AutoPageTarget },
            onToggleAutoScroll = ::toggleAutoScroll,
            onMakeCard = captureViewModel.cardMaker?.let {
                {
                    hideMenu()
                    captureViewModel.makeCardVisible(null, viewer?.visibleSelection(), isLongStrip())
                }
            },
        )
    }

    /** The series a screenshot came from, as the card judge gets it. */
    private fun cardContext(info: ShotInfo): CardSeriesContext? {
        val manga = viewModel.manga ?: return null
        val state = viewModel.state.value
        val chapters = state.viewerChapters
            ?.let { listOfNotNull(it.currChapter, it.prevChapter, it.nextChapter) }
            .orEmpty()
            .map { it.chapter }
        val chapter = chapters.firstOrNull { it.id == info.chapterId } ?: state.currentChapter?.chapter
        return CardSeriesContext(
            seriesTitle = manga.title,
            sourceName = info.source.ifEmpty { state.source?.name.orEmpty() },
            chapterName = chapter?.name ?: info.chapter,
            chapterNumber = chapter?.takeIf { it.isRecognizedNumber }?.chapterNumber,
            genres = CardSeriesContext.genres(manga.genre),
            seriesKey = LeagueHashing.seriesKey(manga.source, manga.url),
        )
    }

    private fun toggleAutoScroll() {
        if (autoScroll.running.value) return autoScroll.pause()
        val mangaId = viewModel.state.value.manga?.id
        val moreToCome = { viewModel.state.value.viewerChapters?.nextChapter != null }
        when (val target = viewer) {
            is AutoScrollTarget -> {
                val level = AutoScrollSpeed.forSeries(
                    AutoScrollSpeed.deserialize(readerPreferences.seriesAutoScrollSpeeds.get()),
                    mangaId,
                    readerPreferences.autoScrollSpeed.get(),
                )
                hideMenu()
                autoScroll.start(target, level, moreToCome)
            }
            is AutoPageTarget -> {
                val seconds = AutoPageInterval.forSeries(
                    AutoPageInterval.deserialize(readerPreferences.seriesAutoPageIntervals.get()),
                    mangaId,
                    readerPreferences.autoPageInterval.get(),
                )
                hideMenu()
                autoScroll.startPaged(target, seconds, moreToCome)
            }
        }
    }

    private fun isLongStrip(): Boolean =
        ReadingMode.fromPreference(viewModel.getMangaReadingMode()).type is ReadingMode.ViewerType.Webtoon

    /**
     * The screenshot screens over the reader: the long screenshot, the styled one over it, and progress while pages
     * are being stitched.
     */
    @Composable
    private fun CaptureScreens() {
        val capture by captureViewModel.state.collectAsState()
        val cardMaker = captureViewModel.cardMaker
        capture.longCapture?.let { request ->
            LongCaptureScreen(
                request = request,
                working = capture.working,
                canMakeCard = cardMaker != null,
                onClose = captureViewModel::closeLongCapture,
                onOutput = { pages, selection, output ->
                    captureViewModel.stitchThen(pages, selection) { bitmap, info ->
                        when (output) {
                            CaptureOutput.SHARE -> captureViewModel.share(bitmap, info)
                            CaptureOutput.SAVE -> captureViewModel.save(bitmap, info)
                            CaptureOutput.STYLE -> captureViewModel.style(bitmap, info)
                            CaptureOutput.CARD -> cardMaker?.onMakeCard(bitmap, info)
                        }
                    }
                },
            )
        }
        capture.styled?.let { request ->
            val layout by captureViewModel.shotLayout.collectAsState()
            StyledShotDialog(
                request = request,
                layout = layout,
                onLayoutChange = captureViewModel.shotLayout::set,
                loadCover = captureViewModel::cover,
                render = captureViewModel::render,
                onDismissRequest = captureViewModel::closeStyled,
                onSave = { captureViewModel.save(it, request.info) },
                onShare = { captureViewModel.share(it, request.info) },
                onMakeCard = cardMaker?.let { maker -> { bitmap -> maker.onMakeCard(bitmap, request.info) } },
            )
        }
        CardMakerDialog(cardMakerViewModel)
        if (capture.working && capture.longCapture == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            autoScroll.pause()
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else if (readerPreferences.fullscreen.get()) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewer
        val readingMode = viewModel.getMangaReadingMode()
        settingsViewModel.setViewerReadingMode(ReadingMode.fromPreference(readingMode))
        val newViewer = ReadingMode.toViewer(readingMode, this)

        setOrientation(viewModel.getMangaOrientation())

        // Destroy previous viewer if there was one
        autoScroll.pause()
        prevViewer?.destroy()
        viewerFlow.value = newViewer

        if (readerPreferences.showReadingMode.get()) {
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        isLoadingViewer = true
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let(::openMangaScreen)
    }

    private fun openMangaScreen(id: Long) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Constants.SHORTCUT_MANGA
                putExtra(Constants.MANGA_EXTRA, id)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(intent)
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (_: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    private fun setChapters(viewerChapters: ViewerChapters) {
        isLoadingViewer = false
        viewer?.setChapters(viewerChapters)

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = this.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage) {
        viewModel.openPageDialog(page)
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launch(Dispatchers.IO) { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(uri: Uri, page: ReaderPage) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number),
        )
        startActivity(intent)
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 the minimum brightness is used, and the overlay darkens further.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness.
     */
    private fun setWindowBrightness(value: Int) {
        // Calculate and set reader brightness.
        val readerBrightness = when {
            value > 0 -> {
                value / 100f
            }
            value < 0 -> {
                0.01f
            }
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = window.attributes.apply { screenBrightness = readerBrightness }
    }
}

private fun viewerColorFilter(grayscale: Boolean, invertedColors: Boolean, enhance: Boolean): ColorFilter? {
    if (!grayscale && !invertedColors && !enhance) return null
    val matrix = ColorMatrix()
    if (enhance) {
        matrix *= PageEnhance.contrast()
    }
    if (invertedColors) {
        matrix *= ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    if (grayscale) {
        matrix *= ColorMatrix().apply { setToSaturation(0f) }
    }
    return ColorFilter.colorMatrix(matrix)
}
