package mihon.feature.friends.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.feature.friends.data.FriendsAccount
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.LocalLibrary
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionStatus
import mihon.feature.friends.domain.Lead
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.Race
import mihon.feature.friends.domain.Tier
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

enum class BoardTab(val period: LeaguePeriod) {
    TODAY(LeaguePeriod.TODAY),
    WEEK(LeaguePeriod.WEEK),
    MONTH(LeaguePeriod.MONTH),
    SEASON(LeaguePeriod.MONTH),
    ALL(LeaguePeriod.ALL),
}

/** Something worth confetti, celebrated once per event. */
@Immutable
sealed interface Celebration {
    data class ChallengeWon(val emoji: String, val title: String) : Celebration
    data class TierUp(val tier: Tier) : Celebration
    data class LevelUp(val level: Int, val name: String) : Celebration
    data class AchievementUnlocked(val emoji: String, val label: String) : Celebration
    data object PerfectDay : Celebration
    data class QuestDone(val emoji: String) : Celebration
    data object TookTheLead : Celebration
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ArenaViewModel(
    private val repository: FriendsRepository,
    private val localLibrary: LocalLibrary,
) : ViewModel() {

    private val tab = MutableStateFlow(BoardTab.WEEK)
    private val busy = MutableStateFlow(false)
    private val genrePeriod = MutableStateFlow(GenrePeriod.SEASON)

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(
        repository.account,
        repository.snapshot,
        tab,
        genrePeriod,
        busy,
    ) { account, snapshot, tab, genrePeriod, busy ->
        val userId = account.userId
        val ready = snapshot != null && userId != null
        val arena = if (ready) ArenaUiModel.build(snapshot!!, userId!!) else null
        State(
            account = account,
            tab = tab,
            arena = arena,
            boards = if (ready) LeagueUiModel.build(snapshot!!, userId!!, tab.period) else null,
            genres = if (arena != null) GenreWarsModel.build(snapshot!!, arena, genrePeriod) else null,
            busy = busy,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            State(repository.account.value),
        )

    init {
        refresh()
    }

    fun setTab(value: BoardTab) = tab.update { value }

    fun setGenrePeriod(value: GenrePeriod) = genrePeriod.update { value }

    fun refresh() {
        if (!repository.account.value.configured || repository.account.value.userId == null) return
        busy.value = true
        viewModelScope.launch {
            try {
                repository.refreshAccount()
                repository.sync()
                repository.account.value.arena?.let { arena ->
                    val snapshot = repository.loadSnapshot(arena)
                    val userId = repository.account.value.userId ?: return@let
                    celebrate(ArenaUiModel.build(snapshot, userId))
                }
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy.value = false
            }
        }
    }

    /** Places my bet (v6); bets are final. */
    fun placeBet(challengeId: Int, stake: Int) {
        viewModelScope.launch {
            try {
                repository.placeBet(challengeId, stake)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** "Add" on an Arena suggestion row: a global search for the title (README v9). */
    fun addSuggestion(suggestion: Suggestion) {
        _events.trySend(Event.Search(suggestion.title))
    }

    /** "Dismiss" / "Read it" on an Arena suggestion row. */
    fun answerSuggestion(suggestion: Suggestion, status: SuggestionStatus) {
        viewModelScope.launch {
            try {
                repository.answerSuggestion(suggestion.id, status)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** A race opens the series from the library when it's there (by series key), else a global search. */
    fun openRace(race: Race) {
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { localLibrary.favoriteId(race.seriesKey) }
                _events.send(if (id != null) Event.OpenManga(id) else Event.Search(race.title))
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun register(name: String, avatar: ByteArray?) {
        busy.value = true
        viewModelScope.launch {
            try {
                repository.register(name, avatar)
                repository.account.value.arena?.let { repository.loadSnapshot(it) }
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy.value = false
            }
        }
    }

    private fun celebrate(model: ArenaUiModel) {
        repository.recordQuests(ArenaUiModel.completedQuestKeys(model))
        val fresh = repository.claimCelebrations(celebrationKeys(model).map { it.first }).toSet()
        celebrationKeys(model)
            .filter { it.first in fresh }
            .mapNotNull { it.second }
            // One quest burst is enough when several complete at once
            .distinctBy { if (it is Celebration.QuestDone) Celebration.QuestDone::class else it }
            .take(MAX_CELEBRATIONS)
            .forEach { _events.trySend(Event.Celebrate(it)) }
    }

    private fun fail(e: Exception) {
        logcat(LogPriority.ERROR, e)
        _events.trySend(Event.Error(e.message ?: e.javaClass.simpleName))
    }

    @Immutable
    data class State(
        val account: FriendsAccount,
        val tab: BoardTab = BoardTab.WEEK,
        val arena: ArenaUiModel? = null,
        val boards: LeagueUiModel? = null,
        val genres: GenreWarsModel? = null,
        val busy: Boolean = false,
    )

    sealed interface Event {
        data class Error(val message: String) : Event
        data class Celebrate(val celebration: Celebration) : Event
        data class OpenManga(val mangaId: Long) : Event
        data class Search(val query: String) : Event
    }

    companion object {
        private const val MAX_CELEBRATIONS = 3

        /**
         * Event keys for everything celebratable right now, most important first; a key with a null celebration is
         * only marked as seen (lower levels reached at the same time as a higher one).
         */
        fun celebrationKeys(model: ArenaUiModel): List<Pair<String, Celebration?>> {
            val me = model.me ?: return emptyList()
            return buildList {
                me.wins.forEach { add("challenge:${it.id}" to Celebration.ChallengeWon(it.emoji, it.title)) }
                Tier.entries.filter { it != Tier.BRONZE && it <= me.tier }.reversed().forEachIndexed { index, tier ->
                    add("tier:${model.season}:${tier.name}" to Celebration.TierUp(tier).takeIf { index == 0 })
                }
                (me.level.level downTo 1).forEach { level ->
                    val celebration = Celebration.LevelUp(level, me.level.name).takeIf { level == me.level.level }
                    add("level:$level" to celebration)
                }
                me.achievements.filter { it.unlocked }.forEach {
                    val achievement = it.achievement
                    add(
                        "achievement:${achievement.name}" to
                            Celebration.AchievementUnlocked(achievement.emoji, achievement.label),
                    )
                }
                if (model.perfectToday) add("perfect:${model.today}" to Celebration.PerfectDay)
                model.quests.filter { it.done }.forEach {
                    add("quest:${model.today}:${it.quest.name}" to Celebration.QuestDone(it.quest.emoji))
                }
                if (model.rival != null && model.lead == Lead.ME) add("lead:${model.today}" to Celebration.TookTheLead)
            }
        }
    }
}
