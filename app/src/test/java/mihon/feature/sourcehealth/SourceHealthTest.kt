package mihon.feature.sourcehealth

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import mihon.domain.source.health.SourceOperation
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.hours

class SourceHealthTest {

    private val now = 1_800_000_000_000L

    private fun hoursAgo(hours: Double) = now - (hours * 1.hours.inWholeMilliseconds).toLong()

    private fun record(vararg outcomes: Pair<Double, SourceErrorKind?>): SourceHealthRecord {
        return outcomes.fold(SourceHealthRecord()) { record, (hours, error) ->
            record.appending(SourceOutcome(hoursAgo(hours), SourceOperation.LibraryUpdate, error))
        }
    }

    private fun status(record: SourceHealthRecord?) = SourceHealthLogic.status(record, now)

    @Test
    fun `no outcomes is unknown`() {
        status(null) shouldBe SourceHealthStatus.Unknown
        status(SourceHealthRecord()) shouldBe SourceHealthStatus.Unknown
    }

    @Test
    fun `successes are healthy`() {
        status(record(3.0 to null, 2.0 to null, 1.0 to null)) shouldBe SourceHealthStatus.Healthy
    }

    @Test
    fun `one failure is degraded`() {
        status(record(2.0 to null, 1.0 to SourceErrorKind.Network)) shouldBe SourceHealthStatus.Degraded
    }

    @Test
    fun `frequent failures are degraded even after a success`() {
        val mixed = record(
            6.0 to null,
            5.0 to SourceErrorKind.Timeout,
            4.0 to null,
            3.0 to SourceErrorKind.Http(500),
            2.0 to null,
            1.0 to null,
        )
        status(mixed) shouldBe SourceHealthStatus.Degraded
        val rare = record(9.0 to SourceErrorKind.Timeout, *(8 downTo 1).map { it.toDouble() to null }.toTypedArray())
        status(rare) shouldBe SourceHealthStatus.Healthy
    }

    @Test
    fun `failures in a row are failing`() {
        status(record(3.0 to null, 2.0 to SourceErrorKind.Http(503), 1.0 to SourceErrorKind.Parse)) shouldBe
            SourceHealthStatus.Failing
    }

    @Test
    fun `failing for a week is dead`() {
        val old = record(24.0 * 9 to null, 24.0 * 8 to SourceErrorKind.Http(404), 1.0 to SourceErrorKind.Http(404))
        status(old) shouldBe SourceHealthStatus.Dead
        val recent = record(24.0 * 6 to SourceErrorKind.Http(404), 1.0 to SourceErrorKind.Http(404))
        status(recent) shouldBe SourceHealthStatus.Failing
    }

    @Test
    fun `a Cloudflare check comes first`() {
        status(record(2.0 to null, 1.0 to SourceErrorKind.Cloudflare)) shouldBe SourceHealthStatus.NeedsCloudflare
        val longFailing = record(24.0 * 10 to SourceErrorKind.Network, 1.0 to SourceErrorKind.Cloudflare)
        status(longFailing) shouldBe SourceHealthStatus.NeedsCloudflare
    }

    @Test
    fun `a success clears the failing streak`() {
        val failing = record(24.0 * 10 to SourceErrorKind.Network, 24.0 * 9 to SourceErrorKind.Network)
        failing.failingSince shouldBe hoursAgo(24.0 * 10)
        val recovered = failing.appending(SourceOutcome(now, SourceOperation.HealthCheck))
        recovered.failingSince.shouldBeNull()
        recovered.lastSuccess shouldBe now
        status(recovered) shouldBe SourceHealthStatus.Degraded
    }

    @Test
    fun `a record keeps the newest outcomes`() {
        val long = record(*(30 downTo 1).map { it.toDouble() to null }.toTypedArray())
        long.outcomes.size shouldBe SourceHealthRecord.KEPT_OUTCOMES
        long.outcomes.last().at shouldBe hoursAgo(1.0)
    }

    @Test
    fun `records survive a round trip through JSON`() {
        val records = mapOf(
            7L to record(3.0 to null, 2.0 to SourceErrorKind.Http(503), 1.0 to SourceErrorKind.Cloudflare),
        )
        val json = Json.encodeToString(records)
        Json.decodeFromString<Map<Long, SourceHealthRecord>>(json) shouldBe records
    }

    @Test
    fun `errors are classified`() {
        SourceErrorKind.classify(IOException("Failed to bypass Cloudflare", CloudflareBypassException())) shouldBe
            SourceErrorKind.Cloudflare
        SourceErrorKind.classify(IOException(CloudflareBypassException())) shouldBe SourceErrorKind.Cloudflare
        SourceErrorKind.classify(HttpException(503)) shouldBe SourceErrorKind.Http(503)
        SourceErrorKind.classify(UnknownHostException("example.test")) shouldBe SourceErrorKind.Network
        SourceErrorKind.classify(SocketTimeoutException("timeout")) shouldBe SourceErrorKind.Timeout
        SourceErrorKind.classify(SourceHealthTimeoutException()) shouldBe SourceErrorKind.Timeout
        SourceErrorKind.classify(NullPointerException()) shouldBe SourceErrorKind.Parse
        SourceErrorKind.classify(IndexOutOfBoundsException()) shouldBe SourceErrorKind.Parse
        SourceErrorKind.classify(IllegalStateException("site is showing a Cloudflare check")) shouldBe
            SourceErrorKind.Cloudflare
        SourceErrorKind.classify(Exception("boom")) shouldBe SourceErrorKind.Other
        SourceErrorKind.classify(CancellationException()).shouldBeNull()
        SourceErrorKind.classify(IOException("Canceled")).shouldBeNull()
        SourceErrorKind.classify(SourceNotInstalledException()).shouldBeNull()
    }

    @Test
    fun `alerts only when a source newly needs attention`() {
        fun alert(old: SourceHealthStatus, new: SourceHealthStatus) = SourceHealthLogic.shouldAlert(old, new)
        alert(SourceHealthStatus.Healthy, SourceHealthStatus.Failing) shouldBe true
        alert(SourceHealthStatus.Degraded, SourceHealthStatus.NeedsCloudflare) shouldBe true
        alert(SourceHealthStatus.Unknown, SourceHealthStatus.Dead) shouldBe true
        alert(SourceHealthStatus.Failing, SourceHealthStatus.Dead) shouldBe true
        alert(SourceHealthStatus.Failing, SourceHealthStatus.NeedsCloudflare) shouldBe true
        alert(SourceHealthStatus.Failing, SourceHealthStatus.Failing) shouldBe false
        alert(SourceHealthStatus.Dead, SourceHealthStatus.Failing) shouldBe false
        alert(SourceHealthStatus.Healthy, SourceHealthStatus.Degraded) shouldBe false
        alert(SourceHealthStatus.Failing, SourceHealthStatus.Healthy) shouldBe false
    }

    @Test
    fun `statuses sort worst first`() {
        SourceHealthStatus.entries.sortedDescending() shouldContainExactly listOf(
            SourceHealthStatus.Dead,
            SourceHealthStatus.NeedsCloudflare,
            SourceHealthStatus.Failing,
            SourceHealthStatus.Degraded,
            SourceHealthStatus.Unknown,
            SourceHealthStatus.Healthy,
        )
    }

    @Test
    fun `checks run at most once a day`() {
        fun due(enabled: Boolean, lastRunHoursAgo: Double?) =
            SourceHealthSchedule.isDue(enabled, lastRunHoursAgo?.let(::hoursAgo), now)
        due(enabled = true, lastRunHoursAgo = null) shouldBe true
        due(enabled = false, lastRunHoursAgo = null) shouldBe false
        due(enabled = true, lastRunHoursAgo = 1.0) shouldBe false
        due(enabled = true, lastRunHoursAgo = 20.0) shouldBe false
        // Periodic work woken a few minutes early still counts
        due(enabled = true, lastRunHoursAgo = 23.9) shouldBe true
        due(enabled = true, lastRunHoursAgo = 30.0) shouldBe true
    }

    @Test
    fun `checked series take turns`() {
        val ids = listOf(30L, 10L, 20L)
        (0..3).mapNotNull { turn -> SourceHealthSchedule.series(ids, turn) { it } } shouldContainExactly
            listOf(10L, 20L, 30L, 10L)
        SourceHealthSchedule.series(emptyList<Long>(), 0) { it }.shouldBeNull()
    }
}
