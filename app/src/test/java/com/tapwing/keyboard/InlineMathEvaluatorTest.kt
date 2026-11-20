package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InlineMathEvaluatorTest {

    @Test
    fun evaluatesGermanAndEnglishArithmeticInStrip() {
        val mul = InlineMathEvaluator.evaluateTrailingExpression("24*7=", "de")
        assertNotNull(mul)
        assertEquals("168", mul?.formattedResult)
        assertEquals(false, mul?.appendEqualsPrefix)

        val dec = InlineMathEvaluator.evaluateTrailingExpression("Preis 19,5+4,5", "de")
        assertNotNull(dec)
        assertEquals("24", dec?.formattedResult)
        assertEquals(true, dec?.appendEqualsPrefix)

        val div = InlineMathEvaluator.evaluateTrailingExpression("(10+5)/2=", "en")
        assertEquals("7.5", div?.formattedResult)

        val pct = InlineMathEvaluator.evaluateTrailingExpression("150+19%=", "de")
        assertEquals("178,5", pct?.formattedResult)

        val pow = InlineMathEvaluator.evaluateTrailingExpression("2^8=", "en")
        assertEquals("256", pow?.formattedResult)

        // Single number without operator must not trigger
        assertNull(InlineMathEvaluator.evaluateTrailingExpression("2026", "de"))
    }
}
