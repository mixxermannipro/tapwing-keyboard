package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGestureDecoderTest {

    @Test
    fun decodesGermanQwertzSwipePath() {
        // Approximate QWERTZ coordinates in 1000x300 space
        val row1 = "qwertzuiop"
        val row2 = "asdfghjkl"
        val row3 = "yxcvbnm"
        val centers = buildList {
            row1.forEachIndexed { idx, ch -> add(KeyCenter(ch, 50f + idx * 100f, 50f)) }
            row2.forEachIndexed { idx, ch -> add(KeyCenter(ch, 100f + idx * 100f, 150f)) }
            row3.forEachIndexed { idx, ch -> add(KeyCenter(ch, 180f + idx * 100f, 250f)) }
        }
        val byChar = centers.associateBy { it.char }

        // Swipe h -> a -> l -> l -> o
        val points = listOf('h', 'a', 'l', 'o').map { ch ->
            val kc = byChar.getValue(ch)
            SwipePoint(kc.x, kc.y)
        }
        val candidates = SwipeGestureDecoder.decodeSwipe(points, centers, locale = "de")
        assertTrue("Expected 'hallo' in $candidates", candidates.any { it.equals("hallo", ignoreCase = true) })
    }

    @Test
    fun decodesGermanUmlautWordWithoutDedicatedUmlautKeys() {
        val row1 = "qwertzuiop"
        val row2 = "asdfghjkl"
        val row3 = "yxcvbnm"
        val centers = buildList {
            row1.forEachIndexed { idx, ch -> add(KeyCenter(ch, 50f + idx * 100f, 50f)) }
            row2.forEachIndexed { idx, ch -> add(KeyCenter(ch, 100f + idx * 100f, 150f)) }
            row3.forEachIndexed { idx, ch -> add(KeyCenter(ch, 180f + idx * 100f, 250f)) }
        }
        val byChar = centers.associateBy { it.char }

        // Swipe u -> b -> e -> r on layout without dedicated 'ü' key
        val points = listOf('u', 'b', 'e', 'r').map { ch ->
            val kc = byChar.getValue(ch)
            SwipePoint(kc.x, kc.y)
        }
        val candidates = SwipeGestureDecoder.decodeSwipe(points, centers, locale = "de")
        assertTrue("Expected 'über' in $candidates", candidates.any { it.equals("über", ignoreCase = true) })
    }
}
