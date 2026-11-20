package com.tapwing.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitKeyboardTest {

    @Test
    fun splitsLetterAndSpaceRowsIntoBalancedLeftAndRightHalves() {
        val topRow = "qwertzuiopü".map { ch ->
            KeySpec(label = ch.toString())
        }
        val splitTop = splitKeyboardRow(topRow)
        assertEquals(2, splitTop.size)
        assertEquals(5, splitTop[0].size)
        assertEquals(6, splitTop[1].size)

        val spaceRow = listOf(
            KeySpec(label = "?123", type = KeyActionType.SYMBOLS_TOGGLE, weight = 1.3f),
            KeySpec(label = ",", output = ","),
            KeySpec(label = "DE", type = KeyActionType.SPACE, output = " ", weight = 4.6f),
            KeySpec(label = ".", output = "."),
            KeySpec(label = "↵", type = KeyActionType.ENTER, weight = 1.6f)
        )
        val splitSpace = splitKeyboardRow(spaceRow)
        assertEquals(2, splitSpace.size)
        assertTrue(splitSpace[0].last().type == KeyActionType.SPACE)
        assertTrue(splitSpace[1].first().type == KeyActionType.SPACE)
    }
}
