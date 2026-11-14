package eu.kanade.tachiyomi.ui.reader.capture

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The reader's screenshots: long screenshots over the strip, styled screenshots of what is on screen, saved to the
 * gallery or shared.
 */
class ReaderCaptureViewModel(
    private val application: Application,
    private val reader: ReaderViewModel,
    private val imageSaver: ImageSaver,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val events = eventChannel.receiveAsFlow()

    private val renderer by lazy { ShotRenderer(application) }

    /** Takes a finished screenshot to "Make a card"; the screenshot screens offer it only once one is set. */
    var cardMaker: ScreenshotCardMaker? = null

    /**
     * Opens the long screenshot screen over the current chapter: from [page]'s top when given, else over [visible]
     * (what a long strip shows), else over the current page.
     */
    fun startLongCapture(page: ReaderPage?, visible: StripSelection?, isLongStrip: Boolean) {
        val region = region(page, visible, isLongStrip) ?: return toast(MR.strings.screenshot_no_page)
        mutableState.update {
            it.copy(longCapture = LongCaptureRequest(region.pages, region.selection, info(region.pages)))
        }
    }

    /** The styled screenshot of what is on screen, or of [page], whole. */
    fun styleVisible(page: ReaderPage?, visible: StripSelection?, isLongStrip: Boolean) {
        val region = region(page, visible, isLongStrip) ?: return toast(MR.strings.screenshot_no_page)
        stitchThen(CapturePages(region.pages), region.selection) { bitmap, info ->
            mutableState.update { it.copy(styled = StyledShotRequest(bitmap, info)) }
        }
    }

    /** "Make a card" (Shiori Cards) from what is on screen, like [styleVisible]. */
    fun makeCardVisible(page: ReaderPage?, visible: StripSelection?, isLongStrip: Boolean) {
        val maker = cardMaker ?: return
        val region = region(page, visible, isLongStrip) ?: return toast(MR.strings.screenshot_no_page)
        stitchThen(CapturePages(region.pages), region.selection, maker::onMakeCard)
    }

    /** Stitches [selection] of [pages] and hands the image and its label to [onStitched]. */
    fun stitchThen(pages: CapturePages, selection: StripSelection, onStitched: (Bitmap, ShotInfo) -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                pages.load(selection.pages)
                val plan = StitchPlan.make(selection, pages.sizeList())
                val bitmap = plan?.let { pages.stitch(it) }
                if (plan == null || bitmap == null) {
                    toast(MR.strings.screenshot_not_loaded)
                } else {
                    val shown = shownPages(plan.slices) { pages.pages.getOrNull(it)?.number }
                    onStitched(bitmap, info(pages.pages, shown))
                }
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }

    /** Opens the styled screenshot screen over whatever is open. */
    fun style(bitmap: Bitmap, info: ShotInfo) {
        mutableState.update { it.copy(styled = StyledShotRequest(bitmap, info)) }
    }

    fun closeLongCapture() {
        mutableState.update { it.copy(longCapture = null) }
    }

    fun closeStyled() {
        mutableState.update { it.copy(styled = null) }
    }

    /** The styled screenshot's label layout. */
    val shotLayout = readerPreferences.screenshotLayout

    /** [image] with its label, off the main thread. */
    suspend fun render(image: Bitmap, info: ShotInfo, cover: Bitmap?, layout: ShotLayout, accent: Int): Bitmap =
        withContext(Dispatchers.Default) { renderer.render(image, info, cover, layout, accent) }

    /** The series' cover for a styled screenshot's label. */
    suspend fun cover(): Bitmap? {
        val manga = reader.manga ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(application)
                    .data(manga)
                    .size(Size(COVER_SIZE, COVER_SIZE * 3 / 2))
                    .allowHardware(false)
                    .build()
                application.imageLoader.execute(request).image?.toBitmap()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e)
                null
            }
        }
    }

    /** Saves [bitmap] to the gallery, in the series' folder when the reader keeps one per series. */
    fun save(bitmap: Bitmap, info: ShotInfo) {
        viewModelScope.launch {
            try {
                val folder = if (readerPreferences.folderPerManga.get()) DiskUtil.buildValidFilename(info.title) else ""
                withContext(Dispatchers.IO) {
                    imageSaver.save(image(bitmap, info, Location.Pictures.create(folder)))
                }
                toast(MR.strings.picture_saved)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
                toast(MR.strings.error_saving_picture)
            }
        }
    }

    /** Opens the share sheet with [bitmap] as a file. */
    fun share(bitmap: Bitmap, info: ShotInfo) {
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    // Only the last shared image is kept
                    application.cacheImageDir.deleteRecursively()
                    imageSaver.save(image(bitmap, info, Location.Cache))
                }
                val text = listOf(info.title, info.chapter, info.pages).filter { it.isNotEmpty() }.joinToString(" · ")
                eventChannel.send(Event.Share(uri, text))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun image(bitmap: Bitmap, info: ShotInfo, location: Location): Image {
        val bytes = ByteArrayOutputStream().use { output ->
            if (encodesAsPng(bitmap.width, bitmap.height)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            output.toByteArray()
        }
        val name = listOf(info.title, info.chapter, info.pages).filter { it.isNotEmpty() }.joinToString(" - ")
        return Image.Page(
            inputStream = { ByteArrayInputStream(bytes) },
            name = DiskUtil.buildValidFilename(name.ifEmpty { "Screenshot" }),
            location = location,
        )
    }

    private class Region(val pages: List<ReaderPage>, val selection: StripSelection)

    /**
     * The current chapter's pages and the part of them to capture: in a long strip, from [page]'s top or what
     * [visible] says is on screen; in paged modes, the whole page.
     */
    private fun region(page: ReaderPage?, visible: StripSelection?, isLongStrip: Boolean): Region? {
        val state = reader.state.value
        val current = page ?: state.currentChapter?.pages?.getOrNull(state.currentPage - 1) ?: return null
        val pages = current.chapter.pages ?: return null
        if (!isLongStrip) return Region(listOf(current), StripSelection.whole(0))
        val index = pages.indexOf(current).takeIf { it >= 0 } ?: return null
        val selection = visible.takeIf { page == null } ?: StripSelection.whole(index)
        return Region(pages, selection)
    }

    /** The label for a screenshot of [pages], showing [shown] when known. */
    fun info(pages: List<ReaderPage>, shown: IntRange? = null): ShotInfo {
        val state = reader.state.value
        val chapter = pages.firstOrNull()?.chapter?.chapter ?: state.currentChapter?.chapter
        val total = pages.firstOrNull()?.chapter?.pages?.size ?: 0
        val pagesLabel = shown?.let { pagesLabel(it, total) }.orEmpty()
        return ShotInfo(
            mangaId = state.manga?.id,
            chapterId = chapter?.id,
            title = state.manga?.title.orEmpty(),
            chapter = chapter?.name.orEmpty(),
            pages = pagesLabel,
            source = state.source?.name.orEmpty(),
        )
    }

    /** "Page 5 of 40", "Pages 5–7 of 40". */
    fun pagesLabel(shown: IntRange, total: Int): String {
        val span = if (shown.first == shown.last) {
            application.stringResource(MR.strings.screenshot_page, shown.first)
        } else {
            application.stringResource(MR.strings.screenshot_pages, shown.first, shown.last)
        }
        return if (total > 0) application.stringResource(MR.strings.screenshot_of_total, span, total) else span
    }

    private fun toast(resource: StringResource) {
        viewModelScope.launch { eventChannel.send(Event.Toast(resource)) }
    }

    /** The styled screenshot screen opens over the long screenshot one, which stays open behind it. */
    @Immutable
    data class State(
        val longCapture: LongCaptureRequest? = null,
        val styled: StyledShotRequest? = null,
        val working: Boolean = false,
    )

    sealed interface Event {
        data class Toast(val resource: StringResource) : Event
        data class Share(val uri: Uri, val text: String) : Event
    }
}

private const val COVER_SIZE = 160
