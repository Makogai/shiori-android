package mihon.feature.friends.cards

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mihon.feature.friends.data.RestCall
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** The v7 requests (backend README "v7 — Shiori Cards"). */
class CardRequests(private val json: Json) {

    /** The art's object name inside `card-art`: `<uid>/<uuid>.jpg`, lowercase. */
    fun artPath(userId: String, uuid: UUID = UUID.randomUUID()): String =
        "${userId.lowercase(Locale.ROOT)}/${uuid.toString().lowercase(Locale.ROOT)}.jpg"

    fun uploadArtPath(artPath: String) = "/storage/v1/object/$ART_BUCKET/$artPath"

    fun pull(request: CardPullRequest) = RestCall(
        method = "POST",
        path = "/functions/v1/pull-card",
        body = json.encodeToString(CardPullRequest.serializer(), request),
    )

    fun startFrame(cardId: String) = RestCall(
        method = "POST",
        path = "/functions/v1/frame-card",
        body = buildJsonObject { put("card_id", cardId) }.toString(),
    )

    fun card(id: String) = RestCall(method = "GET", path = "/rest/v1/cards?id=eq.$id&select=*")

    /** Everyone's cards the caller can see (RLS: the Arena's), newest first; one owner's with [owner]. */
    fun cards(owner: String?, limit: Int = ALBUM_LIMIT) = RestCall(
        method = "GET",
        path = "/rest/v1/cards?select=*" + (owner?.let { "&owner=eq.$it" } ?: "") +
            "&order=created_at.desc&limit=$limit",
    )

    fun feed(limit: Int = FEED_LIMIT) = RestCall(
        method = "POST",
        path = "/rest/v1/rpc/card_feed",
        body = buildJsonObject { put("p_limit", limit) }.toString(),
    )

    fun favorite(id: String, favorite: Boolean) = RestCall(
        method = "PATCH",
        path = "/rest/v1/cards?id=eq.$id",
        body = buildJsonObject { put("favorite", favorite) }.toString(),
        prefer = "return=minimal",
    )

    /** Deleting a card is two calls: the row (mine only), then its art. */
    fun delete(card: ShioriCard, me: String) = listOf(
        RestCall(method = "DELETE", path = "/rest/v1/cards?id=eq.${card.id}&owner=eq.$me"),
        RestCall(method = "DELETE", path = "/storage/v1/object/$ART_BUCKET/${card.artPath}"),
    )

    /** The rendered card's object name inside `card-renders`: `<uid>/<card id>.png`, lowercase. */
    fun renderPath(userId: String, cardId: String): String =
        "${userId.lowercase(Locale.ROOT)}/${cardId.lowercase(Locale.ROOT)}.png"

    fun uploadRenderPath(renderPath: String) = "/storage/v1/object/$RENDER_BUCKET/$renderPath"

    /** Records the uploaded render on the card (the server replaces the time with its own clock). */
    fun recordRender(cardId: String, renderPath: String, now: Instant = Instant.now()) = RestCall(
        method = "PATCH",
        path = "/rest/v1/cards?id=eq.$cardId",
        body = buildJsonObject {
            put("render_path", renderPath)
            put("render_updated_at", DateTimeFormatter.ISO_INSTANT.format(now))
        }.toString(),
        prefer = "return=minimal",
    )

    fun image(bucket: String, path: String) = RestCall(
        method = "GET",
        path = "/storage/v1/object/authenticated/$bucket/$path",
    )

    fun pullsToday(userId: String, day: String) = RestCall(
        method = "GET",
        path = "/rest/v1/card_pulls?select=count&user_id=eq.$userId&day=eq.$day",
    )

    fun decodeCards(text: String): List<ShioriCard> = json.decodeFromString(
        ListSerializer(ShioriCard.serializer()),
        text,
    )

    fun decodePullsToday(text: String): Int =
        json.decodeFromString(ListSerializer(PullCount.serializer()), text).firstOrNull()?.count ?: 0

    /** A 200 answer from `pull-card`. */
    fun decodePull(text: String): CardPullResult {
        val response = json.decodeFromString(CardPullResponse.serializer(), text)
        val pulls = response.pullsToday ?: 0
        val card = response.card
        if (!response.valid || card == null) {
            return CardPullResult.Invalid(response.reason ?: "That doesn't look like a panel.", pulls)
        }
        return CardPullResult.Pulled(card, pulls, startFrame = response.frameJob == "client")
    }

    @kotlinx.serialization.Serializable
    private data class PullCount(val count: Int = 0)

    companion object {
        const val ART_BUCKET = "card-art"
        const val FRAME_BUCKET = "card-frames"
        const val RENDER_BUCKET = "card-renders"
        const val ALBUM_LIMIT = 500
        const val FEED_LIMIT = 50
        const val JUDGE_BUSY = "The card judge is busy right now — try again in a minute."

        /** 429 → the daily limit; 502–504 → the judge is down; anything else as the server put it. */
        fun pullError(status: Int, message: String): CardPullError = when (status) {
            429 -> CardPullError.DailyLimit(CardRules.DAILY_LIMIT, CardRules.DAILY_LIMIT)
            502, 503, 504 -> CardPullError.JudgeUnavailable(
                message.takeUnless {
                    it.isBlank() || it == "HTTP $status"
                } ?: JUDGE_BUSY,
            )
            else -> CardPullError.Other(message)
        }
    }
}
