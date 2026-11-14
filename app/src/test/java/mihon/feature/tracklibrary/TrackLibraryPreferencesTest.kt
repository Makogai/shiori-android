package mihon.feature.tracklibrary

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class TrackLibraryPreferencesTest {

    @Test
    fun `skips are kept per manga and tracker`() {
        val preferences = TrackLibraryPreferences(InMemoryPreferenceStore())
        preferences.setSkipped(trackerId = 2, mangaId = 10, skip = true)
        preferences.isSkipped(2, 10) shouldBe true
        preferences.isSkipped(1, 10) shouldBe false
        preferences.isSkipped(2, 11) shouldBe false

        preferences.setSkipped(trackerId = 2, mangaId = 10, skip = false)
        preferences.isSkipped(2, 10) shouldBe false
    }

    @Test
    fun `skips are read back from the store`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(InMemoryPreference("track_library_skipped", setOf("2:10", "1:10", "12:5", "2:x"), emptySet())),
        )
        val preferences = TrackLibraryPreferences(store)
        preferences.isSkipped(2, 10) shouldBe true
        val keys = preferences.skipped.get()
        TrackLibraryPreferences.skippedIds(keys, trackerId = 2) shouldBe setOf(10L)
        TrackLibraryPreferences.skippedIds(keys, trackerId = 1) shouldBe setOf(10L)
        TrackLibraryPreferences.skippedIds(keys, trackerId = 12) shouldBe setOf(5L)
    }

    @Test
    fun `the post-login offer is made once per tracker`() {
        val preferences = TrackLibraryPreferences(InMemoryPreferenceStore())
        preferences.takeOffer(2) shouldBe true
        preferences.takeOffer(2) shouldBe false
        preferences.takeOffer(1) shouldBe true
    }
}
