package eu.kanade.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.theme.card
import tachiyomi.presentation.core.theme.separator

/** Height of [FloatingTabBar] without its bottom margin. */
val FloatingTabBarHeight = 64.dp

/** Space between [FloatingTabBar] and the bottom of the screen (above the navigation bar). */
val FloatingTabBarMargin = 10.dp

class FloatingTabItem(
    val title: String,
    val icon: Painter,
    val selected: Boolean,
    val badge: Int,
    val badgeDescription: String?,
    val onClick: () -> Unit,
)

/**
 * The iOS tab bar: a floating, translucent capsule over the content, with the selected tab on a tinted
 * pill.
 */
@Composable
fun FloatingTabBar(
    items: List<FloatingTabItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(FloatingTabBarHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.card.copy(alpha = 0.94f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.separator.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                FloatingTab(item, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FloatingTab(item: FloatingTabItem, modifier: Modifier) {
    val color by animateColorAsState(
        if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "tabColor",
    )
    val pill by animateColorAsState(
        // iOS 26: a neutral pill behind the selected tab; only its icon and label take the accent.
        if (item.selected) {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.08f,
            )
        } else {
            MaterialTheme.colorScheme.card.copy(alpha = 0f)
        },
        label = "tabPill",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Tab,
                onClick = item.onClick,
            )
            .semantics(mergeDescendants = true) { selected = item.selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(
                badge = {
                    if (item.badge > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = item.badge.toString(),
                                modifier = Modifier.semantics {
                                    item.badgeDescription?.let { contentDescription = it }
                                },
                            )
                        }
                    }
                },
            ) {
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(25.dp),
                )
            }
            Text(
                text = item.title,
                color = color,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
