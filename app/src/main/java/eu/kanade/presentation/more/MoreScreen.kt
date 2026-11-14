package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.tabRootScrollBehavior
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ChromeReaderMode
import mihon.icons.materialsymbols.automirroredrounded.Help
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.ArrowOutward
import mihon.icons.materialsymbols.rounded.Build
import mihon.icons.materialsymbols.rounded.CloudOff
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.EmojiEvents
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Eyeglasses2
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Lock
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Storage
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.materialsymbols.roundedfilled.VolunteerActivism
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.GroupedSwitchRow
import tachiyomi.presentation.core.components.IosSearchField
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.theme.IosColors

/**
 * The Settings tab, grouped as the iOS app's Settings: quick toggles, tools, then every settings page,
 * then help and about.
 */
@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    /** Null in the plain Shiori build, which has no Arena. */
    onClickLeague: (() -> Unit)?,
    onClickSettings: (SettingsScreen.Destination) -> Unit,
    onClickSupport: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBarScrollBehavior = tabRootScrollBehavior(),
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_settings),
                largeTitle = true,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            item {
                IosSearchField(
                    query = "",
                    onQueryChange = {},
                    placeholder = stringResource(MR.strings.search_settings_hint),
                    onClick = { onClickSettings(SettingsScreen.Destination.Search) },
                )
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = 16.dp)) {
                    GroupedSwitchRow(
                        title = stringResource(MR.strings.pref_incognito_mode),
                        subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                        icon = MaterialSymbols.Rounded.Eyeglasses2,
                        iconColor = IosColors.DarkGray,
                        checked = incognitoMode,
                        onCheckedChange = onIncognitoModeChange,
                    )
                    GroupedDivider()
                    GroupedSwitchRow(
                        title = stringResource(MR.strings.label_downloaded_only),
                        subtitle = stringResource(MR.strings.downloaded_only_summary),
                        icon = MaterialSymbols.Rounded.CloudOff,
                        iconColor = IosColors.Gray,
                        checked = downloadedOnly,
                        onCheckedChange = onDownloadedOnlyChange,
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.label_download_queue),
                        subtitle = downloadQueueSubtitle(downloadQueueStateProvider()),
                        icon = MaterialSymbols.Rounded.Download,
                        iconColor = IosColors.Blue,
                        onClick = onClickDownloadQueue,
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.categories),
                        icon = MaterialSymbols.AutoMirroredRounded.Label,
                        iconColor = IosColors.Teal,
                        onClick = onClickCategories,
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.label_stats),
                        icon = MaterialSymbols.Rounded.QueryStats,
                        iconColor = IosColors.Pink,
                        onClick = onClickStats,
                    )
                    if (onClickLeague != null) {
                        GroupedDivider()
                        GroupedRow(
                            title = stringResource(MR.strings.friends_arena),
                            icon = MaterialSymbols.Rounded.EmojiEvents,
                            iconColor = IosColors.Orange,
                            onClick = onClickLeague,
                        )
                    }
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_appearance),
                        icon = MaterialSymbols.Rounded.Palette,
                        iconColor = IosColors.Blue,
                        onClick = { onClickSettings(SettingsScreen.Destination.Appearance) },
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_library),
                        icon = MaterialSymbols.Rounded.CollectionsBookmark,
                        iconColor = IosColors.Red,
                        onClick = { onClickSettings(SettingsScreen.Destination.Library) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_reader),
                        icon = MaterialSymbols.AutoMirroredRounded.ChromeReaderMode,
                        iconColor = IosColors.Green,
                        onClick = { onClickSettings(SettingsScreen.Destination.Reader) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_downloads),
                        icon = MaterialSymbols.Rounded.Download,
                        iconColor = IosColors.Teal,
                        onClick = { onClickSettings(SettingsScreen.Destination.Downloads) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.browse),
                        icon = MaterialSymbols.Rounded.Explore,
                        iconColor = IosColors.Cyan,
                        onClick = { onClickSettings(SettingsScreen.Destination.Browse) },
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_tracking),
                        icon = MaterialSymbols.Rounded.Sync,
                        iconColor = IosColors.Orange,
                        onClick = { onClickSettings(SettingsScreen.Destination.Tracking) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.label_data_storage),
                        icon = MaterialSymbols.Rounded.Storage,
                        iconColor = IosColors.Purple,
                        onClick = { onClickSettings(SettingsScreen.Destination.DataAndStorage) },
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_security),
                        icon = MaterialSymbols.Rounded.Lock,
                        iconColor = IosColors.Indigo,
                        onClick = { onClickSettings(SettingsScreen.Destination.Security) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_advanced),
                        icon = MaterialSymbols.Rounded.Build,
                        iconColor = IosColors.Gray,
                        onClick = { onClickSettings(SettingsScreen.Destination.Advanced) },
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(top = SectionGap)) {
                    GroupedRow(
                        title = stringResource(MR.strings.label_support_us),
                        icon = MaterialSymbols.RoundedFilled.VolunteerActivism,
                        iconColor = IosColors.Pink,
                        onClick = onClickSupport,
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.label_help),
                        icon = MaterialSymbols.AutoMirroredRounded.Help,
                        iconColor = IosColors.Blue,
                        chevron = false,
                        trailing = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.ArrowOutward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { uriHandler.openUri(Constants.URL_HELP) },
                    )
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(MR.strings.pref_category_about),
                        icon = MaterialSymbols.Rounded.Info,
                        iconColor = IosColors.Gray,
                        value = AboutScreen.getVersionName(buildTime = null),
                        onClick = { onClickSettings(SettingsScreen.Destination.About) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val SectionGap = 32.dp

@Composable
private fun downloadQueueSubtitle(state: DownloadQueueState): String? = when (state) {
    DownloadQueueState.Stopped -> null
    is DownloadQueueState.Paused -> {
        val pending = state.pending
        if (pending == 0) {
            stringResource(MR.strings.paused)
        } else {
            "${stringResource(MR.strings.paused)} • ${
                pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
            }"
        }
    }
    is DownloadQueueState.Downloading -> {
        val pending = state.pending
        pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
    }
}
