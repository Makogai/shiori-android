package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStrip
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Crop
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    isLongStrip: Boolean,
    onStyledScreenshot: () -> Unit,
    onLongScreenshot: () -> Unit,
    onMakeCard: (() -> Unit)?,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    val longScreenshotTitle = if (isLongStrip) {
        MR.strings.screenshot_long_from_here
    } else {
        MR.strings.screenshot_capture_part
    }
    val longScreenshotIcon = if (isLongStrip) {
        CustomMaterialSymbols.Rounded.ReaderLongStrip
    } else {
        MaterialSymbols.Rounded.Crop
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = MaterialSymbols.Rounded.Photo,
                    onClick = { showSetCoverDialog = true },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_copy_to_clipboard),
                    icon = MaterialSymbols.Rounded.ContentCopy,
                    onClick = {
                        onShare(true)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = MaterialSymbols.Rounded.Share,
                    onClick = {
                        onShare(false)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = MaterialSymbols.Rounded.Save,
                    onClick = {
                        onSave()
                        onDismissRequest()
                    },
                )
            }
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.screenshot_styled),
                    icon = MaterialSymbols.Rounded.Palette,
                    onClick = {
                        onDismissRequest()
                        onStyledScreenshot()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(longScreenshotTitle),
                    icon = longScreenshotIcon,
                    onClick = {
                        onDismissRequest()
                        onLongScreenshot()
                    },
                )
                if (onMakeCard != null) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(MR.strings.screenshot_make_card),
                        icon = MaterialSymbols.Rounded.Photo,
                        onClick = {
                            onDismissRequest()
                            onMakeCard()
                        },
                    )
                }
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover()
                showSetCoverDialog = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
