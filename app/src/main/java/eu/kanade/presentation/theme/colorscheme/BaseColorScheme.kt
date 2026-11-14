package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import tachiyomi.presentation.core.theme.iosGrouped

internal abstract class BaseColorScheme {

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    // Cannot be pure black as there's content scrolling behind it
    // https://m3.material.io/components/navigation-bar/guidelines#90615a71-607e-485e-9e09-778bfc080563
    private val surfaceContainer = Color(0xFF0C0C0C)
    private val surfaceContainerHigh = Color(0xFF131313)
    private val surfaceContainerHighest = Color(0xFF1B1B1B)

    fun getColorScheme(
        isDark: Boolean,
        isAmoled: Boolean,
        overrideDarkSurfaceContainers: Boolean,
    ): ColorScheme {
        if (!isDark) return lightScheme.iosGrouped(isDark = false)

        if (!isAmoled) return darkScheme.iosGrouped(isDark = true)

        val amoledScheme = darkScheme.copy(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
        )

        if (!overrideDarkSurfaceContainers) return amoledScheme.iosGrouped(isDark = true)

        return amoledScheme.copy(
            surfaceVariant = surfaceContainer, // Navigation bar background (ThemePrefWidget)
            surfaceContainerLowest = surfaceContainer,
            surfaceContainerLow = surfaceContainer,
            surfaceContainer = surfaceContainer, // Navigation bar background
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
            .iosGrouped(isDark = true)
    }
}
