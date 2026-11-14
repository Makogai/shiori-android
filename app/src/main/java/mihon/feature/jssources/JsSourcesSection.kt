package mihon.feature.jssources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection

@Immutable
data class JsSourceRow(
    val info: JsExtensionInfo,
    val installed: Boolean,
    val hasUpdate: Boolean,
    /** The saved icon file, or the repository's URL before install. */
    val icon: Any?,
    val busy: Boolean,
    /** The index entry to install or update to; null when the repository no longer lists it. */
    val update: JsExtensionInfo?,
)

@Immutable
data class JsSourcesUiState(
    val rows: List<JsSourceRow> = emptyList(),
    val broken: List<JsExtensionManager.Broken> = emptyList(),
) {
    val isEmpty get() = rows.isEmpty() && broken.isEmpty()
}

/** "Our sources (JS)": the JavaScript extensions the iOS app runs too, with install, update and uninstall. */
@Composable
fun JsSourcesSection(
    state: JsSourcesUiState,
    onInstall: (JsExtensionInfo) -> Unit,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmUninstall by remember { mutableStateOf<JsSourceRow?>(null) }

    GroupedSection(
        modifier = modifier.padding(bottom = 8.dp),
        header = stringResource(MR.strings.js_sources_header),
        footer = stringResource(MR.strings.js_sources_footer),
    ) {
        state.rows.forEachIndexed { index, row ->
            if (index > 0) GroupedDivider()
            JsSourceRowContent(
                title = row.info.name,
                subtitle = listOfNotNull(
                    row.info.versionName,
                    row.info.lang.uppercase(),
                    stringResource(MR.strings.js_sources_nsfw).takeIf { row.info.nsfw },
                ).joinToString(" · "),
                icon = row.icon,
            ) {
                when {
                    row.busy -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    !row.installed -> TextButton(onClick = { row.update?.let(onInstall) }) {
                        Text(stringResource(MR.strings.ext_install))
                    }
                    else -> Row {
                        if (row.hasUpdate) {
                            TextButton(onClick = { row.update?.let(onInstall) }) {
                                Text(stringResource(MR.strings.ext_update))
                            }
                        }
                        TextButton(onClick = { confirmUninstall = row }) {
                            Text(stringResource(MR.strings.ext_uninstall), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        state.broken.forEachIndexed { index, broken ->
            if (index > 0 || state.rows.isNotEmpty()) GroupedDivider()
            JsSourceRowContent(
                title = broken.pkg,
                subtitle = stringResource(MR.strings.js_sources_not_loaded, broken.reason),
                icon = null,
            ) {
                TextButton(onClick = { onUninstall(broken.pkg) }) {
                    Text(stringResource(MR.strings.ext_uninstall), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    confirmUninstall?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text(stringResource(MR.strings.ext_confirm_remove)) },
            text = { Text(row.info.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstall(row.info.pkg)
                        confirmUninstall = null
                    },
                ) {
                    Text(stringResource(MR.strings.ext_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

/** A grouped row as [tachiyomi.presentation.core.components.GroupedRow] lays it out, with the extension's icon. */
@Composable
private fun JsSourceRowContent(
    title: String,
    subtitle: String,
    icon: Any?,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            placeholder = ColorPainter(Color(0x1F888888)),
            error = ColorPainter(Color(0x1F888888)),
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(percent = 22)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
