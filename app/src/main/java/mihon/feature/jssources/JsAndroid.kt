package mihon.feature.jssources

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.edit
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Settings in the `source_<id>` preferences, where Mihon's source settings screen and backups keep them. */
internal class SharedPreferencesJsStore(private val preferences: SharedPreferences) : JsPreferenceStore {
    override fun get(key: String): Any? = preferences.all[key]

    override fun set(key: String, value: Any?) = preferences.edit {
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
            else -> remove(key)
        }
    }
}

/** Redraws scrambled images with a Canvas, as upstream's interceptors do. The result is a PNG, as on iOS. */
internal object AndroidJsImageTools : JsImageTools {
    override fun size(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    override fun apply(bytes: ByteArray, transform: JsImageTransform): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val output = Bitmap.createBitmap(transform.width.toInt(), transform.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        transform.tiles.forEach { tile ->
            val from = Rect(tile.sx.toInt(), tile.sy.toInt(), (tile.sx + tile.sw).toInt(), (tile.sy + tile.sh).toInt())
            val to = RectF(
                tile.dx.toFloat(),
                tile.dy.toFloat(),
                (tile.dx + (tile.dw ?: tile.sw)).toFloat(),
                (tile.dy + (tile.dh ?: tile.sh)).toFloat(),
            )
            canvas.drawBitmap(source, from, to, null)
        }
        source.recycle()
        return ByteArrayOutputStream().use {
            output.compress(Bitmap.CompressFormat.PNG, 100, it)
            output.recycle()
            it.toByteArray()
        }
    }
}

/**
 * Runs a page in a hidden WebView for a script, as the iOS host does with WKWebView. The WebView shares the app's
 * cookies, so a check the user passed in the in-app browser counts here too. A hidden run that lands on a Cloudflare
 * check stops instead of working on it, so the app can offer the WebView and the user passes it knowingly. Runs asking
 * to be shown (`visible`) also stay hidden: none of the sources needs one.
 */
internal class AndroidJsWebViewRunner(
    private val context: Context,
    private val userAgent: () -> String,
) : JsWebViewRunner {

    override suspend fun run(request: JsWebViewRequest): JsWebViewResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val run = Run(request) { result -> continuation.resumeWith(result) }
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { run.cancel() } }
            run.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private inner class Run(
        private val request: JsWebViewRequest,
        private val onFinish: (Result<JsWebViewResult>) -> Unit,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val webView = WebView(context)
        private val payloads = mutableListOf<String>()
        private var loaded = false
        private var finished = false

        fun start() {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = request.userAgent ?: userAgent()
                blockNetworkImage = request.loadImages == false
            }
            webView.addJavascriptInterface(Bridge(), "__tachiyomiNative")
            val bootstrap = BRIDGE + (request.script ?: "")
            val atDocumentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (atDocumentStart) WebViewCompat.addDocumentStartJavaScript(webView, bootstrap, setOf("*"))
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (!atDocumentStart) view.evaluateJavascript(bootstrap, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loaded = true
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    val server = errorResponse.responseHeaders?.entries
                        ?.firstOrNull { it.key.equals("server", ignoreCase = true) }?.value.orEmpty()
                    if (request.isForMainFrame && errorResponse.statusCode in setOf(403, 503) &&
                        server.contains("cloudflare", ignoreCase = true)
                    ) {
                        fail(IOException("Cloudflare check at ${request.url.host}", CloudflareBypassException()))
                    }
                }
            }
            webView.loadUrl(request.url, request.headers.orEmpty())
            handler.postDelayed({ finish(result = null, timedOut = true) }, request.timeoutMs)
            if (request.pollScript != null) handler.postDelayed(::poll, POLL_MS)
        }

        fun cancel() = close()

        private fun poll() {
            if (finished) return
            if (loaded) {
                webView.evaluateJavascript(request.pollScript!!) { value ->
                    val text = decode(value)
                    if (!text.isNullOrEmpty()) finish(result = text, timedOut = false)
                }
            }
            handler.postDelayed(::poll, POLL_MS)
        }

        private fun finish(result: String?, timedOut: Boolean) {
            if (finished) return
            finished = true
            webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                val outcome = JsWebViewResult(
                    payloads = payloads.toList(),
                    result = result,
                    html = decode(html).orEmpty(),
                    url = webView.url ?: request.url,
                    timedOut = timedOut,
                )
                close()
                onFinish(Result.success(outcome))
            }
        }

        private fun fail(error: Throwable) {
            if (finished) return
            finished = true
            close()
            onFinish(Result.failure(error))
        }

        private fun close() {
            finished = true
            handler.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.destroy()
        }

        /** `evaluateJavascript` hands back the value as JSON. */
        private fun decode(value: String?): String? =
            runCatching { (Json.parseToJsonElement(value ?: "null") as? JsonPrimitive)?.contentOrNull }.getOrNull()

        private inner class Bridge {
            @JavascriptInterface
            fun post(text: String) {
                handler.post { if (!finished) payloads += text }
            }

            @JavascriptInterface
            fun done() {
                handler.post { finish(result = null, timedOut = false) }
            }
        }
    }

    private companion object {
        const val POLL_MS = 500L

        val BRIDGE = """
            window.__tachiyomi = {
              post: function (text) { __tachiyomiNative.post(String(text)); },
              done: function () { __tachiyomiNative.done(); }
            };
        """.trimIndent() + "\n"
    }
}
