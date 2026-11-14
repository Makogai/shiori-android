package tachiyomi.presentation.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.roundedfilled.Cancel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.separator

/** The iOS search field: a grey rounded pill with a magnifier, placeholder and clear button. */
@Composable
fun IosSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
            // iOS 26 search fields: a white glass capsule with a hairline edge.
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.card)
            .border(0.5.dp, MaterialTheme.colorScheme.separator, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Search,
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(20.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick == null) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) onSearch(query)
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (query.isNotEmpty() && onClick == null) {
            Icon(
                imageVector = MaterialSymbols.RoundedFilled.Cancel,
                contentDescription = stringResource(MR.strings.action_reset),
                tint = secondary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
            )
        }
    }
}
