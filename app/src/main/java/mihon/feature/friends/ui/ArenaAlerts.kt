package mihon.feature.friends.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.ArenaSocial
import mihon.feature.friends.data.FriendsJson
import mihon.feature.friends.data.FriendsPreferences
import mihon.feature.friends.data.FriendsRepository
import mihon.feature.friends.data.FriendsSessionStore
import mihon.feature.friends.domain.OvertakeState
import mihon.feature.friends.domain.Overtakes
import mihon.feature.friends.domain.SocialAlerts
import mihon.feature.friends.domain.SocialCursor
import tachiyomi.core.common.util.system.logcat
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * README v6 local notifications: overtakes and new reactions/comments on my reads. Checked on app foreground (after
 * the foreground sync) and by [ArenaAlertsWorker]; the state lives with the session, so a reset starts over.
 */
@Inject
@SingleIn(AppScope::class)
class ArenaAlerts(
    private val context: Context,
    private val repository: FriendsRepository,
    private val social: ArenaSocial,
    private val store: FriendsSessionStore,
    private val preferences: FriendsPreferences,
) {

    private val mutex = Mutex()

    /** A full Arena load and a check, as the periodic worker runs it. */
    suspend fun runCheck() {
        val account = repository.account.value
        if (!account.configured || repository.myId == null) return
        if (!account.loaded) repository.refreshAccount()
        val arena = repository.account.value.arena ?: return
        check(repository.loadSnapshot(arena))
    }

    suspend fun check(snapshot: ArenaSnapshot) = mutex.withLock {
        val me = repository.myId ?: return@withLock
        val model = ArenaUiModel.build(snapshot, me)
        fun name(userId: String) = model.name(userId).ifBlank { UNKNOWN_PLAYER }

        // State is saved before posting, so each event posts once even if posting fails
        val state = store.cache(CACHE_OVERTAKES)
            ?.let { runCatching { FriendsJson.decodeFromString(OvertakeState.serializer(), it) }.getOrNull() }
            ?: OvertakeState()
        val (next, events) = Overtakes.check(state, ArenaSocialModels.overtakeBoards(model), me)
        store.putCache(CACHE_OVERTAKES, FriendsJson.encodeToString(OvertakeState.serializer(), next))
        // README v6 "Foreground": the state moves on, but nothing is posted while the app is open
        val background = !inForeground()
        if (background && preferences.overtakeAlerts.get()) {
            Overtakes.summary(events.map { Overtakes.body(it, name(it.userId)) })
                ?.let { post("overtake:${events.first().key}", it) }
        }

        val cursor = store.cache(CACHE_SOCIAL_CURSOR)?.let(SocialCursor::normalize)
        if (cursor == null) {
            store.putCache(CACHE_SOCIAL_CURSOR, SocialCursor.format(Instant.now()))
            return@withLock
        }
        try {
            val (reactions, comments) = social.newOnMyReads(cursor)
            val plan = SocialAlerts.plan(cursor, Instant.now(), reactions, comments, seen(), ::name)
            store.putCache(CACHE_SOCIAL_CURSOR, plan.cursor)
            store.putCache(CACHE_SOCIAL_SEEN, FriendsJson.encodeToString(SEEN_SERIALIZER, plan.seen))
            if (background && preferences.socialAlerts.get()) {
                plan.reactions?.let { post("reactions:${plan.cursor}", it) }
                plan.comments?.let { post("comments:${plan.cursor}", it) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Checking reactions and comments failed" }
        }
    }

    private fun seen(): List<String> = store.cache(CACHE_SOCIAL_SEEN)
        ?.let { runCatching { FriendsJson.decodeFromString(SEEN_SERIALIZER, it) }.getOrNull() }
        .orEmpty()

    /** The app is open (any activity started): Arena alerts stay quiet then. */
    private suspend fun inForeground(): Boolean = withContext(Dispatchers.Main) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun post(key: String, body: String) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_ARENA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.notify(notificationId(key), Notifications.CHANNEL_ARENA) {
            setSmallIcon(R.drawable.ic_shiori)
            setContentTitle(TITLE)
            setContentText(body)
            setStyle(NotificationCompat.BigTextStyle().bigText(body))
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }
    }

    companion object {
        const val TITLE = "Reading Arena"
        const val ACTION_OPEN_ARENA = "mihon.feature.friends.OPEN_ARENA"
        private const val UNKNOWN_PLAYER = "Unknown player"
        private const val CACHE_OVERTAKES = "overtake_state"
        private const val CACHE_SOCIAL_CURSOR = "social_cursor"
        private const val CACHE_SOCIAL_SEEN = "social_seen"
        private val SEEN_SERIALIZER = ListSerializer(String.serializer())

        /** Stable per event and well away from the app's other (small, mostly negative) notification ids. */
        fun notificationId(key: String): Int = 1_000_000 + (key.hashCode() and 0x3FFFFFFF)
    }
}

/** Every 45 minutes with a network: the same check as on foreground (README v6, "Notifications"). */
class ArenaAlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.appGraph.arenaAlerts.runCheck()
        Result.success()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        logcat(LogPriority.WARN, e) { "Arena alert check failed" }
        Result.success()
    }

    companion object {
        private const val TAG = "ArenaAlerts"
        private const val INTERVAL_MINUTES = 45L

        fun setupTask(context: Context) {
            val request = PeriodicWorkRequestBuilder<ArenaAlertsWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
