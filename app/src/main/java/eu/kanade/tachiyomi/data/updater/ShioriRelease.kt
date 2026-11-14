package eu.kanade.tachiyomi.data.updater

/**
 * The Shiori release convention (RELEASING-SHIORI.md): tag `v<versionName>-<versionCode>`, e.g. `v0.21.3-40`,
 * with the APK attached as [ASSET_NAME]. Only the versionCode decides whether a release is newer.
 */
object ShioriRelease {

    const val ASSET_NAME = "Shiori-arm64.apk"

    const val PLACEHOLDER_REPO = "OWNER/REPO"

    private val TAG = Regex("""^v.+-(\d+)$""")

    fun isConfigured(repo: String): Boolean = repo.isNotBlank() && repo != PLACEHOLDER_REPO

    fun tag(versionName: String, versionCode: Int): String = "v$versionName-$versionCode"

    /** The versionCode in [tag], or null when the tag doesn't follow the convention. */
    fun versionCode(tag: String): Int? = TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(tag: String, installedVersionCode: Int): Boolean =
        (versionCode(tag) ?: return false) > installedVersionCode
}
