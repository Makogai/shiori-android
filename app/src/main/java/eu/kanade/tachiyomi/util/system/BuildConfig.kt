@file:Suppress("UNUSED", "KotlinConstantConditions")

package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.ShioriRelease

val telemetryIncluded: Boolean
    inline get() = BuildConfig.TELEMETRY_INCLUDED

val updaterEnabled: Boolean
    get() = ShioriRelease.isConfigured(BuildConfig.SHIORI_UPDATE_REPO)

val isDebugBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "debug"

val isNightlyBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "nightly"

val isReleaseBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "release"

val isFossBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "foss"

val isBenchmarkBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE.contains("nonMinified") || BuildConfig.BUILD_TYPE.contains("benchmark")
