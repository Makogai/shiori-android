package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.builtins.ListSerializer
import mihon.feature.friends.data.FriendsJson
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.time.Instant

/** README v6, "Reactions & comments on feed reads". */
class SocialTest {

    private val read = ReadKey("filip", "c1")

    private fun reaction(
        reactor: String,
        emoji: String,
        at: String = "2026-11-12T10:00:00+00:00",
        ref: ReadRef? = null,
    ) = FeedReaction("filip", "c1", reactor, emoji, at, ref)

    @Test
    fun `others' reads show all five emoji with mine highlighted`() {
        val reactions = listOf(reaction("me", "😂"), reaction("ana", "😂"), reaction("kai", "🔥"))
        val chips = Reactions.chips(reactions, "filip", "me")
        chips.map { it.emoji } shouldContainExactly Reactions.EMOJI
        chips.map { it.count } shouldContainExactly listOf(1, 2, 0, 0, 0)
        chips.filter { it.mine }.map { it.emoji } shouldContainExactly listOf("😂")
        chips.all { it.enabled } shouldBe true
    }

    @Test
    fun `my own reads only show emoji with a count, not tappable`() {
        val chips = Reactions.chips(listOf(reaction("ana", "👀"), reaction("kai", "👀")), "me", "me")
        chips shouldContainExactly listOf(ReactionChip("👀", 2, mine = false, enabled = false))
        Reactions.chips(emptyList(), "me", "me").shouldBeEmpty()
    }

    @Test
    fun `tapping my emoji removes it, any other replaces it`() {
        Reactions.toggle("🔥", "🔥") shouldBe null
        Reactions.toggle("🔥", "😭") shouldBe "😭"
        Reactions.toggle(null, "😍") shouldBe "😍"
        val now = Instant.parse("2026-11-12T12:00:00Z")
        val reactions = listOf(reaction("me", "🔥"), reaction("ana", "😂"))
        Reactions.withMine(reactions, read, "me", "😭", now).map { it.reactorId to it.emoji } shouldContainExactly
            listOf("ana" to "😂", "me" to "😭")
        Reactions.withMine(reactions, read, "me", null, now).map { it.reactorId } shouldContainExactly listOf("ana")
    }

    @Test
    fun `comment bodies are trimmed and 1 to 200 characters`() {
        Reactions.commentBody("  so good  ") shouldBe "so good"
        Reactions.commentBody("   ") shouldBe null
        Reactions.commentBody("x".repeat(200)) shouldBe "x".repeat(200)
        Reactions.commentBody("x".repeat(201)) shouldBe null
    }

    @Test
    fun `the cursor is the server's created_at in UTC with six digits and Z`() {
        SocialCursor.normalize("2026-11-11T10:00:00.123456+00:00") shouldBe "2026-11-11T10:00:00.123456Z"
        SocialCursor.normalize("2026-11-11T10:00:00.5+00:00") shouldBe "2026-11-11T10:00:00.500000Z"
        SocialCursor.normalize("2026-11-11T10:00:00+00:00") shouldBe "2026-11-11T10:00:00.000000Z"
        SocialCursor.normalize("2026-11-11T12:30:00.123456+02:30") shouldBe "2026-11-11T10:00:00.123456Z"
        SocialCursor.normalize("2026-11-11T05:00:00.1-05:00") shouldBe "2026-11-11T10:00:00.100000Z"
        SocialCursor.normalize("2026-11-11 10:00:00.123456+00:00") shouldBe "2026-11-11T10:00:00.123456Z"
        SocialCursor.normalize("2026-11-11T10:00:00Z") shouldBe "2026-11-11T10:00:00.000000Z"
        SocialCursor.normalize("yesterday") shouldBe null
        SocialCursor.format(Instant.parse("2026-11-11T10:00:00.123456789Z")) shouldBe "2026-11-11T10:00:00.123456Z"
        // Normalising twice changes nothing
        SocialCursor.normalize("2026-11-11T10:00:00.123456Z") shouldBe "2026-11-11T10:00:00.123456Z"
    }

    @Test
    fun `the cursor is percent-encoded and never carries a plus`() {
        val cursor = SocialCursor.normalize("2026-11-11T12:00:00.000001+02:00")!!
        val encoded = SocialCursor.encode(cursor)
        encoded shouldBe "2026-11-11T10%3A00%3A00.000001Z"
        encoded shouldNotContain "+"
        encoded shouldNotContain " "
        URLDecoder.decode(encoded, "UTF-8") shouldBe cursor
        SocialCursor.max("2026-11-11T10:00:00.000001Z", "2026-11-11T10:00:00.000002Z") shouldBe
            "2026-11-11T10:00:00.000002Z"
        SocialCursor.max("2026-11-11T10:00:00.000002Z", "2026-11-11T10:00:00.000001Z") shouldBe
            "2026-11-11T10:00:00.000002Z"
        SocialCursor.max("2026-11-11T10:00:00.000002Z", null) shouldBe "2026-11-11T10:00:00.000002Z"
    }

    @Test
    fun `the first alert check sets the cursor to now and posts nothing`() {
        val now = Instant.parse("2026-11-12T12:00:00Z")
        val plan = SocialAlerts.plan(null, now, listOf(reaction("ana", "🔥")), emptyList(), emptyList()) { it }
        plan.cursor shouldBe "2026-11-12T12:00:00.000000Z"
        plan.messages.shouldBeEmpty()
        plan.seen.shouldBeEmpty()
    }

    @Test
    fun `later checks post one message per type and move the cursor to the newest`() {
        val ref = ReadRef("One Piece", 40.0, "Chapter 40")
        val reactions = listOf(reaction("filip", "🔥", "2026-11-12T10:00:00.123456+00:00", ref))
        val comments = listOf(
            FeedComment("x", "me", "c1", "filip", "so good", "2026-11-12T10:05:00+00:00", ref),
            // Not newer than the cursor
            FeedComment("y", "me", "c1", "filip", "old", "2026-11-12T09:00:00+00:00", ref),
        )
        val plan = SocialAlerts.plan("2026-11-12T09:30:00Z", Instant.EPOCH, reactions, comments, emptyList()) {
            "Filip"
        }
        plan.reactions shouldBe "Filip reacted 🔥 to One Piece Ch. 40"
        plan.comments shouldBe "Filip commented on One Piece Ch. 40: “so good”"
        plan.cursor shouldBe "2026-11-12T10:05:00.000000Z"
        plan.seen shouldContainExactly listOf("c|x", "r|filip|c1|filip|🔥")

        // The cursor keeps the server's microseconds
        SocialAlerts.plan("2026-11-12T09:30:00Z", Instant.EPOCH, reactions, emptyList(), emptyList()) { "Filip" }
            .cursor shouldBe "2026-11-12T10:00:00.123456Z"
        // Nothing new keeps the cursor (never moves back)
        SocialAlerts.plan("2026-11-12T11:00:00Z", Instant.EPOCH, reactions, comments, emptyList()) { "Filip" } shouldBe
            SocialAlerts.Plan("2026-11-12T11:00:00.000000Z", emptyList())
    }

    @Test
    fun `rows already announced are skipped even when the server returns them again`() {
        val ref = ReadRef("One Piece", 40.0, null)
        val reactions = listOf(reaction("ana", "🔥", "2026-11-12T10:00:00.123456+00:00", ref))
        val first = SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, reactions, emptyList(), emptyList()) {
            "Ana"
        }
        first.reactions shouldBe "Ana reacted 🔥 to One Piece Ch. 40"
        // Same row again from an older cursor (e.g. a millisecond-rounded one): nothing posts
        val again = SocialAlerts.plan("2026-11-12T10:00:00.123Z", Instant.EPOCH, reactions, emptyList(), first.seen) {
            "Ana"
        }
        again.messages.shouldBeEmpty()
        again.cursor shouldBe "2026-11-12T10:00:00.123456Z"
        again.seen shouldContainExactly first.seen
        // Duplicate rows in one response count once
        SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, reactions + reactions, emptyList(), emptyList()) {
            "Ana"
        }.reactions shouldBe "Ana reacted 🔥 to One Piece Ch. 40"
    }

    @Test
    fun `the seen set keeps the newest 300 keys`() {
        val old = (1..300).map { "c|old$it" }
        val comments = listOf(FeedComment("new", "me", "c1", "ana", "hi", "2026-11-12T10:00:00+00:00"))
        val plan = SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, emptyList(), comments, old) { "Ana" }
        plan.seen.size shouldBe 300
        plan.seen.first() shouldBe "c|old2"
        plan.seen.last() shouldBe "c|new"
    }

    @Test
    fun `several reactions or comments group into one message each`() {
        fun r(reactor: String, client: String, at: String) =
            FeedReaction("me", client, reactor, "🔥", "2026-11-12T10:$at:00+00:00", ReadRef("One Piece", 40.0, null))
        val names = mapOf("filip" to "Filip", "anna" to "Anna", "kai" to "Kai", "mo" to "Mo")
        val name = { id: String -> names.getValue(id) }

        // Two people on one read, newest first
        SocialAlerts.plan(
            "2026-11-12T09:00:00Z",
            Instant.EPOCH,
            listOf(r("anna", "c1", "01"), r("filip", "c1", "02")),
            emptyList(),
            emptyList(),
            name,
        ).reactions shouldBe "Filip and Anna reacted to One Piece Ch. 40"

        // Four people on five reads
        val many = listOf(
            r("anna", "c1", "01"),
            r("kai", "c2", "02"),
            r("mo", "c3", "03"),
            r("anna", "c4", "04"),
            r("filip", "c5", "05"),
        )
        val plan = SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, many, emptyList(), emptyList(), name)
        plan.reactions shouldBe "Filip and 3 others reacted to 5 of your reads"
        plan.comments shouldBe null
        plan.cursor shouldBe "2026-11-12T10:05:00.000000Z"

        val comments = listOf(
            FeedComment("a", "me", "c1", "anna", "wow", "2026-11-12T10:01:00+00:00", ReadRef("Berserk", 1.0, null)),
            FeedComment("b", "me", "c2", "filip", "yes", "2026-11-12T10:02:00+00:00", ReadRef("Berserk", 2.0, null)),
            FeedComment("c", "me", "c2", "filip", "again", "2026-11-12T10:03:00+00:00", ReadRef("Berserk", 2.0, null)),
        )
        SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, emptyList(), comments, emptyList(), name)
            .messages shouldContainExactly listOf("Filip and Anna commented on 2 of your reads")
        SocialAlerts.plan("2026-11-12T09:00:00Z", Instant.EPOCH, emptyList(), comments.drop(1), emptyList(), name)
            .comments shouldBe "Filip commented on Berserk Ch. 2"
    }

    @Test
    fun `several overtakes become the first message plus a count`() {
        Overtakes.summary(emptyList()) shouldBe null
        Overtakes.summary(listOf("a")) shouldBe "a"
        Overtakes.summary(listOf("Filip just passed you today — 8 vs 7 chapters 📚", "b", "c")) shouldBe
            "Filip just passed you today — 8 vs 7 chapters 📚 (+2 more)"
    }

    @Test
    fun `read labels and long comments`() {
        SocialAlerts.readLabel(ReadRef("Berserk", 12.5, null)) shouldBe "Berserk Ch. 12.5"
        SocialAlerts.readLabel(ReadRef("Berserk", null, "Prologue")) shouldBe "Berserk Prologue"
        SocialAlerts.readLabel(ReadRef("Berserk", -1.0, null)) shouldBe "Berserk"
        SocialAlerts.cut("x".repeat(120)).length shouldBe 80
    }

    @Test
    fun `decodes rows with the embedded read`() {
        val text = """
            [{"owner_id":"me","client_id":"c1","reactor_id":"filip","emoji":"🔥",
              "created_at":"2026-11-12T10:00:00.5+00:00",
              "reads":{"series_title":"One Piece","chapter_number":40,"chapter_name":"Ch 40"}}]
        """.trimIndent()
        val row = FriendsJson.decodeFromString(ListSerializer(FeedReaction.serializer()), text).single()
        row.read shouldBe ReadKey("me", "c1")
        row.reads?.seriesTitle shouldBe "One Piece"
        row.created shouldBe Instant.parse("2026-11-12T10:00:00.5Z")
    }
}
