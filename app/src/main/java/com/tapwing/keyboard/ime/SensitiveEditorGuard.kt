// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Central privacy guard for all IME learning, suggestion, AI writing, dictation,
 * statistics, Smart Fill, and clipboard paths.
 */
object SensitiveEditorGuard {

    private val CAMEL_CASE_REGEX = Regex("([a-z])([A-Z])")
    private val SEPARATORS_REGEX = Regex("[^\\p{L}\\p{N}]+")

    private val SENSITIVE_AUTOFILL_HINTS = setOf(
        "password",
        "newpassword",
        "currentpassword",
        "creditcardnumber",
        "creditcardsecuritycode",
        "creditcardexpirationdate",
        "creditcardexpirationmonth",
        "creditcardexpirationyear",
        "creditcardexpirationday",
        "smsotpcode",
        "2faappotpcode",
        "emailotpcode",
        "onetimecode",
        "otp",
        "pin",
        "cvv",
        "cvc",
        "iban",
        "securitycode"
    )

    private val SENSITIVE_FIELD_TOKENS = setOf(
        "password", "passwort", "kennwort", "passcode",
        "cvv", "cvc", "iban", "pin", "otp", "tan", "2fa"
    )

    private val SENSITIVE_PHRASES = setOf(
        "confirm password", "new password", "current password",
        "passwort bestätigen", "neues passwort", "aktuelles passwort",
        "credit card", "kreditkarte", "kartennummer", "sicherheitscode",
        "verification code", "bestätigungscode", "einmalpasswort", "one time code"
    )

    fun isSensitiveEditor(info: EditorInfo?): Boolean {
        if (info == null) return true
        val extraHints = info.extras?.getStringArray("autofillHints")
        val combinedFieldName = listOfNotNull(
            info.fieldName?.takeIf { it.isNotBlank() },
            info.privateImeOptions?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { null }
        return isSensitiveEditor(
            inputType = info.inputType,
            imeOptions = info.imeOptions,
            fieldName = combinedFieldName,
            hint = info.hintText,
            autofillHints = extraHints
        )
    }

    internal fun isSensitiveEditor(
        inputType: Int,
        imeOptions: Int,
        fieldName: String? = null,
        hint: CharSequence? = null,
        autofillHints: Array<String>? = null
    ): Boolean {
        if (inputType == InputType.TYPE_NULL) return true
        if ((imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true

        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return true
        }
        if (inputClass == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
        ) {
            return true
        }

        if (autofillHints != null) {
            for (raw in autofillHints) {
                val compacted = normalizeCompact(raw)
                if (compacted in SENSITIVE_AUTOFILL_HINTS) return true
            }
        }

        val normalizedField = normalizeWords(fieldName)
        val normalizedHint = normalizeWords(hint?.toString())
        val fieldTokens = if (normalizedField.isEmpty()) emptyList() else normalizedField.split(' ')
        val hintTokens = if (normalizedHint.isEmpty()) emptyList() else normalizedHint.split(' ')

        if (fieldTokens.any { it in SENSITIVE_FIELD_TOKENS } ||
            hintTokens.any { it in SENSITIVE_FIELD_TOKENS }
        ) {
            return true
        }

        if (SENSITIVE_PHRASES.any { it in normalizedField || it in normalizedHint }) {
            return true
        }

        return false
    }

    /**
     * Sensitive clips (`EXTRA_IS_SENSITIVE`) and one-time verification codes (OTP/2FA) must
     * never be saved to persistent clipboard history.
     */
    fun shouldPersistClipboardEntry(
        isMarkedSensitive: Boolean,
        isVerificationCode: Boolean
    ): Boolean = !isMarkedSensitive && !isVerificationCode

    private fun normalizeWords(value: String?): String = value.orEmpty()
        .replace(CAMEL_CASE_REGEX, "$1 $2")
        .lowercase()
        .replace(SEPARATORS_REGEX, " ")
        .trim()

    private fun normalizeCompact(value: String?): String = value.orEmpty()
        .lowercase()
        .replace(SEPARATORS_REGEX, "")
}
