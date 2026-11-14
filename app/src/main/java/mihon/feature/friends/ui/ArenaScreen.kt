package mihon.feature.friends.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import mihon.feature.friends.cards.ArenaCardsSection
import mihon.feature.friends.cards.CardAlbum
import mihon.feature.friends.cards.CardFeedItem
import mihon.feature.friends.cards.CardFeedRow
import mihon.feature.friends.cards.CardsViewModel
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionStatus
import mihon.feature.friends.domain.Race
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Person
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ArenaScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<ArenaViewModel>()
        val cards = metroViewModel<CardsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val celebrations = remember { mutableStateListOf<Celebration>() }
        var betting by remember { mutableStateOf<ChallengeCard?>(null) }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is ArenaViewModel.Event.Error -> snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.friends_error, event.message),
                    )
                    is ArenaViewModel.Event.Celebrate -> celebrations += event.celebration
                    is ArenaViewModel.Event.OpenManga -> navigator.push(MangaScreen(event.mangaId))
                    is ArenaViewModel.Event.Search -> navigator.push(GlobalSearchScreen(event.query))
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.friends_arena),
                    navigateUp = navigator::pop,
                    actions = {
                        if (state.account.registered) {
                            SuggestionsToolbarAction(
                                count = state.arena?.newSuggestions ?: 0,
                                onClick = { navigator.push(SuggestionsScreen()) },
                            )
                            val me = state.arena?.me
                            IconButton(onClick = { navigator.push(FriendsProfileScreen()) }) {
                                Box {
                                    FriendAvatar(
                                        name = me?.name ?: state.account.profile?.displayName.orEmpty(),
                                        url = me?.avatarUrl,
                                        size = 30.dp,
                                    )
                                    if (me != null) {
                                        TierBadge(
                                            tier = me.tier,
                                            size = 14.dp,
                                            glow = false,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 3.dp, y = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            Box(modifier = Modifier.padding(contentPadding)) {
                val account = state.account
                when {
                    !account.configured -> EmptyScreen(MR.strings.friends_not_configured)
                    account.userId == null || (account.loaded && account.profile == null) ->
                        RegisterContent(busy = state.busy, onRegister = viewModel::register)
                    !account.loaded || state.arena == null -> LoadingScreen()
                    else -> PullRefresh(
                        refreshing = state.busy,
                        enabled = true,
                        onRefresh = {
                            viewModel.refresh()
                            cards.load(force = true)
                        },
                    ) {
                        val myId = state.account.userId
                        CompositionLocalProvider(
                            LocalOpenPlayer provides { id ->
                                navigator.push(if (id == myId) FriendsProfileScreen() else PlayerProfileScreen(id))
                            },
                        ) {
                            ArenaContent(
                                state = state,
                                onTabChange = viewModel::setTab,
                                onGenrePeriodChange = viewModel::setGenrePeriod,
                                onOpenSuggestions = { navigator.push(SuggestionsScreen()) },
                                onAllRecaps = { navigator.push(RecapListScreen()) },
                                onPlaceBet = { betting = it },
                                onOpenRace = viewModel::openRace,
                                onAddSuggestion = viewModel::addSuggestion,
                                onAnswerSuggestion = viewModel::answerSuggestion,
                            )
                        }
                    }
                }
                if (state.busy && state.arena == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        betting?.let { card ->
            StakeSheet(
                card = card,
                onDismissRequest = { betting = null },
                onConfirm = { stake ->
                    betting = null
                    viewModel.placeBet(card.challenge.id, stake)
                },
            )
        }

        celebrations.firstOrNull()?.let { celebration ->
            // A fresh overlay (and timer) per celebration, so the next one in the queue never gets stuck
            key(celebration) {
                CelebrationOverlay(celebration = celebration, onFinished = { celebrations.remove(celebration) })
            }
        }
    }
}

@Composable
private fun ArenaContent(
    state: ArenaViewModel.State,
    onTabChange: (BoardTab) -> Unit,
    onGenrePeriodChange: (GenrePeriod) -> Unit,
    onOpenSuggestions: () -> Unit,
    onAllRecaps: () -> Unit,
    onPlaceBet: (ChallengeCard) -> Unit,
    onOpenRace: (Race) -> Unit,
    onAddSuggestion: (Suggestion) -> Unit,
    onAnswerSuggestion: (Suggestion, SuggestionStatus) -> Unit,
) {
    val model = state.arena ?: return
    val boards = state.boards
    val cardPulls by metroViewModel<CardsViewModel>().repository.feed.collectAsStateWithLifecycle()
    // Section order follows "v6 Arena section order" in the backend README
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
    ) {
        item(key = "hero") { SeasonHero(model) }
        item(key = "faceoff") { RivalryHero(model) }
        if (model.localToday.isNotEmpty()) {
            item(key = "today") { TodaySection(model) }
        }
        model.freshRecap?.let { recap ->
            item(key = "recap") { RecapCard(recap, model, onAllRecaps = onAllRecaps) }
        }
        item(key = "challenges") { ChallengesSection(model, onPlaceBet) }
        if (model.races.isNotEmpty()) {
            item(key = "races") { RacesSection(model, onOpenRace) }
        }
        if (model.quests.isNotEmpty()) {
            item(key = "quests") { QuestsSection(model) }
        }
        item(key = "cards") { ArenaCardsSection() }
        item(key = "boards") { BoardsSection(model, boards, state.tab, onTabChange) }
        state.genres?.let { genres ->
            item(key = "genres") { GenreWarsSection(model, genres, onGenrePeriodChange) }
        }
        if (boards != null) {
            val memberIds = boards.members.keys.toList()
            val me = boards.me
            if (me != null && boards.rivalries.isNotEmpty()) {
                item(key = "h2h") {
                    ArenaSection(
                        stringResource(MR.strings.friends_head_to_head),
                        "🤺",
                        trailing = { SectionTrailing(periodTitle(boards.period)) },
                    ) {
                        boards.rivalries.forEachIndexed { index, rivalry ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                            RivalryCard(
                                rivalry = rivalry,
                                me = me.row,
                                colors = ArenaColors.Me to playerColor(rivalry.rival.userId, model),
                            )
                        }
                    }
                }
            }
            item(key = "titles") {
                ArenaSection(
                    stringResource(MR.strings.friends_titles),
                    "👑",
                    trailing = { SectionTrailing(stringResource(MR.strings.friends_this_month)) },
                ) {
                    TitlesList(
                        titles = boards.titles,
                        members = boards.members,
                        myId = boards.myId,
                        colorOf = { playerColor(it, model) },
                    )
                }
            }
            item(key = "cabinet") { CabinetSection(model, boards) }
            item(key = "achievements") { AchievementsSection(model) }
            if (boards.standings.any { it.row.topSeries.isNotEmpty() }) {
                item(key = "series") {
                    ArenaSection(
                        stringResource(MR.strings.friends_top_series),
                        "📚",
                        trailing = { SectionTrailing(periodTitle(boards.period)) },
                    ) {
                        TopSeriesByPlayer(
                            rows = boards.standings.map { it.row },
                            myId = boards.myId,
                            colorOf = { playerColor(it, model) },
                        )
                    }
                }
            }
            item(key = "chart") {
                ArenaSection(stringResource(MR.strings.friends_daily_chart), "📈") {
                    DailyChart(model = boards, memberIds = memberIds)
                    ChartLegend(members = boards.members, memberIds = memberIds)
                }
            }
            val feed = CardAlbum.merged(boards.feed, cardPulls, FriendsRepository.FEED_SIZE)
            if (feed.isNotEmpty()) {
                item(key = "feed") {
                    LoadFeedSocial(boards.feed)
                    ArenaSection(stringResource(MR.strings.friends_activity), "⚡") {
                        feed.forEach { item ->
                            when (item) {
                                is CardFeedItem.Read -> FeedItem(
                                    row = item.row,
                                    member = boards.members[item.row.userId],
                                )
                                is CardFeedItem.Pull -> CardFeedRow(item.card)
                            }
                        }
                    }
                }
            }
        }
        item(key = "suggestions") {
            SuggestionsSection(
                model,
                onSeeAll = onOpenSuggestions,
                onAdd = onAddSuggestion,
                onAnswer = onAnswerSuggestion,
            )
        }
        item(key = "footer") { ArenaFooter(model) }
    }
}

/** "Days end at midnight in Europe/Zagreb. Updated 21:04." */
@Composable
private fun ArenaFooter(model: ArenaUiModel) {
    val updated = remember(model.now) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(model.now.atZone(ZoneId.systemDefault()))
    }
    Text(
        text = stringResource(MR.strings.friends_footer, model.zone.id, updated),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
fun RegisterContent(busy: Boolean, onRegister: (String, ByteArray?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }
    val valid = name.trim().length in 1..MAX_NAME
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Text(text = "⚔️", fontSize = 56.sp)
        Text(
            text = stringResource(MR.strings.friends_register_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(MR.strings.friends_register_summary),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
        ) {
            FriendAvatar(name = name.ifBlank { "?" }, url = avatarUri?.toString(), size = 96.dp)
        }
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        ) {
            Text(stringResource(MR.strings.friends_pick_avatar))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            label = { Text(stringResource(MR.strings.friends_display_name)) },
            supportingText = { Text("${name.trim().length}/$MAX_NAME") },
            isError = name.isNotEmpty() && !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = valid && !busy,
            onClick = {
                scope.launch {
                    val jpeg = avatarUri?.let { runCatching { AvatarImage.fromUri(context, it) }.getOrNull() }
                    onRegister(name.trim(), jpeg)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(MR.strings.friends_register))
        }
    }
}

const val MAX_NAME = 24
