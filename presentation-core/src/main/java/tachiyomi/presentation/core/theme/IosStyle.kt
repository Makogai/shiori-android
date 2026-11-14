package tachiyomi.presentation.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The iOS app's grouped look on top of a theme: a grey-ish grouped background and a lifted card colour
 * (`Palette.card(dark:)` in the iOS app) used by every Material container, so cards, sheets, dialogs and
 * menus read as iOS cells.
 */
fun ColorScheme.iosGrouped(isDark: Boolean): ColorScheme {
    val groupedBackground = if (!isDark && background.luminance() > 0.93f) {
        lerp(lerp(background, Color.Black, 0.05f), primary, 0.03f)
    } else {
        background
    }
    val card = if (isDark) {
        lerp(lerp(groupedBackground, Color.White, 0.07f), primary, 0.05f)
    } else {
        lerp(groupedBackground, Color.White, 0.75f)
    }
    val fill = lerp(card, onSurface, if (isDark) 0.08f else 0.05f)
    return copy(
        background = groupedBackground,
        surface = groupedBackground,
        surfaceDim = groupedBackground,
        surfaceBright = card,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = fill,
        surfaceVariant = fill,
        outlineVariant = lerp(card, onSurface, if (isDark) 0.16f else 0.13f),
    )
}

/** Fill of grouped rows and cards (iOS `card(dark:)`). */
val ColorScheme.card: Color
    @ReadOnlyComposable
    @Composable
    get() = surfaceContainerLow

/** Hairline separators between grouped rows. */
val ColorScheme.separator: Color
    @ReadOnlyComposable
    @Composable
    get() = outlineVariant

/** The primary colour when it reads on [fill], otherwise the text colour (iOS `readableAccent(on:)`). */
fun ColorScheme.readableAccent(fill: Color = surfaceContainerLow): Color {
    val l1 = primary.luminance()
    val l2 = fill.luminance()
    val ratio = (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    return if (ratio >= 3f) primary else onSurface
}

/**
 * The iOS Dynamic Type scale at its default size: large title 34, title 1 28, title 2 22, title 3 20,
 * headline 17 semibold, body 17, callout 16, subheadline 15, footnote 13, caption 12 and 11.
 */
fun iosTypography(base: Typography = Typography()): Typography = base.copy(
    displaySmall = base.displaySmall.ios(36, 43, FontWeight.Bold, -0.4f),
    headlineLarge = base.headlineLarge.ios(34, 41, FontWeight.Bold, -0.4f),
    headlineMedium = base.headlineMedium.ios(28, 34, FontWeight.Bold, -0.3f),
    headlineSmall = base.headlineSmall.ios(22, 28, FontWeight.Bold, -0.2f),
    titleLarge = base.titleLarge.ios(20, 25, FontWeight.SemiBold, -0.2f),
    titleMedium = base.titleMedium.ios(17, 22, FontWeight.SemiBold, -0.2f),
    titleSmall = base.titleSmall.ios(15, 20, FontWeight.SemiBold, -0.1f),
    bodyLarge = base.bodyLarge.ios(17, 22, FontWeight.Normal, -0.2f),
    bodyMedium = base.bodyMedium.ios(15, 20, FontWeight.Normal, -0.1f),
    bodySmall = base.bodySmall.ios(13, 18, FontWeight.Normal, 0f),
    labelLarge = base.labelLarge.ios(15, 20, FontWeight.SemiBold, -0.1f),
    labelMedium = base.labelMedium.ios(12, 16, FontWeight.Medium, 0f),
    labelSmall = base.labelSmall.ios(11, 13, FontWeight.Medium, 0.05f),
)

private fun TextStyle.ios(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float) = copy(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** Rounder corners, as iOS cells (10), cards (16) and sheets (22). */
val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

object IosDefaults {
    /** Side inset of inset-grouped sections. */
    val SectionInset = 16.dp

    /** Corner radius of inset-grouped sections and cards. */
    val SectionRadius = 20.dp

    val CardRadius = 16.dp

    /** Leading inset of a separator after a row icon (29 icon + 14 gap + 16 padding). */
    val IconRowSeparatorInset = 59.dp

    val CoverRadius = 8.dp
}

/** Shape of row [index] of [count] rows in one inset-grouped section: only the outer corners are rounded. */
fun groupedRowShape(index: Int, count: Int, radius: Dp = IosDefaults.SectionRadius): Shape {
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == count - 1) radius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Background of row [index] of [count] in an inset-grouped section, with the separator above every row but
 * the first. Rows keep their own content padding.
 */
@Composable
fun Modifier.groupedRow(index: Int, count: Int): Modifier {
    val shape = groupedRowShape(index, count)
    return this
        .padding(horizontal = IosDefaults.SectionInset)
        .clip(shape)
        .background(MaterialTheme.colorScheme.card, shape)
}

/** A hairline separator inside an inset-grouped section. */
@Composable
fun GroupedSeparator(modifier: Modifier = Modifier, startInset: Dp = 16.dp) {
    Box(
        modifier = modifier
            .padding(horizontal = IosDefaults.SectionInset)
            .background(MaterialTheme.colorScheme.card),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(start = startInset),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.separator,
        )
    }
}

/** An inset-grouped section: rows in one rounded card, with an optional header and footer around it. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) GroupedSectionHeader(header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IosDefaults.SectionInset)
                .clip(RoundedCornerShape(IosDefaults.SectionRadius))
                .background(MaterialTheme.colorScheme.card),
            content = content,
        )
        if (footer != null) GroupedSectionFooter(footer)
    }
}

/** Header above an inset-grouped section: footnote, uppercase, secondary. */
@Composable
fun GroupedSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 7.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        trailing?.invoke()
    }
}

/** Footer under an inset-grouped section: footnote, secondary. */
@Composable
fun GroupedSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The iOS Settings icon: a white symbol on a small rounded square of its own colour. */
@Composable
fun SettingsIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 29.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

/** iOS system colours, as the Settings icons use them. */
object IosColors {
    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Indigo = Color(0xFF5856D6)
    val Orange = Color(0xFFFF9500)
    val Pink = Color(0xFFFF2D55)
    val Purple = Color(0xFFAF52DE)
    val Red = Color(0xFFFF3B30)
    val Teal = Color(0xFF30B0C7)
    val Cyan = Color(0xFF32ADE6)
    val Yellow = Color(0xFFFFCC00)
    val Gray = Color(0xFF8E8E93)
    val DarkGray = Color(0xFF4D4D4D)
}

/** Content padding of a screen made of inset-grouped sections. */
val GroupedListPadding = PaddingValues(bottom = 24.dp)

/**
 * The iOS 26 glass behind bar buttons: a translucent capsule with a hairline edge. Drawn only when the
 * content has a size, so a bar without buttons shows no empty capsule.
 */
@Composable
fun Modifier.iosGlassCapsule(): Modifier {
    val fill = MaterialTheme.colorScheme.card.copy(alpha = 0.92f)
    val edge = MaterialTheme.colorScheme.separator.copy(alpha = 0.7f)
    return drawBehind {
        if (size.width <= 0f) return@drawBehind
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = Color.Black.copy(alpha = 0.06f), topLeft = Offset(0f, 2.dp.toPx()), cornerRadius = radius)
        drawRoundRect(color = fill, cornerRadius = radius)
        drawRoundRect(color = edge, cornerRadius = radius, style = Stroke(width = 0.5.dp.toPx()))
    }
}
