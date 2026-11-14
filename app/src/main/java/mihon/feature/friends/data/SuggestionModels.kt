package mihon.feature.friends.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
enum class SuggestionStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("added")
    ADDED,

    @SerialName("dismissed")
    DISMISSED,

    /** The recipient read it (README v9); the sender sees it. */
    @SerialName("read")
    READ,
    ;

    val value: String get() = name.lowercase()
}

/** A row of `suggestions`; see "v3 — Suggestions" in the backend README. */
@Serializable
data class Suggestion(
    val id: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    /** The 64-bit source id in decimal; JSON numbers would lose precision. */
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("manga_url") val mangaUrl: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val genres: List<String> = emptyList(),
    val note: String? = null,
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    @SerialName("created_at") val createdAt: String,
    @SerialName("responded_at") val respondedAt: String? = null,
) {
    val created: Instant get() = parseTimestamp(createdAt) ?: Instant.EPOCH
    val responded: Instant? get() = respondedAt?.let(::parseTimestamp)
    val pending: Boolean get() = status == SuggestionStatus.PENDING

    /** The friend took it up: added it or read it. */
    val accepted: Boolean get() = status == SuggestionStatus.ADDED || status == SuggestionStatus.READ
}

/** One row of a send; every key is sent, null when empty. */
@Serializable
data class SuggestionInsert(
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String?,
    @SerialName("manga_url") val mangaUrl: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String?,
    // Not null: the column is NOT NULL with a default that an explicit null would bypass
    val genres: List<String>,
    val note: String?,
)

/** A PostgREST request without the base URL and credentials, so its shape can be checked in tests. */
data class RestCall(
    val method: String,
    val path: String,
    val body: String? = null,
    val prefer: String? = null,
)

/** The suggestion requests of the shared contract. */
class SuggestionRequests(private val json: Json) {

    fun send(rows: List<SuggestionInsert>) = RestCall(
        method = "POST",
        path = "/rest/v1/suggestions?on_conflict=from_user,to_user,source_id,manga_url",
        body = json.encodeToString(ListSerializer(SuggestionInsert.serializer()), rows),
        prefer = "resolution=merge-duplicates,return=representation",
    )

    fun list() = RestCall(
        method = "GET",
        path = "/rest/v1/suggestions?select=*&order=created_at.desc&limit=$LIST_LIMIT",
    )

    fun answer(id: String, me: String, status: SuggestionStatus) = RestCall(
        method = "PATCH",
        path = "/rest/v1/suggestions?id=eq.$id&to_user=eq.$me",
        body = statusBody(status),
        prefer = "return=representation",
    )

    fun markAdded(ids: List<String>, me: String) = RestCall(
        method = "PATCH",
        path = "/rest/v1/suggestions?id=in.(${ids.joinToString(",")})&to_user=eq.$me&status=eq.pending",
        body = statusBody(SuggestionStatus.ADDED),
    )

    fun withdraw(id: String, me: String) = RestCall(
        method = "DELETE",
        path = "/rest/v1/suggestions?id=eq.$id&from_user=eq.$me&status=eq.pending",
    )

    private fun statusBody(status: SuggestionStatus) = buildJsonObject { put("status", status.value) }.toString()

    companion object {
        const val LIST_LIMIT = 500
    }
}
