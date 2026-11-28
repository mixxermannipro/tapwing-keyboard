// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapwing.keyboard.ime.SensitiveEditorGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveEditorInstrumentedTest {

    @Test
    fun realEditorInfoRespectsPasswordOtpAndNoPersonalizedLearning() {
        val passwordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertTrue(SensitiveEditorGuard.isSensitiveEditor(passwordInfo))

        val privateInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        assertTrue(SensitiveEditorGuard.isSensitiveEditor(privateInfo))

        val normalInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hintText = "Message"
        }
        assertFalse(SensitiveEditorGuard.isSensitiveEditor(normalInfo))
    }
}
