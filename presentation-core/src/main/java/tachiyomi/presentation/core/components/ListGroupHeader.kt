package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.theme.readableAccent

@Composable
fun ListGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = 14.dp,
                bottom = 4.dp,
            ),
        // iOS list headers in Recents: subheadline, semibold, in the accent colour.
        color = MaterialTheme.colorScheme.readableAccent(MaterialTheme.colorScheme.background),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}
