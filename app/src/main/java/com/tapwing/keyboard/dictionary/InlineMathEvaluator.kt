package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class MathSuggestion(
    val formattedResult: String,
    val replaceCharCount: Int,
    val appendEqualsPrefix: Boolean
)

/**
 * Pure Kotlin safe recursive-descent arithmetic evaluator for the keyboard suggestion strip.
 * Supports +, -, *, /, ^, and percentage modifiers (e.g. "24*7", "150+19%=", "2^8=", "(12+8)/4=").
 */
object InlineMathEvaluator {

    private val trailingMathRegex = Regex("""([0-9(][0-9.,+\-*/×÷^%()\s]*[+\-*/×÷^][0-9.,+\-*/×÷^%()\s]*)=?$""")

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

        if (!normalized.any { it in charArrayOf('+', '-', '*', '/', '^') }) return null

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
        val usesComma = KeyboardLocales.specFor(locale).usesCommaDecimal
        return if (usesComma) raw.replace('.', ',') else raw
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
                    '+' -> {
                        pos++
                        val term = parseTermWithPercentBase(x)
                        x += term
                    }
                    '-' -> {
                        pos++
                        val term = parseTermWithPercentBase(x)
                        x -= term
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTermWithPercentBase(base: Double): Double {
            val startPos = pos
            val rawTerm = parseTerm()
            // Check if the term ended with '%' at top level (handled in parsePower/parseFactor as /100.0;
            // for "+ 19%" or "- 20%" on base, treat x + y% as x + x * (y/100))
            val slice = s.substring(startPos, pos)
            if (slice.endsWith("%") && !slice.contains('*') && !slice.contains('/')) {
                return base * rawTerm
            }
            return rawTerm
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parsePower() }
                    '/' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parsePower(): Double {
            var x = parseFactor()
            if (pos < s.length && s[pos] == '^') {
                pos++
                val exp = parsePower()
                x = x.pow(exp)
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
            var value: Double
            if (pos < s.length && s[pos] == '(') {
                pos++
                value = parseExpression()
                if (pos >= s.length || s[pos] != ')') {
                    throw IllegalArgumentException("Missing closing paren")
                }
                pos++
            } else {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                    pos++
                }
                if (pos == start) throw IllegalArgumentException("Expected number")
                value = s.substring(start, pos).toDouble()
            }
            while (pos < s.length && s[pos] == '%') {
                pos++
                value /= 100.0
            }
            return value
        }
    }
}
