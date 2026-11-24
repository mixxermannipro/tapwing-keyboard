package com.tapwing.keyboard.ime

import android.text.InputType
import com.tapwing.keyboard.ui.keyboard.KeyboardLayoutPage

/**
 * Smart Gboard-grade layout switching rules:
 * - Numeric / datetime fields open the oversized 3x4 Numpad automatically.
 * - Phone fields open the dedicated Phone Pad (+, *, #) automatically.
 * - Pressing Space on the Symbols page after punctuation automatically snaps back to Letters,
 *   while staying on Symbols when typing numbers or arithmetic expressions.
 */
object SmartLayoutBehavior {

    private val AUTO_RETURN_SYMBOLS = setOf('!', '?', ')', ']', '}', ':', ';', '"', '\'', ',', '.')

    fun initialPageForInputType(inputType: Int): KeyboardLayoutPage {
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_PHONE -> KeyboardLayoutPage.PHONE_PAD
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_DATETIME -> KeyboardLayoutPage.NUMPAD
            else -> KeyboardLayoutPage.LETTERS
        }
    }

    fun shouldAutoReturnToLettersAfterSpace(
        currentPage: KeyboardLayoutPage,
        textBeforeSpace: String
    ): Boolean {
        if (currentPage != KeyboardLayoutPage.SYMBOLS && currentPage != KeyboardLayoutPage.SYMBOLS_EXT) {
            return false
        }
        val lastChar = textBeforeSpace.lastOrNull() ?: return false
        return lastChar in AUTO_RETURN_SYMBOLS
    }
}
