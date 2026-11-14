package mihon.feature.tracklibrary

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/** Settings → Tracking, the Library overflow menu and the post-login offer; [trackerId] preselects a tracker. */
class TrackLibraryScreen(private val trackerId: Long? = null) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<TrackLibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val sheet by viewModel.sheet.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        var trackedExpanded by rememberSaveable { mutableStateOf(false) }
        var confirmAll by remember { mutableStateOf(false) }
        var unlink by remember { mutableStateOf<TrackedRow?>(null) }

        LaunchedEffect(Unit) {
            trackerId?.let(viewModel::selectTracker)
            viewModel.messages.collect { message ->
                val text = when (message) {
                    is LibraryTrackMatcher.Message.BindFailed ->
                        context.stringResource(MR.strings.track_library_link_failed, message.title, message.error ?: "")
                    is LibraryTrackMatcher.Message.BulkDone ->
                        context.stringResource(MR.strings.track_library_bulk_done, message.linked, message.total)
                }
                snackbarHostState.showSnackbar(text)
            }
        }

        val tracker = state.trackers.find { it.id == state.selectedTrackerId }
        val content = state.content

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.track_library_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (state.loaded && tracker == null) {
                NoTrackers(
                    padding = padding,
                    onOpenSettings = { navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking)) },
                )
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(
                        state = state,
                        onSelectTracker = viewModel::selectTracker,
                        onStop = viewModel::stop,
                        onResume = viewModel::resume,
                        onConfirmAll = { confirmAll = true },
                        onShowSkipped = viewModel::setShowSkipped,
                    )
                }
                item(key = "needs_title") {
                    SectionTitle(stringResource(MR.strings.track_library_needs))
                }
                if (content.needs.isEmpty() && tracker != null) {
                    item(key = "needs_empty") {
                        Text(
                            text = stringResource(MR.strings.track_library_all_tracked, tracker.name),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).secondaryItemAlpha(),
                        )
                    }
                }
                items(content.needs, key = { "needs_${it.manga.id}" }) { row ->
                    NeedsItem(
                        row = row,
                        onConfirm = { viewModel.confirm(row) },
                        onOtherResults = { viewModel.openSheet(row.manga, row.subject) },
                        onSkip = { viewModel.setSkipped(row, !row.skipped) },
                        onRetry = { viewModel.retry(row) },
                    )
                }
                item(key = "tracked_title") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { trackedExpanded = !trackedExpanded }
                            .padding(end = 16.dp),
                    ) {
                        SectionTitle(
                            stringResource(MR.strings.track_library_tracked, content.tracked.size),
                            Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (trackedExpanded) {
                                MaterialSymbols.Rounded.KeyboardArrowUp
                            } else {
                                MaterialSymbols.Rounded.KeyboardArrowDown
                            },
                            contentDescription = null,
                        )
                    }
                }
                if (trackedExpanded) {
                    items(content.tracked, key = { "tracked_${it.manga.id}" }) { row ->
                        TrackedItem(
                            row = row,
                            onChange = { viewModel.openSheet(row.manga, row.subject) },
                            onUnlink = { unlink = row },
                        )
                    }
                }
            }
        }

        sheet?.let { current ->
            if (tracker != null) {
                OtherResultsSheet(
                    sheet = current,
                    tracker = tracker,
                    onSearch = viewModel::searchInSheet,
                    onPick = viewModel::pickInSheet,
                    onDismiss = viewModel::closeSheet,
                )
            }
        }

        if (confirmAll && tracker != null) {
            val count = content.strongMatches.size
            AlertDialog(
                onDismissRequest = { confirmAll = false },
                title = { Text(stringResource(MR.strings.track_library_confirm_strong_title, count, tracker.name)) },
                text = { Text(stringResource(MR.strings.track_library_confirm_strong_text, tracker.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.confirmStrongMatches()
                            confirmAll = false
                        },
                    ) { Text(stringResource(MR.strings.track_library_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmAll = false }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }

        unlink?.let { row ->
            AlertDialog(
                onDismissRequest = { unlink = null },
                title = { Text(stringResource(MR.strings.track_library_unlink_title, row.manga.title)) },
                text = { Text(stringResource(MR.strings.track_library_unlink_text, tracker?.name.orEmpty())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.unlink(row)
                            unlink = null
                        },
                    ) { Text(stringResource(MR.strings.track_library_unlink)) }
                },
                dismissButton = {
                    TextButton(onClick = { unlink = null }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun NoTrackers(padding: PaddingValues, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(MR.strings.track_library_no_trackers),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(MR.strings.track_library_open_settings)) }
    }
}

@Composable
private fun Header(
    state: TrackLibraryViewModel.State,
    onSelectTracker: (Long) -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onConfirmAll: () -> Unit,
    onShowSkipped: (Boolean) -> Unit,
) {
    val content = state.content
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            state.trackers.forEach { tracker ->
                FilterChip(
                    selected = tracker.id == state.selectedTrackerId,
                    onClick = { onSelectTracker(tracker.id) },
                    label = { Text(tracker.name) },
                    leadingIcon = { TrackerLogo(tracker) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                MR.strings.track_library_counts,
                content.total,
                content.tracked.size,
                content.toCheck,
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        val pending = content.pending
        if (pending > 0 || state.paused) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.paused) {
                    val left = content.needs.count { it.state == null && !it.skipped }
                    Text(
                        text = stringResource(MR.strings.track_library_stopped, left),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onResume) { Text(stringResource(MR.strings.track_library_resume)) }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(MR.strings.track_library_checking, pending),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = onStop) { Text(stringResource(MR.strings.track_library_stop)) }
                }
            }
        }
        val strong = content.strongMatches.size
        FilledTonalButton(
            onClick = onConfirmAll,
            enabled = strong > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(MR.strings.track_library_confirm_strong, strong))
        }
        if (content.skippedCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowSkipped(!state.showSkipped) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.track_library_show_skipped, content.skippedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.showSkipped, onCheckedChange = onShowSkipped)
            }
        }
    }
}

@Composable
private fun TrackerLogo(tracker: Tracker) {
    Image(
        painter = painterResource(tracker.getLogo()),
        contentDescription = null,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun NeedsItem(
    row: NeedsRow,
    onConfirm: () -> Unit,
    onOtherResults: () -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (row.skipped) Modifier.secondaryItemAlpha() else Modifier),
    ) {
        OurEntry(row.manga)
        Spacer(Modifier.height(8.dp))
        val best = row.best
        when (val state = row.state) {
            null, MatchState.Queued -> StatusLine(stringResource(MR.strings.track_library_waiting))
            MatchState.Searching -> StatusLine(stringResource(MR.strings.track_library_searching), progress = true)
            is MatchState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLine(
                    text = listOfNotNull(stringResource(MR.strings.track_library_search_failed), state.message)
                        .joinToString(": "),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(MR.strings.action_retry)) }
            }
            is MatchState.Found -> if (best == null) {
                StatusLine(stringResource(MR.strings.track_library_no_match))
            } else {
                CandidateCard(best)
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (row.binding) {
                StatusLine(stringResource(MR.strings.track_library_linking), progress = true, Modifier.weight(1f))
            }
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(if (row.skipped) MR.strings.track_library_unskip else MR.strings.track_library_skip),
                )
            }
            TextButton(onClick = onOtherResults, enabled = !row.binding) {
                Text(stringResource(MR.strings.track_library_other_results))
            }
            if (best != null) {
                Button(onClick = onConfirm, enabled = !row.binding) {
                    Text(stringResource(MR.strings.track_library_confirm))
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun OurEntry(manga: Manga, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MangaCover.Book(data = manga.asMangaCover(), modifier = Modifier.height(56.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.secondaryItemAlpha(),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, progress: Boolean = false, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.heightIn(min = 32.dp)) {
        if (progress) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.secondaryItemAlpha())
    }
}

@Composable
private fun CandidateCard(candidate: MatchCandidate, modifier: Modifier = Modifier) {
    val result = candidate.result
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaCover.Book(data = result.cover_url, modifier = Modifier.height(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = candidateMeta(result)
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ScoreChip(candidate)
        }
    }
}

@Composable
private fun candidateMeta(result: TrackSearch): String {
    val chapters = result.total_chapters.takeIf { it > 0 }
        ?.let { stringResource(MR.strings.track_library_chapters, it.toInt()) }
    return listOfNotNull(
        result.start_date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
        result.publishing_type.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
        result.publishing_status.takeIf { it.isNotBlank() }?.lowercase()?.replaceFirstChar { it.uppercase() },
        chapters,
    ).joinToString(" · ")
}

@Composable
private fun ScoreChip(candidate: MatchCandidate) {
    val (container, onContainer) = when {
        candidate.isStrong -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        candidate.score >= WEAK_MATCH ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = onContainer) {
        Text(
            text = stringResource(MR.strings.track_library_score, candidate.score),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TrackedItem(row: TrackedRow, onChange: () -> Unit, onUnlink: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        val progress = row.track.lastChapterRead.takeIf { it > 0 }
            ?.let { stringResource(MR.strings.track_library_progress, it.toString().removeSuffix(".0")) }
        OurEntry(row.manga, listOfNotNull(row.track.title, progress).joinToString(" · "))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onUnlink) { Text(stringResource(MR.strings.track_library_unlink)) }
            TextButton(onClick = onChange) { Text(stringResource(MR.strings.track_library_change)) }
        }
        HorizontalDivider()
    }
}

@Composable
private fun OtherResultsSheet(
    sheet: TrackLibraryViewModel.Sheet,
    tracker: Tracker,
    onSearch: (String) -> Unit,
    onPick: (TrackSearch) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismiss) {
        val query = rememberTextFieldState(sheet.query)
        LaunchedEffect(sheet.query) {
            if (query.text.toString() != sheet.query) query.setTextAndPlaceCursorAtEnd(sheet.query)
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                text = sheet.manga.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                state = query,
                label = { Text(stringResource(MR.strings.track_library_search_hint, tracker.name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { onSearch(query.text.toString()) },
                trailingIcon = {
                    IconButton(onClick = { onSearch(query.text.toString()) }) {
                        Icon(
                            MaterialSymbols.Rounded.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val results = sheet.results
            when {
                results == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                results.isEmpty() -> StatusLine(sheet.error ?: stringResource(MR.strings.no_results_found))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 520.dp),
                ) {
                    items(results, key = { it.result.remote_id }) { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(candidate.result) },
                        )
                    }
                }
            }
        }
    }
}

/** Below this a candidate is likely a different series; the chip turns red. */
private const val WEAK_MATCH = 70
