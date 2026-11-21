package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.tapwing.keyboard.ui.keyboard.KeyActionLabel

internal data class ResolvedImeAction(
    val label: KeyActionLabel,
    val insertNewlineDirectly: Boolean
)

/**
 * Resolves both the visual icon and the click behavior of the bottom-right Enter/Action key.
 * When [alwaysNewline] is enabled, messaging/text fields display the newline icon and always
 * insert '\n' instead of accidentally sending messages, while explicit Search/Next fields keep
 * their navigation actions.
 */
internal object ImeActionResolver {

    fun resolve(imeOptions: Int, inputType: Int, alwaysNewline: Boolean): ResolvedImeAction {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val isMultiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (noEnterAction) {
            return ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
        }

        if (alwaysNewline) {
            return when {
                !isMultiline && action == EditorInfo.IME_ACTION_SEARCH ->
                    ResolvedImeAction(KeyActionLabel.SEARCH, insertNewlineDirectly = false)
                !isMultiline && action == EditorInfo.IME_ACTION_NEXT ->
                    ResolvedImeAction(KeyActionLabel.NEXT, insertNewlineDirectly = false)
                else ->
                    ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
            }
        }

        val label = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> KeyActionLabel.SEARCH
            EditorInfo.IME_ACTION_SEND -> KeyActionLabel.SEND
            EditorInfo.IME_ACTION_DONE -> KeyActionLabel.DONE
            EditorInfo.IME_ACTION_NEXT -> KeyActionLabel.NEXT
            else -> KeyActionLabel.ENTER
        }
        val directNewline = isMultiline ||
            action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        return ResolvedImeAction(label = label, insertNewlineDirectly = directNewline)
    }
}
