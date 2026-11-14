package eu.kanade.tachiyomi.data.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.release.GitHubAsset
import tachiyomi.data.release.GithubRelease

class ShioriReleaseTest {

    @Test
    fun `reads the versionCode from the tag`() {
        ShioriRelease.versionCode("v0.21.3-40") shouldBe 40
        ShioriRelease.versionCode("v1.0.0-beta-112") shouldBe 112
        ShioriRelease.versionCode("v0.21.3") shouldBe null
        ShioriRelease.versionCode("0.21.3-40") shouldBe null
        ShioriRelease.versionCode("v0.21.3-41-plain") shouldBe 41
    }

    @Test
    fun `offers only a higher versionCode`() {
        ShioriRelease.isNewer("v0.21.3-40", installedVersionCode = 39) shouldBe true
        ShioriRelease.isNewer("v0.21.3-39", installedVersionCode = 39) shouldBe false
        ShioriRelease.isNewer("v0.22.0-38", installedVersionCode = 39) shouldBe false
        ShioriRelease.isNewer("v0.22.0", installedVersionCode = 39) shouldBe false
    }

    @Test
    fun `builds the tag and ignores the placeholder repo`() {
        ShioriRelease.tag("0.21.3", 40) shouldBe "v0.21.3-40"
        ShioriRelease.isConfigured("OWNER/REPO") shouldBe false
        ShioriRelease.isConfigured("Makogai/shiori-android") shouldBe true
    }

    @Test
    fun `picks the newest published release carrying this build's APK`() {
        val releases = listOf(
            release("v0.21.3-43", "Shiori-arm64.apk", draft = true),
            release("v0.21.3-42", "Shiori-arm64.apk"),
            release("v0.21.3-41", "Shiori-Friends-arm64.apk", "Shiori-arm64.apk"),
            release("v0.21.3-39", "Shiori-Friends-arm64.apk"),
        )

        ShioriRelease.pick(releases, "Shiori-Friends-arm64.apk", 40)?.first?.version shouldBe "v0.21.3-41"
        ShioriRelease.pick(releases, "Shiori-arm64.apk", 40)?.first?.version shouldBe "v0.21.3-42"
        ShioriRelease.pick(releases, "Shiori-Friends-arm64.apk", 41) shouldBe null
    }

    private fun release(tag: String, vararg assets: String, draft: Boolean = false) = GithubRelease(
        version = tag,
        info = "",
        releaseLink = "",
        assets = assets.map { GitHubAsset(it, "https://example.com/$it") },
        draft = draft,
    )
}
