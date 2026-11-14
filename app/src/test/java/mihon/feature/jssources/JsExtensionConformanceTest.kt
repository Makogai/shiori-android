package mihon.feature.jssources

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Runs the built extensions from the extension repo against their saved responses and `tests.json`, as the iOS app's
 * `ExtensionConformanceTests` do, through the real host: QuickJS, Jsoup and OkHttp. Requests never leave the process:
 * the client answers from `fixtures/`, and a request with no fixture gets a 404 that names it.
 *
 * The files under `resources/jssources` are copies of the extension repo's `dist/<pkg>.js`, `src/<lang>/<name>/tests.json`
 * and `fixtures/`, and its `dist/index.json`.
 */
class JsExtensionConformanceTest {

    private val root = File(javaClass.classLoader!!.getResource("jssources/index.json")!!.toURI()).parentFile
    private val index = Json.parseToJsonElement(File(root, "index.json").readText()).jsonArray

    @TestFactory
    fun extensions(): List<DynamicNode> = root.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }.map { dir ->
        val spec = Json.parseToJsonElement(File(dir, "tests.json").readText()).jsonObject
        val cases = spec["cases"]!!.jsonArray.map { it.jsonObject }
        dynamicContainer(
            dir.name,
            listOf(dynamicTest("source IDs match index.json") { checkIds(dir) }) +
                cases.map { case -> dynamicTest(case.string("name") ?: "unnamed") { run(dir, spec, case) } },
        )
    }

    @Test
    fun `refuses a script built for a newer host API`() {
        val script = "var __extension = { default: { api: ${JS_HOST_API_LEVEL + 1}, sources: [] } };"
        shouldThrow<JsUnsupportedApiException> {
            runBlocking { JsExtensionRuntime.load(script, "future", environment(emptyList(), root, mutableListOf())) }
        }
    }

    private fun checkIds(dir: File) {
        val entry = index.firstOrNull { it.jsonObject.string("pkg") == dir.name }?.jsonObject ?: return
        val expected = entry["sources"]!!.jsonArray.map { it.jsonObject.string("id")!!.toLong() }
        val runtime = load(dir, environment(emptyList(), dir, mutableListOf()))
        try {
            runtime.sources.map { it.id } shouldBe expected
        } finally {
            runtime.close()
        }
    }

    private fun run(dir: File, spec: JsonObject, case: JsonObject) {
        val routes = spec["routes"]?.jsonArray.orEmpty().map { it.jsonObject }
        val requested = mutableListOf<String>()
        val environment = environment(routes, dir, requested)
        val runtime = load(dir, environment)
        try {
            val loaded = runtime.sources[case["source"]?.jsonPrimitive?.int ?: 0]
            val source = JsSource(runtime, loaded, dir.name, environment)
            val args = case["args"]?.jsonArray ?: JsonArray(emptyList())
            val result = try {
                runBlocking { perform(case.string("call")!!, args, source, runtime, loaded.index) }
            } catch (e: Exception) {
                fail("${case.string("call")} failed: ${e.message}\nRequests made: $requested", e)
            }
            val failures = case["expect"]!!.jsonObject.entries.sortedBy { it.key }.mapNotNull { (path, expected) ->
                check(resolve(result, path), path, expected)
            }
            if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
        } finally {
            runtime.close()
        }
    }

    private suspend fun perform(
        call: String,
        args: JsonArray,
        source: JsSource,
        runtime: JsExtensionRuntime,
        index: Int,
    ): JsonElement {
        fun manga() = SManga.create().apply {
            url = args[0].jsonObject.string("url").orEmpty()
            title = args[0].jsonObject.string("title").orEmpty()
        }
        fun page() = Page(
            args[0].jsonObject["index"]?.jsonPrimitive?.int ?: 0,
            args[0].jsonObject.string("url").orEmpty(),
            args[0].jsonObject.string("imageUrl"),
        )
        return when (call) {
            "popular" -> page(source.getPopularManga(args[0].jsonPrimitive.int))
            "latest" -> page(source.getLatestUpdates(args[0].jsonPrimitive.int))
            "search" -> page(
                source.getSearchManga(args[0].jsonPrimitive.int, args[1].jsonPrimitive.content, FilterList()),
            )
            "details" -> {
                val manga = manga()
                val fetched = source.getMangaUpdate(
                    manga,
                    emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga
                json(merge(manga, fetched))
            }
            "chapters" -> JsonArray(
                source.getMangaUpdate(
                    manga(),
                    emptyList(),
                    fetchDetails = false,
                    fetchChapters = true,
                ).chapters.map(::json),
            )
            "mangaUpdate" -> {
                val update = source.getMangaUpdate(manga(), emptyList(), fetchDetails = true, fetchChapters = true)
                buildJsonObject {
                    put("manga", json(update.manga))
                    put("chapters", JsonArray(update.chapters.map(::json)))
                }
            }
            "pages" -> {
                val chapter = SChapter.create().apply {
                    url = args[0].jsonObject.string("url").orEmpty()
                    name = args[0].jsonObject.string("name").orEmpty()
                }
                JsonArray(
                    source.getPageList(chapter).map {
                        buildJsonObject {
                            put("index", it.index)
                            put("url", it.url)
                            put("imageUrl", it.imageUrl)
                        }
                    },
                )
            }
            "imageURL" -> JsonPrimitive(source.getImageUrl(page()))
            "imageTransform" -> {
                val pageJson = jsJson.encodeToJsonElement(JsPage.serializer(), JsPage.from(page()))
                Json.parseToJsonElement(
                    runtime.invoke(index, "getImageTransform", JsonArray(listOf(pageJson, args[1], args[2]))),
                )
            }
            else -> error("Unknown call $call in tests.json")
        }
    }

    // Expectations, as the iOS conformance tests check them

    private fun check(actual: JsonElement?, path: String, expected: JsonElement): String? {
        val label = path.ifEmpty { "result" }
        val operation = (expected as? JsonObject)?.entries?.singleOrNull()
        when (operation?.key) {
            "contains" -> {
                val text = (actual as? JsonPrimitive)?.contentOrNull
                val part = operation.value.jsonPrimitive.content
                return if (text != null && actual.isString &&
                    part in text
                ) {
                    null
                } else {
                    "$label = $actual should contain $part"
                }
            }
            "gt" -> {
                val number = (actual as? JsonPrimitive)?.doubleOrNull
                val bound = operation.value.jsonPrimitive.doubleOrNull ?: 0.0
                return if (number != null && number > bound) null else "$label = $actual should be > $bound"
            }
            "localDate" -> {
                val millis = (actual as? JsonPrimitive)?.doubleOrNull ?: 0.0
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis.toLong()))
                val wanted = operation.value.jsonPrimitive.content
                return if (day == wanted) null else "$label is $day, expected $wanted"
            }
        }
        return if (actual != null && same(actual, expected)) null else "$label = $actual, expected $expected"
    }

    private fun same(actual: JsonElement, expected: JsonElement): Boolean = when {
        actual is JsonNull || expected is JsonNull -> actual is JsonNull && expected is JsonNull
        actual is JsonPrimitive && expected is JsonPrimitive -> when {
            expected.isString -> actual.isString && actual.content == expected.content
            expected.booleanOrNull != null -> actual.booleanOrNull == expected.booleanOrNull
            else -> {
                val a = actual.doubleOrNull
                val e = expected.doubleOrNull
                a != null && e != null && abs(a - e) <= 1e-4 * maxOf(1.0, abs(e))
            }
        }
        actual is JsonArray && expected is JsonArray ->
            actual.size == expected.size && actual.zip(expected).all { (a, e) -> same(a, e) }
        actual is JsonObject && expected is JsonObject ->
            actual.keys == expected.keys && actual.keys.all { same(actual[it]!!, expected[it]!!) }
        else -> false
    }

    private fun resolve(value: JsonElement, path: String): JsonElement? {
        if (path.isEmpty()) return value
        var current: JsonElement? = value
        path.split('.').forEach { key ->
            current = when (val node = current) {
                is JsonArray -> if (key ==
                    "length"
                ) {
                    JsonPrimitive(node.size)
                } else {
                    key.toIntOrNull()?.let(node::getOrNull)
                }
                is JsonObject -> node[key]?.takeUnless { it is JsonNull }
                else -> return null
            }
        }
        return current
    }

    // JSON in the shape the iOS tests produce, with upstream's field names

    private fun page(page: eu.kanade.tachiyomi.source.model.MangasPage) = buildJsonObject {
        put("mangas", JsonArray(page.mangas.map(::json)))
        put("hasNextPage", page.hasNextPage)
    }

    private fun json(manga: SManga) = jsJson.encodeToJsonElement(JsManga.serializer(), JsManga.from(manga))

    private fun json(chapter: SChapter) = jsJson.encodeToJsonElement(JsChapter.serializer(), JsChapter.from(chapter))

    /** What the iOS `details` returns: the manga with the fetched fields that are set, marked initialized. */
    private fun merge(manga: SManga, fetched: SManga) = manga.copy().apply {
        if (fetched.url.isNotEmpty()) url = fetched.url
        if (fetched.title.isNotEmpty()) title = fetched.title
        fetched.artist?.let { artist = it }
        fetched.author?.let { author = it }
        fetched.description?.let { description = it }
        fetched.genre?.let { genre = it }
        fetched.thumbnail_url?.let { thumbnail_url = it }
        status = fetched.status
        update_strategy = fetched.update_strategy
        initialized = true
    }

    // Host setup

    private fun load(dir: File, environment: JsEnvironment) = runBlocking {
        JsExtensionRuntime.load(File(dir, "extension.js").readText(), dir.name, environment)
    }

    private fun environment(routes: List<JsonObject>, dir: File, requested: MutableList<String>): JsEnvironment {
        QuickJsJvm.ready
        val client = OkHttpClient.Builder().addInterceptor(Fixtures(routes, dir, requested)).build()
        val stores = mutableMapOf<Long, JsPreferenceStore>()
        return JsEnvironment(
            client = client,
            preferences = { id -> stores.getOrPut(id) { MemoryStore() } },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            acceptLanguage = "en-US,en;q=0.9",
            rateLimitsEnabled = false,
            log = { level, message -> if (level != "debug") println("$level: $message") },
        )
    }

    private class MemoryStore : JsPreferenceStore {
        private val values = mutableMapOf<String, Any?>()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: Any?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    /** Answers from `fixtures/`; never calls the network. */
    private class Fixtures(
        private val routes: List<JsonObject>,
        private val dir: File,
        private val requested: MutableList<String>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString().substringBefore('#')
            synchronized(requested) { requested += url }
            val route = routes.firstOrNull {
                val target = it.string("url").orEmpty()
                if (it.string("match") == "prefix") url.startsWith(target) else url == target
            }
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            if (route == null) {
                return builder.code(404).message("Not Found")
                    .body("No fixture for $url".toResponseBody("text/plain".toMediaType())).build()
            }
            val file = route.string("file")!!
            val type = if (file.endsWith(".json")) "application/json" else "text/html; charset=utf-8"
            return builder.code(200).message("OK").header("Content-Type", type)
                .body(File(dir, file).readBytes().toResponseBody(type.toMediaType())).build()
        }
    }

    /** Points quickjs-kt at its desktop library from quickjs-kt-jvm, since the app only ships the Android one. */
    private object QuickJsJvm {
        val ready by lazy {
            val os = System.getProperty("os.name").lowercase(Locale.ROOT)
            val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "aarch64" else "x64"
            val (platform, file) = when {
                "mac" in os -> "macos" to "libquickjs.dylib"
                "windows" in os -> "windows" to "libquickjs.dll"
                else -> "linux" to "libquickjs.so"
            }
            val directory = Files.createTempDirectory("quickjs").toFile().apply { deleteOnExit() }
            val library = File(directory, file).apply { deleteOnExit() }
            JsExtensionConformanceTest::class.java.classLoader!!.getResourceAsStream("jni/${platform}_$arch/$file")!!
                .use { input -> library.outputStream().use { input.copyTo(it) } }
            System.setProperty("com.dokar.quickjs.library.path", directory.absolutePath)
            System.setProperty("com.dokar.quickjs.library.name", file)
        }
    }
}

private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
