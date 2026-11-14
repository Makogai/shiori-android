package eu.kanade.tachiyomi.data.updater

import tachiyomi.data.release.GitHubAsset
import tachiyomi.data.release.GithubRelease

/**
 * The Shiori release convention (RELEASING-SHIORI.md): tag `v<versionName>-<versionCode>`, e.g. `v0.21.3-40`,
 * carrying `Shiori-Friends-arm64.apk` and/or `Shiori-arm64.apk`; a release with only the plain APK may add
 * `-plain` to the tag. Only the versionCode decides whether a release is newer.
 */
object ShioriRelease {

    const val PLACEHOLDER_REPO = "OWNER/REPO"

    private val TAG = Regex("""^v.+?-(\d+)(?:-plain)?$""")

    fun isConfigured(repo: String): Boolean = repo.isNotBlank() && repo != PLACEHOLDER_REPO

    fun tag(versionName: String, versionCode: Int): String = "v$versionName-$versionCode"

    /** The versionCode in [tag], or null when the tag doesn't follow the convention. */
    fun versionCode(tag: String): Int? = TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(tag: String, installedVersionCode: Int): Boolean =
        (versionCode(tag) ?: return false) > installedVersionCode

    /**
     * The newest published release newer than [installedVersionCode] that carries [assetName], with that asset.
     * GitHub has a single "latest" release, so both builds scan the release list for their own APK.
     */
    fun pick(
        releases: List<GithubRelease>,
        assetName: String,
        installedVersionCode: Int,
    ): Pair<GithubRelease, GitHubAsset>? = releases
        .asSequence()
        .filter { !it.draft && !it.prerelease && isNewer(it.version, installedVersionCode) }
        .mapNotNull { release -> release.assets.firstOrNull { it.name == assetName }?.let { release to it } }
        .maxByOrNull { (release, _) -> versionCode(release.version) ?: 0 }

    /**
     * Release notes for an update from [installedVersionCode]: every newer release carrying [assetName], newest
     * first, each under its tag, so skipping a release doesn't skip its notes.
     */
    fun combinedNotes(
        releases: List<GithubRelease>,
        assetName: String,
        installedVersionCode: Int,
    ): String {
        val newer = releases
            .filter { release ->
                !release.draft && !release.prerelease && isNewer(release.version, installedVersionCode) &&
                    release.assets.any { it.name == assetName }
            }
            .sortedByDescending { versionCode(it.version) ?: 0 }
        if (newer.size <= 1) return newer.firstOrNull()?.info.orEmpty()
        return newer.joinToString("\n\n") { "### ${it.version}\n${it.info}" }
    }
}
