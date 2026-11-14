package mihon.feature.autoscroll

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A long strip the reader's auto-scroll can move. */
interface AutoScrollTarget {
    /** How far the strip is zoomed in, 1 when not. */
    val zoom: Float

    /** Scrolls the strip down by [px]; false when it is already at its end. */
    fun autoScrollBy(px: Int): Boolean

    /** Called whenever a finger lands on the strip. */
    var onTouchDown: (() -> Unit)?
}

/** A paged viewer the reader's auto page-turn can move. */
interface AutoPageTarget {
    /** Turns to the next page; false when there is none (yet). */
    fun autoTurnPage(): Boolean

    /** Called whenever a finger lands on the pager. */
    var onTouchDown: (() -> Unit)?
}

/**
 * Auto-scroll: the long strip moves on its own with the display's frames. It stops when the strip is touched or the
 * reader leaves, and at the strip's end unless [start]'s `moreToCome` says the next chapter is still loading.
 * In the paged reader ([startPaged]) it turns the page every few seconds instead, stopping the same way.
 */
class ReaderAutoScroll(private val density: Float) {

    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    private var target: AutoScrollTarget? = null
    private var level = AutoScrollSpeed.DEFAULT_LEVEL
    private var moreToCome: () -> Boolean = { false }
    private var lastFrameNanos = 0L
    private var pending = 0f

    private val frameCallback = Choreographer.FrameCallback(::onFrame)

    private var pageTarget: AutoPageTarget? = null
    private var pageMillis = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val turnPage = Runnable(::onTurnPage)

    fun start(target: AutoScrollTarget, level: Int, moreToCome: () -> Boolean) {
        pause()
        this.target = target
        this.level = level
        this.moreToCome = moreToCome
        target.onTouchDown = ::pause
        mutableRunning.value = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun startPaged(target: AutoPageTarget, seconds: Int, moreToCome: () -> Boolean) {
        pause()
        pageTarget = target
        pageMillis = seconds * 1000L
        this.moreToCome = moreToCome
        target.onTouchDown = ::pause
        mutableRunning.value = true
        handler.postDelayed(turnPage, pageMillis)
    }

    fun pause() {
        if (!mutableRunning.value) return
        mutableRunning.value = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        handler.removeCallbacks(turnPage)
        target?.onTouchDown = null
        target = null
        pageTarget?.onTouchDown = null
        pageTarget = null
        lastFrameNanos = 0L
        pending = 0f
    }

    private fun onTurnPage() {
        val target = pageTarget ?: return
        if (!target.autoTurnPage() && !moreToCome()) return pause()
        handler.postDelayed(turnPage, pageMillis)
    }

    private fun onFrame(frameNanos: Long) {
        val target = target ?: return
        val last = lastFrameNanos
        lastFrameNanos = frameNanos
        if (last != 0L) {
            // A long gap (a hitch, the app coming back) doesn't jump the strip.
            val seconds = ((frameNanos - last) / 1e9f).coerceAtMost(0.1f)
            pending += AutoScrollSpeed.distance(level, seconds, density, target.zoom)
            val (step, rest) = AutoScrollSpeed.step(pending)
            pending = rest
            if (step > 0 && !target.autoScrollBy(step)) {
                pending = 0f
                if (!moreToCome()) {
                    pause()
                    return
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }
}
