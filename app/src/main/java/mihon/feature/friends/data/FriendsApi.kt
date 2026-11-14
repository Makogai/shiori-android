package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.feature.friends.domain.Bet
import mihon.feature.friends.domain.CommentInsert
import mihon.feature.friends.domain.FeedComment
import mihon.feature.friends.domain.FeedReaction
import mihon.feature.friends.domain.RaceRow
import mihon.feature.friends.domain.ReactionUpsert
import mihon.feature.friends.domain.ReadRow
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** How the Friends League talks JSON to Supabase. */
val FriendsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = true
}

class FriendsApiException(val code: Int, message: String) : IOException(message)

/** Plain HTTPS calls to the Friends League Supabase project; see the shared contract in the backend README. */
@Inject
@SingleIn(AppScope::class)
class FriendsApi(networkHelper: NetworkHelper) {

    private val client: OkHttpClient by lazy { networkHelper.client }

    /** For the card functions: judging and painting a frame can take two minutes. */
    private val slowClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(SLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrl = BuildConfig.FRIENDS_SUPABASE_URL
    private val key = BuildConfig.FRIENDS_SUPABASE_KEY

    val isConfigured: Boolean = baseUrl.startsWith("https://") && key.isNotEmpty()

    val json = FriendsJson

    suspend fun signUp(): AuthResponse =
        send(post("/auth/v1/signup", null, "{}"), AuthResponse.serializer())

    suspend fun refresh(refreshToken: String): AuthResponse {
        val body = buildJsonObject { put("refresh_token", refreshToken) }.toString()
        return send(post("/auth/v1/token?grant_type=refresh_token", null, body), AuthResponse.serializer())
    }

    suspend fun upsertProfile(token: String, profile: ProfileUpsert): Profile {
        val body = json.encodeToString(ProfileUpsert.serializer(), profile)
        val request = post(
            "/rest/v1/profiles",
            token,
            body,
            Headers.headersOf("Prefer", "resolution=merge-duplicates,return=representation"),
        )
        return send(request, ListSerializer(Profile.serializer())).first()
    }

    suspend fun profile(token: String, userId: String): Profile? =
        send(get("/rest/v1/profiles?id=eq.$userId&select=*", token), ListSerializer(Profile.serializer()))
            .firstOrNull()

    /** Uploads a JPEG avatar and returns its public URL. */
    suspend fun uploadAvatar(token: String, userId: String, jpeg: ByteArray): String {
        val path = "avatars/$userId/avatar-${Instant.now().epochSecond}.jpg"
        val request = request("/storage/v1/object/$path", token)
            .header("x-upsert", "true")
            .post(jpeg.toRequestBody(JPEG))
            .build()
        execute(request)
        return "$baseUrl/storage/v1/object/public/$path"
    }

    /** Uploads a cover thumbnail and returns its public URL (versioned by content). */
    suspend fun uploadCover(token: String, seriesKey: String, jpeg: ByteArray): String {
        val request = request(CoverRules.objectPath(seriesKey), token)
            .header("x-upsert", "true")
            .post(jpeg.toRequestBody(JPEG))
            .build()
        execute(request)
        return CoverRules.publicUrl(baseUrl, seriesKey, CoverRules.version(jpeg))
    }

    suspend fun leagues(token: String): List<League> =
        send(get("/rest/v1/leagues?select=*&order=created_at", token), ListSerializer(League.serializer()))

    suspend fun leave(token: String, leagueId: String, userId: String) {
        val request = request("/rest/v1/league_members?league_id=eq.$leagueId&user_id=eq.$userId", token)
            .delete()
            .build()
        execute(request)
    }

    suspend fun uploadReads(token: String, rows: List<ReadRow>) {
        val body = json.encodeToString(ListSerializer(ReadRow.serializer()), rows)
        execute(post("/rest/v1/reads", token, body, Headers.headersOf("Prefer", "resolution=merge-duplicates")))
    }

    suspend fun board(token: String, leagueId: String, from: Instant, to: Instant): List<BoardRow> =
        send(rpc("league_board", token, rangeBody(leagueId, from, to)), ListSerializer(BoardRow.serializer()))

    suspend fun daily(token: String, leagueId: String, from: Instant, to: Instant): List<DailyRow> =
        send(rpc("league_daily", token, rangeBody(leagueId, from, to)), ListSerializer(DailyRow.serializer()))

    suspend fun hours(token: String, leagueId: String, from: Instant, to: Instant): List<HourRow> =
        send(rpc("league_hours", token, rangeBody(leagueId, from, to)), ListSerializer(HourRow.serializer()))

    suspend fun challenges(token: String): List<Challenge> =
        send(get("/rest/v1/challenges?select=*&order=starts_at", token), ListSerializer(Challenge.serializer()))

    suspend fun challengeBoard(token: String, challengeId: Int): List<ChallengeScore> {
        val body = buildJsonObject { put("p_challenge", challengeId) }
        return send(rpc("challenge_board", token, body), ListSerializer(ChallengeScore.serializer()))
    }

    suspend fun feed(token: String, leagueId: String, limit: Int): List<FeedRow> {
        val body = buildJsonObject {
            put("p_league", leagueId)
            put("p_limit", limit)
        }
        return send(rpc("league_feed", token, body), ListSerializer(FeedRow.serializer()))
    }

    private val suggestionRequests by lazy { SuggestionRequests(json) }

    private val extraRequests by lazy { ArenaExtrasRequests(json) }

    /** All my reads on the server (client id and time), every page. */
    suspend fun ownReads(token: String, me: String): List<ServerRead> {
        val all = mutableListOf<ServerRead>()
        while (true) {
            val page = send(call(extraRequests.ownReads(me, all.size), token), ListSerializer(ServerRead.serializer()))
            all += page
            if (page.size < ArenaExtrasRequests.READS_PAGE) return all
        }
    }

    suspend fun deleteReads(token: String, me: String, clientIds: List<String>) {
        extraRequests.deleteReads(me, clientIds).forEach { execute(call(it, token)) }
    }

    suspend fun joinedAt(token: String, me: String, leagueId: String): Instant? =
        send(call(extraRequests.joinedAt(me, leagueId), token), ListSerializer(MemberJoined.serializer()))
            .firstOrNull()?.let { parseTimestamp(it.joinedAt) }

    suspend fun saveStats(token: String, me: String, stats: LibraryStats) {
        execute(call(extraRequests.saveStats(me, stats, Instant.now()), token))
    }

    suspend fun stats(token: String, users: List<String>): List<ProfileStatsRow> =
        if (users.isEmpty()) {
            emptyList()
        } else {
            send(call(extraRequests.stats(users), token), ListSerializer(ProfileStatsRow.serializer()))
        }

    suspend fun playerFeed(token: String, user: String, before: Instant?, limit: Int): List<FeedRow> =
        send(call(extraRequests.playerFeed(user, before, limit), token), ListSerializer(FeedRow.serializer()))

    suspend fun sendSuggestions(token: String, rows: List<SuggestionInsert>): List<Suggestion> =
        send(call(suggestionRequests.send(rows), token), ListSerializer(Suggestion.serializer()))

    suspend fun suggestions(token: String): List<Suggestion> =
        send(call(suggestionRequests.list(), token), ListSerializer(Suggestion.serializer()))

    suspend fun answerSuggestion(token: String, id: String, me: String, status: SuggestionStatus): Suggestion? =
        send(call(suggestionRequests.answer(id, me, status), token), ListSerializer(Suggestion.serializer()))
            .firstOrNull()

    suspend fun markSuggestionsAdded(token: String, ids: List<String>, me: String) {
        execute(call(suggestionRequests.markAdded(ids, me), token))
    }

    suspend fun withdrawSuggestion(token: String, id: String, me: String) {
        execute(call(suggestionRequests.withdraw(id, me), token))
    }

    private val socialRequests by lazy { SocialRequests(json) }

    suspend fun reactions(token: String, clientIds: Collection<String>): List<FeedReaction> =
        socialRequests.reactions(clientIds).flatMap { send(call(it, token), ListSerializer(FeedReaction.serializer())) }

    suspend fun react(token: String, row: ReactionUpsert) {
        execute(call(socialRequests.react(row), token))
    }

    suspend fun unreact(token: String, ownerId: String, clientId: String, me: String) {
        execute(call(socialRequests.unreact(ownerId, clientId, me), token))
    }

    suspend fun comments(token: String, clientIds: Collection<String>): List<FeedComment> =
        socialRequests.comments(clientIds).flatMap { send(call(it, token), ListSerializer(FeedComment.serializer())) }

    suspend fun comment(token: String, row: CommentInsert): FeedComment? =
        send(call(socialRequests.comment(row), token), ListSerializer(FeedComment.serializer())).firstOrNull()

    suspend fun deleteComment(token: String, id: String, me: String) {
        execute(call(socialRequests.deleteComment(id, me), token))
    }

    suspend fun newReactions(token: String, me: String, cursor: String): List<FeedReaction> =
        send(call(socialRequests.newReactions(me, cursor), token), ListSerializer(FeedReaction.serializer()))

    suspend fun newComments(token: String, me: String, cursor: String): List<FeedComment> =
        send(call(socialRequests.newComments(me, cursor), token), ListSerializer(FeedComment.serializer()))

    suspend fun races(token: String): List<RaceRow> =
        send(call(socialRequests.races(), token), ListSerializer(RaceRow.serializer()))

    suspend fun bets(token: String): List<Bet> =
        send(call(socialRequests.bets(), token), ListSerializer(Bet.serializer()))

    suspend fun placeBet(token: String, me: String, challengeId: Int, stake: Int): Bet? =
        send(
            call(socialRequests.placeBet(me, challengeId, stake), token),
            ListSerializer(Bet.serializer()),
        ).firstOrNull()

    /** Sends [call] and returns the body; [slow] allows the card functions' long answers. */
    suspend fun send(token: String, rest: RestCall, slow: Boolean = false): String =
        execute(call(rest, token), if (slow) slowClient else client)

    /** An object of a private bucket. */
    suspend fun download(token: String, rest: RestCall): ByteArray = withContext(Dispatchers.IO) {
        check(isConfigured) { "Friends League is not configured" }
        client.newCall(call(rest, token)).await().use { response ->
            if (!response.isSuccessful) {
                throw FriendsApiException(response.code, errorMessage(response.body.string(), response.code))
            }
            response.body.bytes()
        }
    }

    suspend fun uploadJpeg(token: String, path: String, jpeg: ByteArray) {
        execute(request(path, token).post(jpeg.toRequestBody(JPEG)).build())
    }

    /** Uploads (or replaces, `x-upsert`) a PNG object at [path] (`/storage/v1/object/<bucket>/<name>`). */
    suspend fun uploadPng(token: String, path: String, png: ByteArray) {
        execute(request(path, token).header("x-upsert", "true").post(png.toRequestBody(PNG)).build())
    }

    private fun call(call: RestCall, token: String): Request {
        val builder = request(call.path, token)
            .method(call.method, call.body?.toRequestBody(JSON))
        call.prefer?.let { builder.header("Prefer", it) }
        return builder.build()
    }

    private fun rangeBody(leagueId: String, from: Instant, to: Instant) = buildJsonObject {
        put("p_league", leagueId)
        put("p_from", DateTimeFormatter.ISO_INSTANT.format(from))
        put("p_to", DateTimeFormatter.ISO_INSTANT.format(to))
    }

    private fun rpc(name: String, token: String, body: JsonObject) = post("/rest/v1/rpc/$name", token, body.toString())

    private fun request(path: String, token: String?): Request.Builder {
        val builder = Request.Builder()
            .url("$baseUrl$path".toHttpUrl())
            .header("apikey", key)
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder
    }

    private fun get(path: String, token: String) = request(path, token).get().build()

    private fun post(path: String, token: String?, body: String, headers: Headers? = null): Request {
        val builder = request(path, token).post(body.toRequestBody(JSON))
        headers?.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private suspend fun <T> send(request: Request, serializer: KSerializer<T>): T {
        val text = execute(request)
        return json.decodeFromString(serializer, text)
    }

    private suspend fun execute(request: Request, using: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        check(isConfigured) { "Friends League is not configured" }
        using.newCall(request).await().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw FriendsApiException(response.code, errorMessage(text, response.code))
            text
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val message = listOf("message", "msg", "error_description", "error")
            .firstNotNullOfOrNull { name -> runCatching { obj?.get(name)?.jsonPrimitive?.content }.getOrNull() }
        return message ?: "HTTP $code"
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
        val PNG = "image/png".toMediaType()
        const val SLOW_TIMEOUT_SECONDS = 150L
    }
}
