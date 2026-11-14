package mihon.feature.sourcehealth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.app.di.appGraph
import mihon.domain.source.health.SourceOperation
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.HourglassEmpty
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Security
import mihon.icons.materialsymbols.roundedfilled.Cancel
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import mihon.icons.materialsymbols.roundedfilled.Dangerous
import mihon.icons.materialsymbols.roundedfilled.Error
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * How each source the library uses has been doing: its status from recent requests, the last success, what went
 * wrong, and what to do about it (check again, pass a Cloudflare check in a WebView, or migrate its series).
 */
class SourceHealthScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<SourceHealthViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        // Composed again when the WebView pushed on top of this screen closes
        LaunchedEffect(Unit) { viewModel.onResume() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.source_health),
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.source_health_check_all),
                                    icon = MaterialSymbols.Rounded.Refresh,
                                    onClick = { viewModel.check() },
                                    enabled = state.items.none { it.checking },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.items.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.source_health_empty,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> SourceHealthContent(
                    state = state,
                    contentPadding = contentPadding,
                    onClickCheck = viewModel::check,
                    onClickWebView = { item ->
                        val url = item.homeUrl ?: return@SourceHealthContent
                        viewModel.openedWebView(item.source.id)
                        navigator.push(WebViewScreen(url, item.source.name, item.source.id))
                    },
                    onClickMigrate = { navigator.push(MigrationConfigScreen(it.mangaIds)) },
                )
            }
        }
    }
}

@Composable
private fun SourceHealthContent(
    state: SourceHealthViewModel.State,
    contentPadding: PaddingValues,
    onClickCheck: (Long) -> Unit,
    onClickWebView: (SourceHealthViewModel.Item) -> Unit,
    onClickMigrate: (SourceHealthViewModel.Item) -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(state.items, key = { it.source.id }) { item ->
            SourceHealthItem(
                item = item,
                onClickCheck = { onClickCheck(item.source.id) },
                onClickWebView = { onClickWebView(item) },
                onClickMigrate = { onClickMigrate(item) },
            )
        }
        item(key = "footer") {
            val footer = buildString {
                append(
                    stringResource(
                        if (state.dailyCheck) MR.strings.source_health_footer else MR.strings.source_health_footer_off,
                    ),
                )
                state.lastCheck?.let {
                    append(" ", stringResource(MR.strings.source_health_last_check, relativeTimeSpanString(it)))
                }
            }
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MaterialTheme.padding.medium),
            )
        }
    }
}

@Composable
private fun SourceHealthItem(
    item: SourceHealthViewModel.Item,
    onClickCheck: () -> Unit,
    onClickWebView: () -> Unit,
    onClickMigrate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(source = item.source)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = item.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(item.status)
            }
            if (item.checking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Column(
            modifier = Modifier.padding(top = MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val details = buildList {
                add(
                    pluralStringResource(
                        MR.plurals.source_health_library_series,
                        item.mangaIds.size,
                        item.mangaIds.size,
                    ),
                )
                add(
                    item.record?.lastSuccess
                        ?.let { stringResource(MR.strings.source_health_last_success, relativeTimeSpanString(it)) }
                        ?: stringResource(MR.strings.source_health_no_success),
                )
                val failure = item.record?.lastFailure
                if (item.status != SourceHealthStatus.Healthy && failure?.error != null) {
                    add(
                        stringResource(
                            MR.strings.source_health_error_summary,
                            errorTitle(failure.error),
                            operationTitle(failure.operation),
                        ),
                    )
                }
                if (!item.installed) add(stringResource(MR.strings.source_health_not_installed))
            }
            details.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val message = item.record?.lastFailure?.message
            if (item.status != SourceHealthStatus.Healthy && message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            if (item.installed) {
                TextButton(onClick = onClickCheck, enabled = !item.checking) {
                    Text(stringResource(MR.strings.source_health_check_now))
                }
            }
            if (item.homeUrl != null) {
                if (item.status == SourceHealthStatus.NeedsCloudflare) {
                    FilledTonalButton(onClick = onClickWebView) {
                        Text(stringResource(MR.strings.source_health_webview))
                    }
                } else {
                    TextButton(onClick = onClickWebView) {
                        Text(stringResource(MR.strings.source_health_webview))
                    }
                }
            }
            if (item.status != SourceHealthStatus.Healthy && item.status != SourceHealthStatus.Unknown) {
                TextButton(onClick = onClickMigrate) {
                    Text(stringResource(MR.strings.source_health_migrate))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: SourceHealthStatus) {
    val color = when (status) {
        SourceHealthStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
        SourceHealthStatus.Healthy -> MaterialTheme.colorScheme.primary
        SourceHealthStatus.Degraded -> MaterialTheme.colorScheme.tertiary
        SourceHealthStatus.Failing,
        SourceHealthStatus.NeedsCloudflare,
        SourceHealthStatus.Dead,
        -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = status.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = statusTitle(status), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val SourceHealthStatus.icon: ImageVector
    get() = when (this) {
        SourceHealthStatus.Unknown -> MaterialSymbols.Rounded.HourglassEmpty
        SourceHealthStatus.Healthy -> MaterialSymbols.RoundedFilled.CheckCircle
        SourceHealthStatus.Degraded -> MaterialSymbols.RoundedFilled.Error
        SourceHealthStatus.Failing -> MaterialSymbols.RoundedFilled.Cancel
        SourceHealthStatus.NeedsCloudflare -> MaterialSymbols.Rounded.Security
        SourceHealthStatus.Dead -> MaterialSymbols.RoundedFilled.Dangerous
    }

@Composable
private fun statusTitle(status: SourceHealthStatus): String = stringResource(
    when (status) {
        SourceHealthStatus.Unknown -> MR.strings.source_health_status_unknown
        SourceHealthStatus.Healthy -> MR.strings.source_health_status_healthy
        SourceHealthStatus.Degraded -> MR.strings.source_health_status_degraded
        SourceHealthStatus.Failing -> MR.strings.source_health_status_failing
        SourceHealthStatus.NeedsCloudflare -> MR.strings.source_health_status_needs_cloudflare
        SourceHealthStatus.Dead -> MR.strings.source_health_status_dead
    },
)

@Composable
private fun errorTitle(kind: SourceErrorKind): String = when (kind) {
    is SourceErrorKind.Http -> stringResource(MR.strings.source_health_error_http, kind.code)
    SourceErrorKind.Network -> stringResource(MR.strings.source_health_error_network)
    SourceErrorKind.Cloudflare -> stringResource(MR.strings.source_health_error_cloudflare)
    SourceErrorKind.Parse -> stringResource(MR.strings.source_health_error_parse)
    SourceErrorKind.Timeout -> stringResource(MR.strings.source_health_error_timeout)
    SourceErrorKind.Other -> stringResource(MR.strings.source_health_error_other)
}

@Composable
private fun operationTitle(operation: SourceOperation): String = stringResource(
    when (operation) {
        SourceOperation.LibraryUpdate -> MR.strings.source_health_operation_library_update
        SourceOperation.Browse -> MR.strings.source_health_operation_browse
        SourceOperation.Details -> MR.strings.source_health_operation_details
        SourceOperation.Pages -> MR.strings.source_health_operation_pages
        SourceOperation.HealthCheck -> MR.strings.source_health_operation_health_check
    },
)

/** Browse's per-source dot: shown while a source is degraded or worse, coloured as its status. */
@Composable
fun SourceHealthDot(sourceId: Long) {
    val monitor = LocalContext.current.appGraph.sourceHealthMonitor
    val records by monitor.records.collectAsStateWithLifecycle()
    val status = remember(records) { monitor.status(sourceId) }
    val color = when (status) {
        SourceHealthStatus.Healthy, SourceHealthStatus.Unknown -> return
        SourceHealthStatus.Degraded -> MaterialTheme.colorScheme.tertiary
        SourceHealthStatus.Failing,
        SourceHealthStatus.NeedsCloudflare,
        SourceHealthStatus.Dead,
        -> MaterialTheme.colorScheme.error
    }
    val description = statusTitle(status)
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description },
    )
}
