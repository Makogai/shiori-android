package mihon.feature.common.utils

import tachiyomi.domain.manga.model.Manga

/**
 * Names a series by its source and URL. Data kept outside the database is keyed by this instead of the
 * manga id, which a backup restore does not keep.
 */
val Manga.seriesKey: String
    get() = seriesKey(source, url)

fun seriesKey(source: Long, url: String): String = "$source:$url"

/** The source id and URL of a [seriesKey], or null when it is malformed. */
fun parseSeriesKey(key: String): Pair<Long, String>? {
    val separator = key.indexOf(':')
    if (separator <= 0) return null
    val source = key.substring(0, separator).toLongOrNull() ?: return null
    return source to key.substring(separator + 1)
}
