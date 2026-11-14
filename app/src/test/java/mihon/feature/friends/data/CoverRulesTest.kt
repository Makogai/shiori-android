package mihon.feature.friends.data

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import mihon.feature.friends.domain.LeagueHashing
import org.junit.jupiter.api.Test

class CoverRulesTest {

    private val base = "https://project.supabase.co"
    private val key = LeagueHashing.seriesKey(2499283573021220255, "/series/one-piece")

    @Test
    fun `paths and public urls follow the bucket layout`() {
        key shouldBe "753909c2c70484e640cb2079c96851f7"
        CoverRules.objectPath(key) shouldBe "/storage/v1/object/covers/$key.jpg"
        CoverRules.publicUrl(base, key, "0a1b2c3d") shouldBe
            "$base/storage/v1/object/public/covers/$key.jpg?v=0a1b2c3d"
        CoverRules.publicUrl(base, key) shouldBe "$base/storage/v1/object/public/covers/$key.jpg"
        CoverSeries(2499283573021220255, "/series/one-piece", null).key shouldBe key
    }

    @Test
    fun `version is the first 8 hex chars of SHA-256 of the bytes`() {
        // SHA-256("abc") = ba7816bf8f01cfea...
        CoverRules.version("abc".toByteArray()) shouldBe "ba7816bf"
        CoverRules.version(ByteArray(0)) shouldBe "e3b0c442"
    }

    @Test
    fun `uploads when nothing was uploaded or the cover changed`() {
        val fingerprint = CoverRules.fingerprint("https://src/c.jpg", 1_700_000_000_000)
        fingerprint shouldBe "https://src/c.jpg|1700000000000"
        CoverRules.fingerprint(null, null) shouldBe "|"
        CoverRules.needsUpload(null, fingerprint) shouldBe true
        CoverRules.needsUpload(CoverRecord(fingerprint, "u"), fingerprint) shouldBe false
        CoverRules.needsUpload(
            CoverRecord(CoverRules.fingerprint("https://src/c.jpg", 1_700_000_000_001), "u"),
            fingerprint,
        ) shouldBe true
    }

    @Test
    fun `candidates try the shared thumbnail before the original`() {
        val shared = "$base/storage/v1/object/public/covers/$key.jpg?v=12345678"
        CoverRules.candidates(shared, key, base) shouldContainExactly listOf(shared)
        CoverRules.candidates("https://src/c.jpg", key, base) shouldContainExactly
            listOf("$base/storage/v1/object/public/covers/$key.jpg", "https://src/c.jpg")
        CoverRules.candidates("https://src/c.jpg", null, base) shouldContainExactly listOf("https://src/c.jpg")
        CoverRules.candidates(null, key, base) shouldContainExactly
            listOf("$base/storage/v1/object/public/covers/$key.jpg")
        CoverRules.candidates(null, null, base).shouldBeEmpty()
        CoverRules.candidates(" ", null, "").shouldBeEmpty()
    }

    @Test
    fun `initials are the first letters of the first two words`() {
        CoverRules.initials("one piece") shouldBe "OP"
        CoverRules.initials("  Berserk ") shouldBe "B"
        CoverRules.initials("Vinland Saga Season Two") shouldBe "VS"
        CoverRules.initials("") shouldBe ""
    }

    @Test
    fun `placeholder hue is stable and in range`() {
        CoverRules.hue("One Piece") shouldBe CoverRules.hue("One Piece")
        listOf("", "a", "One Piece", "ベルセルク").forEach { (CoverRules.hue(it) in 0f..359f) shouldBe true }
    }

    @Test
    fun `feed rows decode the optional series key`() {
        val rows = FriendsJson.decodeFromString(
            ListSerializer(FeedRow.serializer()),
            """[{"user_id":"u","read_at":"2026-11-12T10:00:00+00:00","series_title":"A","series_key":"$key"},
               {"user_id":"u","read_at":"2026-11-12T10:00:00+00:00","series_title":"B"}]""",
        )
        rows.map { it.seriesKey } shouldContainExactly listOf(key, null)
    }
}
