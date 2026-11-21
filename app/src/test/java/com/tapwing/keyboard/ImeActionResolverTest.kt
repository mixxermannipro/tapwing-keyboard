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
    fun alwaysNewlineKeepsSearchAndNextInSingleLineNavigationFields() {
        val searchRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.SEARCH, searchRes.label)
        assertFalse(searchRes.insertNewlineDirectly)

        val nextRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_NEXT,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.NEXT, nextRes.label)
        assertFalse(nextRes.insertNewlineDirectly)
    }

    @Test
    fun alwaysNewlinePrefersEnterInMultilineEvenWithSearchAction() {
        val multilineSearch = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            alwaysNewline = true
        )
        assertEquals(KeyActionLabel.ENTER, multilineSearch.label)
        assertTrue(multilineSearch.insertNewlineDirectly)
    }

    @Test
    fun dynamicModeRespectsEditorImeActionWhenAlwaysNewlineIsOff() {
        val sendRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_SEND,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false
        )
        assertEquals(KeyActionLabel.SEND, sendRes.label)
        assertFalse(sendRes.insertNewlineDirectly)

        val doneRes = ImeActionResolver.resolve(
            imeOptions = EditorInfo.IME_ACTION_DONE,
            inputType = InputType.TYPE_CLASS_TEXT,
            alwaysNewline = false
        )
        assertEquals(KeyActionLabel.DONE, doneRes.label)
        assertFalse(doneRes.insertNewlineDirectly)
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
