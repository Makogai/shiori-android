package mihon.feature.tracklibrary

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Title similarity for matching library entries to tracker results, 0-100. The iOS app implements the same rules;
 * keep both in step.
 *
 * Titles are compared as tokens: diacritics, case, apostrophes and other punctuation are dropped ("&" reads "and",
 * "×" reads "x"), a leading article and any "the" go, "2nd" reads as "2", roman numerals II-IX read as digits, "season", "part",
 * "vol" and "volume" are ignored when the title has a number, and long vowels fold ("ou", "oo" -> "o", "uu" -> "u",
 * particle "wo" -> "o"). Identical tokens (in any order, or once spaces are removed)
 * score 100. Otherwise the score is the mean of the token-set and token-sort ratios, where a ratio is
 * 2 * LCS / (length a + length b) over the joined strings. Different numbers cap the score at 60 (both titles have
 * numbers) or 80 (only one has), so another season or volume never counts as a strong match.
 */
object TitleMatcher {

    const val STRONG_MATCH = 90

    /** Applied to fuzzy hits through an alternative title; an exact alternative-title hit still scores 100. */
    private const val ALT_TITLE_FACTOR = 0.95

    private val articles = setOf("the", "a", "an")
    private val seasonWords = setOf("season", "part", "vol", "volume")
    private val romanNumerals = mapOf(
        "ii" to "2",
        "iii" to "3",
        "iv" to "4",
        "vi" to "6",
        "vii" to "7",
        "viii" to "8",
        "ix" to "9",
    )
    private val ordinal = Regex("""^(\d+)(st|nd|rd|th)$""")
    private val marks = Regex("""\p{M}+""")
    private val apostrophes = Regex("""['’‘`´]""")
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Best score of [candidate] (main title first, then its alternative titles) against [ours] (the library title
     * first, then the alternative titles we know).
     */
    fun score(ours: List<String>, candidate: List<String>): Int {
        var best = 0
        ours.forEachIndexed { i, our ->
            candidate.forEachIndexed { j, their ->
                val s = similarity(our, their)
                val weighted = if ((i == 0 && j == 0) || s == 100) s else (s * ALT_TITLE_FACTOR).roundToInt()
                if (weighted > best) best = weighted
            }
        }
        return best
    }

    fun isStrong(score: Int) = score >= STRONG_MATCH

    fun similarity(a: String, b: String): Int {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0
        if (ta.joinToString("") == tb.joinToString("") || ta.sorted() == tb.sorted()) return 100

        val numbersA = ta.filter(::isNumber).toSet()
        val numbersB = tb.filter(::isNumber).toSet()
        val cap = when {
            numbersA == numbersB -> 100
            numbersA.isNotEmpty() && numbersB.isNotEmpty() -> 60
            else -> 80
        }
        val sortRatio = ratio(ta.sorted().joinToString(" "), tb.sorted().joinToString(" "))
        val raw = ((tokenSetRatio(ta.toSet(), tb.toSet()) + sortRatio) / 2).roundToInt()
        return minOf(raw, cap)
    }

    fun tokens(title: String): List<String> {
        val folded = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("×", " x ")
            .replace(apostrophes, "")
        var tokens = folded.split(separators).filter { it.isNotEmpty() }
            .map { token ->
                ordinal.matchEntire(token)?.groupValues?.get(1)
                    ?: romanNumerals[token]
                    ?: foldRomanisation(token)
            }
        if (tokens.size > 1 && tokens.first() in articles) tokens = tokens.drop(1)
        // "Promised Neverland, The"
        tokens = tokens.filter { it != "the" }.ifEmpty { tokens }
        if (tokens.any(::isNumber)) tokens = tokens.filter { it !in seasonWords }.ifEmpty { tokens }
        return tokens
    }

    private fun foldRomanisation(token: String): String {
        if (token == "wo") return "o"
        if (token.any { it.isDigit() }) return token
        return token.replace("ou", "o").replace("oo", "o").replace("uu", "u")
    }

    private fun isNumber(token: String) = token.all { it.isDigit() }

    private fun tokenSetRatio(a: Set<String>, b: Set<String>): Double {
        val intersection = (a intersect b).sorted().joinToString(" ")
        val onlyA = (a - b).sorted().joinToString(" ")
        val onlyB = (b - a).sorted().joinToString(" ")
        val withA = "$intersection $onlyA".trim()
        val withB = "$intersection $onlyB".trim()
        return maxOf(ratio(intersection, withA), ratio(intersection, withB), ratio(withA, withB))
    }

    /** Indel similarity, 0-100: 2 * longest common subsequence / total length. */
    private fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 100.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var previous = IntArray(b.length + 1)
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                current[j] = if (a[i - 1] == b[j - 1]) {
                    previous[j - 1] + 1
                } else {
                    maxOf(previous[j], current[j - 1])
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        return 200.0 * previous[b.length] / (a.length + b.length)
    }

    private val altTitleLine = Regex(
        """^\s*(?:alternative(?: titles?| names?)?|alt(?:ernate|\.)? ?(?:titles?|names?)|associated names?|""" +
            """other names?|also known as|aka)\s*[:：]\s*(.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val altTitleSeparators = Regex("""\s*[;|,、／/]\s*""")

    /** Alternative titles sources put in the description ("Alternative: A; B", "Associated Names: ..."). */
    fun altTitlesFromDescription(description: String?, title: String): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        val normalizedTitle = tokens(title)
        return altTitleLine.findAll(description)
            .flatMap { it.groupValues[1].split(altTitleSeparators) }
            .map { it.trim() }
            .filter { it.length >= 2 && tokens(it).isNotEmpty() && tokens(it) != normalizedTitle }
            .distinctBy { tokens(it) }
            .take(MAX_ALT_TITLES)
            .toList()
    }

    private val subtitleSplit = Regex("""\s*(?::\s|\s[-–—]\s)\s*""")
    private val trailingParenthetical = Regex("""\s*[(\[][^)\]]*[)\]]\s*$""")

    /**
     * What to search the tracker for, in order: the title, the title without its subtitle or trailing "(...)",
     * then the alternative titles. The shortened title is only a query; it is never scored as one of our titles,
     * so "Solo Leveling: Ragnarok" can't match "Solo Leveling" exactly.
     */
    fun searchQueries(title: String, altTitles: List<String>): List<String> {
        val base = title.replace(trailingParenthetical, "").split(subtitleSplit).first().trim()
        return (listOf(title, base) + altTitles)
            .filter { it.isNotBlank() && tokens(it).isNotEmpty() }
            .distinctBy { tokens(it) }
            .take(MAX_QUERIES)
    }

    private const val MAX_ALT_TITLES = 5
    private const val MAX_QUERIES = 3
}
