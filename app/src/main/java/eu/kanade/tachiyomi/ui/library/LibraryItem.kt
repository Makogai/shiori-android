package eu.kanade.tachiyomi.ui.library

import mihon.feature.library.TrackBadge
import tachiyomi.domain.library.model.LibraryManga

data class LibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Int,
    val unreadCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
) {
    val id: Long = libraryManga.id

    data class Badges(
        val downloadCount: Int,
        val unreadCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
        val trackStatus: TrackBadge? = null,
        val stalled: Boolean = false,
    )
}
