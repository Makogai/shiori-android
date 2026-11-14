package mihon.feature.readerextras

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.feature.common.utils.seriesKey
import mihon.feature.pagebookmarks.PageBookmark
import mihon.feature.pagebookmarks.PageBookmarks
import mihon.feature.queue.BingeQueue
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.BookmarkAdd
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Closes the reader once the chapter it was set in is left, or at a time. */
sealed interface SleepTimer {
    data class AfterChapter(val chapterId: Long) : SleepTimer

    data class At(val time: Instant) : SleepTimer
}

/** The series the queue offers next, and the chapter to open it at when it has one. */
data class QueueOffer(val manga: Manga, val chapter: Chapter?)

/** Page bookmarks, the sleep timer and the "Next in queue" offer, kept apart from [ReaderViewModel]. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ReaderExtrasViewModel(
    private val pageBookmarks: PageBookmarks,
    private val queue: BingeQueue,
    private val chapterRepository: ChapterRepository,
    private val getNextChapters: GetNextChapters,
) : ViewModel() {

    sealed interface Dialog {
        data class Bookmark(val manga: Manga, val chapter: Chapter, val page: Int, val existing: PageBookmark?) :
            Dialog

        data class Sleep(val current: SleepTimer?, val chapterId: Long) : Dialog
    }

    private val mutableDialog = MutableStateFlow<Dialog?>(null)
    val dialog: StateFlow<Dialog?> = mutableDialog.asStateFlow()

    private val mutableSleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = mutableSleepTimer.asStateFlow()

    private val mutableQueueOffer = MutableStateFlow<QueueOffer?>(null)
    val queueOffer: StateFlow<QueueOffer?> = mutableQueueOffer.asStateFlow()

    /** The series an offer was already looked for, so it is made once per reading session. */
    private var offeredFor: Long? = null

    fun showBookmark(manga: Manga, chapter: Chapter, page: Int) {
        mutableDialog.value = Dialog.Bookmark(manga, chapter, page, pageBookmarks.find(manga, chapter.url, page))
    }

    fun showSleepTimer(chapterId: Long) {
        mutableDialog.value = Dialog.Sleep(sleepTimer.value, chapterId)
    }

    fun dismissDialog() {
        mutableDialog.value = null
    }

    fun saveBookmark(dialog: Dialog.Bookmark, note: String) {
        pageBookmarks.save(
            PageBookmark(
                series = dialog.manga.seriesKey,
                chapterUrl = dialog.chapter.url,
                chapterName = dialog.chapter.name,
                page = dialog.page,
                note = note.trim(),
                createdAt = dialog.existing?.createdAt ?: Clock.System.now().toEpochMilliseconds(),
            ),
        )
        dismissDialog()
    }

    fun removeBookmark(bookmark: PageBookmark) {
        pageBookmarks.remove(bookmark)
        dismissDialog()
    }

    fun setSleepTimer(timer: SleepTimer?) {
        mutableSleepTimer.value = timer
        dismissDialog()
    }

    /** Called on the last page of the last loaded chapter: offers the next queued series once [manga] is done. */
    fun onReachedEnd(manga: Manga, currentChapterId: Long) {
        if (offeredFor == manga.id) return
        offeredFor = manga.id
        viewModelScope.launch {
            val unreadLeft = chapterRepository.getChapterByMangaId(manga.id, applyScanlatorFilter = true)
                .any { !it.read && it.id != currentChapterId }
            if (unreadLeft) return@launch
            val key = manga.seriesKey
            val nextKey = BingeQueue.nextAfter(queue.get(), key) ?: return@launch
            queue.remove(key)
            val next = queue.resolve(listOf(nextKey), emptyList()).firstOrNull() ?: return@launch
            mutableQueueOffer.value = QueueOffer(next, getNextChapters.await(next.id).firstOrNull())
        }
    }

    fun dismissQueueOffer() {
        mutableQueueOffer.value = null
    }
}

/** The reader's top bar overflow entries for these extras. */
@Composable
fun readerExtrasActions(
    viewModel: ReaderExtrasViewModel,
    state: ReaderViewModel.State,
): List<AppBar.AppBarAction> {
    val manga = state.manga ?: return emptyList()
    val chapter = state.currentChapter?.chapter ?: return emptyList()
    val timer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    return listOf(
        AppBar.OverflowAction(
            title = stringResource(MR.strings.page_bookmark_add),
            icon = MaterialSymbols.Rounded.BookmarkAdd,
            onClick = { viewModel.showBookmark(manga, chapter, (state.currentPage - 1).coerceAtLeast(0)) },
        ),
        AppBar.OverflowAction(
            title = if (timer == null) {
                stringResource(MR.strings.sleep_timer)
            } else {
                stringResource(MR.strings.sleep_timer_on)
            },
            icon = MaterialSymbols.Rounded.Schedule,
            onClick = { viewModel.showSleepTimer(chapter.id) },
        ),
    )
}

/** Runs the sleep timer, makes the queue offer and shows the extras' dialogs. */
@Composable
fun ReaderExtrasHost(
    viewModel: ReaderExtrasViewModel,
    state: ReaderViewModel.State,
    onClose: () -> Unit,
    onOpenOffer: (QueueOffer) -> Unit,
) {
    val context = LocalContext.current
    val timer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val offer by viewModel.queueOffer.collectAsStateWithLifecycle()
    val currentChapterId = state.currentChapter?.chapter?.id

    LaunchedEffect(timer, currentChapterId) {
        when (val current = timer) {
            null -> return@LaunchedEffect
            is SleepTimer.AfterChapter -> if (currentChapterId == null || currentChapterId == current.chapterId) {
                return@LaunchedEffect
            }
            is SleepTimer.At -> delay(current.time - Clock.System.now())
        }
        context.toast(MR.strings.sleep_timer_closed)
        onClose()
    }

    val manga = state.manga
    val atEnd = state.totalPages > 0 && state.currentPage == state.totalPages &&
        state.viewerChapters?.nextChapter == null
    LaunchedEffect(atEnd, currentChapterId) {
        if (atEnd && manga != null && currentChapterId != null) viewModel.onReachedEnd(manga, currentChapterId)
    }

    when (val current = dialog) {
        is ReaderExtrasViewModel.Dialog.Bookmark -> PageBookmarkDialog(
            dialog = current,
            onSave = { viewModel.saveBookmark(current, it) },
            onRemove = current.existing?.let { { viewModel.removeBookmark(it) } },
            onDismiss = viewModel::dismissDialog,
        )
        is ReaderExtrasViewModel.Dialog.Sleep -> SleepTimerDialog(
            dialog = current,
            onSet = viewModel::setSleepTimer,
            onDismiss = viewModel::dismissDialog,
        )
        null -> {}
    }

    offer?.let { current ->
        AlertDialog(
            onDismissRequest = viewModel::dismissQueueOffer,
            title = { Text(stringResource(MR.strings.binge_queue_next)) },
            text = { Text(stringResource(MR.strings.binge_queue_next_body, current.manga.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissQueueOffer()
                        onOpenOffer(current)
                    },
                ) {
                    Text(
                        stringResource(
                            if (current.chapter !=
                                null
                            ) {
                                MR.strings.action_resume
                            } else {
                                MR.strings.binge_queue_open
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQueueOffer) { Text(stringResource(MR.strings.action_not_now)) }
            },
        )
    }
}

@Composable
private fun PageBookmarkDialog(
    dialog: ReaderExtrasViewModel.Dialog.Bookmark,
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val note = rememberTextFieldState(dialog.existing?.note.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.page_bookmark_title, dialog.page + 1)) },
        text = {
            OutlinedTextField(
                state = note,
                label = { Text(stringResource(MR.strings.page_bookmark_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.text.toString()) }) { Text(stringResource(MR.strings.action_save)) }
        },
        dismissButton = {
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text(stringResource(MR.strings.action_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
            }
        },
    )
}

private val SLEEP_DURATIONS = listOf(15.minutes, 30.minutes, 60.minutes)

@Composable
private fun SleepTimerDialog(
    dialog: ReaderExtrasViewModel.Dialog.Sleep,
    onSet: (SleepTimer?) -> Unit,
    onDismiss: () -> Unit,
) {
    var afterChapter by remember { mutableStateOf(dialog.current is SleepTimer.AfterChapter) }
    var duration by remember { mutableStateOf<Duration?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.sleep_timer)) },
        text = {
            Column {
                (dialog.current as? SleepTimer.At)?.let { at ->
                    val left = (at.time - Clock.System.now()).inWholeMinutes.coerceAtLeast(1)
                    Text(stringResource(MR.strings.sleep_timer_left, left))
                }
                LabeledCheckbox(
                    label = stringResource(MR.strings.sleep_timer_after_chapter),
                    checked = afterChapter,
                    onCheckedChange = {
                        afterChapter = it
                        if (it) duration = null
                    },
                )
                SLEEP_DURATIONS.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = duration == option,
                            onClick = {
                                duration = option
                                afterChapter = false
                            },
                        )
                        Text(stringResource(MR.strings.sleep_timer_minutes, option.inWholeMinutes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = afterChapter || duration != null,
                onClick = {
                    val timer = if (afterChapter) {
                        SleepTimer.AfterChapter(dialog.chapterId)
                    } else {
                        duration?.let { SleepTimer.At(Clock.System.now() + it) }
                    }
                    onSet(timer)
                },
            ) { Text(stringResource(MR.strings.action_start)) }
        },
        dismissButton = {
            if (dialog.current != null) {
                TextButton(onClick = { onSet(null) }) { Text(stringResource(MR.strings.sleep_timer_off)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
            }
        },
    )
}
