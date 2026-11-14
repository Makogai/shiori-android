package mihon.feature.friends.data

import kotlinx.serialization.json.Json
import mihon.feature.friends.domain.BetInsert
import mihon.feature.friends.domain.CommentInsert
import mihon.feature.friends.domain.ReactionUpsert
import mihon.feature.friends.domain.SocialCursor

/** The v6 requests of the shared contract: reactions, comments, races and bets. */
class SocialRequests(private val json: Json) {

    /** Reactions on loaded reads, at most 100 distinct client ids per request. */
    fun reactions(clientIds: Collection<String>): List<RestCall> = chunks(clientIds).map { ids ->
        RestCall(
            method = "GET",
            path = "/rest/v1/feed_reactions?select=owner_id,client_id,reactor_id,emoji,created_at" +
                "&client_id=in.(${ids.joinToString(",")})",
        )
    }

    fun react(row: ReactionUpsert) = RestCall(
        method = "POST",
        path = "/rest/v1/feed_reactions?on_conflict=owner_id,client_id,reactor_id",
        body = json.encodeToString(ReactionUpsert.serializer(), row),
        prefer = "resolution=merge-duplicates,return=minimal",
    )

    fun unreact(ownerId: String, clientId: String, me: String) = RestCall(
        method = "DELETE",
        path = "/rest/v1/feed_reactions?owner_id=eq.$ownerId&client_id=eq.$clientId&reactor_id=eq.$me",
    )

    /** Comments on loaded reads, oldest first, same chunking as reactions. */
    fun comments(clientIds: Collection<String>): List<RestCall> = chunks(clientIds).map { ids ->
        RestCall(
            method = "GET",
            path = "/rest/v1/feed_comments?select=id,owner_id,client_id,author_id,body,created_at" +
                "&client_id=in.(${ids.joinToString(",")})&order=created_at",
        )
    }

    fun comment(row: CommentInsert) = RestCall(
        method = "POST",
        path = "/rest/v1/feed_comments",
        body = json.encodeToString(CommentInsert.serializer(), row.copy(body = row.body.trim())),
        prefer = "return=representation",
    )

    fun deleteComment(id: String, me: String) = RestCall(
        method = "DELETE",
        path = "/rest/v1/feed_comments?id=eq.$id&author_id=eq.$me",
    )

    /** Others' reactions on my reads newer than [cursor] (a [SocialCursor], sent percent-encoded), read embedded. */
    fun newReactions(me: String, cursor: String) = RestCall(
        method = "GET",
        path = "/rest/v1/feed_reactions?select=owner_id,client_id,reactor_id,emoji,created_at,$READ_EMBED" +
            "&owner_id=eq.$me&reactor_id=neq.$me&created_at=gt.${SocialCursor.encode(
                cursor,
            )}&order=created_at&limit=$NEW_LIMIT",
    )

    fun newComments(me: String, cursor: String) = RestCall(
        method = "GET",
        path = "/rest/v1/feed_comments?select=id,owner_id,client_id,author_id,body,created_at,$READ_EMBED" +
            "&owner_id=eq.$me&author_id=neq.$me&created_at=gt.${SocialCursor.encode(
                cursor,
            )}&order=created_at&limit=$NEW_LIMIT",
    )

    fun races() = RestCall(method = "POST", path = "/rest/v1/rpc/arena_races", body = "{}")

    fun bets() = RestCall(
        method = "GET",
        path = "/rest/v1/bets?select=user_id,challenge_id,stake,created_at&order=created_at",
    )

    fun placeBet(me: String, challengeId: Int, stake: Int) = RestCall(
        method = "POST",
        path = "/rest/v1/bets",
        body = json.encodeToString(BetInsert.serializer(), BetInsert(me, challengeId, stake)),
        prefer = "return=representation",
    )

    private fun chunks(clientIds: Collection<String>) = clientIds.distinct().chunked(CHUNK)

    companion object {
        const val CHUNK = 100
        const val NEW_LIMIT = 50
        private const val READ_EMBED = "reads(series_title,chapter_number,chapter_name)"
    }
}
