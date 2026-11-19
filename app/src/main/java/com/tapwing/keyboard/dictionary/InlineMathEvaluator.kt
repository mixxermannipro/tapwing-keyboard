package com.tapwing.keyboard.dictionary

import java.math.BigDecimal
import java.math.RoundingMode

data class MathSuggestion(
    val formattedResult: String,
    val replaceCharCount: Int,
    val appendEqualsPrefix: Boolean
)

/**
 * Pure Kotlin safe recursive-descent arithmetic evaluator for the keyboard suggestion strip.
 * Evaluates trailing math expressions such as "24*7", "19,5+4,5=", or "(12+8)/4=" into a 1-tap chip.
 */
object InlineMathEvaluator {

    private val trailingMathRegex = Regex("""([0-9(][0-9.,+\-*/×÷()\s]*[+\-*/×÷][0-9.,+\-*/×÷()\s]*)=?$""")

    fun evaluateTrailingExpression(textBeforeCursor: String, locale: String): MathSuggestion? {
        if (textBeforeCursor.isEmpty()) return null
        val match = trailingMathRegex.find(textBeforeCursor) ?: return null
        val fullMatch = match.value
        val hasTrailingEquals = fullMatch.endsWith("=")
        val exprRaw = if (hasTrailingEquals) fullMatch.dropLast(1) else fullMatch

        val normalized = exprRaw
            .replace(" ", "")
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')

        if (!normalized.any { it in charArrayOf('+', '-', '*', '/') }) return null

        val value = runCatching { Parser(normalized).parse() }.getOrNull() ?: return null
        if (!value.isFinite()) return null

        val formatted = formatNumber(value, locale)
        return MathSuggestion(
            formattedResult = formatted,
            replaceCharCount = fullMatch.length,
            appendEqualsPrefix = !hasTrailingEquals
        )
    }

    private fun formatNumber(value: Double, locale: String): String {
        val bd = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        val raw = bd.toPlainString()
        return if (locale.startsWith("de")) raw.replace('.', ',') else raw
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val res = parseExpression()
            if (pos != s.length) throw IllegalArgumentException("Unexpected trailing token")
            return res
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; x += parseTerm() }
                    '-' -> { pos++; x -= parseTerm() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parseFactor() }
                    '/' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): Double {
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseFactor()
            }
            if (pos < s.length && s[pos] == '-') {
                pos++
                return -parseFactor()
            }
            if (pos < s.length && s[pos] == '(') {
                pos++
                val x = parseExpression()
                if (pos >= s.length || s[pos] != ')') {
                    throw IllegalArgumentException("Missing closing paren")
                }
                pos++
                return x
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                pos++
            }
            if (pos == start) throw IllegalArgumentException("Expected number")
            return s.substring(start, pos).toDouble()
        }
    }
}
