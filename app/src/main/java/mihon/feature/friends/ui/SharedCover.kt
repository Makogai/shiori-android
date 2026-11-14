package mihon.feature.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.BuildConfig
import mihon.feature.friends.data.CoverRules

/**
 * A friend's cover: the shared thumbnail, then the original URL, then a title placeholder — never a broken image.
 * Size and shape come from [modifier].
 */
@Composable
fun SharedCover(coverUrl: String?, seriesKey: String?, title: String, modifier: Modifier = Modifier) {
    val candidates = remember(coverUrl, seriesKey) {
        CoverRules.candidates(coverUrl, seriesKey, BuildConfig.FRIENDS_SUPABASE_URL)
    }
    var index by remember(candidates) { mutableIntStateOf(0) }
    Box(modifier = modifier) {
        CoverPlaceholder(title = title, modifier = Modifier.matchParentSize())
        candidates.getOrNull(index)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { index++ },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A diagonal gradient with a hue from the title and its initials. */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val hue = remember(title) { CoverRules.hue(title) }
    val brush = remember(hue) {
        Brush.linearGradient(
            colors = listOf(Color.hsv(hue, 0.55f, 0.85f), Color.hsv((hue + 40f) % 360f, 0.7f, 0.5f)),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    BoxWithConstraints(modifier = modifier.background(brush), contentAlignment = Alignment.Center) {
        Text(
            text = remember(title) { CoverRules.initials(title) },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (maxWidth.value * 0.32f).coerceIn(8f, 40f).sp,
            maxLines = 1,
        )
    }
}
