package eu.kanade.presentation.reader.capture

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.reader.capture.ShotInfo
import eu.kanade.tachiyomi.ui.reader.capture.ShotLayout
import eu.kanade.tachiyomi.ui.reader.capture.StyledShotRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The styled screenshot's preview: the shot with its label, the label's layout, then save, share or make a card.
 */
@Composable
fun StyledShotDialog(
    request: StyledShotRequest,
    layout: ShotLayout,
    onLayoutChange: (ShotLayout) -> Unit,
    loadCover: suspend () -> Bitmap?,
    render: suspend (image: Bitmap, info: ShotInfo, cover: Bitmap?, layout: ShotLayout, accent: Int) -> Bitmap,
    onDismissRequest: () -> Unit,
    onSave: (Bitmap) -> Unit,
    onShare: (Bitmap) -> Unit,
    onMakeCard: ((Bitmap) -> Unit)?,
) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val cover by produceState<Bitmap?>(null, request) { value = loadCover() }
    val rendered by produceState<Bitmap?>(null, request, layout, cover, accent) {
        value = render(request.image, request.info, cover, layout, accent)
    }
    val preview by produceState<Bitmap?>(null, rendered) {
        value = rendered?.let { withContext(Dispatchers.Default) { previewOf(it) } }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.screenshot),
                    navigateUp = onDismissRequest,
                    navigationIcon = MaterialSymbols.Rounded.Close,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val scroll = rememberScrollState()
                // Opens on the bottom, where the label is
                LaunchedEffect(preview) { scroll.scrollTo(scroll.maxValue) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = remember(preview) { preview?.asImageBitmap() }
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    }
                }

                Text(
                    text = stringResource(MR.strings.screenshot_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    LAYOUTS.forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = option == layout,
                            onClick = { onLayoutChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, LAYOUTS.size),
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val shot = rendered
                    OutlinedButton(
                        onClick = { shot?.let(onSave) },
                        enabled = shot != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(MaterialSymbols.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(MR.strings.action_save), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (onMakeCard != null) {
                        FilledTonalButton(
                            onClick = { shot?.let(onMakeCard) },
                            enabled = shot != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                MaterialSymbols.Rounded.Photo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(MR.strings.screenshot_make_card),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Button(
                        onClick = { shot?.let(onShare) },
                        enabled = shot != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(MaterialSymbols.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(MR.strings.action_share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Small enough to draw: a long screenshot can be taller than a GPU texture may be. */
private fun previewOf(bitmap: Bitmap): Bitmap {
    val scale = min(1f, min(MAX_PREVIEW_WIDTH / bitmap.width, MAX_PREVIEW_HEIGHT / bitmap.height))
    if (scale >= 1f) return bitmap
    return bitmap.scale(
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
    )
}

private const val MAX_PREVIEW_WIDTH = 1080f
private const val MAX_PREVIEW_HEIGHT = 4096f

private val LAYOUTS = listOf(
    ShotLayout.FOOTER to MR.strings.screenshot_label_footer,
    ShotLayout.CHIP to MR.strings.screenshot_label_chip,
    ShotLayout.NONE to MR.strings.screenshot_label_none,
)
