package com.pyreview.app.data.ocr

data class CodeLine(
    val text: String,
    val uncertain: Boolean = false,
    val page: Int = 1,
)

data class PageJoin(
    val previousPage: Int,
    val nextPage: Int,
    val removedLines: Int,
    val indentShift: Int,
) {
    val overlapFound: Boolean get() = removedLines > 0
}

data class MergedCode(val lines: List<CodeLine>, val joins: List<PageJoin>) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

object PageMerger {
    private const val MAX_OVERLAP = 40
    private const val FUZZY_MIN_LENGTH = 6
    private const val FUZZY_MIN_SIMILARITY = 0.8

    private val TRIVIAL = setOf(
        "else:", "try:", "finally:", "pass", "return", "break", "continue",
        ")", "]", "}", "):", "),", "],", "},", "\"\"\"", "'''",
    )

    private data class Overlap(
        val length: Int,
        val prevTailDrop: Int,
        val nextHeadDrop: Int,
        val indentShift: Int,
    ) {
        val drops: Int get() = prevTailDrop + nextHeadDrop
    }

    fun merge(pages: List<List<CodeLine>>): MergedCode {
        val out = mutableListOf<CodeLine>()
        val joins = mutableListOf<PageJoin>()

        pages.forEachIndexed { index, raw ->
            val pageNo = index + 1
            val page = trimBlankEdges(raw).map { it.copy(page = pageNo) }
            if (page.isEmpty()) return@forEachIndexed
            if (out.isEmpty()) {
                out += page
                return@forEachIndexed
            }

            val previousPage = out.last().page
            val overlap = findOverlap(out, page)
            if (overlap == null) {
                joins += PageJoin(previousPage, pageNo, removedLines = 0, indentShift = 0)
                out += page
                return@forEachIndexed
            }

            val shift = overlap.indentShift
            repeat(overlap.prevTailDrop) { out.removeAt(out.lastIndex) }

            val overlapStart = out.size - overlap.length
            for (j in 0 until overlap.length) {
                val kept = out[overlapStart + j]
                val other = page[overlap.nextHeadDrop + j]
                if (kept.uncertain && !other.uncertain) {
                    out[overlapStart + j] = other.copy(text = reindent(other.text, shift), page = kept.page)
                }
            }

            page.drop(overlap.nextHeadDrop + overlap.length)
                .forEach { out += it.copy(text = reindent(it.text, shift)) }

            joins += PageJoin(
                previousPage = previousPage,
                nextPage = pageNo,
                removedLines = overlap.length + overlap.drops,
                indentShift = shift,
            )
        }
        return MergedCode(out, joins)
    }

    private fun findOverlap(prev: List<CodeLine>, next: List<CodeLine>): Overlap? {
        var best: Overlap? = null
        for (tailDrop in 0..1) {
            if (tailDrop == 1 && (prev.size < 2 || !prev.last().uncertain)) continue
            for (headDrop in 0..1) {
                if (headDrop == 1 && (next.size < 2 || !next.first().uncertain)) continue

                val maxLength = minOf(MAX_OVERLAP, prev.size - tailDrop, next.size - headDrop)
                for (k in maxLength downTo 1) {
                    val start = prev.size - tailDrop - k
                    val allMatch = (0 until k).all { j -> linesMatch(prev[start + j].text, next[headDrop + j].text) }
                    if (!allMatch) continue

                    val meaningful = (0 until k).filter { j -> isMeaningful(prev[start + j].text) }
                    if (meaningful.isEmpty()) continue
                    if (k == 1 && !isDistinctive(prev[start].text)) continue

                    val shift = mostCommonShift(meaningful.map { j ->
                        leadingSpaces(prev[start + j].text) - leadingSpaces(next[headDrop + j].text)
                    })
                    val candidate = Overlap(k, tailDrop, headDrop, shift)
                    val current = best
                    if (current == null ||
                        candidate.length > current.length ||
                        (candidate.length == current.length && candidate.drops < current.drops)
                    ) {
                        best = candidate
                    }
                    break
                }
            }
        }
        return best
    }

    private fun mostCommonShift(shifts: List<Int>): Int =
        shifts.groupingBy { it }.eachCount()
            .entries
            .sortedWith(compareBy({ -it.value }, { kotlin.math.abs(it.key) }))
            .first()
            .key

    internal fun linesMatch(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (na.isEmpty() || nb.isEmpty()) return false
        val longest = maxOf(na.length, nb.length)
        if (longest < FUZZY_MIN_LENGTH) return false
        val distance = editDistance(na.lowercase(), nb.lowercase())
        return 1.0 - distance.toDouble() / longest >= FUZZY_MIN_SIMILARITY
    }

    private fun isMeaningful(text: String): Boolean {
        val n = normalize(text)
        return n.isNotEmpty() && n !in TRIVIAL
    }

    private fun isDistinctive(text: String): Boolean = isMeaningful(text) && normalize(text).length >= 4

    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }

    private fun leadingSpaces(text: String): Int = text.takeWhile { it == ' ' }.length

    internal fun reindent(text: String, shift: Int): String = when {
        text.isBlank() || shift == 0 -> text
        shift > 0 -> " ".repeat(shift) + text
        else -> text.drop(minOf(-shift, leadingSpaces(text)))
    }

    private fun trimBlankEdges(lines: List<CodeLine>): List<CodeLine> =
        lines.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }

    private fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }
}
