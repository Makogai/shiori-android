package mihon.feature.friends.data

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

/** CARD_SPEC §16: the Library switches, both on by default, kept per device. */
class FriendsPreferencesTest {

    @Test
    fun `library switches default on`() {
        val preferences = FriendsPreferences(InMemoryPreferenceStore())
        preferences.showLibraryBanner.get() shouldBe true
        preferences.showCardsButton.get() shouldBe true
        preferences.overtakeAlerts.get() shouldBe true
        preferences.socialAlerts.get() shouldBe true
    }

    @Test
    fun `the cards collection scope defaults to mine and is kept per device`() {
        FriendsPreferences(InMemoryPreferenceStore()).cardsCollectionScope.get() shouldBe "mine"
        val preferences = FriendsPreferences(
            InMemoryPreferenceStore(sequenceOf(InMemoryPreference("cards_collection_scope", "everyone", "mine"))),
        )
        preferences.cardsCollectionScope.key() shouldBe "cards_collection_scope"
        preferences.cardsCollectionScope.get() shouldBe "everyone"
        preferences.cardsCollectionScope.set("mine")
        preferences.cardsCollectionScope.get() shouldBe "mine"
    }

    @Test
    fun `library switches are read back from the device's store under their own keys`() {
        val preferences = FriendsPreferences(InMemoryPreferenceStore())
        preferences.showCardsButton.key() shouldBe "friends_library_cards_button"
        preferences.showLibraryBanner.key() shouldBe "friends_library_banner"

        val cardsOff = FriendsPreferences(
            InMemoryPreferenceStore(sequenceOf(InMemoryPreference("friends_library_cards_button", false, true))),
        )
        cardsOff.showCardsButton.get() shouldBe false
        cardsOff.showLibraryBanner.get() shouldBe true

        val arenaOff = FriendsPreferences(
            InMemoryPreferenceStore(sequenceOf(InMemoryPreference("friends_library_banner", false, true))),
        )
        arenaOff.showLibraryBanner.get() shouldBe false
        arenaOff.showCardsButton.get() shouldBe true

        // Setting one leaves the other alone
        preferences.showCardsButton.set(false)
        preferences.showCardsButton.get() shouldBe false
        preferences.showLibraryBanner.get() shouldBe true
    }
}
