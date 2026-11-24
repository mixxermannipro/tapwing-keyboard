package com.tapwing.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedTextCaseCyclerTest {

    @Test
    fun cyclesLowerToTitleToUpperToLower() {
        val step1 = SelectedTextCaseCycler.cycle("hallo welt", "de")
        assertEquals("Hallo Welt", step1)

        val step2 = SelectedTextCaseCycler.cycle(step1, "de")
        assertEquals("HALLO WELT", step2)

        val step3 = SelectedTextCaseCycler.cycle(step2, "de")
        assertEquals("hallo welt", step3)
    }

    @Test
    fun handlesGermanUmlautsAndHyphenatedWords() {
        assertEquals("Über-Mäßig", SelectedTextCaseCycler.cycle("über-mäßig", "de"))
        assertEquals("ÜBER-MÄSSIG", SelectedTextCaseCycler.cycle("Über-Mäßig", "de"))
    }

    @Test
    fun leavesNonLetterTextUnchanged() {
        assertEquals("12345 !?", SelectedTextCaseCycler.cycle("12345 !?", "en"))
        assertEquals("", SelectedTextCaseCycler.cycle("", "en"))
    }
}
