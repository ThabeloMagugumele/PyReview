package com.pyreview.app.data.review

data class ReviewComparison(
    val fixed: List<Finding>,
    val stillThere: List<Finding>,
    val new: List<Finding>,
    val errorChange: Int,
    val warningChange: Int,
) {
    val verdict: String
        get() = when {
            errorChange < 0 || (errorChange == 0 && warningChange < 0) -> "Fewer problems than last time. Nice one!"
            errorChange > 0 || warningChange > 0 -> "More problems than last time. Check what's new below."
            else -> "About the same number of problems as before."
        }

    companion object {
        fun of(earlier: List<Finding>, later: List<Finding>): ReviewComparison {
            val before = earlier.filter { it.severity != Severity.GOOD }
            val after = later.filter { it.severity != Severity.GOOD }
            val afterKeys = after.map(::key).toMutableList()
            val beforeKeys = before.map(::key).toMutableList()

            val fixed = before.filter { f -> !afterKeys.remove(key(f)) }
            val new = after.filter { f -> !beforeKeys.remove(key(f)) }
            val stillThere = after.filter { it !in new }

            fun count(list: List<Finding>, s: Severity) = list.count { it.severity == s }
            return ReviewComparison(
                fixed = fixed,
                stillThere = stillThere,
                new = new,
                errorChange = count(after, Severity.ERROR) - count(before, Severity.ERROR),
                warningChange = count(after, Severity.WARNING) - count(before, Severity.WARNING),
            )
        }

        private fun key(f: Finding) = f.category + ":" + f.title.lowercase().filter { it.isLetterOrDigit() }
    }
}
