package com.tapwing.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.tapwing.keyboard.ui.keyboard.KeyActionLabel

internal data class ResolvedImeAction(
    val label: KeyActionLabel,
    val insertNewlineDirectly: Boolean,
    val editorActionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    val sendEnterKeyFallback: Boolean = false
)

/**
 * Resolves both the visual icon and the click behavior of the bottom-right Enter/Action key.
 * In smart contextual mode ([alwaysNewline] == false):
 * - Search fields & browser address/search bars trigger SEARCH / GO directly (with Enter key fallback).
 * - Messaging apps that already provide a dedicated Send button insert a newline instead of sending.
 * - Multi-line text editors (e.g. Notes, Email body) insert a newline; single-line forms show Next/Done/Send.
 */
internal object ImeActionResolver {

    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.instagram.android",
        "com.discord",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.snapchat.android",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.Slack",
        "com.microsoft.teams"
    )

    private val BROWSER_OR_SEARCH_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.google.android.googlequicksearchbox",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser"
    )

    fun resolve(
        imeOptions: Int,
        inputType: Int,
        alwaysNewline: Boolean,
        packageName: String = ""
    ): ResolvedImeAction {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val isMultiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val pkg = packageName.trim()
        val isMessagingApp = pkg in MESSAGING_PACKAGES
        val isBrowserOrSearchApp = pkg in BROWSER_OR_SEARCH_PACKAGES

        if (alwaysNewline) {
            return when {
                !isMultiline && action == EditorInfo.IME_ACTION_SEARCH ->
                    ResolvedImeAction(
                        label = KeyActionLabel.SEARCH,
                        insertNewlineDirectly = false,
                        editorActionId = EditorInfo.IME_ACTION_SEARCH,
                        sendEnterKeyFallback = true
                    )
                !isMultiline && action == EditorInfo.IME_ACTION_GO ->
                    ResolvedImeAction(
                        label = KeyActionLabel.GO,
                        insertNewlineDirectly = false,
                        editorActionId = EditorInfo.IME_ACTION_GO,
                        sendEnterKeyFallback = true
                    )
                !isMultiline && action == EditorInfo.IME_ACTION_NEXT ->
                    ResolvedImeAction(
                        label = KeyActionLabel.NEXT,
                        insertNewlineDirectly = false,
                        editorActionId = EditorInfo.IME_ACTION_NEXT
                    )
                else ->
                    ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
            }
        }

        // Explicit search bars always execute Search (even inside messaging apps like WhatsApp search)
        if (action == EditorInfo.IME_ACTION_SEARCH) {
            return ResolvedImeAction(
                label = KeyActionLabel.SEARCH,
                insertNewlineDirectly = false,
                editorActionId = EditorInfo.IME_ACTION_SEARCH,
                sendEnterKeyFallback = true
            )
        }

        // Explicit Go / navigate actions (e.g. URL bar)
        if (action == EditorInfo.IME_ACTION_GO) {
            return ResolvedImeAction(
                label = KeyActionLabel.GO,
                insertNewlineDirectly = false,
                editorActionId = EditorInfo.IME_ACTION_GO,
                sendEnterKeyFallback = true
            )
        }

        // Messaging apps have a dedicated Send button next to the input box -> bottom-right acts as paragraph/newline
        if (isMessagingApp && (
                isMultiline ||
                    action == EditorInfo.IME_ACTION_SEND ||
                    action == EditorInfo.IME_ACTION_NONE ||
                    action == EditorInfo.IME_ACTION_UNSPECIFIED ||
                    noEnterAction
                )
        ) {
            return ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
        }

        if (noEnterAction) {
            return ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
        }

        // Browser / URL bar variations even when IME action wasn't explicitly set
        if (!isMultiline && typeClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                return ResolvedImeAction(
                    label = KeyActionLabel.GO,
                    insertNewlineDirectly = false,
                    editorActionId = EditorInfo.IME_ACTION_GO,
                    sendEnterKeyFallback = true
                )
            }
        }

        if (!isMultiline && isBrowserOrSearchApp &&
            (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED)
        ) {
            return ResolvedImeAction(
                label = KeyActionLabel.SEARCH,
                insertNewlineDirectly = false,
                editorActionId = EditorInfo.IME_ACTION_SEARCH,
                sendEnterKeyFallback = true
            )
        }

        return when (action) {
            EditorInfo.IME_ACTION_NEXT -> ResolvedImeAction(
                label = KeyActionLabel.NEXT,
                insertNewlineDirectly = false,
                editorActionId = EditorInfo.IME_ACTION_NEXT
            )
            EditorInfo.IME_ACTION_DONE -> ResolvedImeAction(
                label = KeyActionLabel.DONE,
                insertNewlineDirectly = false,
                editorActionId = EditorInfo.IME_ACTION_DONE
            )
            EditorInfo.IME_ACTION_SEND -> if (isMultiline) {
                ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
            } else {
                ResolvedImeAction(
                    label = KeyActionLabel.SEND,
                    insertNewlineDirectly = false,
                    editorActionId = EditorInfo.IME_ACTION_SEND
                )
            }
            else -> ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true)
        }
    }
}
