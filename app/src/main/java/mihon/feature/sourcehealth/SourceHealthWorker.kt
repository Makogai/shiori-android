package mihon.feature.sourcehealth

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/** The daily source health check, under the library update's network restrictions. */
class SourceHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        // The network request constraint only applies from Android 9
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val restrictions = graph.libraryPreferences.autoUpdateDeviceRestrictions.get()
            if (DEVICE_ONLY_ON_WIFI in restrictions && !applicationContext.isConnectedToWifi()) return Result.retry()
        }
        return try {
            graph.sourceHealthMonitor.runDailyCheckIfDue()
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Source health check failed" }
            Result.success()
        }
    }

    companion object {
        private const val TAG = "SourceHealth"
        private const val WORK_NAME_PERIODIC = "SourceHealth-periodic"
        private const val WORK_NAME_NOW = "SourceHealth-now"

        /** Schedules the daily check, or cancels it when it is off. */
        fun setupTask(context: Context) {
            val graph = context.appGraph
            if (!graph.sourceHealthPreferences.dailyCheck.get()) {
                context.workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<SourceHealthWorker>(
                SourceHealthSchedule.INTERVAL.inWholeHours,
                TimeUnit.HOURS,
                SourceHealthSchedule.TOLERANCE.inWholeMinutes,
                TimeUnit.MINUTES,
            )
                .addTag(TAG)
                .setConstraints(constraints(context))
                .build()
            context.workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Runs the check soon, once the network allows, if a day has passed since the last one. */
        fun runIfDue(context: Context) {
            val preferences = context.appGraph.sourceHealthPreferences
            val lastRun = preferences.lastCheck.get().takeIf { it > 0 }
            val now = Clock.System.now().toEpochMilliseconds()
            if (!SourceHealthSchedule.isDue(preferences.dailyCheck.get(), lastRun, now)) return
            val request = OneTimeWorkRequestBuilder<SourceHealthWorker>()
                .addTag(TAG)
                .setConstraints(constraints(context))
                .build()
            context.workManager.enqueueUniqueWork(WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }

        private fun constraints(context: Context): Constraints {
            val restrictions = context.appGraph.libraryPreferences.autoUpdateDeviceRestrictions.get()
            val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val networkRequest = NetworkRequest.Builder().apply {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }
                .build()
            return Constraints.Builder()
                // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                .setRequiredNetworkRequest(networkRequest, networkType)
                .build()
        }
    }
}
