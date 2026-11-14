package mihon.feature.library

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Typo-tolerant matching for library search words, as the iOS app's search: text is folded (case and
 * accents ignored, punctuation as spaces), and a query word matches a word of the text by its start,
 * anywhere, or within one typo for 4–5 letters and two from 6.
 */
object FuzzySearch {

    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(marks, "")
        val result = StringBuilder(folded.length)
        var lastWasSpace = true
        for (char in folded) {
            when {
                char == '\'' || char == '’' || char == '`' -> continue
                char.isLetterOrDigit() -> {
                    result.append(char)
                    lastWasSpace = false
                }
                !lastWasSpace -> {
                    result.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return result.toString().trimEnd()
    }

    fun allowedTypos(length: Int): Int = when {
        length < 4 -> 0
        length < 6 -> 1
        else -> 2
    }

    /** Whether [query] (one word) matches [text]: contained, or a near miss of one of its words or their starts. */
    fun matches(query: String, text: String): Boolean {
        val word = normalize(query).replace(" ", "")
        if (word.isEmpty()) return true
        val normalized = normalize(text)
        if (normalized.contains(word) || normalized.replace(" ", "").contains(word)) return true
        val allowed = allowedTypos(word.length)
        if (allowed == 0) return false
        return normalized.split(' ').any { candidate ->
            distance(word, candidate, allowed) <= allowed ||
                (candidate.length > word.length && distance(word, candidate.take(word.length), allowed) <= allowed)
        }
    }

    /** Optimal string alignment distance (Levenshtein plus swapped neighbours), giving up past [limit]. */
    fun distance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        if (a.isEmpty() || b.isEmpty()) return max(a.length, b.length)
        var previous2 = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (row in 1..a.length) {
            current[0] = row
            var rowBest = row
            for (column in 1..b.length) {
                val cost = if (a[row - 1] == b[column - 1]) 0 else 1
                var value = min(min(previous[column] + 1, current[column - 1] + 1), previous[column - 1] + cost)
                if (row > 1 && column > 1 && a[row - 1] == b[column - 2] && a[row - 2] == b[column - 1]) {
                    value = min(value, previous2[column - 2] + 1)
                }
                current[column] = value
                rowBest = min(rowBest, value)
            }
            if (rowBest > limit) return limit + 1
            val recycled = previous2
            previous2 = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }
}
