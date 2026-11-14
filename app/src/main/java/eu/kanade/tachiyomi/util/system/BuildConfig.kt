@file:Suppress("UNUSED", "KotlinConstantConditions")

package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.ShioriRelease

val telemetryIncluded: Boolean
    inline get() = BuildConfig.TELEMETRY_INCLUDED

val updaterEnabled: Boolean
    get() = ShioriRelease.isConfigured(BuildConfig.SHIORI_UPDATE_REPO)

/** False in the plain Shiori build, which ships without a Friends server and so hides every social entry point. */
val friendsConfigured: Boolean
    get() = BuildConfig.FRIENDS_SUPABASE_URL.startsWith("https://") && BuildConfig.FRIENDS_SUPABASE_KEY.isNotEmpty()

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
