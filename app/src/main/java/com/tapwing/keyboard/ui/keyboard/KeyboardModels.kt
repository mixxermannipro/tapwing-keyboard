package com.tapwing.keyboard.ui.keyboard

enum class KeyboardLayoutPage {
    LETTERS,
    SYMBOLS,
    SYMBOLS_EXT
}

enum class ShiftState {
    OFF,
    ONCE,
    CAPS_LOCK
}

enum class KeyboardPanelMode {
    KEYS,
    TOOLS_HUB,
    WRITING_TOOLS,
    WRITING_ONBOARDING,
    VOICE_ONBOARDING,
    CLIPBOARD,
    EMOJI
}

enum class KeyActionType {
    CHAR,
    SHIFT,
    BACKSPACE,
    SYMBOLS_TOGGLE,
    SYMBOLS_EXT_TOGGLE,
    COMMA,
    EMOJI_PANEL,
    SPACE,
    PERIOD,
    ENTER,
    LANGUAGE_SWITCH
}

data class KeySpec(
    val label: String,
    val output: String = label,
    val hint: String? = null,
    val weight: Float = 1f,
    val type: KeyActionType = KeyActionType.CHAR,
    val alternates: List<String> = emptyList()
)

object KeyboardLayouts {

    private val germanRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "1", alternates = listOf("1")),
            KeySpec("w", hint = "2", alternates = listOf("2")),
            KeySpec("e", hint = "3", alternates = listOf("3", "é", "è", "ê", "€")),
            KeySpec("r", hint = "4", alternates = listOf("4")),
            KeySpec("t", hint = "5", alternates = listOf("5")),
            KeySpec("z", hint = "6", alternates = listOf("6")),
            KeySpec("u", hint = "7", alternates = listOf("7", "ü", "ú", "ù")),
            KeySpec("i", hint = "8", alternates = listOf("8", "í", "ì")),
            KeySpec("o", hint = "9", alternates = listOf("9", "ö", "ó", "ò")),
            KeySpec("p", hint = "0", alternates = listOf("0")),
            KeySpec("ü", alternates = listOf("u", "ú"))
        ),
        listOf(
            KeySpec("a", alternates = listOf("ä", "á", "à", "@")),
            KeySpec("s", alternates = listOf("ß", "ś", "š")),
            KeySpec("d"),
            KeySpec("f"),
            KeySpec("g"),
            KeySpec("h"),
            KeySpec("j"),
            KeySpec("k"),
            KeySpec("l"),
            KeySpec("ö", alternates = listOf("o", "ó")),
            KeySpec("ä", alternates = listOf("a", "á"))
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("y"),
            KeySpec("x"),
            KeySpec("c", alternates = listOf("ç", "ć")),
            KeySpec("v"),
            KeySpec("b"),
            KeySpec("n", alternates = listOf("ñ")),
            KeySpec("m"),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("?123", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec("Deutsch", output = " ", weight = 3.8f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-", "'")),
            KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
        )
    )

    private val germanRowsCompact: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "1", alternates = listOf("1")),
            KeySpec("w", hint = "2", alternates = listOf("2")),
            KeySpec("e", hint = "3", alternates = listOf("3", "é", "è", "€")),
            KeySpec("r", hint = "4", alternates = listOf("4")),
            KeySpec("t", hint = "5", alternates = listOf("5")),
            KeySpec("z", hint = "6", alternates = listOf("6")),
            KeySpec("u", hint = "7", alternates = listOf("7", "ü")),
            KeySpec("i", hint = "8", alternates = listOf("8")),
            KeySpec("o", hint = "9", alternates = listOf("9", "ö")),
            KeySpec("p", hint = "0", alternates = listOf("0"))
        ),
        listOf(
            KeySpec("a", alternates = listOf("ä", "á", "à")),
            KeySpec("s", alternates = listOf("ß")),
            KeySpec("d"),
            KeySpec("f"),
            KeySpec("g"),
            KeySpec("h"),
            KeySpec("j"),
            KeySpec("k"),
            KeySpec("l")
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("y"),
            KeySpec("x"),
            KeySpec("c"),
            KeySpec("v"),
            KeySpec("b"),
            KeySpec("n"),
            KeySpec("m"),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("?123", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec("Deutsch", output = " ", weight = 3.8f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-")),
            KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
        )
    )

    private val englishRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "1", alternates = listOf("1")),
            KeySpec("w", hint = "2", alternates = listOf("2")),
            KeySpec("e", hint = "3", alternates = listOf("3", "é", "è")),
            KeySpec("r", hint = "4", alternates = listOf("4")),
            KeySpec("t", hint = "5", alternates = listOf("5")),
            KeySpec("y", hint = "6", alternates = listOf("6")),
            KeySpec("u", hint = "7", alternates = listOf("7", "ü")),
            KeySpec("i", hint = "8", alternates = listOf("8")),
            KeySpec("o", hint = "9", alternates = listOf("9", "ö")),
            KeySpec("p", hint = "0", alternates = listOf("0"))
        ),
        listOf(
            KeySpec("a", alternates = listOf("ä", "á", "à", "@")),
            KeySpec("s", alternates = listOf("ß", "$")),
            KeySpec("d"),
            KeySpec("f"),
            KeySpec("g"),
            KeySpec("h"),
            KeySpec("j"),
            KeySpec("k"),
            KeySpec("l")
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("z"),
            KeySpec("x"),
            KeySpec("c", alternates = listOf("ç")),
            KeySpec("v"),
            KeySpec("b"),
            KeySpec("n", alternates = listOf("ñ")),
            KeySpec("m"),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("?123", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec("English", output = " ", weight = 3.8f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-", "'")),
            KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
        )
    )

    val numberRow: List<KeySpec> = (1..9).map { KeySpec(it.toString()) } + KeySpec("0")

    private val symbolRows: List<List<KeySpec>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeySpec(it) },
        listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/").map { KeySpec(it) },
        listOf(
            KeySpec("=\\<", weight = 1.45f, type = KeyActionType.SYMBOLS_EXT_TOGGLE),
            KeySpec("*"),
            KeySpec("\""),
            KeySpec("'"),
            KeySpec(":"),
            KeySpec(";"),
            KeySpec("!"),
            KeySpec("?"),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("ABC", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec(" ", weight = 3.8f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD),
            KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
        )
    )

    private val symbolExtRows: List<List<KeySpec>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ").map { KeySpec(it) },
        listOf("£", "¢", "$", "¥", "^", "°", "=", "{", "}", "\\").map { KeySpec(it) },
        listOf(
            KeySpec("?123", weight = 1.45f, type = KeyActionType.SYMBOLS_EXT_TOGGLE),
            KeySpec("%"),
            KeySpec("©"),
            KeySpec("®"),
            KeySpec("™"),
            KeySpec("✓"),
            KeySpec("["),
            KeySpec("]"),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("ABC", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec("<", weight = 1f),
            KeySpec(">", weight = 1f),
            KeySpec(" ", weight = 3.8f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD),
            KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
        )
    )

    fun rowsFor(
        page: KeyboardLayoutPage,
        locale: String,
        showGermanUmlauts: Boolean,
        showNumberRow: Boolean
    ): List<List<KeySpec>> {
        return when (page) {
            KeyboardLayoutPage.LETTERS -> {
                val base = when {
                    locale.startsWith("de") && showGermanUmlauts -> germanRows
                    locale.startsWith("de") -> germanRowsCompact
                    else -> englishRows
                }
                if (showNumberRow) listOf(numberRow) + base else base
            }
            KeyboardLayoutPage.SYMBOLS -> symbolRows
            KeyboardLayoutPage.SYMBOLS_EXT -> symbolExtRows
        }
    }
}
