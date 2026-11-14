package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class FriendsPreferences(preferenceStore: PreferenceStore) {

    /** CARD_SPEC §16 "Show Arena on Library" (per device): the Arena card on the Library home. */
    val showLibraryBanner: Preference<Boolean> = preferenceStore.getBoolean("friends_library_banner", true)

    /** CARD_SPEC §16 "Show Cards button on Library" (per device): the 🃏 button in the Library top bar. */
    val showCardsButton: Preference<Boolean> = preferenceStore.getBoolean("friends_library_cards_button", true)

    /** "Show Cards on Library" (per device): the strip of my latest cards at the top of the Library. */
    val showLibraryCards: Preference<Boolean> = preferenceStore.getBoolean("friends_library_cards", true)

    /** CARD_SPEC §13 the collection's "Mine | Everyone" (per device, hub and album): "mine" or "everyone". */
    val cardsCollectionScope: Preference<String> = preferenceStore.getString("cards_collection_scope", "mine")

    val showStreakCard: Preference<Boolean> = preferenceStore.getBoolean("friends_history_streak_card", true)

    /** README v6: post "X just passed you" notifications (the state is tracked either way). */
    val overtakeAlerts: Preference<Boolean> = preferenceStore.getBoolean("friends_overtake_alerts", true)

    /** README v6: post notifications for new reactions and comments on my reads. */
    val socialAlerts: Preference<Boolean> = preferenceStore.getBoolean("friends_social_alerts", true)
}
