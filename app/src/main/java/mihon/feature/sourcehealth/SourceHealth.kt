package mihon.feature.sourcehealth

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.domain.source.health.SourceOperation
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Why a source request failed, coarse enough to suggest what to do about it. */
@Serializable
sealed interface SourceErrorKind {
    @Serializable
    @SerialName("network")
    data object Network : SourceErrorKind

    @Serializable
    @SerialName("http")
    data class Http(val code: Int) : SourceErrorKind

    @Serializable
    @SerialName("cloudflare")
    data object Cloudflare : SourceErrorKind

    /** The site's markup changed or the extension threw while reading it. */
    @Serializable
    @SerialName("parse")
    data object Parse : SourceErrorKind

    @Serializable
    @SerialName("timeout")
    data object Timeout : SourceErrorKind

    @Serializable
    @SerialName("other")
    data object Other : SourceErrorKind

    companion object {
        /** Sorts an error into a kind. Cancellations and missing extensions say nothing about the site: null. */
        fun classify(error: Throwable): SourceErrorKind? {
            val chain = generateSequence(error) { it.cause }.take(MAX_CAUSES).toList()
            return when {
                // Checked before cancellations, which would otherwise swallow a check's own timeout
                chain.any { it is SourceHealthTimeoutException } -> Timeout
                error is CancellationException -> null
                chain.any { it is SourceNotInstalledException } -> null
                // OkHttp's message for a cancelled call
                error is IOException && error.message == "Canceled" -> null
                chain.any { it is CloudflareBypassException } -> Cloudflare
                chain.any { it is HttpException } -> Http(chain.firstNotNullOf { (it as? HttpException)?.code })
                chain.any { it.message?.contains("cloudflare", ignoreCase = true) == true } -> Cloudflare
                chain.any { it is InterruptedIOException } -> Timeout
                chain.any { it is IOException } -> Network
                PARSE_ERRORS.any { type -> type.isInstance(error) } -> Parse
                else -> Other
            }
        }

        private const val MAX_CAUSES = 8

        // What an extension's parsing throws when the markup isn't what it expects
        private val PARSE_ERRORS = listOf(
            IllegalArgumentException::class,
            IllegalStateException::class,
            NullPointerException::class,
            IndexOutOfBoundsException::class,
            NoSuchElementException::class,
            ClassCastException::class,
        )
    }
}

/** Raised when a health check takes too long. */
class SourceHealthTimeoutException : Exception("The source took too long to answer")

/** One request's outcome. */
@Serializable
data class SourceOutcome(
    val at: Long,
    val operation: SourceOperation,
    /** Null for a success. */
    val error: SourceErrorKind? = null,
    val message: String? = null,
) {
    val succeeded: Boolean
        get() = error == null
}

/** A source's recent outcomes, newest last. */
@Serializable
data class SourceHealthRecord(
    val outcomes: List<SourceOutcome> = emptyList(),
    val lastSuccess: Long? = null,
    /** The first failure since the last success; kept apart because old outcomes are dropped. */
    val failingSince: Long? = null,
) {
    fun appending(outcome: SourceOutcome): SourceHealthRecord {
        return SourceHealthRecord(
            outcomes = (outcomes + outcome).takeLast(KEPT_OUTCOMES),
            lastSuccess = if (outcome.succeeded) maxOf(lastSuccess ?: outcome.at, outcome.at) else lastSuccess,
            failingSince = if (outcome.succeeded) null else failingSince ?: outcome.at,
        )
    }

    val lastFailure: SourceOutcome?
        get() = outcomes.lastOrNull { !it.succeeded }

    /** Failures in a row at the end of the record. */
    val failureStreak: Int
        get() = outcomes.takeLastWhile { !it.succeeded }.size

    companion object {
        const val KEPT_OUTCOMES = 20
    }
}

/** Declared from best to worst, so comparing sorts by severity. */
enum class SourceHealthStatus {
    Healthy,
    Unknown,
    Degraded,
    Failing,
    NeedsCloudflare,
    Dead,
    ;

    /** Something the user should look at: badges and alerts count these. */
    val isProblem: Boolean
        get() = this == Failing || this == NeedsCloudflare || this == Dead
}

object SourceHealthLogic {
    /** Failing for longer than this is Dead. */
    val DEAD_AFTER = 7.days

    /** Failures in a row that make a source Failing rather than Degraded. */
    const val FAILING_STREAK = 2

    /**
     * The status of a record:
     * - the last request hit a Cloudflare check: Needs Cloudflare check (the user can fix it);
     * - failures in a row since more than a week ago: Dead;
     * - two or more failures in a row: Failing;
     * - the last request failed, or a third of the recent ones did: Degraded;
     * - otherwise Healthy (Not checked yet without outcomes).
     */
    fun status(record: SourceHealthRecord?, now: Long): SourceHealthStatus {
        val last = record?.outcomes?.lastOrNull() ?: return SourceHealthStatus.Unknown
        if (last.error == SourceErrorKind.Cloudflare) return SourceHealthStatus.NeedsCloudflare
        val streak = record.failureStreak
        if (streak >= FAILING_STREAK) {
            val since = record.failingSince
            return if (since != null && now - since > DEAD_AFTER.inWholeMilliseconds) {
                SourceHealthStatus.Dead
            } else {
                SourceHealthStatus.Failing
            }
        }
        if (streak == 1) return SourceHealthStatus.Degraded
        val recent = record.outcomes.takeLast(10)
        val failures = recent.count { !it.succeeded }
        return if (failures > 0 &&
            failures * 3 >= recent.size
        ) {
            SourceHealthStatus.Degraded
        } else {
            SourceHealthStatus.Healthy
        }
    }

    /**
     * Whether a change of status is worth a notification: a source newly needing attention, or one getting worse in
     * a way that asks for something different (a Cloudflare check, or giving up on it).
     */
    fun shouldAlert(old: SourceHealthStatus, new: SourceHealthStatus): Boolean {
        if (!new.isProblem || new == old) return false
        return !old.isProblem || new == SourceHealthStatus.Dead || new == SourceHealthStatus.NeedsCloudflare
    }
}

/** When the daily health check runs: at most once a day, when it is on. */
object SourceHealthSchedule {
    val INTERVAL = 24.hours

    /** Periodic work runs a little early or late. */
    val TOLERANCE = 15.minutes

    fun isDue(enabled: Boolean, lastRun: Long?, now: Long): Boolean {
        if (!enabled) return false
        if (lastRun == null) return true
        return now - lastRun >= (INTERVAL - TOLERANCE).inWholeMilliseconds
    }

    /** Which library series a source's check fetches: they take turns, by [turn]. */
    fun <T> series(candidates: List<T>, turn: Int, id: (T) -> Long): T? {
        if (candidates.isEmpty()) return null
        val ordered = candidates.sortedBy(id)
        return ordered[turn.mod(ordered.size)]
    }
}
