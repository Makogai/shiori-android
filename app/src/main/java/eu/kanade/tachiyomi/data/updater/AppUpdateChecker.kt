package eu.kanade.tachiyomi.data.updater

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tachiyomi.data.release.GithubRelease
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release

/** Checks the Shiori releases repo (never mihonapp's) for a release with a higher versionCode. */
@Inject
class AppUpdateChecker(
    private val network: NetworkHelper,
    private val json: Json,
) {

    suspend fun checkForUpdate(): GetApplicationRelease.Result {
        if (!ShioriRelease.isConfigured(GITHUB_REPO)) return GetApplicationRelease.Result.NoNewUpdate
        val release = withContext(Dispatchers.IO) {
            with(json) {
                network.client
                    .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
                    .awaitSuccess()
                    .parseAs<GithubRelease>()
            }
        }
        val apk = release.assets.firstOrNull { it.name == ShioriRelease.ASSET_NAME }
        if (apk == null || !ShioriRelease.isNewer(release.version, BuildConfig.VERSION_CODE)) {
            return GetApplicationRelease.Result.NoNewUpdate
        }
        return GetApplicationRelease.Result.NewUpdate(
            Release(
                version = release.version,
                info = release.info,
                releaseLink = release.releaseLink,
                downloadLink = apk.downloadLink,
            ),
        )
    }
}

val GITHUB_REPO: String = BuildConfig.SHIORI_UPDATE_REPO

val RELEASE_TAG: String = ShioriRelease.tag(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
