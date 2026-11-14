package mihon.feature.jssources

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.define
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** What the host needs from the app. Tests pass a client that answers from fixtures and in-memory settings. */
class JsEnvironment(
    val client: OkHttpClient,
    val preferences: (sourceId: Long) -> JsPreferenceStore,
    val scope: CoroutineScope,
    val acceptLanguage: String,
    val webView: JsWebViewRunner? = null,
    val imageTools: JsImageTools? = null,
    val rateLimitsEnabled: Boolean = true,
    val log: (level: String, message: String) -> Unit = { _, _ -> },
)

fun interface JsWebViewRunner {
    suspend fun run(request: JsWebViewRequest): JsWebViewResult
}

/** A source's stored settings: Boolean, String or Set<String> values. */
interface JsPreferenceStore {
    fun get(key: String): Any?
    fun set(key: String, value: Any?)
}

/** Reads an image's size and redraws it from tiles (API level 2). */
interface JsImageTools {
    fun size(bytes: ByteArray): Pair<Int, Int>?
    fun apply(bytes: ByteArray, transform: JsImageTransform): ByteArray
}

/** A script's own error, with the message it gave. */
class JsScriptException(message: String) : Exception(message)

class JsLoadException(message: String) : Exception(message)

internal data class JsLoadedSource(
    val index: Int,
    val info: JsSourceInfo,
    val id: Long,
    val rateLimiters: List<JsRateLimiter>,
    val headers: Map<String, String>,
    val preferences: JsPreferenceStore,
)

/**
 * One loaded extension script and the QuickJS runtime it runs in, the Android twin of the iOS `ExtensionContext`.
 *
 * The bundle defines `__extension.default` (`api`, `sources`, `describe`, `invoke`, `rateLimitFor`, `cookiesFor`) and
 * calls `__host`, which a small shim builds on top of `__native` bindings: QuickJS can't hand Kotlin objects or
 * functions to the script, so HTML elements are integer handles and callbacks wait in the shim until Kotlin delivers
 * their result. Every evaluation is short; nothing waits on the network while holding the runtime.
 */
class JsExtensionRuntime private constructor(
    val name: String,
    private val environment: JsEnvironment,
) : Closeable {

    private val quickJs: QuickJs
    private val handles = JsHtmlHandles()
    private val lock = Any()
    private val calls = HashMap<Int, CompletableDeferred<String>>()
    private val results = ConcurrentHashMap<Int, List<String?>>()
    private val nextId = AtomicInteger(1)
    private val failures = RecentFailures()

    /** Requests of every source go through the app's client, with the script's cookie rules and rate limits added. */
    val client: OkHttpClient = JsHttp.clientFor(environment.client)

    @Volatile
    internal var sources: List<JsLoadedSource> = emptyList()
        private set

    init {
        JsNative.ensureLoaded()
        quickJs = QuickJs.create(Dispatchers.Default)
    }

    private suspend fun start(script: String) {
        installHostApi()
        evaluate(SHIM, "host.js")
        try {
            evaluate(script, "$name.js")
        } catch (e: QuickJsException) {
            throw JsLoadException(e.message ?: e.toString())
        }
        val api = evaluate(
            "typeof __extension === 'object' && __extension !== null && __extension.default ? __extension.default.api : null",
        ) as? Number ?: throw JsLoadException("The script does not define __extension.default")
        if (api.toInt() > JS_HOST_API_LEVEL) throw JsUnsupportedApiException(api.toInt())
        val count = (evaluate("__extension.default.sources.length") as Number).toInt()
        val described = (0..<count).map { index ->
            val json = evaluateString("__extension.default.describe($index)")
            val info = try {
                jsJson.decodeFromString<JsSourceInfo>(json)
            } catch (e: Exception) {
                throw JsLoadException("Source $index has an invalid description: ${e.message}")
            }
            val id = info.id?.let { it.toLongOrNull() ?: throw JsLoadException("Invalid source ID $it") }
                ?: jsSourceId(info.name, info.lang, info.versionId ?: 1)
            val limiters = if (environment.rateLimitsEnabled) {
                info.client.rateLimits.map { JsRateLimiter(it.permits, it.periodMs, it.intervalMs) }
            } else {
                emptyList()
            }
            JsLoadedSource(index, info, id, limiters, emptyMap(), environment.preferences(id))
        }
        sources = described
        // Headers may read settings, which need the sources in place
        sources = described.map { source ->
            val json = evaluateString("JSON.stringify(__extension.default.sources[${source.index}].headers)")
            source.copy(
                headers = jsJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json),
            )
        }
    }

    /** Calls `method` on source [index] with JSON [arguments] and returns the JSON result. */
    suspend fun invoke(index: Int, method: String, arguments: JsonArray): String {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<String>()
        synchronized(lock) { calls[id] = deferred }
        try {
            try {
                evaluate("__shiori.invoke($id, $index, ${literal(method)}, ${literal(arguments.toString())})")
            } catch (e: QuickJsException) {
                complete(id, e.message ?: e.toString(), null)
            }
            return try {
                deferred.await()
            } catch (e: JsScriptException) {
                throw failures.original(e.message.orEmpty()) ?: e
            }
        } finally {
            finish(id)
        }
    }

    /** Index of the rate-limit rule the script applies to [url], or null. */
    internal suspend fun rateLimitRule(index: Int, url: String): Int? =
        (evaluate("__extension.default.rateLimitFor($index, ${literal(url)})") as? Number)?.toInt()

    /** Cookies the script's cookie rules add to a request for [url]. */
    internal suspend fun cookies(index: Int, url: String): List<Pair<String, String>> {
        val json = evaluateString("__extension.default.cookiesFor($index, ${literal(url)})")
        return jsJson.decodeFromString(ListSerializer(ListSerializer(String.serializer())), json)
            .mapNotNull { if (it.size == 2) it[0] to it[1] else null }
    }

    override fun close() {
        val pending = synchronized(lock) { calls.values.toList().also { calls.clear() } }
        pending.forEach { it.completeExceptionally(CancellationException("$name was unloaded")) }
        handles.clear()
        quickJs.close()
    }

    private fun complete(id: Int, error: String?, result: String?) {
        val deferred = synchronized(lock) { calls.remove(id) } ?: return
        if (error !=
            null
        ) {
            deferred.completeExceptionally(JsScriptException(error))
        } else {
            deferred.complete(result ?: "null")
        }
    }

    private fun finish(id: Int) {
        synchronized(lock) {
            calls.remove(id)
            // Handles only live while a call runs; scripts don't keep parsed pages between calls
            if (calls.isEmpty()) handles.clear()
        }
    }

    // Host API

    private fun installHostApi() {
        quickJs.define("__native") {
            function("http") { args -> startHttp(args.int(0), args.string(1)) }
            function("webview") { args -> startWebView(args.int(0), args.string(1)) }
            function("take") { args -> results.remove(args.int(0)) ?: listOf("No result", null) }
            function("complete") { args -> complete(args.int(0), args[1] as String?, args[2] as String?) }
            function("parseHtml") { args -> handles.parse(args.string(0), args.string(1)) }
            function("html") { args -> handles.call(args.string(0), args.int(1), args[2] as String?) }
            function("prefGet") { args -> preferenceJson(args.int(0), args.string(1)) }
            function("prefSet") { args -> setPreference(args.int(0), args.string(1), args.string(2)) }
            function("evaluate") { args -> evaluateIsolated(args.string(0)) }
            function("md5") { args -> hex("MD5", args.string(0)) }
            function("sha256") { args -> hex("SHA-256", args.string(0)) }
            function("base64Encode") { args -> Base64.getEncoder().encodeToString(args.string(0).toByteArray()) }
            function("base64Decode") { args -> base64Decode(args.string(0)) }
            function("log") { args -> environment.log(args.string(0), "[$name] ${args.string(1)}") }
        }
    }

    private fun startHttp(id: Int, payloadJson: String) {
        val payload = jsJson.decodeFromString<JsHttpPayload>(payloadJson)
        val source = sources.getOrNull(payload.source) ?: error("Unknown source ${payload.source}")
        val request = JsHttp.buildRequest(
            payload = payload,
            site = source.info.baseUrl.toHttpUrlOrNull(),
            acceptLanguage = environment.acceptLanguage,
            rateLimiter = payload.rateLimit?.let { source.rateLimiters.getOrNull(it) },
        )
        respondLater(id) { jsJson.encodeToString(JsHttpResult.serializer(), JsHttp.send(client, payload, request)) }
    }

    private fun startWebView(id: Int, payloadJson: String) {
        val request = jsJson.decodeFromString<JsWebViewRequest>(payloadJson)
        respondLater(id) {
            val runner = environment.webView ?: throw IllegalStateException("This app has no WebView for extensions.")
            // `result: null` must reach the script as null, not as a missing key
            Json.encodeToString(JsWebViewResult.serializer(), runner.run(request))
        }
    }

    /** Runs [work] off the runtime, then hands its result or error to the callback waiting in the shim. */
    private fun respondLater(id: Int, work: suspend () -> String) {
        environment.scope.launch {
            results[id] = try {
                listOf(null, work())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                listOf(failures.record(e), null)
            }
            try {
                evaluate("__shiori.deliver($id)")
            } catch (e: QuickJsException) {
                environment.log("error", "[$name] ${e.message}")
            }
        }
    }

    private fun preferenceJson(index: Int, key: String): String? {
        val value = sources.getOrNull(index)?.preferences?.get(key) ?: return null
        return when (value) {
            is Boolean -> JsonPrimitive(value).toString()
            is Number -> JsonPrimitive(value).toString()
            is String -> JsonPrimitive(value).toString()
            is Collection<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) }).toString()
            else -> null
        }
    }

    private fun setPreference(index: Int, key: String, json: String) {
        val preferences = sources.getOrNull(index)?.preferences ?: return
        val value = runCatching { jsJson.parseToJsonElement(json) }.getOrNull()
        when {
            value is JsonArray -> preferences.set(
                key,
                value.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }.toSet(),
            )
            value is JsonPrimitive && value.isString -> preferences.set(key, value.content)
            value is JsonPrimitive && value.booleanOrNull != null -> preferences.set(key, value.booleanOrNull)
            else -> preferences.set(key, null)
        }
    }

    private suspend fun evaluate(code: String, file: String = "call.js"): Any? = quickJs.evaluate<Any?>(
        code,
        file,
        false,
    )

    private suspend fun evaluateString(code: String): String =
        evaluate(code) as? String ?: throw JsLoadException("$code did not return a string")

    companion object {
        /** Evaluates [script] and reads its sources; throws [JsLoadException] or [JsUnsupportedApiException]. */
        suspend fun load(script: String, name: String, environment: JsEnvironment): JsExtensionRuntime {
            val runtime = JsExtensionRuntime(name, environment)
            try {
                runtime.start(script)
            } catch (e: Throwable) {
                runtime.close()
                throw e
            }
            return runtime
        }

        /** Evaluates a site's script in a fresh, empty runtime, as upstream's `QuickJs.create().use { }`. */
        internal fun evaluateIsolated(script: String): String? = runBlocking {
            JsNative.ensureLoaded()
            val isolated = QuickJs.create(Dispatchers.Default)
            try {
                val code = "(function () { var v = (0, eval)(${literal(script)});" +
                    " return v === undefined || v === null ? null : String(v); })()"
                isolated.evaluate<Any?>(code, "isolated.js", false) as String?
            } catch (e: QuickJsException) {
                throw IllegalStateException("JavaScript error: ${e.message}")
            } finally {
                isolated.close()
            }
        }

        private fun literal(text: String) = Json.encodeToString(String.serializer(), text)

        private fun hex(algorithm: String, text: String) =
            MessageDigest.getInstance(algorithm).digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun base64Decode(text: String): String? {
            val bytes = runCatching { Base64.getMimeDecoder().decode(text) }.getOrNull() ?: return null
            return try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }

        private fun Array<Any?>.int(index: Int) = (this[index] as Number).toInt()
        private fun Array<Any?>.string(index: Int) = this[index] as? String ?: this[index].toString()

        /** Builds `__host` (host API level [JS_HOST_API_LEVEL]) and `__shiori`, the entry points Kotlin calls. */
        private val SHIM = """
            (() => {
              "use strict";
              const n = globalThis.__native;
              const waiting = new Map();
              let nextRequest = 1;
              class HostElement {
                constructor(handle) { this.handle = handle; }
                select(css) { return n.html("select", this.handle, css).map((h) => new HostElement(h)); }
                selectFirst(css) { return wrap(n.html("selectFirst", this.handle, css)); }
                text() { return n.html("text", this.handle, null); }
                ownText() { return n.html("ownText", this.handle, null); }
                wholeText() { return n.html("wholeText", this.handle, null); }
                html() { return n.html("html", this.handle, null); }
                outerHtml() { return n.html("outerHtml", this.handle, null); }
                attr(name) { return n.html("attr", this.handle, name); }
                hasAttr(name) { return n.html("hasAttr", this.handle, name); }
                absUrl(name) { return n.html("absUrl", this.handle, name); }
                tagName() { return n.html("tagName", this.handle, null); }
                id() { return n.html("id", this.handle, null); }
                hasClass(name) { return n.html("hasClass", this.handle, name); }
                data() { return n.html("data", this.handle, null); }
                val() { return n.html("val", this.handle, null); }
                parent() { return wrap(n.html("parent", this.handle, null)); }
                children() { return n.html("children", this.handle, null).map((h) => new HostElement(h)); }
                nextElementSibling() { return wrap(n.html("nextElementSibling", this.handle, null)); }
                previousElementSibling() { return wrap(n.html("previousElementSibling", this.handle, null)); }
                location() { return n.html("location", this.handle, null); }
              }
              const wrap = (handle) => (handle < 0 ? null : new HostElement(handle));
              const request = (start) => (payload, callback) => {
                const id = nextRequest++;
                waiting.set(id, callback);
                try {
                  start(id, payload);
                } catch (e) {
                  waiting.delete(id);
                  callback(String(e && e.message ? e.message : e), null);
                }
              };
              globalThis.__host = Object.freeze({
                apiLevel: $JS_HOST_API_LEVEL,
                http: request((id, payload) => n.http(id, payload)),
                webview: request((id, payload) => n.webview(id, payload)),
                parseHtml: (html, baseUri) => new HostElement(n.parseHtml(html, baseUri)),
                prefGet: (source, key) => n.prefGet(source, key),
                prefSet: (source, key, json) => { n.prefSet(source, key, json); },
                evaluate: (script) => n.evaluate(script),
                md5: (text) => n.md5(text),
                sha256: (text) => n.sha256(text),
                base64Encode: (text) => n.base64Encode(text),
                base64Decode: (text) => n.base64Decode(text),
                log: (level, message) => { n.log(level, String(message)); },
              });
              globalThis.__shiori = Object.freeze({
                deliver(id) {
                  const callback = waiting.get(id);
                  waiting.delete(id);
                  const result = n.take(id);
                  if (callback) callback(result[0], result[1]);
                },
                invoke(call, index, method, args) {
                  __extension.default.invoke(index, method, args, (error, result) =>
                    n.complete(call, error === undefined ? null : error, result === undefined ? null : result));
                },
              });
            })();
        """.trimIndent()
    }
}

class JsUnsupportedApiException(val required: Int) :
    Exception("The extension needs host API $required; this app provides $JS_HOST_API_LEVEL. Update the app.")

/** The ID a source without a fixed one gets, as `HttpSource.generateId` and the iOS app compute it. */
internal fun jsSourceId(name: String, lang: String, versionId: Int): Long {
    val bytes = MessageDigest.getInstance("MD5").digest("${name.lowercase()}/$lang/$versionId".toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}

/**
 * Errors handed to scripts that the app wants back as themselves when a script rethrows them: a Cloudflare check (so the
 * app offers the WebView) and an HTTP status, matched by message as the iOS host does.
 */
private class RecentFailures {
    private val entries = ArrayDeque<Pair<String, Throwable>>()

    fun record(error: Throwable): String {
        val message = when {
            error is JsHttpStatusException -> error.message!!
            else -> error.message ?: error::class.simpleName.orEmpty()
        }
        if (error is JsHttpStatusException || error.isCloudflare()) {
            synchronized(entries) {
                entries.removeAll { it.first == message }
                entries.addLast(message to error)
                if (entries.size > 8) entries.removeFirst()
            }
        }
        return message
    }

    fun original(text: String): Throwable? {
        val error = synchronized(entries) { entries.lastOrNull { text.contains(it.first) }?.second } ?: return null
        return if (error is JsHttpStatusException) error.toHttpException() else error
    }

    private fun Throwable.isCloudflare(): Boolean =
        generateSequence(this) { it.cause }.any { it is CloudflareBypassException }
}
