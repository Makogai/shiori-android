package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mihon.feature.friends.domain.CommentInsert
import mihon.feature.friends.domain.FeedComment
import mihon.feature.friends.domain.FeedReaction
import mihon.feature.friends.domain.ReactionUpsert
import mihon.feature.friends.domain.Reactions
import mihon.feature.friends.domain.ReadKey
import mihon.feature.friends.domain.ReadSocial
import java.time.Instant

/** Reactions and comments on feed reads (README v6, section 3), shared by the Arena feed and profile Activity. */
@Inject
@SingleIn(AppScope::class)
class ArenaSocial(
    private val repository: FriendsRepository,
    private val api: FriendsApi,
) {

    /** Reactions and comments of every read loaded so far. */
    val social: StateFlow<Map<ReadKey, ReadSocial>>
        field = MutableStateFlow(emptyMap())

    /** Fetches reactions and comments of [reads], replacing what was known about them. */
    suspend fun load(reads: Collection<ReadKey>) {
        if (reads.isEmpty()) return
        val keys = reads.toSet()
        val ids = keys.map { it.clientId }.distinct()
        val (reactions, comments) = coroutineScope {
            val reactions = async { repository.authed { api.reactions(it, ids) } }
            val comments = async { repository.authed { api.comments(it, ids) } }
            reactions.await().filter { it.read in keys } to comments.await().filter { it.read in keys }
        }
        val byReadReactions = reactions.groupBy { it.read }
        val byReadComments = comments.groupBy { it.read }
        social.update { current ->
            current + keys.associateWith { key ->
                ReadSocial(
                    reactions = byReadReactions[key].orEmpty(),
                    comments = byReadComments[key].orEmpty().sortedBy { it.created },
                )
            }
        }
    }

    /** Sets my reaction on [read] to [emoji], or removes it when null; shown at once, undone if the request fails. */
    suspend fun react(read: ReadKey, emoji: String?) {
        val me = requireNotNull(repository.myId)
        require(read.ownerId != me) { "You can't react to your own reads" }
        val before = social.value[read] ?: ReadSocial()
        update(read) { it.copy(reactions = Reactions.withMine(it.reactions, read, me, emoji, Instant.now())) }
        try {
            repository.authed { token ->
                if (emoji == null) {
                    api.unreact(token, read.ownerId, read.clientId, me)
                } else {
                    api.react(token, ReactionUpsert(read.ownerId, read.clientId, me, emoji))
                }
            }
        } catch (e: Exception) {
            update(read) { before }
            throw e
        }
    }

    suspend fun comment(read: ReadKey, text: String) {
        val me = requireNotNull(repository.myId)
        val body = requireNotNull(Reactions.commentBody(text)) { "Comments are 1–200 characters" }
        val created = repository.authed { api.comment(it, CommentInsert(read.ownerId, read.clientId, me, body)) }
        if (created != null) {
            update(read) { it.copy(comments = it.comments + created) }
        } else {
            load(listOf(read))
        }
    }

    suspend fun deleteComment(comment: FeedComment) {
        val me = requireNotNull(repository.myId)
        repository.authed { api.deleteComment(it, comment.id, me) }
        update(comment.read) { social -> social.copy(comments = social.comments.filter { it.id != comment.id }) }
    }

    /** Others' reactions and comments on my reads newer than [cursor]. */
    suspend fun newOnMyReads(cursor: String): Pair<List<FeedReaction>, List<FeedComment>> = coroutineScope {
        val me = requireNotNull(repository.myId)
        val reactions = async { repository.authed { api.newReactions(it, me, cursor) } }
        val comments = async { repository.authed { api.newComments(it, me, cursor) } }
        reactions.await() to comments.await()
    }

    fun clear() {
        social.value = emptyMap()
    }

    private fun update(read: ReadKey, change: (ReadSocial) -> ReadSocial) =
        social.update { it + (read to change(it[read] ?: ReadSocial())) }
}
