package mihon.feature.autoscroll

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

/**
 * Auto-scroll: the long strip moves on its own with the display's frames. It stops when the strip is touched or the
 * reader leaves, and at the strip's end unless [start]'s `moreToCome` says the next chapter is still loading.
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

    fun start(target: AutoScrollTarget, level: Int, moreToCome: () -> Boolean) {
        pause()
        this.target = target
        this.level = level
        this.moreToCome = moreToCome
        target.onTouchDown = ::pause
        mutableRunning.value = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun pause() {
        if (!mutableRunning.value) return
        mutableRunning.value = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        target?.onTouchDown = null
        target = null
        lastFrameNanos = 0L
        pending = 0f
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
