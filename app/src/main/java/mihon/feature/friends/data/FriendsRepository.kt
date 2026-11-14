package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.feature.friends.domain.Bet
import mihon.feature.friends.domain.Bets
import mihon.feature.friends.domain.DailyQuests
import mihon.feature.friends.domain.DayTotal
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.LeaguePeriods
import mihon.feature.friends.domain.LocalRead
import mihon.feature.friends.domain.LocalToday
import mihon.feature.friends.domain.RaceRow
import mihon.feature.friends.domain.ReadReconcile
import mihon.feature.friends.domain.ReadRows
import mihon.feature.friends.domain.Seasons
import mihon.feature.friends.domain.UploadCursor
import mihon.feature.friends.domain.WeeklyRecaps
import tachiyomi.core.common.util.system.logcat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class FriendsAccount(
    val configured: Boolean,
    val userId: String? = null,
    val profile: Profile? = null,
    /** The one built-in league every player joins on registering. */
    val arena: League? = null,
    /** Whether the account was fetched from the server at least once this session. */
    val loaded: Boolean = false,
) {
    val registered: Boolean get() = userId != null && profile != null
}

/** Everything the Arena shows, fetched in one go. */
data class ArenaSnapshot(
    val arena: League,
    val zone: ZoneId,
    val now: Instant,
    val boards: Map<LeaguePeriod, List<BoardRow>>,
    /** Counted chapters and seconds per player and Arena day, all time. */
    val daily: List<DayTotal>,
    /** Hour-of-day chapter counts for the current month. */
    val hours: List<HourRow>,
    /** Hour-of-day chapter counts, all time. */
    val lifetimeHours: List<HourRow>,
    val feed: List<FeedRow>,
    val challenges: List<Challenge>,
    /** Scores of every challenge that has started. */
    val challengeBoards: Map<Int, List<ChallengeScore>>,
    /** Final month boards of the seasons before the current one. */
    val seasonBoards: Map<YearMonth, List<BoardRow>>,
    /** Quest numbers per Arena day (from [DailyQuests.EPOCH]) and player, for days anyone read. */
    val questFacts: Map<LocalDate, Map<String, QuestFacts>>,
    /** Own quests completed on this device, as `<date>:<QUEST>`. */
    val localQuests: Set<String> = emptySet(),
    /** Suggestions sent or received, as [FriendsRepository.suggestions] has them. */
    val suggestions: List<Suggestion> = emptyList(),
    /** Every weekly bet (v6). */
    val bets: List<Bet> = emptyList(),
    /** Rows of `arena_races` (v6). */
    val races: List<RaceRow> = emptyList(),
    /** Boards of every ended week (v6 recaps), by challenge id. */
    val recapBoards: Map<Int, RecapBoards> = emptyMap(),
    /** Each player's own zone (README v9 "Today"); empty when it couldn't be loaded. */
    val localZones: Map<String, ZoneId> = emptyMap(),
    /** Today's board per zone id, over that zone's local day. */
    val localTodayBoards: Map<String, List<BoardRow>> = emptyMap(),
)

/** The boards a weekly recap needs: the week's window and the 7 days before it. */
@Serializable
data class RecapBoards(val window: List<BoardRow> = emptyList(), val previous: List<BoardRow> = emptyList())

@Inject
@SingleIn(AppScope::class)
class FriendsRepository(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val api: FriendsApi,
    private val store: FriendsSessionStore,
    private val localReads: LocalReads,
    private val localLibrary: LocalLibrary,
    private val coverThumbnails: CoverThumbnails,
    private val statsCollector: LibraryStatsCollector,
) {

    val account: StateFlow<FriendsAccount>
        field = MutableStateFlow(initialAccount())

    val snapshot: StateFlow<ArenaSnapshot?>
        field = MutableStateFlow(null)

    /** Suggestions sent or received, null before the first load. */
    val suggestions: StateFlow<List<Suggestion>?>
        field = MutableStateFlow(null)

    /** Players' published library stats (v5), by user id. */
    val profileStats: StateFlow<Map<String, ProfileStatsRow>>
        field = MutableStateFlow(emptyMap())

    private val tokenMutex = Mutex()
    private val syncMutex = Mutex()
    private val statsMutex = Mutex()
    private var pendingSync: Job? = null

    /** When I joined the Arena, fetched once per session for reconciling. */
    private var joinedAt: Instant? = null

    init {
        store.cursor = store.cursor.onLaunch(registered = store.session != null, now = System.currentTimeMillis())
    }

    private fun initialAccount() = FriendsAccount(
        configured = api.isConfigured,
        userId = store.session?.userId,
    )

    /** The signed-in player's id. */
    val myId: String? get() = store.session?.userId

    /**
     * Uploads new reads and refreshes the Arena in the background, coalescing quick repeats; [onLoaded] gets the
     * fresh snapshot (the foreground alert check).
     */
    fun requestSync(delay: Duration = Duration.ZERO, onLoaded: (suspend (ArenaSnapshot) -> Unit)? = null) {
        if (!api.isConfigured || store.session == null) return
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(delay)
            try {
                if (!account.value.loaded) refreshAccount()
                sync()
                account.value.arena?.let { loadSnapshot(it) }?.let { onLoaded?.invoke(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Friends League sync failed" }
            }
        }
    }

    suspend fun refreshAccount() {
        val userId = store.session?.userId ?: return
        val profile = authed { api.profile(it, userId) }
        val leagues = authed { api.leagues(it) }
        val arena = leagues.firstOrNull { it.id == ARENA_ID } ?: leagues.firstOrNull()
        account.update { it.copy(userId = userId, profile = profile, arena = arena, loaded = true) }
    }

    suspend fun register(displayName: String, avatarJpeg: ByteArray?) {
        if (store.session == null) {
            val auth = api.signUp()
            store.session = auth.toSession()
            store.cursor = UploadCursor.onRegistered(System.currentTimeMillis())
            account.update { it.copy(userId = auth.user.id) }
        }
        // Creating the profile joins the Arena on the server
        saveProfile(displayName, avatarJpeg, avatarUrl = null, title = null)
        refreshAccount()
    }

    suspend fun updateProfile(displayName: String, avatarJpeg: ByteArray?, title: String?) {
        saveProfile(displayName, avatarJpeg, account.value.profile?.avatarUrl, title)
    }

    private suspend fun saveProfile(displayName: String, avatarJpeg: ByteArray?, avatarUrl: String?, title: String?) {
        val userId = requireNotNull(store.session?.userId)
        val newAvatarUrl = avatarJpeg?.let { jpeg -> authed { api.uploadAvatar(it, userId, jpeg) } } ?: avatarUrl
        val upsert = ProfileUpsert(
            id = userId,
            displayName = displayName.trim(),
            avatarUrl = newAvatarUrl,
            app = APP,
            title = title,
            updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
        )
        val profile = authed { api.upsertProfile(it, upsert) }
        account.update { it.copy(profile = profile) }
    }

    /** Leaves the Arena and forgets this player; the next registration starts a new one at 0. */
    suspend fun resetPlayer() {
        val userId = store.session?.userId
        val arena = account.value.arena
        if (userId != null && arena != null) {
            runCatching { authed { api.leave(it, arena.id, userId) } }
        }
        pendingSync?.cancel()
        joinedAt = null
        profileStats.value = emptyMap()
        store.clear()
        store.cursor = store.cursor.onLaunch(registered = false, now = System.currentTimeMillis())
        snapshot.value = null
        suggestions.value = null
        account.value = FriendsAccount(configured = api.isConfigured)
    }

    /**
     * Uploads qualifying reads (README v5: finished, read ≥ 15 s) since the cursor; everyone starts at 0, so there's
     * never a backfill. Then reconciles with the server: my rows that no longer qualify are deleted, and qualifying
     * reads since I joined that the server lacks are sent. Publishes the library stats afterwards.
     */
    suspend fun sync() {
        val changed = syncMutex.withLock { uploadReads() } ?: return
        publishStats(force = changed)
    }

    /** Whether anything changed on the server, or null when not playing. */
    private suspend fun uploadReads(): Boolean? {
        val userId = store.session?.userId ?: return null
        val arena = account.value.arena ?: return null
        val cursor = store.cursor
        val history = localReads.readSince(Instant.EPOCH)
        val fresh = history.filter { it.readAt.toEpochMilli() > cursor.uploadedUntil && ReadRows.qualifies(it) }
        val serverIds = authed { api.ownReads(it, userId) }.map { it.clientId }
        val start = joinedAt ?: authed { api.joinedAt(it, userId, arena.id) }?.also { joinedAt = it }
        val diff = ReadReconcile.diff(history, serverIds, start?.toEpochMilli())
        val reads = fresh + diff.missing
        val rows = ReadRows.buildAll(reads, userId).distinctBy { it.clientId }
        val covers = publishCovers(rows.map { row -> row.seriesKey to row.coverUrl }.toMap(), reads)
        val withCovers = rows.map { it.copy(coverUrl = covers.getOrElse(it.seriesKey) { it.coverUrl }) }
        ReadRows.batches(withCovers).forEach { batch ->
            authed { api.uploadReads(it, batch) }
        }
        if (diff.stale.isNotEmpty()) authed { api.deleteReads(it, userId, diff.stale) }
        val newest = fresh.maxOfOrNull { it.readAt.toEpochMilli() }
        if (newest != null && newest > cursor.uploadedUntil) store.cursor = cursor.copy(uploadedUntil = newest)
        return rows.isNotEmpty() || diff.stale.isNotEmpty()
    }

    /** Upserts my library stats: right after an upload ([force]), else at most every 30 minutes. */
    suspend fun publishStats(force: Boolean) = statsMutex.withLock {
        val userId = store.session?.userId ?: return@withLock
        val now = System.currentTimeMillis()
        val last = store.cache(CACHE_STATS_PUBLISHED)?.toLongOrNull()
        if (!LibraryStatsRules.due(last, now, force)) return@withLock
        try {
            val stats = statsCollector.collect()
            authed { api.saveStats(it, userId, stats) }
            store.putCache(CACHE_STATS_PUBLISHED, now.toString())
            val row = ProfileStatsRow(userId, stats, DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(now)))
            profileStats.update { it + (userId to row) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Publishing library stats failed" }
        }
    }

    /** Loads a player's published library stats into [profileStats]. */
    suspend fun loadProfileStats(userId: String) {
        val row = authed { api.stats(it, listOf(userId)) }.firstOrNull() ?: return
        profileStats.update { it + (userId to row) }
    }

    /** One player's counted reads, newest first, older than [before] when given. */
    suspend fun playerFeed(userId: String, before: Instant?): List<FeedRow> =
        authed { api.playerFeed(it, userId, before, ActivityDays.PAGE_SIZE) }

    /** Remembers own completed quests (`<date>:<QUEST>`) on this device. */
    fun recordQuests(completed: Set<String>) {
        val known = store.completedQuests
        if (!known.containsAll(completed)) store.completedQuests = known + completed
    }

    /** Marks [keys] as celebrated and returns the ones that weren't before. */
    fun claimCelebrations(keys: List<String>): List<String> {
        val seen = store.celebrated
        val fresh = keys.filter { it !in seen }
        if (fresh.isNotEmpty()) store.celebrated = seen + fresh
        return fresh
    }

    suspend fun loadSnapshot(arena: League): ArenaSnapshot = coroutineScope {
        val zone = LeaguePeriods.zone(arena.timezone)
        val now = Instant.now()
        val today = LeaguePeriods.localDate(now, zone)
        val month = LeaguePeriods.range(LeaguePeriod.MONTH, now, zone)
        val all = LeaguePeriods.range(LeaguePeriod.ALL, now, zone)

        val boards = LeaguePeriod.entries.associateWith { period ->
            val range = LeaguePeriods.range(period, now, zone)
            async { authed { api.board(it, arena.id, range.from, range.to) } }
        }
        val daily = async { authed { api.daily(it, arena.id, all.from, all.to) } }
        val hours = async { authed { api.hours(it, arena.id, month.from, month.to) } }
        val lifetimeHours = async { authed { api.hours(it, arena.id, all.from, all.to) } }
        val feed = async { authed { api.feed(it, arena.id, FEED_SIZE) } }
        val challenges = async { authed { api.challenges(it) } }
        val suggestionList = async {
            try {
                loadSuggestions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Loading suggestions failed" }
                suggestions.value.orEmpty()
            }
        }

        // v6 data fails soft: before 006_social.sql is applied these endpoints don't exist
        val bets = async { soft("Loading bets", emptyList()) { authed { api.bets(it) } } }
        val races = async { soft("Loading races", emptyList()) { authed { api.races(it) } } }

        val dailyTotals = daily.await().map { DayTotal(it.userId, it.date, it.chapters, it.seconds) }
        val challengeList = challenges.await()
        val challengeBoards = async { loadChallengeBoards(challengeList, now) }
        val recapBoards = async { soft("Loading recaps", emptyMap()) { loadRecapBoards(arena, challengeList, now) } }
        val seasonBoards = async { loadSeasonBoards(arena, zone, today) }
        val questFacts = async { loadQuestFacts(arena, zone, today, dailyTotals) }
        val localToday = async {
            soft("Loading today", emptyMap<String, ZoneId>() to emptyMap()) {
                loadLocalToday(
                    arena,
                    zone,
                    now,
                    boards.getValue(LeaguePeriod.ALL).await(),
                    boards.getValue(LeaguePeriod.TODAY).await(),
                )
            }
        }

        ArenaSnapshot(
            arena = arena,
            zone = zone,
            now = now,
            boards = boards.mapValues { it.value.await() },
            daily = dailyTotals,
            hours = hours.await(),
            lifetimeHours = lifetimeHours.await(),
            feed = feed.await(),
            challenges = challengeList,
            challengeBoards = challengeBoards.await(),
            seasonBoards = seasonBoards.await(),
            questFacts = questFacts.await(),
            localQuests = store.completedQuests,
            suggestions = suggestionList.await(),
            bets = bets.await(),
            races = races.await(),
            recapBoards = recapBoards.await(),
            localZones = localToday.await().first,
            localTodayBoards = localToday.await().second,
        ).also { snapshot.value = it }
    }

    /**
     * README v9 "Today": every player's zone from `profile_stats` (else the Arena's), and one
     * `league_board` per distinct zone over its local day (the Arena's own is already loaded).
     */
    private suspend fun loadLocalToday(
        arena: League,
        zone: ZoneId,
        now: Instant,
        players: List<BoardRow>,
        arenaToday: List<BoardRow>,
    ): Pair<Map<String, ZoneId>, Map<String, List<BoardRow>>> {
        val ids = players.map { it.userId }
        if (ids.isEmpty()) return emptyMap<String, ZoneId>() to emptyMap()
        val published = authed { api.stats(it, ids) }.associate { it.userId to it.stats.timezone }
        val zones = ids.associateWith { LocalToday.zone(published[it], zone) }
        val boards = zones.values.distinctBy { it.id }.associate { local ->
            local.id to if (local.id == zone.id) {
                arenaToday
            } else {
                val day = LeaguePeriods.range(LeaguePeriod.TODAY, now, local)
                authed { api.board(it, arena.id, day.from, day.to) }
            }
        }
        return zones to boards
    }

    /** The Arena's players: the all-time board of the loaded snapshot, else fetched. */
    suspend fun arenaPlayers(): List<BoardRow> {
        snapshot.value?.boards?.get(LeaguePeriod.ALL)?.let { return it }
        val arena = account.value.arena ?: run {
            refreshAccount()
            account.value.arena
        } ?: return emptyList()
        val all = LeaguePeriods.range(LeaguePeriod.ALL, Instant.now(), LeaguePeriods.zone(arena.timezone))
        return authed { api.board(it, arena.id, all.from, all.to) }
    }

    /** Fetches every suggestion and marks incoming pending ones already in the library as added. */
    suspend fun loadSuggestions(): List<Suggestion> {
        val me = store.session?.userId ?: return emptyList()
        val fetched = authed { api.suggestions(it) }
        val autoAdded = SuggestionRules.autoAdded(fetched, me, localLibrary.favoriteKeys())
        val list = if (autoAdded.isEmpty()) {
            fetched
        } else {
            try {
                authed { api.markSuggestionsAdded(it, autoAdded, me) }
                SuggestionRules.withStatus(fetched, autoAdded, SuggestionStatus.ADDED, Instant.now())
            } catch (e: FriendsApiException) {
                logcat(LogPriority.WARN, e) { "Marking suggestions added failed" }
                fetched
            }
        }
        publishSuggestions(list)
        return list
    }

    suspend fun suggest(
        friends: List<String>,
        sourceId: Long,
        sourceName: String?,
        mangaUrl: String,
        title: String,
        coverUrl: String?,
        genres: List<String>?,
        note: String?,
    ): List<Suggestion> {
        val me = requireNotNull(store.session?.userId)
        val fallback = SuggestionRules.coverUrl(coverUrl)
        val cover = coverThumbnails.publish(listOf(CoverSeries(sourceId, mangaUrl, fallback)), ::uploadCover)
            .values.firstOrNull() ?: fallback
        val rows = SuggestionRules.inserts(me, friends, sourceId, sourceName, mangaUrl, title, cover, genres, note)
        if (rows.isEmpty()) return emptyList()
        val sent = authed { api.sendSuggestions(it, rows) }
        publishSuggestions(SuggestionRules.merge(suggestions.value.orEmpty(), sent))
        return sent
    }

    suspend fun answerSuggestion(id: String, status: SuggestionStatus) {
        val me = requireNotNull(store.session?.userId)
        val updated = authed { api.answerSuggestion(it, id, me, status) }
        val current = suggestions.value.orEmpty()
        publishSuggestions(
            if (updated != null) {
                SuggestionRules.merge(current, listOf(updated))
            } else {
                SuggestionRules.withStatus(current, listOf(id), status, Instant.now())
            },
        )
    }

    suspend fun withdrawSuggestion(id: String) {
        val me = requireNotNull(store.session?.userId)
        authed { api.withdrawSuggestion(it, id, me) }
        publishSuggestions(suggestions.value.orEmpty().filter { it.id != id })
    }

    /** Shared thumbnail URLs for the series of [reads], keyed by series key; [fallbacks] are the rows' covers. */
    private suspend fun publishCovers(fallbacks: Map<String, String?>, reads: List<LocalRead>): Map<String, String?> {
        val series = reads.map { CoverSeries(it.sourceId, it.mangaUrl, null) }
            .distinctBy { it.key }
            .filter { it.key in fallbacks }
            .map { it.copy(fallback = fallbacks[it.key]) }
        return coverThumbnails.publish(series, ::uploadCover)
    }

    private suspend fun uploadCover(seriesKey: String, jpeg: ByteArray): String =
        authed { api.uploadCover(it, seriesKey, jpeg) }

    private fun publishSuggestions(list: List<Suggestion>) {
        suggestions.value = list
        snapshot.update { it?.copy(suggestions = list) }
    }

    /** Places my bet on a weekly challenge; bets are final. */
    suspend fun placeBet(challengeId: Int, stake: Int) {
        val me = requireNotNull(store.session?.userId)
        require(Bets.validStake(stake))
        val bet = authed { api.placeBet(it, me, challengeId, stake) } ?: Bet(me, challengeId, stake)
        snapshot.update { current ->
            current?.copy(bets = current.bets.filterNot { it.userId == me && it.challengeId == challengeId } + bet)
        }
    }

    /**
     * Window and previous-7-days boards of every ended week (README v6, "Weekly recaps"); those of weeks that ended
     * more than 3 hours ago come from the cache once fetched.
     */
    private suspend fun loadRecapBoards(arena: League, challenges: List<Challenge>, now: Instant) = coroutineScope {
        val cached = readCache(CACHE_RECAPS, recapCacheSerializer).orEmpty()
        val settledBefore = now.minus(WeeklyRecaps.SETTLE_AFTER)
        val weeks = challenges.filter { Bets.isWeekly(it.start, it.end) && it.end <= now }
        val result = weeks.associate { week ->
            val fromCache = cached[week.id.toString()]?.takeIf { week.end <= settledBefore }
            week.id to (
                fromCache?.let { CompletableDeferred(it) } ?: async {
                    val before = week.start.minus(WEEK)
                    val window = async { authed { api.board(it, arena.id, week.start, week.end) } }
                    val previous = async { authed { api.board(it, arena.id, before, week.start) } }
                    RecapBoards(window.await(), previous.await())
                }
                )
        }.mapValues { it.value.await() }
        val settled = weeks.filter { it.end <= settledBefore }.associate { it.id.toString() to result.getValue(it.id) }
        if (settled.keys != cached.keys) writeCache(CACHE_RECAPS, recapCacheSerializer, settled)
        result
    }

    private suspend fun <T> soft(what: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        logcat(LogPriority.WARN, e) { "$what failed" }
        fallback
    }

    /** Boards of every started challenge; those over for a day or more come from the cache once fetched. */
    private suspend fun loadChallengeBoards(challenges: List<Challenge>, now: Instant) = coroutineScope {
        val cached = readCache(CACHE_CHALLENGES, challengeCacheSerializer).orEmpty()
        val settledBefore = now.minusSeconds(SETTLE_SECONDS)
        val result = challenges.filter { it.start <= now }
            .associate { challenge ->
                val fromCache = cached[challenge.id.toString()]?.takeIf { challenge.end <= settledBefore }
                challenge.id to (
                    fromCache?.let { CompletableDeferred(it) }
                        ?: async { authed { api.challengeBoard(it, challenge.id) } }
                    )
            }
            .mapValues { it.value.await() }
        val settled = challenges.filter { it.end <= settledBefore && it.id in result }
            .associate { it.id.toString() to result.getValue(it.id) }
        if (settled.keys != cached.keys) writeCache(CACHE_CHALLENGES, challengeCacheSerializer, settled)
        result
    }

    /** Final boards of past seasons (calendar months); those over for a day or more come from the cache. */
    private suspend fun loadSeasonBoards(arena: League, zone: ZoneId, today: LocalDate) = coroutineScope {
        val cached = readCache(CACHE_SEASONS, seasonCacheSerializer).orEmpty()

        // A season is settled from the second day after it ended
        fun settled(season: YearMonth) = season.plusMonths(1).atDay(2) <= today
        val result = Seasons.past(YearMonth.from(today)).associateWith { season ->
            val range = LeaguePeriods.monthRange(season.atDay(1), zone)
            cached[season.toString()]?.takeIf { settled(season) }?.let { CompletableDeferred(it) }
                ?: async { authed { api.board(it, arena.id, range.from, range.to) } }
        }.mapValues { it.value.await() }
        val toCache = result.filterKeys(::settled).mapKeys { it.key.toString() }
        if (toCache.keys != cached.keys) writeCache(CACHE_SEASONS, seasonCacheSerializer, toCache)
        result
    }

    /**
     * Quest numbers `league_daily` can't give (distinct series, early and late chapters, a new series this week) for
     * every day since the quests started on which anyone read. Days before yesterday are cached once fetched.
     */
    private suspend fun loadQuestFacts(
        arena: League,
        zone: ZoneId,
        today: LocalDate,
        daily: List<DayTotal>,
    ): Map<LocalDate, Map<String, QuestFacts>> = coroutineScope {
        val cached = readCache(CACHE_QUESTS, questCacheSerializer).orEmpty()
        val days = daily.filter { it.chapters > 0 && it.day >= DailyQuests.EPOCH && it.day <= today }
            .map { it.day }
            .distinct()
        val yesterday = today.minusDays(1)
        val result = days.associateWith { day ->
            val fromCache = cached[day.toString()]?.takeIf { day < yesterday }
            fromCache?.let { CompletableDeferred(it) } ?: async { fetchQuestFacts(arena, zone, day) }
        }.mapValues { it.value.await() }
        val toCache = result.filterKeys { it < yesterday }.mapKeys { it.key.toString() }
        if (toCache.keys != cached.keys) writeCache(CACHE_QUESTS, questCacheSerializer, toCache)
        result
    }

    private suspend fun fetchQuestFacts(arena: League, zone: ZoneId, day: LocalDate): Map<String, QuestFacts> =
        coroutineScope {
            val range = LeaguePeriods.dayRange(day, zone)
            val weekStart = LeaguePeriods.weekRange(LeaguePeriods.weekStart(day), zone).from
            val dayBoard = async { authed { api.board(it, arena.id, range.from, range.to) } }
            val hours = async { authed { api.hours(it, arena.id, range.from, range.to) } }
            val weekThrough = async { authed { api.board(it, arena.id, weekStart, range.to) } }
            val weekBefore = if (weekStart < range.from) {
                async { authed { api.board(it, arena.id, weekStart, range.from) } }
            } else {
                CompletableDeferred(emptyList())
            }
            val hourRows = hours.await().groupBy { it.userId }
            val seriesBefore = weekBefore.await().associate { it.userId to it.series }
            val seriesThrough = weekThrough.await().associate { it.userId to it.series }
            dayBoard.await().filter { it.chapters > 0 }.associate { row ->
                val userHours = hourRows[row.userId].orEmpty()
                row.userId to QuestFacts(
                    series = row.series,
                    early = userHours.filter { it.hour < DailyQuests.EARLY_BEFORE_HOUR }.sumOf { it.chapters },
                    late = userHours.filter { it.hour >= DailyQuests.LATE_FROM_HOUR }.sumOf { it.chapters },
                    newSeries = (seriesThrough[row.userId] ?: 0) > (seriesBefore[row.userId] ?: 0),
                )
            }
        }

    private fun <T> readCache(name: String, serializer: KSerializer<T>): T? =
        store.cache(name)?.let { runCatching { api.json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> writeCache(name: String, serializer: KSerializer<T>, value: T) =
        store.putCache(name, api.json.encodeToString(serializer, value))

    private suspend fun accessToken(forceRefresh: Boolean): String = tokenMutex.withLock {
        val session = store.session ?: throw FriendsApiException(401, "Not registered")
        val fresh = session.accessToken.isNotEmpty() && session.expiresAt - 60_000 > System.currentTimeMillis()
        if (fresh && !forceRefresh) return@withLock session.accessToken
        val auth = api.refresh(session.refreshToken)
        store.session = auth.toSession()
        auth.accessToken
    }

    internal suspend fun <T> authed(block: suspend (token: String) -> T): T = try {
        block(accessToken(forceRefresh = false))
    } catch (e: FriendsApiException) {
        if (e.code != 401) throw e
        block(accessToken(forceRefresh = true))
    }

    private fun AuthResponse.toSession() = FriendsSession(
        userId = user.id,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = System.currentTimeMillis() + expiresIn * 1000,
    )

    companion object {
        const val APP = "mihon"
        const val ARENA_ID = "0a12e000-0000-4000-8000-000000000001"
        const val FEED_SIZE = 50
        val READER_SYNC_DELAY = 3.seconds

        /** Results older than this are treated as final (late uploads have had a day to arrive). */
        private const val SETTLE_SECONDS = 86_400L

        private const val CACHE_CHALLENGES = "challenges"
        private const val CACHE_SEASONS = "seasons"
        private const val CACHE_QUESTS = "quests"
        private const val CACHE_STATS_PUBLISHED = "stats_published_at"
        private const val CACHE_RECAPS = "recaps"
        private val WEEK = java.time.Duration.ofDays(7)

        private val recapCacheSerializer = MapSerializer(String.serializer(), RecapBoards.serializer())

        private val challengeCacheSerializer =
            MapSerializer(String.serializer(), ListSerializer(ChallengeScore.serializer()))
        private val seasonCacheSerializer = MapSerializer(String.serializer(), ListSerializer(BoardRow.serializer()))
        private val questCacheSerializer =
            MapSerializer(String.serializer(), MapSerializer(String.serializer(), QuestFacts.serializer()))
    }
}
