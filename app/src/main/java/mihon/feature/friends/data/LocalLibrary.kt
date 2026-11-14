package mihon.feature.friends.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.feature.friends.domain.LeagueHashing
import tachiyomi.domain.manga.interactor.GetFavorites

/** The local library as far as suggestions need it. */
@Inject
@SingleIn(AppScope::class)
class LocalLibrary(private val getFavorites: GetFavorites) {

    /** `(source id, manga url)` of every favourite. */
    suspend fun favoriteKeys(): Set<Pair<Long, String>> =
        getFavorites.await().mapTo(HashSet()) { it.source to it.url }

    /** The library manga whose `series_key` ("Uploading reads") is [seriesKey], or null. */
    suspend fun favoriteId(seriesKey: String): Long? =
        getFavorites.await().firstOrNull { LeagueHashing.seriesKey(it.source, it.url) == seriesKey }?.id
}
