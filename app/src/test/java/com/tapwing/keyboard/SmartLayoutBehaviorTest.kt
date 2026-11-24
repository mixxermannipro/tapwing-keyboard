package com.tapwing.keyboard.ime

import android.text.InputType
import com.tapwing.keyboard.ui.keyboard.KeyboardLayoutPage
import com.tapwing.keyboard.ui.keyboard.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLayoutBehaviorTest {

    @Test
    fun selectsNumpadAndPhonePadForNumericAndPhoneFields() {
        assertEquals(
            KeyboardLayoutPage.NUMPAD,
            SmartLayoutBehavior.initialPageForInputType(InputType.TYPE_CLASS_NUMBER)
        )
        assertEquals(
            KeyboardLayoutPage.NUMPAD,
            SmartLayoutBehavior.initialPageForInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )
        assertEquals(
            KeyboardLayoutPage.NUMPAD,
            SmartLayoutBehavior.initialPageForInputType(InputType.TYPE_CLASS_DATETIME)
        )
        assertEquals(
            KeyboardLayoutPage.PHONE_PAD,
            SmartLayoutBehavior.initialPageForInputType(InputType.TYPE_CLASS_PHONE)
        )
        assertEquals(
            KeyboardLayoutPage.LETTERS,
            SmartLayoutBehavior.initialPageForInputType(InputType.TYPE_CLASS_TEXT)
        )
    }

    @Test
    fun numpadAndPhonePadRowsContainAllDigits() {
        val numpadLabels = KeyboardLayouts.rowsFor(KeyboardLayoutPage.NUMPAD, "de", false, true)
            .flatten().map { it.label }.toSet()
        val phoneLabels = KeyboardLayouts.rowsFor(KeyboardLayoutPage.PHONE_PAD, "de", false, true)
            .flatten().map { it.label }.toSet()

        for (digit in '0'..'9') {
            assertTrue("Missing digit $digit in NUMPAD", digit.toString() in numpadLabels)
            assertTrue("Missing digit $digit in PHONE_PAD", digit.toString() in phoneLabels)
        }
        assertTrue("*" in phoneLabels)
        assertTrue("#" in phoneLabels)
    }

    @Test
    fun autoReturnsToLettersAfterSymbolPunctuationButNotAfterNumbers() {
        assertTrue(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.SYMBOLS, "Hallo!"))
        assertTrue(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.SYMBOLS, "Wie?"))
        assertTrue(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.SYMBOLS_EXT, "(test)"))

        // Stay on symbols when typing numbers or math
        assertFalse(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.SYMBOLS, "2026"))
        assertFalse(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.SYMBOLS, "24+"))
        assertFalse(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.LETTERS, "Hallo!"))
        assertFalse(SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(KeyboardLayoutPage.NUMPAD, "12."))
    }
}
