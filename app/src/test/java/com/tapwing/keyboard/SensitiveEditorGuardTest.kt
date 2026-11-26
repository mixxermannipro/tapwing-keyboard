// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.tapwing.keyboard.ime.SensitiveEditorGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveEditorGuardTest {

    @Test
    fun detectsPasswordsNoPersonalizedLearningCreditCardsAndOtpFields() {
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                imeOptions = 0
            )
        )
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                imeOptions = 0
            )
        )
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                autofillHints = arrayOf("creditCardSecurityCode")
            )
        )
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                hint = "Einmalpasswort / TAN"
            )
        )
        assertTrue(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = 0,
                fieldName = "smsOtpCode"
            )
        )
    }

    @Test
    fun allowsOrdinaryTextAndEmailEditors() {
        assertFalse(
            SensitiveEditorGuard.isSensitiveEditor(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                imeOptions = 0,
                fieldName = "email",
                hint = "E-Mail-Adresse",
                autofillHints = arrayOf("emailAddress")
            )
        )
    }

    @Test
    fun blocksSensitiveOrOtpClipboardEntriesFromPersistence() {
        assertFalse(SensitiveEditorGuard.shouldPersistClipboardEntry(isMarkedSensitive = true, isVerificationCode = false))
        assertFalse(SensitiveEditorGuard.shouldPersistClipboardEntry(isMarkedSensitive = false, isVerificationCode = true))
        assertFalse(SensitiveEditorGuard.shouldPersistClipboardEntry(isMarkedSensitive = true, isVerificationCode = true))
        assertTrue(SensitiveEditorGuard.shouldPersistClipboardEntry(isMarkedSensitive = false, isVerificationCode = false))
    }
}
