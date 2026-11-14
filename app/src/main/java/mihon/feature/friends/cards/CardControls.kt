package mihon.feature.friends.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.IosColors
import tachiyomi.presentation.core.theme.readableAccent

/** The accent on the black maker screen, as iOS tints its buttons: white when it's too dark to read on black. */
val ColorScheme.accentOnBlack: Color
    get() = if (primary.luminance() >= 0.1f) primary else Color.White

/** The album thumbnail's glow: the rank's colour, stronger for higher ranks (iOS `CardStyle.glow`). */
fun Modifier.cardGlow(grade: CardGrade): Modifier {
    val (alpha, radius) = CardBackLayout.thumbGlow(grade)
    return dropShadow(CardCornerShape, Shadow(radius = radius.dp, color = Color(grade.rankColor).copy(alpha = alpha)))
}

/**
 * The iOS bordered button: a grey capsule with the accent's text, red for [destructive] ones. With [title] null only
 * the icon shows, and [label] names it for TalkBack.
 */
@Composable
fun CardBorderedButton(
    title: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    label: String? = title,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    content: Color = if (destructive) IosColors.Red else MaterialTheme.colorScheme.readableAccent(),
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = content),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
    ) {
        CardButtonLabel(title, icon, label)
    }
}

/** The iOS prominent, large button: an accent capsule with a headline title. */
@Composable
fun CardProminentButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 50.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        CardButtonLabel(title, icon, title, MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CardButtonLabel(
    title: String?,
    icon: ImageVector?,
    label: String?,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = if (title == null) label else null,
                modifier = Modifier.size(18.dp),
            )
        }
        if (title != null) {
            Text(
                text = title,
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Pulled today 3/10" in a capsule tinted with the accent, as at the top of the iOS hub. */
@Composable
fun PulledTodayPill(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(MR.strings.cards_pulled_today, count, CardRules.DAILY_LIMIT),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
