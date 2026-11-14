package mihon.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import mihon.feature.common.utils.seriesKey
import mihon.feature.ratings.PersonalRatings
import mihon.feature.rhythm.ReleaseRhythm
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.EmojiEvents
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.FormatListNumbered
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.core.common.preference.toggle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.GroupedSwitchRow
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import java.time.DayOfWeek

/**
 * The details page's own section, under the description: AniList's "More like this" first, then how often the
 * series updates, the user's score, Up next and top lists, and page bookmarks.
 */
@Composable
fun MangaExtrasSection(
    mangaId: Long,
    onOpenPage: (chapterId: Long, page: Int) -> Unit,
    onSearch: (String) -> Unit,
    onManageTopLists: () -> Unit,
) {
    val viewModel = assistedMetroViewModel<MangaExtrasViewModel, MangaExtrasViewModel.Factory> { create(mangaId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recommended by viewModel.recommended.collectAsStateWithLifecycle()
    val showPersonalBox by viewModel.showPersonalBox.collectAsState()
    val showMoreLikeThis by viewModel.showMoreLikeThis.collectAsState()
    val showEnglishTitles by viewModel.showEnglishTitles.collectAsState()
    val expanded by viewModel.moreLikeThisExpanded.collectAsState()
    val current = state ?: return
    var showScore by rememberSaveable { mutableStateOf(false) }
    var showLists by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        if (showMoreLikeThis && !current.manga.isLocal()) {
            MoreLikeThisSection(
                state = recommended,
                expanded = expanded,
                showEnglishTitles = showEnglishTitles,
                onToggle = { viewModel.moreLikeThisExpanded.toggle() },
                onLoad = viewModel::loadRecommendations,
                onSearch = onSearch,
            )
        }

        if (showPersonalBox) {
            PersonalBox(current, onScore = { showScore = true }, onQueue = viewModel::toggleQueue) { showLists = true }
        }

        if (current.bookmarks.isNotEmpty()) {
            GroupedSection(header = stringResource(MR.strings.page_bookmarks)) {
                current.bookmarks.forEachIndexed { index, item ->
                    if (index > 0) GroupedDivider()
                    val bookmark = item.bookmark
                    val page = stringResource(MR.strings.page_bookmark_page, bookmark.page + 1)
                    GroupedRow(
                        title = bookmark.chapterName,
                        subtitle = if (bookmark.note.isBlank()) page else "$page · ${bookmark.note}",
                        icon = MaterialSymbols.Rounded.Bookmark,
                        chevron = false,
                        onClick = item.chapterId?.let { id -> { onOpenPage(id, bookmark.page) } },
                        trailing = {
                            IconButton(onClick = { viewModel.removeBookmark(bookmark) }) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Delete,
                                    contentDescription = stringResource(MR.strings.action_delete),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showScore) {
        ScoreDialog(
            score = current.score,
            onSet = {
                viewModel.setScore(it)
                showScore = false
            },
            onDismiss = { showScore = false },
        )
    }
    if (showLists) {
        val key = current.manga.seriesKey
        TopListsDialog(
            lists = current.topLists.map { it.id to it.name },
            checked = current.topLists.filter { key in it.entries }.map { it.id }.toSet(),
            onToggle = viewModel::toggleTopList,
            onCreate = viewModel::createTopList,
            onManage = {
                showLists = false
                onManageTopLists()
            },
            onDismiss = { showLists = false },
        )
    }
}

@Composable
private fun PersonalBox(
    current: MangaExtrasViewModel.State,
    onScore: () -> Unit,
    onQueue: () -> Unit,
    onLists: () -> Unit,
) {
    GroupedSection {
        current.rhythm?.let { rhythm ->
            GroupedRow(
                title = rhythmText(rhythm),
                icon = MaterialSymbols.Rounded.Schedule,
                iconColor = Color(0xFF34C759),
            )
            GroupedDivider()
        }
        GroupedRow(
            title = stringResource(MR.strings.personal_score),
            icon = MaterialSymbols.Rounded.Favorite,
            iconColor = Color(0xFFFF2D55),
            value = current.score?.let { stringResource(MR.strings.personal_score_value, it) }
                ?: stringResource(MR.strings.personal_score_none),
            onClick = onScore,
        )
        GroupedDivider()
        GroupedSwitchRow(
            title = stringResource(MR.strings.binge_queue_title),
            checked = current.inQueue,
            onCheckedChange = { onQueue() },
            icon = MaterialSymbols.Rounded.FormatListNumbered,
            iconColor = Color(0xFF5856D6),
        )
        GroupedDivider()
        val key = current.manga.seriesKey
        GroupedRow(
            title = stringResource(MR.strings.top_lists),
            icon = MaterialSymbols.Rounded.EmojiEvents,
            iconColor = Color(0xFFFF9500),
            value = current.topLists.filter { key in it.entries }.joinToString { it.name }.ifEmpty { null },
            onClick = onLists,
        )
    }
}

@Composable
private fun rhythmText(rhythm: ReleaseRhythm): String = when (rhythm) {
    is ReleaseRhythm.Weekly -> stringResource(
        when (rhythm.day) {
            DayOfWeek.MONDAY -> MR.strings.rhythm_weekly_monday
            DayOfWeek.TUESDAY -> MR.strings.rhythm_weekly_tuesday
            DayOfWeek.WEDNESDAY -> MR.strings.rhythm_weekly_wednesday
            DayOfWeek.THURSDAY -> MR.strings.rhythm_weekly_thursday
            DayOfWeek.FRIDAY -> MR.strings.rhythm_weekly_friday
            DayOfWeek.SATURDAY -> MR.strings.rhythm_weekly_saturday
            DayOfWeek.SUNDAY -> MR.strings.rhythm_weekly_sunday
        },
    )
    is ReleaseRhythm.EveryNDays -> when (rhythm.days) {
        1 -> stringResource(MR.strings.rhythm_daily)
        in 28..31 -> stringResource(MR.strings.rhythm_monthly)
        else -> stringResource(MR.strings.rhythm_every_n_days, rhythm.days)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreDialog(
    score: Int?,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.personal_score)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 5,
            ) {
                (PersonalRatings.MIN..PersonalRatings.MAX).forEach { value ->
                    FilterChip(
                        selected = value == score,
                        onClick = { onSet(value) },
                        label = { Text("$value") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
        dismissButton = {
            if (score != null) {
                TextButton(onClick = { onSet(null) }) { Text(stringResource(MR.strings.personal_score_clear)) }
            }
        },
    )
}

@Composable
private fun TopListsDialog(
    lists: List<Pair<Long, String>>,
    checked: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val newList = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.top_lists)) },
        text = {
            Column {
                lists.forEach { (id, name) ->
                    LabeledCheckbox(label = name, checked = id in checked, onCheckedChange = { onToggle(id) })
                }
                OutlinedTextField(
                    state = newList,
                    label = { Text(stringResource(MR.strings.top_list_new)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = newList.text.toString()
                    if (name.isNotBlank()) onCreate(name)
                    onDismiss()
                },
            ) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onManage) { Text(stringResource(MR.strings.top_lists_manage)) }
        },
    )
}
