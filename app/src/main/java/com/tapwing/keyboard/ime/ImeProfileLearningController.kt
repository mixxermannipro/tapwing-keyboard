// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ime

import com.tapwing.keyboard.data.SmartFieldType

/**
 * Tracks the currently active structured form field's typed text while the user is typing in it,
 * so that when focus switches to another field (`onStartInput`) we learn from the PREVIOUS field's
 * captured snapshot rather than reading from the newly focused `InputConnection`.
 *
 * Free-text fields (`SmartFieldType.NONE`) and private/sensitive fields are never captured.
 */
internal class ImeProfileLearningController {

    data class Snapshot(
        val fieldType: SmartFieldType,
        val text: String,
        val appPackage: String,
        val editorGeneration: Long
    )

    private var pending: Snapshot? = null

    fun updateActiveFieldText(
        fieldType: SmartFieldType,
        rawText: String,
        appPackage: String,
        editorGeneration: Long,
        privateEditor: Boolean,
        autoLearnEnabled: Boolean,
        learningEnabled: Boolean,
        incognitoMode: Boolean
    ) {
        if (privateEditor || !autoLearnEnabled || !learningEnabled || incognitoMode ||
            fieldType == SmartFieldType.NONE
        ) {
            if (pending?.editorGeneration == editorGeneration) {
                pending = null
            }
            return
        }
        val clean = rawText.trim()
        if (clean.length !in 2..90 || '\n' in clean) {
            if (pending?.editorGeneration == editorGeneration) {
                pending = null
            }
            return
        }
        pending = Snapshot(
            fieldType = fieldType,
            text = clean,
            appPackage = appPackage,
            editorGeneration = editorGeneration
        )
    }

    fun consumePending(): Snapshot? {
        val current = pending
        pending = null
        return current
    }

    fun clear() {
        pending = null
    }
}
