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
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** The long strip's auto-scroll speeds: the series' own (set here while reading it) and the default. */
@Composable
fun ColumnScope.AutoScrollSettings(viewModel: ReaderSettingsViewModel) {
    SeriesAndDefaultSliders(
        viewModel = viewModel,
        default = viewModel.preferences.autoScrollSpeed,
        series = viewModel.preferences.seriesAutoScrollSpeeds,
        range = AutoScrollSpeed.levels,
        seriesLabel = MR.strings.auto_scroll_series_speed,
        defaultLabel = MR.strings.auto_scroll_default_speed,
        useDefaultLabel = MR.strings.auto_scroll_use_default,
    )
}

/** The paged reader's auto page-turn intervals: the series' own and the default. */
@Composable
fun ColumnScope.AutoPageSettings(viewModel: ReaderSettingsViewModel) {
    SeriesAndDefaultSliders(
        viewModel = viewModel,
        default = viewModel.preferences.autoPageInterval,
        series = viewModel.preferences.seriesAutoPageIntervals,
        range = AutoPageInterval.seconds,
        seriesLabel = MR.strings.auto_page_series_interval,
        defaultLabel = MR.strings.auto_page_default_interval,
        useDefaultLabel = MR.strings.auto_page_use_default,
    )
}

@Composable
private fun ColumnScope.SeriesAndDefaultSliders(
    viewModel: ReaderSettingsViewModel,
    default: Preference<Int>,
    series: Preference<String>,
    range: IntRange,
    seriesLabel: StringResource,
    defaultLabel: StringResource,
    useDefaultLabel: StringResource,
) {
    val manga by viewModel.mangaFlow.collectAsState()
    val defaultValue by default.collectAsState()
    val serialized by series.collectAsState()
    val values = remember(serialized, range) { AutoScrollSpeed.deserialize(serialized, range) }
    val mangaId = manga?.id

    if (mangaId != null) {
        SliderItem(
            value = (values[mangaId] ?: defaultValue).coerceIn(range),
            valueRange = range,
            label = stringResource(seriesLabel),
            onChange = { series.set(AutoScrollSpeed.serialize(values + (mangaId to it))) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (mangaId in values) {
            TextButton(
                onClick = { series.set(AutoScrollSpeed.serialize(values - mangaId)) },
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
            ) {
                Text(stringResource(useDefaultLabel))
            }
        }
    }
    SliderItem(
        value = defaultValue,
        valueRange = range,
        label = stringResource(defaultLabel),
        onChange = default::set,
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
