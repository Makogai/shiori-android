package mihon.feature.jssources

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Base64
import java.util.Locale

/** A request as the SDK's `HttpClient` sends it to `__host.http`. */
@Serializable
internal data class JsHttpPayload(
    val source: Int,
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyBase64: String? = null,
    val fetch: String? = null,
    val initiator: String? = null,
    val cookies: List<List<String>> = emptyList(),
    val rateLimit: Int? = null,
    val ensureSuccess: Boolean = true,
    val responseType: String = "text",
)

@Serializable
internal data class JsHttpResult(val status: Int, val url: String, val headers: Map<String, String>, val body: String)

/** What [JsInterceptor] does for one request: the script's cookie rule, its rate limit and an image transform. */
internal class JsRequestTag(
    val cookies: List<Pair<String, String>> = emptyList(),
    val rateLimiter: JsRateLimiter? = null,
    val transform: (suspend (ByteArray) -> ByteArray)? = null,
)

/** A 404 or other unwanted status, worded as the iOS host does so scripts and logs read the same. */
internal class JsHttpStatusException(val code: Int, url: HttpUrl) : Exception("HTTP $code from ${url.host}")

internal object JsHttp {

    suspend fun send(client: OkHttpClient, payload: JsHttpPayload, request: Request): JsHttpResult {
        val response = client.newCall(request).await()
        return response.use {
            if (payload.ensureSuccess && !it.isSuccessful) throw JsHttpStatusException(it.code, it.request.url)
            val body = if (payload.responseType == "base64") {
                Base64.getEncoder().encodeToString(it.body.bytes())
            } else {
                it.body.string()
            }
            JsHttpResult(
                status = it.code,
                url = it.request.url.toString(),
                headers = it.headers.names().associate { name ->
                    name.lowercase(Locale.ROOT) to
                        it.headers.values(name).first()
                },
                body = body,
            )
        }
    }

    fun buildRequest(
        payload: JsHttpPayload,
        site: HttpUrl?,
        acceptLanguage: String,
        rateLimiter: JsRateLimiter?,
    ): Request {
        val url = payload.url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: ${payload.url}")
        val headers = Headers.Builder()
        payload.headers.forEach { (name, value) ->
            headers.removeAll(name)
            headers.addUnsafeNonAscii(name, value)
        }
        val bytes = payload.bodyBase64?.let { Base64.getMimeDecoder().decode(it) } ?: payload.body?.toByteArray()
        val method = payload.method.uppercase(Locale.ROOT)
        JsFetch.of(payload.fetch)?.apply(
            headers = headers,
            url = url,
            method = method,
            initiator = payload.initiator?.toHttpUrlOrNull(),
            site = site,
            acceptLanguage = acceptLanguage,
        )
        val body = when {
            bytes != null -> bytes.toRequestBody(headers["Content-Type"]?.toMediaTypeOrNull())
            method in METHODS_WITH_BODY -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        return Request.Builder()
            .url(url)
            .headers(headers.build())
            .method(method, body)
            .tag(
                JsRequestTag::class.java,
                JsRequestTag(
                    cookies = payload.cookies.mapNotNull { if (it.size == 2) it[0] to it[1] else null },
                    rateLimiter = rateLimiter,
                ),
            )
            .build()
    }

    /** The client a source's requests go through: the app's, with the script's cookies, rate limits and transforms first. */
    fun clientFor(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .apply { interceptors().add(0, JsInterceptor(base.cookieJar)) }
        .build()

    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")
}

/**
 * Applies a [JsRequestTag]. Cookie rules are stored for the request's host, so they win over older values as upstream's
 * `addCookie` interceptor does, and stay in the shared jar the WebView uses too.
 */
internal class JsInterceptor(private val cookieJar: CookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = request.tag(JsRequestTag::class.java) ?: return chain.proceed(request)
        if (tag.cookies.isNotEmpty()) {
            val cookies = tag.cookies.mapNotNull { (name, value) ->
                runCatching {
                    Cookie.Builder().domain(request.url.host).path("/").name(name).value(value).build()
                }.getOrNull()
            }
            cookieJar.saveFromResponse(request.url, cookies)
        }
        tag.rateLimiter?.let { runBlocking { it.acquire() } }
        val response = chain.proceed(request)
        val transform = tag.transform
        if (transform == null || !response.isSuccessful) return response
        val type = response.body.contentType()
        val bytes = response.body.bytes()
        val transformed = runBlocking { transform(bytes) }
        return response.newBuilder()
            .removeHeader("Content-Length")
            .body(transformed.toResponseBody(if (transformed === bytes) type else "image/png".toMediaType()))
            .build()
    }
}

/**
 * How a browser would make the request: as a page navigation, a script's fetch or an image load. Mirrors keiyoushi's
 * `Fetch` and the iOS host, with Chrome's `Accept` values to match the app's Chrome user agent. Headers already set win.
 */
internal enum class JsFetch(
    private val accept: String,
    private val mode: String,
    private val destination: String,
    private val priority: String,
) {
    DOCUMENT(
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "navigate",
        "document",
        "u=0, i",
    ),
    XHR("*/*", "cors", "empty", "u=1, i"),
    IMAGE("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8", "no-cors", "image", "i"),
    ;

    fun apply(
        headers: Headers.Builder,
        url: HttpUrl,
        method: String,
        initiator: HttpUrl?,
        site: HttpUrl?,
        acceptLanguage: String,
    ) {
        fun fill(name: String, value: String?) {
            if (value != null && headers[name] == null) headers[name] = value
        }
        val from = initiator ?: headers["Referer"]?.toHttpUrlOrNull() ?: site
        fill("Accept-Language", acceptLanguage.ifEmpty { null })
        fill("Accept", accept)
        // Browsers send fetch metadata to secure origins only
        if (url.isHttps) {
            fill("Sec-Fetch-Site", from?.let { relation(it, url) } ?: "none")
            fill("Sec-Fetch-Mode", mode)
            fill("Sec-Fetch-Dest", destination)
        }
        if (this == DOCUMENT) fill("Upgrade-Insecure-Requests", "1")
        fill("Priority", priority)
        if (from != null && headers["Origin"] == null) {
            val crossOriginXhr = this == XHR && origin(from) != origin(url)
            if ((method != "GET" && method != "HEAD") || crossOriginXhr) headers["Origin"] = origin(from)
        }
    }

    companion object {
        fun of(kind: String?) = when (kind) {
            "document" -> DOCUMENT
            "xhr" -> XHR
            "image" -> IMAGE
            else -> null
        }

        fun relation(initiator: HttpUrl, target: HttpUrl): String = when {
            origin(initiator) == origin(target) -> "same-origin"
            registrableDomain(initiator.host) == registrableDomain(target.host) -> "same-site"
            else -> "cross-site"
        }

        private fun origin(url: HttpUrl): String {
            val defaultPort = HttpUrl.defaultPort(url.scheme)
            return if (url.port ==
                defaultPort
            ) {
                "${url.scheme}://${url.host}"
            } else {
                "${url.scheme}://${url.host}:${url.port}"
            }
        }

        /** The last two labels, as on iOS; hosts such as example.co.uk would need a public-suffix list. */
        private fun registrableDomain(host: String): String = host.split('.').takeLast(2).joinToString(".")
    }
}

/**
 * One rate-limit rule of a source: `permits` requests per `periodMs`, at least `intervalMs` apart. Each request reserves
 * the earliest slot that keeps both limits and sleeps until then, so waiting requests are served in order.
 */
internal class JsRateLimiter(
    private val permits: Int,
    private val periodMs: Long,
    private val intervalMs: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val dispatches = ArrayDeque<Long>()

    suspend fun acquire() {
        val wait = reserve() - now()
        if (wait > 0) delay(wait)
    }

    @Synchronized
    fun reserve(): Long {
        var slot = now()
        if (dispatches.size >= permits) slot = maxOf(slot, dispatches[dispatches.size - permits] + periodMs)
        dispatches.lastOrNull()?.let { slot = maxOf(slot, it + intervalMs) }
        dispatches.addLast(slot)
        while (dispatches.size > permits) dispatches.removeFirst()
        return slot
    }
}

/** `Accept-Language` from the preferred languages, as browsers send it: `en-US,en;q=0.9,de;q=0.8`. */
internal fun acceptLanguage(languages: List<Locale>): String {
    val tags = mutableListOf<String>()
    languages.forEach { locale ->
        val tag = locale.toLanguageTag()
        if (tag !in tags) tags += tag
        val base = locale.language
        if (base.isNotEmpty() && base != tag && base !in tags) tags += base
    }
    return tags.take(5).mapIndexed { index, tag -> if (index == 0) tag else "$tag;q=0.${10 - index}" }.joinToString(",")
}

/** The status error a script rethrew, as Mihon's own [HttpException], so the app shows and classifies it as usual. */
internal fun JsHttpStatusException.toHttpException() = HttpException(code)
