package mihon.feature.autoscroll

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** The long strip's auto-scroll speeds: the series' own (set here while reading it) and the default. */
@Composable
fun ColumnScope.AutoScrollSettings(viewModel: ReaderSettingsViewModel) {
    val preferences = viewModel.preferences
    val manga by viewModel.mangaFlow.collectAsState()
    val default by preferences.autoScrollSpeed.collectAsState()
    val serialized by preferences.seriesAutoScrollSpeeds.collectAsState()
    val speeds = remember(serialized) { AutoScrollSpeed.deserialize(serialized) }
    val mangaId = manga?.id

    if (mangaId != null) {
        SliderItem(
            value = AutoScrollSpeed.forSeries(speeds, mangaId, default),
            valueRange = AutoScrollSpeed.levels,
            label = stringResource(MR.strings.auto_scroll_series_speed),
            onChange = { preferences.seriesAutoScrollSpeeds.set(AutoScrollSpeed.serialize(speeds + (mangaId to it))) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (mangaId in speeds) {
            TextButton(
                onClick = { preferences.seriesAutoScrollSpeeds.set(AutoScrollSpeed.serialize(speeds - mangaId)) },
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
            ) {
                Text(stringResource(MR.strings.auto_scroll_use_default))
            }
        }
    }
    SliderItem(
        value = default,
        valueRange = AutoScrollSpeed.levels,
        label = stringResource(MR.strings.auto_scroll_default_speed),
        onChange = preferences.autoScrollSpeed::set,
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
