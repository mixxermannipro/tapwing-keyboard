package com.tapwing.keyboard.ime

import com.tapwing.keyboard.data.KeyboardLocales
import java.util.Locale

/**
 * Cycles selected text casing on Shift press:
 * lowercase / mixed -> Title Case -> UPPERCASE -> lowercase.
 */
object SelectedTextCaseCycler {

    fun cycle(selectedText: String, localeCode: String): String {
        if (selectedText.isEmpty() || selectedText.none(Char::isLetter)) return selectedText
        val locale = Locale.forLanguageTag(KeyboardLocales.specFor(localeCode).bcp47Tag)
        val letters = selectedText.filter(Char::isLetter)
        val isAllUpper = letters.all(Char::isUpperCase)
        val isAllLower = letters.all(Char::isLowerCase)
        val titleCased = toTitleCase(selectedText, locale)

        return when {
            isAllUpper -> selectedText.lowercase(locale)
            selectedText == titleCased -> selectedText.uppercase(locale)
            isAllLower -> titleCased
            else -> titleCased
        }
    }

    private fun toTitleCase(text: String, locale: Locale): String {
        val sb = StringBuilder(text.length)
        var startOfWord = true
        for (ch in text) {
            if (ch.isLetter()) {
                if (startOfWord) {
                    sb.append(ch.toString().uppercase(locale))
                    startOfWord = false
                } else {
                    sb.append(ch.toString().lowercase(locale))
                }
            } else {
                sb.append(ch)
                if (ch.isWhitespace() || ch in "-/(\"[{'’") {
                    startOfWord = true
                }
            }
        }
        return sb.toString()
    }
}
