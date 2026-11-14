package mihon.feature.friends.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import mihon.feature.friends.ui.arenaCard
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.ChevronRight
import mihon.icons.materialsymbols.rounded.FilterList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.GroupedDivider
import tachiyomi.presentation.core.components.GroupedRow
import tachiyomi.presentation.core.components.SegmentedPicker
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.GroupedSection
import tachiyomi.presentation.core.theme.IosColors
import tachiyomi.presentation.core.theme.readableAccent
import tachiyomi.presentation.core.theme.separator
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
 * The album grid under its header (Mine | Everyone, grade chips, count + filter menu), which stays on top as the iOS
 * one does; the season headers pin. [hub]: the hub's sections scroll first, above the album. The scope is kept per
 * device and shared by both.
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

    Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
        AlbumHeader(
            scope = scope,
            source = source,
            filter = filter,
            sort = sort,
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
                    top = 12.dp,
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
                        CollectionStats(mine) { card -> navigator.push(CardDetailScreen(card.id)) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hub_title", contentType = "hub") {
                        Text(
                            text = stringResource(
                                if (everyone) MR.strings.cards_everyones_cards else MR.strings.cards_my_collection,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
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
                    stickyHeader(key = "season_${section.season}", contentType = "season") { SeasonHeader(section) }
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

/**
 * The bar under the title, as the iOS album's: Mine | Everyone, grade-count chips (tap toggles the grade filter),
 * "15 cards" and the filter/sort menu, which names the sort.
 */
@Composable
private fun AlbumHeader(
    scope: CardCollectionScope,
    source: List<ShioriCard>,
    filter: CardAlbumFilter,
    sort: CardAlbumSort,
    shownCount: Int,
    onScope: (CardCollectionScope) -> Unit,
    onToggleGrade: (CardGrade) -> Unit,
    onMenu: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.readableAccent(MaterialTheme.colorScheme.background)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SegmentedPicker(
                options = CardCollectionScope.entries.map { stringResource(it.title) },
                selectedIndex = scope.ordinal,
                onSelect = { onScope(CardCollectionScope.entries[it]) },
            )
            CardGradeCounts(cards = source, selected = filter.grades, onToggle = onToggleGrade)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CardAlbum.countText(shownCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val menuLabel = stringResource(MR.strings.cards_filter_sort)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onMenu)
                        .semantics { contentDescription = menuLabel }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Filled while a filter is on, as iOS's line.3.horizontal.decrease.circle.fill
                    Icon(
                        imageVector = MaterialSymbols.Rounded.FilterList,
                        contentDescription = null,
                        tint = if (filter.isActive) MaterialTheme.colorScheme.onPrimary else accent,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (filter.isActive) accent else Color.Transparent)
                            .border(1.5.dp, accent, CircleShape)
                            .padding(3.dp),
                    )
                    Text(
                        text = stringResource(sort.title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.separator)
    }
}

/** "Season 2 · 2026" with "12 cards" right-aligned, over the grid's background. */
@Composable
private fun SeasonHeader(section: CardAlbumSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = section.countText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
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
            .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!loaded) {
            Text(
                text = stringResource(MR.strings.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        CardBackFan()
        Text(
            text = stringResource(if (filtered) MR.strings.cards_no_match_title else MR.strings.cards_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(if (filtered) MR.strings.cards_no_match_hint else MR.strings.cards_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (filtered) {
            CardBorderedButton(title = stringResource(MR.strings.cards_clear_filters), icon = null, onClick = onClear)
        }
    }
}

/** The "Make a card" card with "Pulled today n/10" in a capsule beside its title. */
@Composable
private fun HubIntro(pulledToday: Int) {
    Row(
        modifier = Modifier.arenaCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "🃏", fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics {})
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.cards_make_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PulledTodayPill(pulledToday)
            }
            Text(
                text = stringResource(MR.strings.cards_make_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

/** "Collection" with "12 cards · 5 series", and the rarest card as a row that opens it. */
@Composable
private fun CollectionStats(mine: List<ShioriCard>, onOpen: (ShioriCard) -> Unit) {
    Column(modifier = Modifier.arenaCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(MR.strings.cards_collection),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CardAlbum.collectionText(mine),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val rarest = remember(mine) { CardAlbum.rarest(mine) } ?: return@Column
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(rarest) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardMiniature(rarest, Modifier.width(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(MR.strings.cards_rarest),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rarest.cardName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rarest.grade.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(rarest.grade.rankColor),
                )
            }
            Icon(
                imageVector = MaterialSymbols.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val FRIENDS_STRIP = 20

/**
 * Sort and filters, as the iOS album's menu: inset-grouped lists of choices, a checkmark on the chosen ones, and
 * Clear filters while one is on.
 */
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
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            ChoiceSection(
                header = stringResource(MR.strings.cards_sort),
                choices = sorts.map { stringResource(it.title) to (it == sort) },
                onChoose = { onSort(sorts[it]) },
            )
            ChoiceSection(
                header = stringResource(MR.strings.cards_grade),
                choices = CardGrade.entries.map { it.label to (it in filter.grades) },
                onChoose = { onFilter(filter.copy(grades = filter.grades.toggle(CardGrade.entries[it]))) },
            )
            ChoiceSection(
                header = stringResource(MR.strings.cards_theme),
                choices = CardTheme.entries.map { it.title to (it in filter.themes) },
                onChoose = { onFilter(filter.copy(themes = filter.themes.toggle(CardTheme.entries[it]))) },
            )
            ChoiceSection(
                header = stringResource(MR.strings.cards_series),
                choices = listOf(stringResource(MR.strings.cards_all_series) to (filter.series == null)) +
                    series.map { (id, title) -> title to (filter.series == id) },
                onChoose = { onFilter(filter.copy(series = if (it == 0) null else series[it - 1].first)) },
            )
            if (owners != null) {
                ChoiceSection(
                    header = stringResource(MR.strings.cards_owner),
                    choices = listOf(stringResource(MR.strings.cards_everyone) to (filter.owner == null)) +
                        owners.map { (id, name) -> name to (filter.owner == id) },
                    onChoose = { onFilter(filter.copy(owner = if (it == 0) null else owners[it - 1].first)) },
                )
            }
            if (filter.isActive) {
                GroupedSection(modifier = Modifier.padding(top = 24.dp)) {
                    GroupedRow(
                        title = stringResource(MR.strings.cards_clear_filters),
                        titleColor = IosColors.Red,
                        chevron = false,
                        onClick = { onFilter(CardAlbumFilter()) },
                    )
                }
            }
        }
    }
}

/** One inset-grouped list of choices; a checkmark marks each chosen one. */
@Composable
private fun ChoiceSection(header: String, choices: List<Pair<String, Boolean>>, onChoose: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.readableAccent()
    GroupedSection(header = header) {
        choices.forEachIndexed { index, (title, chosen) ->
            if (index > 0) GroupedDivider(startInset = 16.dp)
            GroupedRow(
                title = title,
                chevron = false,
                trailing = {
                    if (chosen) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Check,
                            contentDescription = stringResource(MR.strings.selected),
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                onClick = { onChoose(index) },
            )
        }
    }
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
