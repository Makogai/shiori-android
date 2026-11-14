package mihon.feature.friends.data

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.feature.friends.domain.UploadCursor

data class FriendsSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    /** Epoch milliseconds. */
    val expiresAt: Long,
)

/**
 * Session and sync bookkeeping in app-private storage, outside the preference store so it never ends up in
 * backups.
 */
@Inject
@SingleIn(AppScope::class)
class FriendsSessionStore(private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("friends_league", Context.MODE_PRIVATE) }

    var session: FriendsSession?
        get() {
            val userId = prefs.getString(KEY_USER_ID, null) ?: return null
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
            return FriendsSession(
                userId = userId,
                accessToken = prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty(),
                refreshToken = refreshToken,
                expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_USER_ID)
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_EXPIRES_AT)
            } else {
                putString(KEY_USER_ID, value.userId)
                putString(KEY_ACCESS_TOKEN, value.accessToken)
                putString(KEY_REFRESH_TOKEN, value.refreshToken)
                putLong(KEY_EXPIRES_AT, value.expiresAt)
            }
        }

    var cursor: UploadCursor
        get() = UploadCursor(prefs.getLong(KEY_UPLOADED_UNTIL, 0), prefs.getBoolean(KEY_ARENA_STARTED, false))
        set(value) = prefs.edit {
            putLong(KEY_UPLOADED_UNTIL, value.uploadedUntil)
            putBoolean(KEY_ARENA_STARTED, value.arenaStarted)
        }

    /** Celebrations already shown, so each event celebrates once. */
    var celebrated: Set<String>
        get() = prefs.getStringSet(KEY_CELEBRATED, null).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_CELEBRATED, value) }

    /** Own completed daily quests as `<date>:<QUEST>`, kept in case the server can't derive them later. */
    var completedQuests: Set<String>
        get() = prefs.getStringSet(KEY_COMPLETED_QUESTS, null).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_COMPLETED_QUESTS, value) }

    /** JSON caches of server results that no longer change (finished days, challenges and seasons). */
    fun cache(name: String): String? = prefs.getString(KEY_CACHE_PREFIX + name, null)

    fun putCache(name: String, json: String) = prefs.edit { putString(KEY_CACHE_PREFIX + name, json) }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_UPLOADED_UNTIL = "uploaded_until"
        const val KEY_ARENA_STARTED = "arena_started"
        const val KEY_CELEBRATED = "celebrated"
        const val KEY_COMPLETED_QUESTS = "completed_quests"
        const val KEY_CACHE_PREFIX = "cache_"
    }
}
