package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.feature.friends.data.FriendsPreferences
import tachiyomi.core.common.util.system.logcat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Card screens' actions: favourite, delete, the front and back PNGs (gallery or share) and the print sheet. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class CardsViewModel(
    val repository: CardRepository,
    val preferences: FriendsPreferences,
    private val imageSaver: ImageSaver,
) : ViewModel() {

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val refreshing: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            refreshing.value = force
            try {
                repository.load(force)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun loadPulledToday(userId: String) {
        viewModelScope.launch { repository.loadPulledToday(userId) }
    }

    fun toggleFavorite(card: ShioriCard) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(card)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun delete(card: ShioriCard, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(card)
                onDeleted()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Save front: the 1500 × 2100 front PNG to the gallery (AI cards: the AI image). */
    fun saveFront(card: ShioriCard) {
        viewModelScope.launch {
            try {
                val bytes = png(front(card))
                withContext(Dispatchers.IO) {
                    imageSaver.save(
                        page(bytes, "${card.cardName} - ${card.serialText}", Location.Pictures.create(FOLDER)),
                    )
                }
                _events.send(Event.Saved)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Save back: the app-drawn 1500 × 2100 back PNG to the gallery. */
    fun saveBack(card: ShioriCard) {
        viewModelScope.launch {
            try {
                val bytes = png(repository.back(card))
                withContext(Dispatchers.IO) {
                    imageSaver.save(
                        page(bytes, "${card.cardName} - ${card.serialText} - back", Location.Pictures.create(FOLDER)),
                    )
                }
                _events.send(Event.Saved)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Share (front): the front PNG through the share sheet. */
    fun share(card: ShioriCard) {
        viewModelScope.launch {
            try {
                sharePng(png(front(card)), "${card.cardName} - ${card.serialText}", card)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Share the original panel: the screenshot an AI card was drawn from. */
    fun shareOriginal(card: ShioriCard, panel: Bitmap) {
        viewModelScope.launch {
            try {
                sharePng(png(panel), "${card.cardName} - ${card.serialText} - original", card)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private suspend fun sharePng(bytes: ByteArray, name: String, card: ShioriCard) {
        val uri = withContext(Dispatchers.IO) {
            // Only the last shared image is kept
            imageSaver.context.cacheImageDir.deleteRecursively()
            imageSaver.save(page(bytes, name, Location.Cache))
        }
        _events.send(Event.Share(uri, "${card.cardName} · ${card.grade.label}", "image/png"))
    }

    /** Print sheet: front and back at 63 × 88 mm on one PDF page, shared. */
    fun printSheet(card: ShioriCard) {
        viewModelScope.launch {
            try {
                val front = front(card)
                val back = repository.back(card)
                val context = imageSaver.context
                val uri = withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, PRINT_FOLDER)
                    // Only the last sheet is kept
                    folder.deleteRecursively()
                    folder.mkdirs()
                    val name = DiskUtil.buildValidFilename(CardPrintSheet.fileName(card.cardName, card.serialText))
                    val file = File(folder, "$name.pdf")
                    file.outputStream().use { output ->
                        CardPrintPdf.write(front, back, CardPrintSheet.caption(card.cardName, card.serialText), output)
                    }
                    file.getUriCompat(context)
                }
                _events.send(
                    Event.Share(uri, CardPrintSheet.caption(card.cardName, card.serialText), "application/pdf"),
                )
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private suspend fun front(card: ShioriCard): Bitmap {
        val images = repository.images(card)
        return withContext(Dispatchers.Default) {
            repository.renderer.export(CardFace(card, repository.zone), images.art, images.customFrame)
        } ?: error("The card pack is missing")
    }

    private suspend fun png(bitmap: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun page(bytes: ByteArray, name: String, location: Location): Image = Image.Page(
        inputStream = { ByteArrayInputStream(bytes) },
        name = DiskUtil.buildValidFilename(name),
        location = location,
    )

    private suspend fun fail(e: Exception) {
        if (e is CancellationException) throw e
        logcat(LogPriority.ERROR, e)
        _events.send(Event.Error(e.message ?: e.javaClass.simpleName))
    }

    sealed interface Event {
        data object Saved : Event
        data class Share(val uri: Uri, val text: String, val type: String) : Event
        data class Error(val message: String) : Event
    }

    private companion object {
        const val FOLDER = "Shiori Cards"
        const val PRINT_FOLDER = "shiori_print"
    }
}
