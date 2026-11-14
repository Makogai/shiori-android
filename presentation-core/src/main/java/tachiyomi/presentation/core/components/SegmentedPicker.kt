package tachiyomi.presentation.core.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.theme.card

/** The iOS segmented control: equal segments on a grey track, the selected one on a raised thumb. */
@Composable
fun SegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumb = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f) else MaterialTheme.colorScheme.card
    val shape = RoundedCornerShape(9.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(track)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segmentWidth * selectedIndex, label = "segmentThumb")
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(if (dark) 0.dp else 2.dp, RoundedCornerShape(7.dp))
                .background(thumb, RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .semantics { selected = index == selectedIndex },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        fontSize = 13.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
