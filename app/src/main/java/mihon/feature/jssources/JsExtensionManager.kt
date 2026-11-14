package mihon.feature.jssources

import android.content.Context
import androidx.core.os.LocaleListCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.security.MessageDigest

/**
 * Installs, updates and removes our JavaScript extensions, the ones the iOS app runs, and keeps the installed ones
 * loaded. Mirrors the iOS `ExtensionManager`: a repository serves an `index.json`; each installed extension lives in
 * `files/js-extensions/<pkg>/` as `extension.js`, `extension.json` (the index entry and repository it came from) and
 * `icon.png`.
 */
@Inject
@SingleIn(AppScope::class)
class JsExtensionManager(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
    private val network: NetworkHelper,
) {
    class Installed internal constructor(
        val info: JsExtensionInfo,
        val repository: String,
        val sources: List<JsSource>,
        val icon: File?,
        internal val runtime: JsExtensionRuntime,
    )

    /** Installed but not loadable, for example after an app downgrade, so it can be seen and removed. */
    data class Broken(val pkg: String, val reason: String)

    @Serializable
    private data class Manifest(val info: JsExtensionInfo, val repository: String)

    private val directory = File(context.filesDir, "js-extensions")
    private val mutex = Mutex()

    private val installedMap = MutableStateFlow<Map<String, Installed>?>(null)
    private val brokenMap = MutableStateFlow<Map<String, Broken>>(emptyMap())
    private val availableList = MutableStateFlow<List<JsExtensionInfo>>(emptyList())

    /** Null until the installed extensions have loaded. */
    val installed: StateFlow<Map<String, Installed>?> = installedMap.asStateFlow()
    val broken: StateFlow<Map<String, Broken>> = brokenMap.asStateFlow()
    val available: StateFlow<List<JsExtensionInfo>> = availableList.asStateFlow()

    /** Every source of every installed extension, once they have loaded. */
    val sources: Flow<List<JsSource>> = installedMap.filterNotNull().map { map -> map.values.flatMap { it.sources } }

    private val environment by lazy {
        JsEnvironment(
            client = network.client,
            preferences = { id ->
                SharedPreferencesJsStore(context.getSharedPreferences("source_$id", Context.MODE_PRIVATE))
            },
            scope = scope,
            acceptLanguage = acceptLanguage(
                LocaleListCompat.getAdjustedDefault().let { list -> (0..<list.size()).mapNotNull(list::get) },
            ),
            webView = AndroidJsWebViewRunner(context, network::defaultUserAgentProvider),
            imageTools = AndroidJsImageTools,
            log = { level, message ->
                val priority = when (level) {
                    "error" -> LogPriority.ERROR
                    "warn" -> LogPriority.WARN
                    else -> LogPriority.DEBUG
                }
                logcat(priority) { message }
            },
        )
    }

    init {
        scope.launch(Dispatchers.IO) { loadInstalled() }
    }

    fun iconFile(sourceId: Long): File? =
        installedMap.value?.values?.firstOrNull { extension -> extension.sources.any { it.id == sourceId } }?.icon

    /** Reads the repository's index; [reload] skips the HTTP cache, for an explicit refresh. */
    suspend fun refresh(reload: Boolean = false) = withContext(Dispatchers.IO) {
        val request = if (reload) {
            GET(
                DEFAULT_REPOSITORY,
                cache = CacheControl.FORCE_NETWORK,
            )
        } else {
            GET(DEFAULT_REPOSITORY)
        }
        val json = network.client.newCall(request).awaitSuccess().use { it.body.string() }
        val index = jsJson.decodeFromString(ListSerializer(JsExtensionInfo.serializer()), json)
        availableList.value = index
        refreshIcons(index, DEFAULT_REPOSITORY)
    }

    fun hasUpdate(installed: Installed): Boolean =
        availableList.value.any { it.pkg == installed.info.pkg && it.versionCode > installed.info.versionCode }

    /**
     * Downloads, verifies and loads [info], replacing any installed version. Nothing is written unless the script
     * matches the index's SHA-256, needs a host API this app has, and loads without error.
     */
    suspend fun install(info: JsExtensionInfo, repository: String = DEFAULT_REPOSITORY) = withContext(Dispatchers.IO) {
        if (info.api > JS_HOST_API_LEVEL) throw JsUnsupportedApiException(info.api)
        val base = repository.toHttpUrl()
        val data = download(base.resolve(info.file) ?: error("Invalid file ${info.file}"))
        val digest = sha256(data)
        if (!digest.equals(info.sha256, ignoreCase = true)) {
            throw IllegalStateException(
                "The downloaded extension does not match the repository (expected ${info.sha256}, got $digest)",
            )
        }
        val script = data.toString(Charsets.UTF_8)
        mutex.withLock {
            val runtime = JsExtensionRuntime.load(script, info.pkg, environment)
            val folder = File(directory, info.pkg).apply { mkdirs() }
            File(folder, SCRIPT).writeAtomically(data)
            File(folder, MANIFEST).writeAtomically(
                jsJson.encodeToString(Manifest.serializer(), Manifest(info, repository)).toByteArray(),
            )
            val icon = File(folder, ICON)
            val iconData = iconUrl(info, base)?.let { runCatching { download(it) }.getOrNull() }
            if (iconData != null) icon.writeAtomically(iconData) else icon.delete()
            replace(info.pkg, installed(info, repository, runtime, icon))
        }
    }

    suspend fun uninstall(pkg: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(directory, pkg).deleteRecursively()
            replace(pkg, null)
        }
    }

    private suspend fun loadInstalled() = mutex.withLock {
        val loaded = mutableMapOf<String, Installed>()
        val broken = mutableMapOf<String, Broken>()
        directory.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val pkg = folder.name
            // Throwable: a native library that won't load must not keep the app's other sources from loading
            try {
                val manifest = jsJson.decodeFromString(Manifest.serializer(), File(folder, MANIFEST).readText())
                val runtime = JsExtensionRuntime.load(File(folder, SCRIPT).readText(), pkg, environment)
                loaded[pkg] = installed(manifest.info, manifest.repository, runtime, File(folder, ICON))
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Couldn't load JS extension $pkg" }
                broken[pkg] = Broken(pkg, e.message ?: e.toString())
            }
        }
        brokenMap.value = broken
        installedMap.value = loaded
    }

    /** Saves icons that are missing, such as for an extension installed before the repository published them. */
    private suspend fun refreshIcons(index: List<JsExtensionInfo>, repository: String) = mutex.withLock {
        val base = repository.toHttpUrl()
        index.forEach { info ->
            val current = installedMap.value?.get(info.pkg)?.takeIf { it.repository == repository } ?: return@forEach
            val icon = File(directory, "${info.pkg}/$ICON")
            if (icon.exists()) return@forEach
            val data = iconUrl(info, base)?.let { runCatching { download(it) }.getOrNull() } ?: return@forEach
            icon.writeAtomically(data)
            installedMap.update {
                it.orEmpty() +
                    (info.pkg to installed(current.info, repository, current.runtime, icon))
            }
        }
    }

    private fun installed(info: JsExtensionInfo, repository: String, runtime: JsExtensionRuntime, icon: File) =
        Installed(
            info = info,
            repository = repository,
            sources = runtime.sources.map { JsSource(runtime, it, info.pkg, environment) },
            icon = icon.takeIf { it.exists() },
            runtime = runtime,
        )

    private fun replace(pkg: String, extension: Installed?) {
        val previous = installedMap.value?.get(pkg)
        installedMap.update { map ->
            if (extension ==
                null
            ) {
                map.orEmpty() - pkg
            } else {
                map.orEmpty() + (pkg to extension)
            }
        }
        brokenMap.update { it - pkg }
        if (previous != null && previous.runtime !== extension?.runtime) previous.runtime.close()
    }

    private suspend fun download(url: HttpUrl): ByteArray =
        network.client.newCall(GET(url, cache = CacheControl.FORCE_NETWORK)).awaitSuccess().use { it.body.bytes() }

    /** Where [info]'s icon is in the default repository, for extensions not installed yet. */
    fun iconUrl(info: JsExtensionInfo): String? = iconUrl(info, DEFAULT_REPOSITORY.toHttpUrl())?.toString()

    /** The index's icon, else where the extension repo's pipeline puts it. */
    private fun iconUrl(info: JsExtensionInfo, base: HttpUrl): HttpUrl? =
        base.resolve(info.icon?.ifEmpty { null } ?: "icon/${info.pkg}.png")

    private fun File.writeAtomically(data: ByteArray) {
        val temporary = File(parentFile, "$name.tmp")
        temporary.writeBytes(data)
        if (!temporary.renameTo(this)) {
            temporary.delete()
            throw IllegalStateException("Couldn't write $path")
        }
    }

    companion object {
        const val DEFAULT_REPOSITORY =
            "https://raw.githubusercontent.com/Makogai/tachiyomi-ios-extensions/repo/index.json"

        private const val SCRIPT = "extension.js"
        private const val MANIFEST = "extension.json"
        private const val ICON = "icon.png"

        fun sha256(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    }
}
