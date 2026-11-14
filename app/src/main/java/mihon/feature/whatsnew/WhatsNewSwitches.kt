package mihon.feature.whatsnew

/** A setting a release offers to turn on or off from the "What's new" sheet. */
enum class WhatsNewSwitch {
    PersonalBox,
    MoreLikeThis,
    DailyGoal,
    ChapterToast,
    EnglishTitles,
}

/** The settings each release introduced, by versionCode; a release lists only its own new things. */
val switchesByVersion: Map<Int, List<WhatsNewSwitch>> = mapOf(
    41 to listOf(
        WhatsNewSwitch.PersonalBox,
        WhatsNewSwitch.MoreLikeThis,
        WhatsNewSwitch.DailyGoal,
        WhatsNewSwitch.ChapterToast,
    ),
    47 to listOf(WhatsNewSwitch.EnglishTitles),
)

/** The switches to show with [versions]: only those of the releases being shown, so later updates don't repeat them. */
fun whatsNewSwitches(
    versions: List<Int>,
    byVersion: Map<Int, List<WhatsNewSwitch>> = switchesByVersion,
): List<WhatsNewSwitch> = versions.flatMap { byVersion[it].orEmpty() }.distinct()
