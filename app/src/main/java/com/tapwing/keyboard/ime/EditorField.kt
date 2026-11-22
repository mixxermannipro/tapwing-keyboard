package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal enum class EditorField {
    TEXT, PASSWORD, USERNAME, EMAIL, URL, STRUCTURED, UNKNOWN;

    fun isPrivate(imeOptions: Int): Boolean =
        this == PASSWORD || this == USERNAME || this == UNKNOWN ||
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    fun allowsLearning(imeOptions: Int): Boolean = this == TEXT && !isPrivate(imeOptions)

    companion object {
        private val camelCase = Regex("([a-z])([A-Z])")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        private val passwords = setOf("password", "passwort", "kennwort", "passcode")
        private val usernames = setOf("username", "benutzername", "nutzername")
        private val emailHints = setOf(
            "email", "email address", "email adresse", "e mail", "e mail address",
            "e mail adresse", "email or username", "username or email",
            "e mail oder benutzername", "benutzername oder e mail"
        )
        private val passwordHints = passwords + setOf(
            "confirm password", "new password", "current password",
            "passwort bestätigen", "neues passwort", "aktuelles passwort"
        )

        private fun words(value: String?): String = value.orEmpty()
            .replace(camelCase, "$1 $2").lowercase().replace(separators, " ").trim()

        fun detect(inputType: Int, fieldName: String? = null, hint: String? = null): EditorField {
            if (inputType == InputType.TYPE_NULL) return UNKNOWN
            val type = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return PASSWORD
            if (type != InputType.TYPE_CLASS_TEXT) return STRUCTURED

            val field = words(fieldName)
            val label = words(hint)
            val nameTokens = field.split(' ')
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                nameTokens.any { it in passwords } || label in passwordHints
            ) return PASSWORD
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                nameTokens.contains("email") || label in emailHints
            ) return EMAIL
            if (nameTokens.any { it in usernames } || "user name" in field ||
                label in usernames || label == "user name"
            ) return USERNAME
            if (variation == InputType.TYPE_TEXT_VARIATION_URI) return URL
            return TEXT
        }
    }
}
