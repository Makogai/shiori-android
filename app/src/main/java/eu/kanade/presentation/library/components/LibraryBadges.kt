package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.feature.library.TrackBadge
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.HourglassEmpty
import mihon.icons.materialsymbols.roundedfilled.Bookmark
import mihon.icons.materialsymbols.roundedfilled.Pause
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.theme.IosColors

/** The iOS app's dark glass badge for downloads and local / language marks. */
private val NeutralBadgeColor = Color.Black.copy(alpha = 0.62f)

@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = NeutralBadgeColor,
            textColor = Color.White,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = MaterialSymbols.Rounded.Folder,
            color = NeutralBadgeColor,
            iconColor = Color.White,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = NeutralBadgeColor,
            textColor = Color.White,
        )
    }
}

/** The tracker status (reading is what a library mostly is, so it isn't marked) and the stalled mark. */
@Composable
internal fun StatusBadges(badges: LibraryItem.Badges) {
    if (badges.stalled) {
        Badge(imageVector = MaterialSymbols.Rounded.HourglassEmpty, color = IosColors.Indigo, iconColor = Color.White)
    }
    val (icon, color) = when (badges.trackStatus) {
        TrackBadge.PlanToRead -> MaterialSymbols.RoundedFilled.Bookmark to IosColors.Blue
        TrackBadge.OnHold -> MaterialSymbols.RoundedFilled.Pause to IosColors.Orange
        TrackBadge.Dropped -> MaterialSymbols.Rounded.Close to IosColors.Red
        TrackBadge.Completed -> MaterialSymbols.Rounded.Check to IosColors.Green
        TrackBadge.Reading, null -> return
    }
    Badge(imageVector = icon, color = color, iconColor = Color.White)
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
        }
    }
}
