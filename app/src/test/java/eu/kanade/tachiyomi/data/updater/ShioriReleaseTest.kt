package eu.kanade.tachiyomi.data.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShioriReleaseTest {

    @Test
    fun `reads the versionCode from the tag`() {
        ShioriRelease.versionCode("v0.21.3-40") shouldBe 40
        ShioriRelease.versionCode("v1.0.0-beta-112") shouldBe 112
        ShioriRelease.versionCode("v0.21.3") shouldBe null
        ShioriRelease.versionCode("0.21.3-40") shouldBe null
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
}
