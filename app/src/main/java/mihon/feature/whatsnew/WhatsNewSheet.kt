package mihon.feature.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.BuildConfig
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Book
import mihon.icons.materialsymbols.rounded.CalendarMonth
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.LocalFireDepartment
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.rounded.Mobile
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.RocketLaunch
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.GroupedSwitchRow
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.util.collectAsState

/** The release whose features the switches below belong to. */
private const val SWITCHES_VERSION = 41

private class Highlight(val icon: ImageVector, val color: Color, val title: StringResource, val detail: StringResource)

/** Highlights per versionCode, newest last; add an entry for each release that has news. */
private val notesByVersion: List<Pair<Int, List<Highlight>>> = listOf(
    41 to listOf(
        Highlight(
            MaterialSymbols.Rounded.Favorite,
            Color(0xFFFF2D55),
            MR.strings.whats_new_41_series_title,
            MR.strings.whats_new_41_series,
        ),
        Highlight(
            MaterialSymbols.Rounded.Book,
            Color(0xFF007AFF),
            MR.strings.whats_new_41_reader_title,
            MR.strings.whats_new_41_reader,
        ),
        Highlight(
            MaterialSymbols.Rounded.Photo,
            Color(0xFFAF52DE),
            MR.strings.whats_new_41_screenshots_title,
            MR.strings.whats_new_41_screenshots,
        ),
        Highlight(
            MaterialSymbols.Rounded.CalendarMonth,
            Color(0xFFFF9500),
            MR.strings.whats_new_41_recaps_title,
            MR.strings.whats_new_41_recaps,
        ),
        Highlight(
            MaterialSymbols.Rounded.LocalLibrary,
            Color(0xFF34C759),
            MR.strings.whats_new_41_library_title,
            MR.strings.whats_new_41_library,
        ),
        Highlight(
            MaterialSymbols.Rounded.Person,
            Color(0xFF5856D6),
            MR.strings.whats_new_41_onboarding_title,
            MR.strings.whats_new_41_onboarding,
        ),
    ),
    42 to listOf(
        Highlight(
            MaterialSymbols.Rounded.CalendarMonth,
            Color(0xFFFF9500),
            MR.strings.whats_new_42_recaps_title,
            MR.strings.whats_new_42_recaps,
        ),
        Highlight(
            MaterialSymbols.Rounded.Book,
            Color(0xFF007AFF),
            MR.strings.whats_new_42_menus_title,
            MR.strings.whats_new_42_menus,
        ),
    ),
    44 to listOf(
        Highlight(
            MaterialSymbols.Rounded.Photo,
            Color(0xFFAF52DE),
            MR.strings.whats_new_44_cards_title,
            MR.strings.whats_new_44_cards,
        ),
        Highlight(
            MaterialSymbols.Rounded.Info,
            Color(0xFF8E8E93),
            MR.strings.whats_new_44_version_title,
            MR.strings.whats_new_44_version,
        ),
    ),
    45 to listOf(
        Highlight(
            MaterialSymbols.Rounded.RocketLaunch,
            Color(0xFFFF9500),
            MR.strings.whats_new_45_faster_title,
            MR.strings.whats_new_45_faster,
        ),
        Highlight(
            MaterialSymbols.Rounded.Mobile,
            Color(0xFF8E8E93),
            MR.strings.whats_new_45_plain_title,
            MR.strings.whats_new_45_plain,
        ),
        Highlight(
            MaterialSymbols.Rounded.Schedule,
            Color(0xFF007AFF),
            MR.strings.whats_new_45_paged_title,
            MR.strings.whats_new_45_paged,
        ),
        Highlight(
            MaterialSymbols.Rounded.Share,
            Color(0xFFAF52DE),
            MR.strings.whats_new_45_cards_title,
            MR.strings.whats_new_45_cards,
        ),
    ),
)

/** The notes to show: every release after [sinceVersion] (so a skipped update isn't missed), else the latest. */
fun whatsNewVersions(sinceVersion: Int, all: List<Int> = notesByVersion.map { it.first }): List<Int> =
    all.filter { it > sinceVersion }.ifEmpty { all.takeLast(1) }.sortedDescending()

/** Whether to show the sheet after an update; a fresh install, which gets onboarding instead, never does. */
fun shouldShowWhatsNew(seenVersion: Int, currentVersion: Int, onboarded: Boolean): Boolean =
    onboarded && seenVersion < currentVersion && notesByVersion.any { it.first > seenVersion }

/** The release's highlights and switches for the settings it introduced, shown once after each update. */
@Composable
fun WhatsNewSheet(sinceVersion: Int = 0, onDismissRequest: () -> Unit) {
    val versions = whatsNewVersions(sinceVersion)
    val graph = LocalContext.current.appGraph
    val library = graph.libraryPreferences
    val habits = graph.habitsPreferences

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.whats_new_in_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            versions.forEach { version ->
                val highlights = notesByVersion.first { it.first == version }.second
                GroupedSection(header = if (versions.size > 1) "Version $version" else null) {
                    highlights.forEachIndexed { index, item ->
                        if (index > 0) GroupedDivider()
                        GroupedRow(
                            title = stringResource(item.title),
                            subtitle = stringResource(item.detail),
                            icon = item.icon,
                            iconColor = item.color,
                            chevron = false,
                        )
                    }
                }
            }
            // Switches only for the release that brought them, not on every later update.
            if (SWITCHES_VERSION in versions) {
                GroupedSection(
                    header = stringResource(MR.strings.whats_new_switches),
                    footer = stringResource(MR.strings.whats_new_switches_footer),
                ) {
                    PreferenceSwitch(
                        library.detailsPersonalBox,
                        MR.strings.pref_details_personal_box,
                        MaterialSymbols.Rounded.Favorite,
                        Color(0xFFFF2D55),
                    )
                    GroupedDivider()
                    PreferenceSwitch(
                        library.detailsMoreLikeThis,
                        MR.strings.pref_details_more_like_this,
                        MaterialSymbols.Rounded.Explore,
                        Color(0xFFAF52DE),
                    )
                    GroupedDivider()
                    PreferenceSwitch(
                        habits.dailyGoalEnabled,
                        MR.strings.habits_pref_goal,
                        MaterialSymbols.Rounded.LocalFireDepartment,
                        Color(0xFFFF9500),
                    )
                    GroupedDivider()
                    PreferenceSwitch(
                        habits.chapterToast,
                        MR.strings.habits_pref_toast,
                        MaterialSymbols.Rounded.Info,
                        Color(0xFF007AFF),
                    )
                }
            }
            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(stringResource(MR.strings.whats_new_done))
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(preference: Preference<Boolean>, title: StringResource, icon: ImageVector, color: Color) {
    val checked by preference.collectAsState()
    GroupedSwitchRow(
        title = stringResource(title),
        checked = checked,
        onCheckedChange = preference::set,
        icon = icon,
        iconColor = color,
    )
}
