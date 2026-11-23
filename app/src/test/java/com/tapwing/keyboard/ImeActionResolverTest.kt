package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.tapwing.keyboard.ui.keyboard.KeyActionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeActionResolverTest {

    @Test
    fun alwaysNewlineOverridesSendAndDoneInMessagingFields() {
        val sendRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEND,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.ENTER, sendRes.label)
        assertTrue(sendRes.insertNewlineDirectly)

        val doneRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_DONE,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.ENTER, doneRes.label)
        assertTrue(doneRes.insertNewlineDirectly)
    }

    @Test
    fun alwaysNewlineKeepsSearchGoAndNextInSingleLineNavigationFields() {
        val searchRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.SEARCH, searchRes.label)
        assertFalse(searchRes.insertNewlineDirectly)

        val goRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_GO,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.GO, goRes.label)
        assertFalse(goRes.insertNewlineDirectly)

        val nextRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_NEXT,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.NEXT, nextRes.label)
        assertFalse(nextRes.insertNewlineDirectly)
    }

    @Test
    fun whatsappChatUsesEnterParagraphWhileWhatsappSearchExecutesSearch() {
        val chatRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEND,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            alwaysNewline = false,
            packageName = "com.whatsapp"
        )
        assertEquals(KeyActionLabel.ENTER, chatRes.label)
        assertTrue(chatRes.insertNewlineDirectly)

        val chatSingleSend = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEND,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false,
            packageName = "com.whatsapp"
        )
        assertEquals(KeyActionLabel.ENTER, chatSingleSend.label)
        assertTrue(chatSingleSend.insertNewlineDirectly)

        val searchRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false,
            packageName = "com.whatsapp"
        )
        assertEquals(KeyActionLabel.SEARCH, searchRes.label)
        assertFalse(searchRes.insertNewlineDirectly)
        assertTrue(searchRes.sendEnterKeyFallback)
    }

    @Test
    fun browserUrlBarAndGoogleSearchExecuteGoAndSearch() {
        val chromeUrl = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_NONE,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            alwaysNewline = false,
            packageName = "com.android.chrome"
        )
        assertEquals(KeyActionLabel.GO, chromeUrl.label)
        assertFalse(chromeUrl.insertNewlineDirectly)
        assertTrue(chromeUrl.sendEnterKeyFallback)

        val googleSearch = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false,
            packageName = "com.google.android.googlequicksearchbox"
        )
        assertEquals(KeyActionLabel.SEARCH, googleSearch.label)
        assertFalse(googleSearch.insertNewlineDirectly)
        assertTrue(googleSearch.sendEnterKeyFallback)
    }

    @Test
    fun dynamicModeRespectsFormNextDoneAndMultilineEmailBody() {
        val nextRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_NEXT,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false
        )
        assertEquals(KeyActionLabel.NEXT, nextRes.label)
        assertFalse(nextRes.insertNewlineDirectly)

        val doneRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_DONE,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false
        )
        assertEquals(KeyActionLabel.DONE, doneRes.label)
        assertFalse(doneRes.insertNewlineDirectly)

        val multilineEmail = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_NONE,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            alwaysNewline = false,
            packageName = "com.google.android.gm"
        )
        assertEquals(KeyActionLabel.ENTER, multilineEmail.label)
        assertTrue(multilineEmail.insertNewlineDirectly)
    }

    @Test
    fun noEnterActionFlagAlwaysForcesNewline() {
        val res = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false
        )
        assertEquals(KeyActionLabel.ENTER, res.label)
        assertTrue(res.insertNewlineDirectly)
    }
}
