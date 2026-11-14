package mihon.feature.friends.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** Adaptive columns between [CardAlbum.GRID_MIN] and [CardAlbum.GRID_MAX] dp wide (CARD_SPEC §13). */
private object AlbumColumns : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = CardAlbum.columns(
            availableSize.toFloat(),
            CardAlbum.GRID_MIN * density,
            CardAlbum.GRID_MAX * density,
            spacing.toFloat(),
        )
        val total = availableSize - spacing * (count - 1)
        val base = total / count
        val extra = total % count
        return List(count) { base + if (it < extra) 1 else 0 }
    }
}

/** The album (CARD_SPEC §13): my cards or everyone's ("Mine | Everyone"), by season, filtered and sorted. */
class CardAlbumScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        CardsScaffold(title = stringResource(MR.strings.cards_album), onBack = navigator::pop) { padding ->
            CardAlbumBody(hub = false, contentPadding = padding)
        }
    }
}

/**
 * The Shiori Cards hub, opened from the Library top bar and the Arena: today's pulls, how to make a card, recent
 * pulls from friends, the collection's stats, then the album: "My collection" or "Everyone's cards".
 */
class CardsHubScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        CardsScaffold(title = stringResource(MR.strings.cards_title), onBack = navigator::pop) { padding ->
            CardAlbumBody(hub = true, contentPadding = padding)
        }
    }
}

@Composable
private fun CardsScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { scrollBehavior -> AppBar(title = title, navigateUp = onBack, scrollBehavior = scrollBehavior) },
    ) { contentPadding -> content(contentPadding) }
}

/**
 * The album grid. Not [hub]: the header (Mine | Everyone, grade chips, count + menu) stays above the grid and the
 * season headers pin. [hub]: the hub's sections scroll first, then the album header pins and the season headers
 * scroll. The scope is kept per device and shared by both.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardAlbumBody(hub: Boolean, contentPadding: PaddingValues) {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<CardsViewModel>()
    val repository = viewModel.repository
    val album by repository.album.collectAsStateWithLifecycle()
    val loaded by repository.loaded.collectAsStateWithLifecycle()
    val pulled by repository.pulledToday.collectAsStateWithLifecycle()
    val scopePreference = viewModel.preferences.cardsCollectionScope
    val scopeValue by scopePreference.collectAsState()
    val scope = CardCollectionScope.of(scopeValue)
    val everyone = scope == CardCollectionScope.EVERYONE
    var filter by remember { mutableStateOf(CardAlbumFilter()) }
    var sort by rememberSaveable { mutableStateOf(CardAlbumSort.NEWEST) }
    var showFilters by remember { mutableStateOf(false) }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.load()
        repository.me?.let(viewModel::loadPulledToday)
    }

    val me = repository.me
    val zone = repository.zone
    val ownerName = rememberCardOwnerNames(repository)
    val mine = remember(album, me) { CardAlbum.scoped(album, CardCollectionScope.MINE, me) }
    val source = if (everyone) album else mine
    val shown = remember(source, filter, sort, ownerName) { CardAlbum.apply(source, filter, sort, ownerName) }
    val sections = remember(shown, zone, sort) {
        CardAlbum.sections(shown, zone, oldestFirst = sort == CardAlbumSort.OLDEST)
    }
    val albumIds = remember(sections) { CardAlbum.albumIds(sections) }
    val friends = remember(album, me) { CardAlbum.fromFriends(album, me, FRIENDS_STRIP) }
    val friendIds = remember(friends) { friends.map { it.id } }

    val header = @Composable {
        AlbumHeader(
            scope = scope,
            source = source,
            filter = filter,
            shownCount = shown.size,
            onScope = { chosen ->
                if (chosen != scope) {
                    scopePreference.set(chosen.value)
                    filter = filter.copy(owner = null)
                }
            },
            onToggleGrade = { grade -> filter = filter.copy(grades = filter.grades.toggle(grade)) },
            onMenu = { showFilters = true },
        )
    }

    Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
        if (!hub) header()
        PullRefresh(
            refreshing = refreshing,
            enabled = true,
            onRefresh = {
                viewModel.load(force = true)
                me?.let(viewModel::loadPulledToday)
            },
        ) {
            LazyVerticalGrid(
                columns = AlbumColumns,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (hub) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hub_today", contentType = "hub") {
                        HubIntro(pulled[me] ?: 0)
                    }
                    if (friends.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hub_friends", contentType = "hub") {
                            FriendsStrip(friends, repository) { card ->
                                navigator.push(CardDetailScreen(card.id, friendIds))
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hub_stats", contentType = "hub") {
                        CollectionStats(mine, repository) { card ->
                            navigator.push(CardDetailScreen(card.id))
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hub_title", contentType = "hub") {
                        Text(
                            text = stringResource(
                                if (everyone) MR.strings.cards_everyones_cards else MR.strings.cards_my_collection,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    stickyHeader(key = "album_header", contentType = "album_header") {
                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) { header() }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "empty") {
                        AlbumEmpty(
                            loaded = loaded,
                            filtered = filter.isActive,
                            onClear = { filter = CardAlbumFilter() },
                        )
                    }
                }
                sections.forEach { section ->
                    val key = "season_${section.season}"
                    if (hub) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = key, contentType = "season") {
                            SeasonHeader(section)
                        }
                    } else {
                        stickyHeader(key = key, contentType = "season") { SeasonHeader(section) }
                    }
                    items(section.cards, key = { it.id }, contentType = { "card" }) { card ->
                        val caption = if (everyone) {
                            "${ownerName(card.owner)} · ${card.grade.label}"
                        } else {
                            card.grade.label
                        }
                        CardThumb(card = card, caption = caption) {
                            navigator.push(CardDetailScreen(card.id, albumIds))
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        val owners = album.map { it.owner }.distinct()
        FilterSheet(
            filter = filter,
            sort = sort,
            sorts = CardAlbumSort.offered(scope),
            series = CardAlbum.series(source),
            owners = if (everyone) {
                owners.map { it to ownerName(it) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
            } else {
                null
            },
            onFilter = { filter = it },
            onSort = { sort = it },
            onDismissRequest = { showFilters = false },
        )
    }
}

/** Mine | Everyone, grade-count chips (tap toggles the grade filter), "15 cards" and the filter/sort menu. */
@Composable
private fun AlbumHeader(
    scope: CardCollectionScope,
    source: List<ShioriCard>,
    filter: CardAlbumFilter,
    shownCount: Int,
    onScope: (CardCollectionScope) -> Unit,
    onToggleGrade: (CardGrade) -> Unit,
    onMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            CardCollectionScope.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == scope,
                    onClick = { onScope(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, CardCollectionScope.entries.size),
                ) {
                    Text(stringResource(option.title))
                }
            }
        }
        CardGradeCounts(
            cards = source,
            selected = filter.grades,
            onToggle = onToggleGrade,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = CardAlbum.countText(shownCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMenu) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.FilterList,
                    contentDescription = stringResource(MR.strings.cards_filter_sort),
                    tint = if (filter.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** "Season 2 · 2026" with "12 cards" right-aligned. */
@Composable
private fun SeasonHeader(section: CardAlbumSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = section.countText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Three fanned card backs, "No cards yet" and how to make one; with filters on, "No cards match" + Clear filters. */
@Composable
private fun AlbumEmpty(loaded: Boolean, filtered: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!loaded) {
            Text(
                text = stringResource(MR.strings.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        CardBackFan(modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = stringResource(if (filtered) MR.strings.cards_no_match_title else MR.strings.cards_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (filtered) {
            OutlinedButton(onClick = onClear) { Text(stringResource(MR.strings.cards_clear_filters)) }
        } else {
            Text(
                text = stringResource(MR.strings.cards_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** The "Make a card" card with "Pulled today n/10". */
@Composable
private fun HubIntro(pulledToday: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "📸", style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(MR.strings.cards_make_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(MR.strings.cards_make_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PulledToday(pulledToday)
        }
    }
}

/** Recent pulls by others, newest first; tap opens the pager over this strip. */
@Composable
private fun FriendsStrip(cards: List<ShioriCard>, repository: CardRepository, onOpen: (ShioriCard) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(MR.strings.cards_from_friends),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.id }) { card ->
                CardThumb(
                    card = card,
                    caption = "${cardOwnerName(repository, card.owner)} · ${card.grade.label}",
                    modifier = Modifier.width(96.dp),
                ) { onOpen(card) }
            }
        }
    }
}

/** "12 cards · 5 series" and the rarest card (the grade chips are in the album header just below). */
@Composable
private fun CollectionStats(mine: List<ShioriCard>, repository: CardRepository, onOpen: (ShioriCard) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(MR.strings.cards_collection),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CardAlbum.collectionText(mine),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val rarest = remember(mine) { CardAlbum.rarest(mine) } ?: return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(card = rarest, caption = rarest.grade.label, modifier = Modifier.width(96.dp)) { onOpen(rarest) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(MR.strings.cards_rarest),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = rarest.cardName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = rarest.grade.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = repository.accent(rarest.grade),
                )
                Text(
                    text = rarest.seriesLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val FRIENDS_STRIP = 20

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filter: CardAlbumFilter,
    sort: CardAlbumSort,
    sorts: List<CardAlbumSort>,
    series: List<Pair<String, String>>,
    owners: List<Pair<String, String>>?,
    onFilter: (CardAlbumFilter) -> Unit,
    onSort: (CardAlbumSort) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Heading(MR.strings.cards_sort)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sorts.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        label = { Text(stringResource(option.title)) },
                    )
                }
            }
            Heading(MR.strings.cards_grade)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardGrade.entries.forEach { grade ->
                    FilterChip(
                        selected = grade in filter.grades,
                        onClick = { onFilter(filter.copy(grades = filter.grades.toggle(grade))) },
                        label = { Text(grade.label) },
                    )
                }
            }
            Heading(MR.strings.cards_theme)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = theme in filter.themes,
                        onClick = { onFilter(filter.copy(themes = filter.themes.toggle(theme))) },
                        label = { Text(theme.title) },
                    )
                }
            }
            Heading(MR.strings.cards_series)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.series == null,
                    onClick = { onFilter(filter.copy(series = null)) },
                    label = { Text(stringResource(MR.strings.cards_all_series)) },
                )
                series.forEach { (id, title) ->
                    FilterChip(
                        selected = filter.series == id,
                        onClick = { onFilter(filter.copy(series = id)) },
                        label = { Text(title) },
                    )
                }
            }
            if (owners != null) {
                Heading(MR.strings.cards_owner)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.owner == null,
                        onClick = { onFilter(filter.copy(owner = null)) },
                        label = { Text(stringResource(MR.strings.cards_everyone)) },
                    )
                    owners.forEach { (id, name) ->
                        FilterChip(
                            selected = filter.owner == id,
                            onClick = { onFilter(filter.copy(owner = id)) },
                            label = { Text(name) },
                        )
                    }
                }
            }
            if (filter.isActive) {
                TextButton(onClick = { onFilter(CardAlbumFilter()) }) {
                    Text(stringResource(MR.strings.cards_clear_filters), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Heading(resource: StringResource) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val CardAlbumSort.title: StringResource
    get() = when (this) {
        CardAlbumSort.NEWEST -> MR.strings.cards_sort_newest
        CardAlbumSort.OLDEST -> MR.strings.cards_sort_oldest
        CardAlbumSort.GRADE -> MR.strings.cards_sort_grade
        CardAlbumSort.SERIES -> MR.strings.cards_sort_series
        CardAlbumSort.OWNER -> MR.strings.cards_sort_owner
    }

private val CardCollectionScope.title: StringResource
    get() = when (this) {
        CardCollectionScope.MINE -> MR.strings.cards_mine
        CardCollectionScope.EVERYONE -> MR.strings.cards_everyone
    }

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
