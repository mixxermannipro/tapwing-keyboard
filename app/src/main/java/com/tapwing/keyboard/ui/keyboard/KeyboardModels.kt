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
    EMOJI,
    RESIZE,
    TEXT_EDIT,
    QUICK_SEARCH
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

    /**
     * 1:1 German QWERTZ layout matching PixelBoard's key arrangement & top-center symbol hints:
     * Row 1: q(%) w(\) e(|) r(=) t([) z(]) u(<) i(>) o({) p(})
     * Row 2: a(@) s(#) d(€) f(_) g(&) h(-) j(+) k(() l())
     * Row 3: ⇧ y(*) x(") c(') v(:) b(;) n(!) m(?) ⌫
     * Row 4: ?123 , ☺ [Space] . ↵
     */
    private val germanPixelRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "%", alternates = listOf("%", "1")),
            KeySpec("w", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = listOf("|", "€", "é", "è", "ê", "3")),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("z", hint = "]", alternates = listOf("]", "6")),
            KeySpec("u", hint = "<", alternates = listOf("<", "ü", "ú", "ù", "7")),
            KeySpec("i", hint = ">", alternates = listOf(">", "í", "ì", "8")),
            KeySpec("o", hint = "{", alternates = listOf("{", "ö", "ó", "ò", "9")),
            KeySpec("p", hint = "}", alternates = listOf("}", "0"))
        ),
        listOf(
            KeySpec("a", hint = "@", alternates = listOf("@", "ä", "á", "à", "â")),
            KeySpec("s", hint = "#", alternates = listOf("#", "ß", "ś", "š")),
            KeySpec("d", hint = "€", alternates = listOf("€")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")"))
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("y", hint = "*", alternates = listOf("*")),
            KeySpec("x", hint = "\"", alternates = listOf("\"")),
            KeySpec("c", hint = "'", alternates = listOf("'", "ç", "ć")),
            KeySpec("v", hint = ":", alternates = listOf(":")),
            KeySpec("b", hint = ";", alternates = listOf(";")),
            KeySpec("n", hint = "!", alternates = listOf("!", "ñ")),
            KeySpec("m", hint = "?", alternates = listOf("?")),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("?123", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec("Deutsch", output = " ", weight = 3.85f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-", "'")),
            KeySpec("↵", weight = 1.5f, type = KeyActionType.ENTER)
        )
    )

    private val germanRowsWithDirectUmlauts: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "%", alternates = listOf("%", "1")),
            KeySpec("w", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = listOf("|", "é", "è", "€", "3")),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("z", hint = "]", alternates = listOf("]", "6")),
            KeySpec("u", hint = "<", alternates = listOf("<", "ü", "ú", "7")),
            KeySpec("i", hint = ">", alternates = listOf(">", "í", "8")),
            KeySpec("o", hint = "{", alternates = listOf("{", "ö", "ó", "9")),
            KeySpec("p", hint = "}", alternates = listOf("}", "0")),
            KeySpec("ü", alternates = listOf("u", "ú"))
        ),
        listOf(
            KeySpec("a", hint = "@", alternates = listOf("@", "ä", "á", "à")),
            KeySpec("s", hint = "#", alternates = listOf("#", "ß", "ś")),
            KeySpec("d", hint = "€", alternates = listOf("€")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")")),
            KeySpec("ö", alternates = listOf("o", "ó")),
            KeySpec("ä", alternates = listOf("a", "á"))
        ),
        germanPixelRows[2],
        germanPixelRows[3]
    )

    private val englishRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "%", alternates = listOf("%", "1")),
            KeySpec("w", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = listOf("|", "é", "è", "3")),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("y", hint = "]", alternates = listOf("]", "6")),
            KeySpec("u", hint = "<", alternates = listOf("<", "ü", "7")),
            KeySpec("i", hint = ">", alternates = listOf(">", "8")),
            KeySpec("o", hint = "{", alternates = listOf("{", "ö", "9")),
            KeySpec("p", hint = "}", alternates = listOf("}", "0"))
        ),
        listOf(
            KeySpec("a", hint = "@", alternates = listOf("@", "ä", "á", "à")),
            KeySpec("s", hint = "#", alternates = listOf("#", "ß", "$")),
            KeySpec("d", hint = "$", alternates = listOf("$")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")"))
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("z", hint = "*", alternates = listOf("*")),
            KeySpec("x", hint = "\"", alternates = listOf("\"")),
            KeySpec("c", hint = "'", alternates = listOf("'", "ç")),
            KeySpec("v", hint = ":", alternates = listOf(":")),
            KeySpec("b", hint = ";", alternates = listOf(";")),
            KeySpec("n", hint = "!", alternates = listOf("!", "ñ")),
            KeySpec("m", hint = "?", alternates = listOf("?")),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        listOf(
            KeySpec("?123", weight = 1.35f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 1f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 1f, type = KeyActionType.EMOJI_PANEL),
            KeySpec("English", output = " ", weight = 3.85f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-", "'")),
            KeySpec("↵", weight = 1.5f, type = KeyActionType.ENTER)
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
            KeySpec(" ", weight = 3.85f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD),
            KeySpec("↵", weight = 1.5f, type = KeyActionType.ENTER)
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
            KeySpec(" ", weight = 3.85f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 1f, type = KeyActionType.PERIOD),
            KeySpec("↵", weight = 1.5f, type = KeyActionType.ENTER)
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
                    locale.startsWith("de") && showGermanUmlauts -> germanRowsWithDirectUmlauts
                    locale.startsWith("de") -> germanPixelRows
                    else -> englishRows
                }
                if (showNumberRow) listOf(numberRow) + base else base
            }
            KeyboardLayoutPage.SYMBOLS -> symbolRows
            KeyboardLayoutPage.SYMBOLS_EXT -> symbolExtRows
        }
    }
}
