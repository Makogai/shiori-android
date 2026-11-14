package tachiyomi.presentation.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import tachiyomi.presentation.core.theme.IosDefaults
import tachiyomi.presentation.core.theme.SettingsIcon

/**
 * A row of an inset-grouped list, as in the iOS Settings app: an optional coloured icon, title and
 * subtitle, a trailing value and a disclosure chevron when it opens a page.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    value: String? = null,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon = icon, color = iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        trailing?.invoke()
        if (chevron && onClick != null) {
            Icon(
                imageVector = MaterialSymbols.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(22.dp),
            )
        }
    }
}

/** A [GroupedRow] with an iOS style switch. */
@Composable
fun GroupedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    GroupedRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        chevron = false,
        modifier = modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
        trailing = {
            IosSwitch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    )
}

/** A Material switch coloured as the iOS one: accent track when on, grey track and white thumb otherwise. */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = iosSwitchColors(),
        thumbContent = null,
    )
}

@Composable
fun iosSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
    uncheckedBorderColor = Color.Transparent,
)

/** Leading inset of the separator under a [GroupedRow] with an icon. */
val GroupedRowIconInset = IosDefaults.IconRowSeparatorInset

/** Hairline between rows inside a [tachiyomi.presentation.core.theme.GroupedSection]. */
@Composable
fun GroupedDivider(startInset: androidx.compose.ui.unit.Dp = GroupedRowIconInset) {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = startInset),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
