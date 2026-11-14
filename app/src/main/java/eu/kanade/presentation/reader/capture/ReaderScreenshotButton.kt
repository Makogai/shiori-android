package eu.kanade.presentation.reader.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStrip
import mihon.icons.custommaterialsymbols.rounded.ScreenshotFrame
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Image
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Photo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The reader menu's screenshot button: a long screenshot over the strip, a styled one of the screen, a Shiori card
 * of the screen (when Friends is set up), or the page as is (the long tap page actions).
 */
@Composable
fun ReaderScreenshotButton(
    onLongScreenshot: () -> Unit,
    onStyledScreenshot: () -> Unit,
    onPageAsIs: () -> Unit,
    onMakeCard: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = CustomMaterialSymbols.Rounded.ScreenshotFrame,
                contentDescription = stringResource(MR.strings.screenshot),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.screenshot_long)) },
                leadingIcon = { Icon(CustomMaterialSymbols.Rounded.ReaderLongStrip, contentDescription = null) },
                onClick = {
                    expanded = false
                    onLongScreenshot()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.screenshot_styled)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Palette, contentDescription = null) },
                onClick = {
                    expanded = false
                    onStyledScreenshot()
                },
            )
            if (onMakeCard != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.screenshot_make_card)) },
                    leadingIcon = { Icon(MaterialSymbols.Rounded.Photo, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMakeCard()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.screenshot_page_as_is)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Image, contentDescription = null) },
                onClick = {
                    expanded = false
                    onPageAsIs()
                },
            )
        }
    }
}
