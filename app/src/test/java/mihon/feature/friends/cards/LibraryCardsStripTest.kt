package mihon.feature.friends.cards

import io.kotest.matchers.shouldBe
import mihon.feature.friends.ui.ArenaSummary
import org.junit.jupiter.api.Test

/** When the Library shows its cards strip: the setting on and a Friends account signed in. */
class LibraryCardsStripTest {

    @Test
    fun `shows with the setting on and a signed-in account`() {
        ArenaSummary.showsCardsStrip(setting = true, configured = true, userId = "me") shouldBe true
    }

    @Test
    fun `hidden with the setting off, no backend or no session`() {
        ArenaSummary.showsCardsStrip(setting = false, configured = true, userId = "me") shouldBe false
        ArenaSummary.showsCardsStrip(setting = true, configured = false, userId = "me") shouldBe false
        ArenaSummary.showsCardsStrip(setting = true, configured = true, userId = null) shouldBe false
    }

    @Test
    fun `the default summary hides it until the account is known`() {
        ArenaSummary(configured = false, registered = false).showCardsStrip shouldBe false
    }
}
