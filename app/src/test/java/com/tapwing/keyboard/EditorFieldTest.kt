package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditorFieldTest {
    @Test
    fun recognizesTypedAndNamedLoginFields() {
        assertEquals(EditorField.PASSWORD, EditorField.detect(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(EditorField.PASSWORD, EditorField.detect(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertEquals(EditorField.USERNAME, EditorField.detect(InputType.TYPE_CLASS_TEXT, fieldName = "account_userName"))
        assertEquals(EditorField.EMAIL, EditorField.detect(InputType.TYPE_CLASS_TEXT, hint = "E-Mail-Adresse"))
        assertEquals(EditorField.EMAIL, EditorField.detect(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(EditorField.PASSWORD, EditorField.detect(InputType.TYPE_CLASS_TEXT, hint = "Passwort bestätigen"))
    }

    @Test
    fun labelsInAnOrdinarySentenceDoNotBecomeLoginFields() {
        assertEquals(EditorField.TEXT, EditorField.detect(InputType.TYPE_CLASS_TEXT, hint = "Write an email to the team"))
        assertEquals(EditorField.TEXT, EditorField.detect(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT))
        assertEquals(EditorField.STRUCTURED, EditorField.detect(InputType.TYPE_CLASS_PHONE))
        assertFalse(EditorField.TEXT.allowsLearning(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(EditorField.EMAIL.allowsLearning(0))
    }
}
