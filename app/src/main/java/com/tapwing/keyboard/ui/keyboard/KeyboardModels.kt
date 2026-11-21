package com.tapwing.keyboard.ui.keyboard

import com.tapwing.keyboard.data.KeyboardLocales

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
    CUSTOMIZE_BAR,
    WRITING_TOOLS,
    WRITING_ONBOARDING,
    VOICE_ONBOARDING,
    CLIPBOARD,
    EMOJI,
    RESIZE,
    TEXT_EDIT,
    QUICK_SEARCH,
    SNIPPETS
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
        bottomRow("Deutsch")
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

    private val swissGermanRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "%", alternates = listOf("%", "1")),
            KeySpec("w", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = listOf("|", "é", "è", "ê", "€", "3")),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("z", hint = "]", alternates = listOf("]", "6")),
            KeySpec("u", hint = "<", alternates = listOf("<", "ü", "ù", "ú", "7")),
            KeySpec("i", hint = ">", alternates = listOf(">", "î", "ï", "8")),
            KeySpec("o", hint = "{", alternates = listOf("{", "ö", "ô", "ò", "9")),
            KeySpec("p", hint = "}", alternates = listOf("}", "0"))
        ),
        listOf(
            KeySpec("a", hint = "@", alternates = listOf("@", "ä", "à", "â", "á")),
            KeySpec("s", hint = "#", alternates = listOf("#", "ß", "ś")),
            KeySpec("d", hint = "$", alternates = listOf("$", "€")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")"))
        ),
        germanPixelRows[2],
        bottomRow("Deutsch (CH)")
    )

    private fun qwertyRows(
        spaceLabel: String,
        currencyHint: String = "$",
        aAlts: List<String> = listOf("@", "ä", "á", "à", "â", "ã"),
        eAlts: List<String> = listOf("|", "é", "è", "ê", "ë", "€", "3"),
        iAlts: List<String> = listOf(">", "í", "ì", "î", "ï", "8"),
        oAlts: List<String> = listOf("{", "ö", "ó", "ò", "ô", "õ", "9"),
        uAlts: List<String> = listOf("<", "ü", "ú", "ù", "û", "7"),
        sAlts: List<String> = listOf("#", "ß", "ś", "ş", "š"),
        cAlts: List<String> = listOf("'", "ç", "ć"),
        nAlts: List<String> = listOf("!", "ñ", "ń"),
        zAlts: List<String> = listOf("*", "ż", "ź"),
        lAlts: List<String> = listOf(")", "ł"),
        gAlts: List<String> = listOf("&", "ğ")
    ): List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", hint = "%", alternates = listOf("%", "1")),
            KeySpec("w", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = eAlts),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("y", hint = "]", alternates = listOf("]", "6")),
            KeySpec("u", hint = "<", alternates = uAlts),
            KeySpec("i", hint = ">", alternates = iAlts),
            KeySpec("o", hint = "{", alternates = oAlts),
            KeySpec("p", hint = "}", alternates = listOf("}", "0"))
        ),
        listOf(
            KeySpec("a", hint = "@", alternates = aAlts),
            KeySpec("s", hint = "#", alternates = sAlts),
            KeySpec("d", hint = currencyHint, alternates = listOf(currencyHint, "$", "€", "£")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = gAlts),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = lAlts)
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("z", hint = "*", alternates = zAlts),
            KeySpec("x", hint = "\"", alternates = listOf("\"")),
            KeySpec("c", hint = "'", alternates = cAlts),
            KeySpec("v", hint = ":", alternates = listOf(":")),
            KeySpec("b", hint = ";", alternates = listOf(";")),
            KeySpec("n", hint = "!", alternates = nAlts),
            KeySpec("m", hint = "?", alternates = listOf("?")),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        bottomRow(spaceLabel)
    )

    private val frenchAzertyRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("a", hint = "%", alternates = listOf("%", "à", "â", "á", "1")),
            KeySpec("z", hint = "\\", alternates = listOf("\\", "2")),
            KeySpec("e", hint = "|", alternates = listOf("|", "é", "è", "ê", "ë", "€", "3")),
            KeySpec("r", hint = "=", alternates = listOf("=", "4")),
            KeySpec("t", hint = "[", alternates = listOf("[", "5")),
            KeySpec("y", hint = "]", alternates = listOf("]", "ÿ", "6")),
            KeySpec("u", hint = "<", alternates = listOf("<", "ù", "û", "ü", "7")),
            KeySpec("i", hint = ">", alternates = listOf(">", "î", "ï", "8")),
            KeySpec("o", hint = "{", alternates = listOf("{", "ô", "œ", "ö", "9")),
            KeySpec("p", hint = "}", alternates = listOf("}", "0"))
        ),
        listOf(
            KeySpec("q", hint = "@", alternates = listOf("@")),
            KeySpec("s", hint = "#", alternates = listOf("#")),
            KeySpec("d", hint = "€", alternates = listOf("€")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")")),
            KeySpec("m", hint = "?", alternates = listOf("?"))
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("w", hint = "*", alternates = listOf("*")),
            KeySpec("x", hint = "\"", alternates = listOf("\"")),
            KeySpec("c", hint = "'", alternates = listOf("'", "ç")),
            KeySpec("v", hint = ":", alternates = listOf(":")),
            KeySpec("b", hint = ";", alternates = listOf(";")),
            KeySpec("n", hint = "!", alternates = listOf("!", "ñ")),
            KeySpec("'", hint = "«", alternates = listOf("«", "»")),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        bottomRow("Français")
    )

    private val spanishRows: List<List<KeySpec>> = listOf(
        qwertyRows(
            spaceLabel = "Español",
            currencyHint = "€",
            aAlts = listOf("@", "á", "à", "ä"),
            eAlts = listOf("|", "é", "è", "€", "3"),
            iAlts = listOf(">", "í", "ï", "8"),
            oAlts = listOf("{", "ó", "ö", "9"),
            uAlts = listOf("<", "ú", "ü", "7"),
            nAlts = listOf("!", "ñ")
        )[0],
        listOf(
            KeySpec("a", hint = "@", alternates = listOf("@", "á", "à")),
            KeySpec("s", hint = "#", alternates = listOf("#")),
            KeySpec("d", hint = "€", alternates = listOf("€", "$")),
            KeySpec("f", hint = "_", alternates = listOf("_")),
            KeySpec("g", hint = "&", alternates = listOf("&")),
            KeySpec("h", hint = "-", alternates = listOf("-")),
            KeySpec("j", hint = "+", alternates = listOf("+")),
            KeySpec("k", hint = "(", alternates = listOf("(")),
            KeySpec("l", hint = ")", alternates = listOf(")")),
            KeySpec("ñ", alternates = listOf("n", "~"))
        ),
        listOf(
            KeySpec("⇧", weight = 1.45f, type = KeyActionType.SHIFT),
            KeySpec("z", hint = "*", alternates = listOf("*")),
            KeySpec("x", hint = "\"", alternates = listOf("\"")),
            KeySpec("c", hint = "'", alternates = listOf("'", "ç")),
            KeySpec("v", hint = ":", alternates = listOf(":")),
            KeySpec("b", hint = ";", alternates = listOf(";")),
            KeySpec("n", hint = "!", alternates = listOf("!", "¡", "ñ")),
            KeySpec("m", hint = "?", alternates = listOf("?", "¿")),
            KeySpec("⌫", weight = 1.45f, type = KeyActionType.BACKSPACE)
        ),
        bottomRow("Español")
    )

    private val turkishRows: List<List<KeySpec>> = qwertyRows(
        spaceLabel = "Türkçe",
        currencyHint = "₺",
        aAlts = listOf("@", "â"),
        eAlts = listOf("|", "€", "3"),
        iAlts = listOf(">", "ı", "İ", "î", "8"),
        oAlts = listOf("{", "ö", "ô", "9"),
        uAlts = listOf("<", "ü", "û", "7"),
        sAlts = listOf("#", "ş", "ß"),
        cAlts = listOf("'", "ç"),
        gAlts = listOf("&", "ğ")
    )

    private fun bottomRow(spaceLabel: String): List<KeySpec> = listOf(
        KeySpec("?123", weight = 1.42f, type = KeyActionType.SYMBOLS_TOGGLE),
        KeySpec(",", weight = 0.95f, type = KeyActionType.COMMA),
        KeySpec("☺", weight = 0.95f, type = KeyActionType.EMOJI_PANEL),
        KeySpec(spaceLabel, output = " ", weight = 4.18f, type = KeyActionType.SPACE),
        KeySpec(".", weight = 0.95f, type = KeyActionType.PERIOD, alternates = listOf("?", "!", ",", ":", ";", "-", "'")),
        KeySpec("↵", weight = 1.55f, type = KeyActionType.ENTER)
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
            KeySpec("ABC", weight = 1.42f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec(",", weight = 0.95f, type = KeyActionType.COMMA),
            KeySpec("☺", weight = 0.95f, type = KeyActionType.EMOJI_PANEL),
            KeySpec(" ", weight = 4.18f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 0.95f, type = KeyActionType.PERIOD),
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
            KeySpec("ABC", weight = 1.42f, type = KeyActionType.SYMBOLS_TOGGLE),
            KeySpec("<", weight = 0.95f),
            KeySpec(">", weight = 0.95f),
            KeySpec(" ", weight = 4.18f, type = KeyActionType.SPACE),
            KeySpec(".", weight = 0.95f, type = KeyActionType.PERIOD),
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
                val base = when (locale) {
                    "de_CH" -> swissGermanRows
                    "en_GB" -> qwertyRows(spaceLabel = "English (UK)", currencyHint = "£")
                    "fr" -> frenchAzertyRows
                    "es" -> spanishRows
                    "it" -> qwertyRows(
                        spaceLabel = "Italiano",
                        currencyHint = "€",
                        aAlts = listOf("@", "à", "á"),
                        eAlts = listOf("|", "è", "é", "€", "3"),
                        iAlts = listOf(">", "ì", "í", "8"),
                        oAlts = listOf("{", "ò", "ó", "9"),
                        uAlts = listOf("<", "ù", "ú", "7")
                    )
                    "pt" -> qwertyRows(
                        spaceLabel = "Português",
                        currencyHint = "€",
                        aAlts = listOf("@", "á", "à", "â", "ã"),
                        eAlts = listOf("|", "é", "ê", "€", "3"),
                        oAlts = listOf("{", "ó", "ô", "õ", "9"),
                        cAlts = listOf("'", "ç")
                    )
                    "nl" -> qwertyRows(
                        spaceLabel = "Nederlands",
                        currencyHint = "€",
                        eAlts = listOf("|", "é", "ë", "è", "ê", "€", "3"),
                        iAlts = listOf(">", "ï", "í", "8"),
                        oAlts = listOf("{", "ö", "ó", "9"),
                        uAlts = listOf("<", "ü", "ú", "7")
                    )
                    "pl" -> qwertyRows(
                        spaceLabel = "Polski",
                        currencyHint = "zł",
                        aAlts = listOf("@", "ą", "á"),
                        eAlts = listOf("|", "ę", "é", "€", "3"),
                        oAlts = listOf("{", "ó", "9"),
                        sAlts = listOf("#", "ś"),
                        cAlts = listOf("'", "ć"),
                        nAlts = listOf("!", "ń"),
                        zAlts = listOf("*", "ż", "ź"),
                        lAlts = listOf(")", "ł")
                    )
                    "tr" -> turkishRows
                    else -> {
                        val baseLang = KeyboardLocales.baseLang(locale)
                        when {
                            baseLang == "de" && showGermanUmlauts -> germanRowsWithDirectUmlauts
                            baseLang == "de" -> germanPixelRows
                            else -> qwertyRows(spaceLabel = "English", currencyHint = "$")
                        }
                    }
                }
                val rowsWithTopHints = if (showNumberRow) {
                    base
                } else {
                    val topRow = base.firstOrNull().orEmpty().mapIndexed { idx, spec ->
                        if (idx < 10) {
                            val digitHint = ((idx + 1) % 10).toString()
                            val mergedAlts = (listOf(digitHint) + spec.alternates).distinct()
                            spec.copy(hint = digitHint, alternates = mergedAlts)
                        } else {
                            spec
                        }
                    }
                    listOf(topRow) + base.drop(1)
                }
                if (showNumberRow) listOf(numberRow) + rowsWithTopHints else rowsWithTopHints
            }
            KeyboardLayoutPage.SYMBOLS -> symbolRows
            KeyboardLayoutPage.SYMBOLS_EXT -> symbolExtRows
        }
    }
}
