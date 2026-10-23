package com.pyreview.app.data.review

import com.pyreview.app.data.local.CodingStandard

object QuickChecks {
    private val KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
        "match", "case", "self", "cls",
    )

    private val BUILTINS = setOf(
        "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr", "dict", "dir", "divmod", "enumerate",
        "eval", "exec", "filter", "float", "format", "frozenset", "getattr", "hasattr", "hash", "help", "hex",
        "id", "input", "int", "isinstance", "issubclass", "iter", "len", "list", "map", "max", "min", "next",
        "object", "oct", "open", "ord", "pow", "print", "property", "range", "repr", "reversed", "round", "set",
        "setattr", "slice", "sorted", "staticmethod", "classmethod", "str", "sum", "super", "tuple", "type",
        "vars", "zip", "Exception", "ValueError", "TypeError", "KeyError", "IndexError", "ZeroDivisionError",
        "NameError", "RuntimeError", "AttributeError", "FileNotFoundError", "StopIteration", "__name__",
        "__main__", "__init__", "__file__",
    )

    private val STRING = Regex("\"\"\".*?\"\"\"|'''.*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val IDENT = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val BLOCK_START = Regex("^(def|class|if|elif|else|for|while|try|except|finally|with)\\b")

    private class Builder {
        val findings = mutableListOf<Finding>()
        fun add(severity: Severity, category: String, title: String, line: Int, explanation: String, topic: String = "") {
            findings += Finding(
                severity = severity, category = category, title = title,
                lineStart = line, lineEnd = line, explanation = explanation, learnMoreTopic = topic,
            )
        }
    }

    fun review(code: String, standards: List<CodingStandard>): ReviewResult {
        val raw = code.lines()
        val clean = raw.map { STRING.replace(it, "\"\"").substringBefore('#') }
        val standardsText = standards.joinToString("\n") { it.content }.lowercase()
        val b = Builder()

        checkBrackets(clean, b)
        checkMissingColons(clean, b)
        val defined = definedNames(clean)
        checkUnusedImports(clean, b)
        checkTypos(clean, defined, b)
        checkPatterns(raw, clean, b)
        checkStyle(raw, clean, standardsText, b)
        addPositives(raw, clean, b)

        val problems = b.findings.count { it.severity != Severity.GOOD }
        val summary = if (problems == 0) {
            "The quick checks didn't find any common mistakes. Run the full AI review later for a proper look."
        } else {
            "The quick checks found ${if (problems == 1) "1 thing" else "$problems things"} to look at. " +
                "Run the full AI review later for a proper look."
        }
        return ReviewResult(summary, b.findings.take(12), quickChecks = true).tidied(raw.size)
    }

    private fun checkBrackets(clean: List<String>, b: Builder) {
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        val stack = ArrayDeque<Pair<Char, Int>>()
        clean.forEachIndexed { i, line ->
            for (c in line) {
                when (c) {
                    '(', '[', '{' -> stack.addLast(c to i + 1)
                    ')', ']', '}' -> if (stack.lastOrNull()?.first == pairs[c]) {
                        stack.removeLast()
                    } else {
                        b.add(
                            Severity.ERROR, "SYNTAX", "Closing '$c' without a matching opening bracket", i + 1,
                            "Python found a '$c' here but no matching opening bracket before it, so it can't " +
                                "understand this line. Count the brackets on this line and the ones above it.",
                            "SyntaxError",
                        )
                        return
                    }
                }
            }
        }
        stack.firstOrNull()?.let { (c, line) ->
            b.add(
                Severity.ERROR, "SYNTAX", "'$c' is opened but never closed", line,
                "This bracket is never closed, so Python keeps reading the following lines as part of it and " +
                    "reports a syntax error. Check where this expression was meant to end.",
                "SyntaxError",
            )
        }
    }

    private fun checkMissingColons(clean: List<String>, b: Builder) {
        clean.forEachIndexed { i, line ->
            val t = line.trim()
            val opens = t.count { it in "([{" } - t.count { it in ")]}" }
            if (BLOCK_START.containsMatchIn(t) && !t.endsWith(":") && !t.endsWith("\\") && opens == 0) {
                if (t.startsWith("else") && t != "else") return@forEachIndexed
                b.add(
                    Severity.ERROR, "SYNTAX", "Missing colon at the end of the line", i + 1,
                    "Lines that start a block, like if, for, def or else, must end with a colon. Without it " +
                        "Python stops with a SyntaxError before running anything.",
                    "SyntaxError",
                )
            }
        }
    }

    private fun definedNames(clean: List<String>): Set<String> {
        val names = mutableSetOf<String>()
        val assign = Regex("^\\s*([A-Za-z_][\\w\\s,\\[\\]().]*?)\\s*(=|\\+=|-=|\\*=|/=|//=|%=)(?!=)")
        clean.forEach { line ->
            assign.find(line)?.groupValues?.get(1)?.let { names += IDENT.findAll(it).map { m -> m.value } }
            Regex("\\bfor\\s+(.+?)\\s+in\\b").findAll(line).forEach { m -> names += IDENT.findAll(m.groupValues[1]).map { it.value } }
            Regex("\\b(def|class)\\s+([A-Za-z_]\\w*)").find(line)?.let { names += it.groupValues[2] }
            Regex("\\bdef\\s+\\w+\\s*\\((.*)\\)").find(line)?.let { m -> m.groupValues[1].split(',').forEach { addParam(it, names) } }
            Regex("\\blambda\\s+([^:]*):").findAll(line).forEach { m -> m.groupValues[1].split(',').forEach { addParam(it, names) } }
            Regex("\\bas\\s+([A-Za-z_]\\w*)").findAll(line).forEach { names += it.groupValues[1] }
            importedNames(line).forEach { (name, _) -> names += name }
            Regex("\\b(global|nonlocal)\\s+(.+)").find(line)?.let { m -> names += IDENT.findAll(m.groupValues[2]).map { it.value } }
        }
        return names
    }

    private fun addParam(param: String, names: MutableSet<String>) {
        IDENT.find(param.trim().trimStart('*'))?.let { names += it.value }
    }

    private fun importedNames(line: String): List<Pair<String, String>> {
        val t = line.trim()
        Regex("^import\\s+(.+)").find(t)?.let { m ->
            return m.groupValues[1].split(',').map { part ->
                val p = part.trim()
                val alias = Regex("\\bas\\s+(\\w+)").find(p)?.groupValues?.get(1)
                (alias ?: p.substringBefore('.').trim()) to p
            }
        }
        Regex("^from\\s+\\S+\\s+import\\s+(.+)").find(t)?.let { m ->
            return m.groupValues[1].removePrefix("(").removeSuffix(")").split(',').mapNotNull { part ->
                val p = part.trim()
                if (p == "*" || p.isEmpty()) return@mapNotNull null
                val alias = Regex("\\bas\\s+(\\w+)").find(p)?.groupValues?.get(1)
                (alias ?: p) to p
            }
        }
        return emptyList()
    }

    private fun checkUnusedImports(clean: List<String>, b: Builder) {
        clean.forEachIndexed { i, line ->
            importedNames(line).forEach { (name, label) ->
                val usedElsewhere = clean.withIndex().any { (j, other) ->
                    j != i && IDENT.findAll(other).any { it.value == name }
                }
                if (!usedElsewhere) {
                    b.add(
                        Severity.WARNING, "QUALITY", "Unused import $label", i + 1,
                        "\"$label\" is imported but never used in this file. Unused imports make readers " +
                            "wonder what they are for and slightly slow down start-up.",
                        "import statement",
                    )
                }
            }
        }
    }

    private fun checkTypos(clean: List<String>, defined: Set<String>, b: Builder) {
        val reported = mutableSetOf<String>()
        clean.forEachIndexed { i, line ->
            if (line.trim().let { it.startsWith("import ") || it.startsWith("from ") }) return@forEachIndexed
            IDENT.findAll(line).forEach { m ->
                val name = m.value
                val before = line.getOrNull(m.range.first - 1)
                val after = line.substring(m.range.last + 1).trimStart()
                val isAttribute = before == '.'
                val isKeywordArgument = after.startsWith("=") && !after.startsWith("==")
                if (name in defined || name in KEYWORDS || name in BUILTINS || isAttribute || isKeywordArgument ||
                    name.length < 3 || name in reported
                ) {
                    return@forEach
                }
                val match = defined.firstOrNull { d -> d.length >= 3 && distance(d.lowercase(), name.lowercase()) in 1..2 }
                    ?: return@forEach
                reported += name
                b.add(
                    Severity.ERROR, "BUG", "\"$name\" is never defined. Did you mean \"$match\"?", i + 1,
                    "Python raises a NameError when it reaches a name that was never given a value. " +
                        "\"$name\" looks very close to \"$match\", which is defined elsewhere, so check the spelling.",
                    "NameError",
                )
            }
        }
    }

    private fun checkPatterns(raw: List<String>, clean: List<String>, b: Builder) {
        clean.forEachIndexed { i, line ->
            val n = i + 1
            if (Regex("/\\s*len\\s*\\(").containsMatchIn(line)) {
                b.add(
                    Severity.WARNING, "BUG", "Dividing by len() fails for an empty list", n,
                    "If the list is empty, len() is 0 and Python raises a ZeroDivisionError. Think about what " +
                        "should happen when there is nothing to divide.",
                    "ZeroDivisionError",
                )
            }
            if (Regex("[!=]=\\s*None\\b").containsMatchIn(line)) {
                b.add(
                    Severity.WARNING, "BEST_PRACTICE", "Comparing to None with == or !=", n,
                    "None is a single special object, so Python style compares it by identity. Comparing with " +
                        "== can give surprising results with some objects.",
                    "is operator",
                )
            }
            if (Regex("^\\s*except\\s*:").containsMatchIn(line)) {
                b.add(
                    Severity.WARNING, "BEST_PRACTICE", "Bare except catches every error", n,
                    "An except with no error type also catches mistakes you didn't expect, and even Ctrl+C, " +
                        "which hides real bugs. Think about which errors you actually expect here.",
                    "handling exceptions",
                )
            }
            if (Regex("\\b(eval|exec)\\s*\\(").containsMatchIn(line)) {
                b.add(
                    Severity.ERROR, "SECURITY", "eval/exec runs text as code", n,
                    "eval and exec run any text as Python code. If that text ever comes from a user or a file, " +
                        "someone could make your program do anything.",
                    "eval",
                )
            }
            if (Regex("(?i)\\b(password|passwd|secret|api_key|token)\\s*=\\s*[\"']").containsMatchIn(raw[i])) {
                b.add(
                    Severity.WARNING, "SECURITY", "A secret is written directly in the code", n,
                    "Passwords and keys in source code end up in version control and screenshots. Secrets are " +
                        "usually read from configuration or environment variables instead.",
                    "environment variables",
                )
            }
        }
    }

    private fun checkStyle(raw: List<String>, clean: List<String>, standards: String, b: Builder) {
        val longLines = raw.withIndex().filter { it.value.length > 99 }
        longLines.firstOrNull()?.let { (i, _) ->
            b.add(
                Severity.WARNING, "QUALITY", "Very long line", i + 1,
                "This line is over 99 characters" +
                    (if (longLines.size > 1) " (and ${longLines.size - 1} more are too)" else "") +
                    ". Long lines are hard to read on small screens and in code reviews.",
                "PEP 8",
            )
        }
        val snakeRule = "snake_case" in standards
        clean.forEachIndexed { i, line ->
            Regex("\\bdef\\s+([a-z]+[A-Z]\\w*)").find(line)?.let { m ->
                b.add(
                    Severity.WARNING, if (snakeRule) "STANDARDS" else "QUALITY",
                    "Function name ${m.groupValues[1]} isn't snake_case", i + 1,
                    "Python function names are usually written in lowercase words joined by underscores" +
                        (if (snakeRule) ", and your team's coding standard asks for snake_case." else "."),
                    "PEP 8 naming",
                )
            }
        }
        if ("docstring" in standards) {
            var reported = 0
            clean.forEachIndexed { i, line ->
                if (reported < 3 && Regex("^\\s*def\\s").containsMatchIn(line)) {
                    val next = raw.drop(i + 1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    if (!next.startsWith("\"\"\"") && !next.startsWith("'''")) {
                        reported++
                        b.add(
                            Severity.WARNING, "STANDARDS", "Function has no docstring", i + 1,
                            "Your team's coding standard asks for a docstring in every function. A docstring is " +
                                "a short description that tells other developers what the function does.",
                            "docstring",
                        )
                    }
                }
            }
        }
        raw.withIndex().firstOrNull { (_, l) -> l.isNotBlank() && l.takeWhile { it == ' ' }.length % 4 != 0 }?.let { (i, _) ->
            b.add(
                Severity.WARNING, "QUALITY", "Indentation isn't a multiple of 4 spaces", i + 1,
                "Python code normally uses 4 spaces per level. Uneven indentation is easy to misread and can " +
                    "put a line in the wrong block.",
                "indentation",
            )
        }
    }

    private fun addPositives(raw: List<String>, clean: List<String>, b: Builder) {
        val functions = clean.mapNotNull { Regex("\\bdef\\s+(\\w+)").find(it)?.groupValues?.get(1) }
        if (functions.isNotEmpty() && functions.all { it == it.lowercase() }) {
            b.add(
                Severity.GOOD, "QUALITY", "Clear, snake_case function names", 0,
                "Your function names follow the usual Python style, which makes the code easier for others to read.",
            )
        }
        if (functions.size >= 2) {
            b.add(
                Severity.GOOD, "BEST_PRACTICE", "Code is split into functions", 0,
                "Breaking the work into small functions makes each part easier to test and reuse.",
            )
        }
        val indents = raw.filter { it.isNotBlank() }.map { it.takeWhile { c -> c == ' ' }.length }
        if (raw.none { '\t' in it } && indents.all { it % 4 == 0 } && indents.any { it > 0 }) {
            b.add(
                Severity.GOOD, "QUALITY", "Consistent indentation", 0,
                "Every block is indented with 4 spaces, so the structure of the code is easy to follow.",
            )
        }
    }

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length]
    }
}
