package mihon.feature.friends.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.feature.friends.data.FriendsApi
import mihon.feature.friends.data.FriendsApiException
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.domain.LeaguePeriods
import tachiyomi.core.common.util.system.logcat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Where a pull is (CARD_SPEC §8). */
sealed interface CardPullPhase {
    data object Idle : CardPullPhase
    data object Uploading : CardPullPhase
    data object Summoning : CardPullPhase
    data class Revealed(val card: ShioriCard) : CardPullPhase

    /** [retry]: whether another crop may help (not after the daily limit). */
    data class Failed(val message: String, val retry: Boolean) : CardPullPhase
}

/** The art and, once made, the custom frame of a card. */
data class CardImages(val art: Bitmap? = null, val customFrame: Bitmap? = null)

/**
 * Shiori Cards: the album, the feed's pulls, today's counts, pulling, and polling custom frames while they're
 * forged. Card art and frames are cached on disk by object path (they never change).
 */
@Inject
@SingleIn(AppScope::class)
class CardRepository(
    private val context: Context,
    @AppCoroutineScope private val scope: CoroutineScope,
    private val api: FriendsApi,
    private val friends: FriendsRepository,
    val pack: CardPack,
) {
    private val requests = CardRequests(api.json)

    val renderer by lazy { CardRenderer(pack) }

    val album: StateFlow<List<ShioriCard>>
        field = MutableStateFlow(emptyList())

    val feed: StateFlow<List<ShioriCard>>
        field = MutableStateFlow(emptyList())

    /** Valid pulls today per player. */
    val pulledToday: StateFlow<Map<String, Int>>
        field = MutableStateFlow(emptyMap())

    val loaded: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val pull: StateFlow<CardPullPhase>
        field = MutableStateFlow<CardPullPhase>(CardPullPhase.Idle)

    /** Cards whose custom frame just landed, for the burst. */
    val forged: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet())

    private val polling = mutableMapOf<String, Job>()

    /** Renders uploaded or uploading this session ("<card id>|<frame path>"). */
    private val renderUploads = mutableSetOf<String>()
    private val loadMutex = Mutex()
    private val memory = object : LruCache<String, Bitmap>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val stills = object : LruCache<String, Bitmap>(STILL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Album thumbnails (CARD_SPEC §13): 300 × 420, rendered once per card look. */
    private val thumbs = object : LruCache<String, Bitmap>(thumbCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thumbDispatcher = Dispatchers.Default.limitedParallelism(THUMB_PARALLELISM)
    private val folder by lazy { File(context.cacheDir, "shiori_cards") }

    val isAvailable: Boolean get() = api.isConfigured && friends.myId != null

    val me: String? get() = friends.myId

    val zone: ZoneId
        get() = friends.snapshot.value?.zone ?: LeaguePeriods.zone(friends.account.value.arena?.timezone)

    fun card(id: String): ShioriCard? =
        album.value.firstOrNull { it.id == id } ?: feed.value.firstOrNull { it.id == id }

    /** Player names for cards: "You" is the caller's job. */
    fun playerName(id: String): String? =
        friends.snapshot.value?.boards?.values?.firstNotNullOfOrNull { rows ->
            rows.firstOrNull { it.userId == id }?.displayName
        }

    suspend fun load(force: Boolean = false) {
        if (!isAvailable) return
        loadMutex.withLock {
            if (loaded.value && !force) return
            try {
                coroutineScope {
                    val all = async { friends.authed { api.send(it, requests.cards(owner = null)) } }
                    val pulls = async { friends.authed { api.send(it, requests.feed()) } }
                    album.value = requests.decodeCards(all.await())
                    feed.value = requests.decodeCards(pulls.await())
                }
                loaded.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Loading cards failed" }
                return
            }
        }
        me?.let { loadPulledToday(it) }
        resumeWatching()
        catchUpRenders()
    }

    suspend fun loadPulledToday(userId: String) {
        if (!isAvailable) return
        try {
            val day = CardAlbum.day(Instant.now(), zone)
            val count = requests.decodePullsToday(friends.authed { api.send(it, requests.pullsToday(userId, day)) })
            pulledToday.update { it + (userId to count) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Loading today's pulls failed" }
        }
    }

    /** The art and (when ready) the custom frame of [card]. */
    suspend fun images(card: ShioriCard): CardImages = coroutineScope {
        val art = async { image(CardRequests.ART_BUCKET, card.artPath) }
        val frame = card.frameImagePath?.let { image(CardRequests.FRAME_BUCKET, it) }
        CardImages(art.await(), frame)
    }

    /**
     * [card] drawn still, [width] pixels wide (thumbnails, and the live view with animations off), without the card
     * number (the view draws it over the shine). With [animated]
     * (the detail view or the reveal under Reduce Motion) an animated card edit shows its GIF's first frame.
     */
    suspend fun still(card: ShioriCard, width: Int, animated: Boolean = false): Bitmap? {
        val animation = if (animated) animation(card) else null
        val key = "${card.id}|${card.frameImagePath}|$width|${animation != null}"
        stills.get(key)?.let { return it }
        val images = images(card)
        val bitmap = withContext(Dispatchers.Default) {
            renderer.render(
                CardFace(card, zone),
                images.art,
                images.customFrame,
                width,
                animationFrame = animation?.firstFrame,
                include = onScreen,
            )
        } ?: return null
        if (images.art != null || images.customFrame != null) stills.put(key, bitmap)
        return bitmap
    }

    /** On screen the card number is drawn by the view, over the shine ([CardNumberOverlay]). */
    private val onScreen: (CardLayer) -> Boolean = { it != CardLayer.Metadata }

    /** The album thumbnail's cache key: `card id | frame | frame_anim | name`. */
    fun thumbnailKey(card: ShioriCard): String =
        "${card.id}|${card.frameImagePath}|${card.frameAnimPath}|${card.cardName}"

    /** The thumbnail if it's already rendered (no work, safe on the main thread). */
    fun cachedThumbnail(card: ShioriCard): Bitmap? = thumbs.get(thumbnailKey(card))

    /**
     * [card] still at 300 × 420 (no animation, holo at the export tilt; an AI card its still image), rendered off the
     * main thread a couple at a time and kept in memory.
     */
    suspend fun thumbnail(card: ShioriCard): Bitmap? {
        val key = thumbnailKey(card)
        thumbs.get(key)?.let { return it }
        // AI cards and border frames don't draw the art
        val frame = card.frameImagePath?.let { image(CardRequests.FRAME_BUCKET, it) }
        val art = if (card.frameImagePath == null) image(CardRequests.ART_BUCKET, card.artPath) else null
        val bitmap = withContext(thumbDispatcher) {
            renderer.render(CardFace(card, zone), art, frame, THUMB_WIDTH, include = onScreen)
        } ?: return null
        if (art != null || frame != null) thumbs.put(key, bitmap)
        return bitmap
    }

    /** The back's fonts: bundled Inter for the small lines, the system serif (Noto Serif CJK) for 栞. */
    val backFonts: CardBackFonts by lazy { CardBackFonts(pack.supportTypeface) }

    /** The app-drawn v1.2 back of [card] (export / print), [width] pixels wide, 5:7. */
    suspend fun back(card: ShioriCard, width: Int = CardSpec.WIDTH.toInt()): Bitmap =
        withContext(Dispatchers.Default) { CardBackRenderer.render(CardBackFace(card), backFonts, width) }

    /** The live view's cached layers of [card] at [width] pixels. */
    suspend fun live(card: ShioriCard, width: Int): CardLiveLayers? {
        val images = images(card)
        val animation = animation(card)
        return withContext(Dispatchers.Default) {
            renderer.live(CardFace(card, zone), images.art, images.customFrame, width, animation)
        }
    }

    /** An animated card edit's GIF, decoded fresh (each view plays its own); null when missing or undecodable. */
    private suspend fun animation(card: ShioriCard): CardFrameAnimation? {
        val path = card.frameAnimationPath ?: return null
        if (!CardFrameAnimation.canPlay(android.os.Build.VERSION.SDK_INT)) return null
        val bytes = bytes(CardRequests.FRAME_BUCKET, path)
        return withContext(Dispatchers.Default) { CardFrameAnimation.decode(bytes) }
    }

    private suspend fun image(bucket: String, path: String): Bitmap? {
        if (path.isEmpty()) return null
        val key = "$bucket/$path"
        memory.get(key)?.let { return it }
        return bytes(bucket, path)?.let {
            withContext(Dispatchers.Default) { decode(it) }
        }?.also { memory.put(key, it) }
    }

    /** An object's bytes: from the disk cache, else downloaded (and kept). */
    private suspend fun bytes(bucket: String, path: String): ByteArray? {
        val key = "$bucket/$path"
        return withContext(Dispatchers.IO) {
            val file = File(folder, hash(key))
            val bytes = file.takeIf { it.exists() }?.readBytes() ?: try {
                friends.authed { api.download(it, requests.image(bucket, path)) }.also { store(file, it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Card image $key failed" }
                null
            }
            bytes
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
    )

    private fun store(file: File, bytes: ByteArray) {
        try {
            folder.mkdirs()
            file.writeBytes(bytes)
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Caching a card image failed" }
        }
    }

    fun resetPull() {
        pull.value = CardPullPhase.Idle
    }

    /**
     * Uploads the crop and pulls a card from it, named [name] when given (else the AI names it); the phase says how
     * it went.
     */
    fun pullCard(jpeg: ByteArray, series: CardSeriesContext, name: String? = null) {
        val me = me ?: run {
            pull.value = CardPullPhase.Failed("Join the Reading Arena to collect cards.", retry = false)
            return
        }
        pull.value = CardPullPhase.Uploading
        scope.launch {
            try {
                val path = requests.artPath(me)
                friends.authed { api.uploadJpeg(it, requests.uploadArtPath(path), jpeg) }
                withContext(Dispatchers.IO) { store(File(folder, hash("${CardRequests.ART_BUCKET}/$path")), jpeg) }
                pull.value = CardPullPhase.Summoning
                val call = requests.pull(CardPullRequest(path, series, name))
                val result = try {
                    requests.decodePull(friends.authed { api.send(it, call, slow = true) })
                } catch (e: FriendsApiException) {
                    throw CardRequests.pullError(e.code, e.message.orEmpty())
                }
                when (result) {
                    is CardPullResult.Pulled -> onPulled(result, me)
                    is CardPullResult.Invalid -> {
                        pulledToday.update { it + (me to result.pullsToday) }
                        pull.value = CardPullPhase.Failed(result.reason, retry = true)
                    }
                }
            } catch (e: CardPullError.DailyLimit) {
                pulledToday.update { it + (me to e.used) }
                pull.value = CardPullPhase.Failed(e.message.orEmpty(), retry = false)
            } catch (e: CardPullError) {
                pull.value = CardPullPhase.Failed(e.message.orEmpty(), retry = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Pulling a card failed" }
                pull.value = CardPullPhase.Failed(friendly(e), retry = true)
            }
        }
    }

    private fun onPulled(result: CardPullResult.Pulled, me: String) {
        val card = result.card
        album.update { listOf(card) + it.filterNot { old -> old.id == card.id } }
        if (card.grade.isFeedWorthy) feed.update { listOf(card) + it }
        pulledToday.update { it + (me to result.pullsToday) }
        pull.value = CardPullPhase.Revealed(card)
        // The finished card for the Discord post (which waits up to 90 s for it)
        scope.launch { uploadRender(card) }
        if (result.startFrame) startFrame(card.id)
        watchFrame(card)
    }

    private fun friendly(e: Exception): String = when {
        e is java.io.InterruptedIOException -> "The summoning took too long. Try again in a minute."
        e is FriendsApiException -> e.message.orEmpty()
        else -> "Something went wrong summoning the card. Try again."
    }

    /** Asks the server to paint the frame (fire and forget; it answers once it's done). */
    private fun startFrame(cardId: String) {
        scope.launch {
            try {
                friends.authed { api.send(it, requests.startFrame(cardId), slow = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Starting a card frame failed" }
            }
        }
    }

    /** Polls a pending card every 5 s for up to 2 minutes until its frame is ready or failed. */
    fun watchFrame(card: ShioriCard) {
        if (card.frameStatus != CardFrameStatus.PENDING || !isAvailable) return
        synchronized(polling) {
            if (polling[card.id]?.isActive == true) return
            polling[card.id] = scope.launch {
                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < CardRules.POLL_LIMIT_MS) {
                    delay(CardRules.POLL_INTERVAL_MS)
                    val fresh = try {
                        requests.decodeCards(friends.authed { api.send(it, requests.card(card.id)) }).firstOrNull()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    } ?: continue
                    if (fresh.frameStatus != CardFrameStatus.PENDING) {
                        update(fresh)
                        return@launch
                    }
                }
            }
        }
    }

    /** Back in the foreground (or on a reload): look at my pending cards again. */
    fun resumeWatching() {
        val me = me ?: return
        album.value.filter { it.owner == me && it.frameStatus == CardFrameStatus.PENDING }.forEach(::watchFrame)
    }

    /**
     * Opening a card of mine still pending after polling stopped (or older than 5 minutes) asks the server to run
     * the frame again; it only does when the last run's lease has expired.
     */
    fun reviveFrame(card: ShioriCard) {
        if (card.owner != me || !CardAlbum.isStalePending(card, Instant.now())) return
        if (synchronized(polling) { polling[card.id]?.isActive == true }) return
        startFrame(card.id)
        watchFrame(card)
    }

    private suspend fun update(card: ShioriCard) {
        album.update { list -> list.map { if (it.id == card.id) card else it } }
        feed.update { list -> list.map { if (it.id == card.id) card else it } }
        pull.update { if (it is CardPullPhase.Revealed && it.card.id == card.id) CardPullPhase.Revealed(card) else it }
        if (card.frameStatus == CardFrameStatus.READY) {
            images(card)
            forged.update { it + card.id }
            // Re-render with the custom frame: Discord swaps the card's image
            uploadRender(card)
        }
    }

    /**
     * Renders one of my cards and uploads it for Discord (backend README v7 "Rendered cards"): after the reveal, when
     * its frame is ready, and in the album catch-up. Best effort: a failure is logged and tried again by the next
     * trigger. Returns whether a render was uploaded.
     */
    suspend fun uploadRender(card: ShioriCard): Boolean {
        val me = me ?: return false
        if (card.owner != me || !isAvailable) return false
        val key = "${card.id}|${card.frameImagePath ?: "-"}"
        if (!synchronized(renderUploads) { renderUploads.add(key) }) return false
        var keep = false
        try {
            val images = images(card)
            // Without the art (or a ready frame that didn't download) the render would be wrong
            if (images.art == null || (card.frameImagePath != null && images.customFrame == null)) return false
            val png = withContext(Dispatchers.Default) { renderPng(CardFace(card, zone), images) } ?: return false
            // The frame got ready while this pack-frame render was drawn: the framed one wins
            if (card.frameImagePath == null && this.card(card.id)?.frameImagePath != null) {
                keep = true
                return false
            }
            val path = requests.renderPath(me, card.id)
            friends.authed { api.uploadPng(it, requests.uploadRenderPath(path), png) }
            friends.authed { api.send(it, requests.recordRender(card.id, path)) }
            val now = Instant.now().toString()
            fun withRender(list: List<ShioriCard>) =
                list.map { if (it.id == card.id) it.copy(renderPath = path, renderUpdatedAt = now) else it }
            album.update(::withRender)
            feed.update(::withRender)
            keep = true
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Uploading a card render failed" }
            return false
        } finally {
            if (!keep) synchronized(renderUploads) { renderUploads.remove(key) }
        }
    }

    /** The export render (holo at the export tilt) at 1080 px, smaller when the PNG would be too big. */
    private fun renderPng(face: CardFace, images: CardImages): ByteArray? {
        for (width in CardRules.RENDER_WIDTHS) {
            val bitmap = renderer.render(face, images.art, images.customFrame, width) ?: return null
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            bitmap.recycle()
            if (bytes.size <= CardRules.RENDER_MAX_BYTES) return bytes
        }
        return null
    }

    /** My cards whose render is older than their frame, one after another. */
    private fun catchUpRenders() {
        val me = me ?: return
        val stale = album.value.filter { it.owner == me && CardAlbum.renderIsStale(it) }
        if (stale.isEmpty()) return
        scope.launch { stale.forEach { uploadRender(it) } }
    }

    suspend fun toggleFavorite(card: ShioriCard) {
        if (card.owner != me) return
        val next = !card.favorite
        album.update { list -> list.map { if (it.id == card.id) it.copy(favorite = next) else it } }
        try {
            friends.authed { api.send(it, requests.favorite(card.id, next)) }
        } catch (e: Exception) {
            album.update { list -> list.map { if (it.id == card.id) it.copy(favorite = card.favorite) else it } }
            throw e
        }
    }

    suspend fun delete(card: ShioriCard) {
        val me = me ?: return
        if (card.owner != me) return
        val (row, art) = requests.delete(card, me)
        friends.authed { api.send(it, row) }
        // A failure here only leaves an orphaned file
        try {
            friends.authed { api.send(it, art) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Deleting card art failed" }
        }
        album.update { list -> list.filterNot { it.id == card.id } }
        feed.update { list -> list.filterNot { it.id == card.id } }
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IMAGE_CACHE_BYTES = 48 * 1024 * 1024
        const val STILL_CACHE_BYTES = 32 * 1024 * 1024
        const val THUMB_WIDTH = 300
        const val THUMB_PARALLELISM = 2

        /** 300 × 420 × 4 bytes each: up to 64 MB (≈ 130 thumbnails), less on small heaps. */
        fun thumbCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()
    }
}
