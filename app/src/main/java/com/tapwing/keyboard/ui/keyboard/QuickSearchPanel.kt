package com.tapwing.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.TapwingPrefs

private const val MAX_QUERY_LENGTH = 512
private val trailingQueryWord = Regex("""\s*\S+\s*$""")

@Composable
fun QuickSearchPanel(
    prefs: TapwingPrefs,
    initialQuery: String,
    onBack: () -> Unit,
    onInsertSearchLink: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onSwitchLanguage: () -> Unit
) {
    var query by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, selection = TextRange(initialQuery.length)))
    }
    var page by remember { mutableStateOf(KeyboardLayoutPage.LETTERS) }
    var shift by remember { mutableStateOf(ShiftState.OFF) }

    fun replaceSelection(text: String, start: Int = query.selection.min) {
        val result = query.text.replaceRange(start, query.selection.max, text)
        if (result.length <= MAX_QUERY_LENGTH) query = TextFieldValue(result, TextRange(start + text.length))
    }

    fun backspace() {
        val cursor = query.selection.min
        val from = if (query.selection.collapsed && cursor > 0) query.text.offsetByCodePoints(cursor, -1) else cursor
        replaceSelection("", from)
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { if (it.text.length <= MAX_QUERY_LENGTH) query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                    label = { Text(stringResource(R.string.search_query_label, prefs.quickSearchEngine.label)) },
                    trailingIcon = if (query.text.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = TextFieldValue("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    } else null
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = { onInsertSearchLink(query.text) }, enabled = query.text.isNotBlank()) {
                    Text(stringResource(R.string.search_insert_link))
                }
                Button(onClick = { onOpenSearch(query.text) }, enabled = query.text.isNotBlank()) {
                    Text(stringResource(R.string.search_open))
                }
            }
        }
        KeyboardGrid(
            prefs = prefs.copy(showNumberRow = false, oneHandedMode = OneHandedMode.OFF, glideTypingEnabled = false),
            layoutPage = page,
            shiftState = shift,
            imeActionLabel = KeyActionLabel.SEARCH,
            onKeyPress = { key, output ->
                when (key.type) {
                    KeyActionType.SHIFT -> shift = when (shift) {
                        ShiftState.OFF -> ShiftState.ONCE
                        ShiftState.ONCE -> ShiftState.CAPS_LOCK
                        ShiftState.CAPS_LOCK -> ShiftState.OFF
                    }
                    KeyActionType.BACKSPACE -> backspace()
                    KeyActionType.SYMBOLS_TOGGLE -> page = if (page == KeyboardLayoutPage.LETTERS) {
                        KeyboardLayoutPage.SYMBOLS
                    } else KeyboardLayoutPage.LETTERS
                    KeyActionType.SYMBOLS_EXT_TOGGLE -> page = if (page == KeyboardLayoutPage.SYMBOLS_EXT) {
                        KeyboardLayoutPage.SYMBOLS
                    } else KeyboardLayoutPage.SYMBOLS_EXT
                    KeyActionType.LANGUAGE_SWITCH -> onSwitchLanguage()
                    KeyActionType.ENTER -> { if (query.text.isNotBlank()) onOpenSearch(query.text) }
                    KeyActionType.SPACE -> replaceSelection(" ")
                    KeyActionType.EMOJI_PANEL -> Unit
                    KeyActionType.CHAR, KeyActionType.COMMA, KeyActionType.PERIOD -> {
                        replaceSelection(output)
                        if (shift == ShiftState.ONCE) shift = ShiftState.OFF
                    }
                }
            },
            onSwipeWord = { _, _ -> },
            onCursorMove = { dx, _ ->
                if (dx != 0) {
                    query = query.copy(selection = TextRange((query.selection.end + dx).coerceIn(0, query.text.length)))
                }
            },
            onDeletePreviousWord = {
                val start = trailingQueryWord.find(query.text.take(query.selection.min))?.range?.first
                if (start == null) backspace() else replaceSelection("", start)
            },
            onSwitchLanguage = onSwitchLanguage,
            onToggleOneHandedSide = {},
            onExitOneHanded = {}
        )
    }
}
