package mihon.feature.whatsnew

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WhatsNewSwitchesTest {

    @Test
    fun `each release shows only the switches it introduced`() {
        whatsNewSwitches(listOf(47)) shouldBe listOf(WhatsNewSwitch.EnglishTitles)
        whatsNewSwitches(listOf(41)) shouldBe listOf(
            WhatsNewSwitch.PersonalBox,
            WhatsNewSwitch.MoreLikeThis,
            WhatsNewSwitch.DailyGoal,
            WhatsNewSwitch.ChapterToast,
        )
        whatsNewSwitches(listOf(46, 45)) shouldBe emptyList()
    }

    @Test
    fun `a skipped update shows the switches of every release being shown`() {
        whatsNewSwitches(listOf(47, 46, 42, 41)) shouldBe listOf(
            WhatsNewSwitch.EnglishTitles,
            WhatsNewSwitch.PersonalBox,
            WhatsNewSwitch.MoreLikeThis,
            WhatsNewSwitch.DailyGoal,
            WhatsNewSwitch.ChapterToast,
        )
    }

    @Test
    fun `no switch is offered by more than one release`() {
        val all = switchesByVersion.values.flatten()
        all.distinct() shouldBe all
    }
}
