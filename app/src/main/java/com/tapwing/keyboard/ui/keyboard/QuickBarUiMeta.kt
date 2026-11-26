package com.tapwing.keyboard.ui.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs

fun QuickBarAction.icon(): ImageVector = when (this) {
    QuickBarAction.WRITING_TOOLS -> Icons.Rounded.AutoAwesome
    QuickBarAction.CLIPBOARD -> Icons.Rounded.ContentPaste
    QuickBarAction.TEXT_EDIT -> Icons.Rounded.OpenWith
    QuickBarAction.SCAN_TEXT -> Icons.Rounded.DocumentScanner
    QuickBarAction.SETTINGS -> Icons.Rounded.Settings
    QuickBarAction.RESIZE -> Icons.Rounded.Height
    QuickBarAction.EMOJI -> Icons.Rounded.EmojiEmotions
    QuickBarAction.ONE_HANDED -> Icons.Rounded.PhoneAndroid
    QuickBarAction.INCOGNITO -> Icons.Rounded.VisibilityOff
    QuickBarAction.QUICK_SEARCH -> Icons.Rounded.Search
    QuickBarAction.APPLE_MODE -> Icons.Rounded.Smartphone
    QuickBarAction.LANGUAGE -> Icons.Rounded.Language
    QuickBarAction.UNDO -> Icons.AutoMirrored.Rounded.Undo
    QuickBarAction.SNIPPETS -> Icons.Rounded.Description
}

fun QuickBarAction.shortLabel(de: Boolean, prefs: TapwingPrefs): String = when (this) {
    QuickBarAction.WRITING_TOOLS -> if (de) "Schreibhilfe" else "Writing"
    QuickBarAction.CLIPBOARD -> if (de) "Ablage" else "Clipboard"
    QuickBarAction.TEXT_EDIT -> if (de) "Text-Edit" else "Text edit"
    QuickBarAction.SCAN_TEXT -> if (de) "Scannen" else "Scan OCR"
    QuickBarAction.SETTINGS -> if (de) "Optionen" else "Settings"
    QuickBarAction.RESIZE -> if (de) "Größe" else "Resize"
    QuickBarAction.EMOJI -> "Emoji"
    QuickBarAction.ONE_HANDED -> when (prefs.oneHandedMode) {
        OneHandedMode.OFF -> if (de) "Einhändig" else "One-hand"
        OneHandedMode.RIGHT -> if (de) "Rechts" else "Right"
        OneHandedMode.LEFT -> if (de) "Links" else "Left"
    }
    QuickBarAction.INCOGNITO -> if (de) "Inkognito" else "Incognito"
    QuickBarAction.QUICK_SEARCH -> if (de) "Websuche" else "Search"
    QuickBarAction.APPLE_MODE -> if (de) "Glas" else "Glass"
    QuickBarAction.LANGUAGE -> if (de) "Sprache" else "Language"
    QuickBarAction.UNDO -> if (de) "Zurück" else "Undo"
    QuickBarAction.SNIPPETS -> if (de) "Bausteine" else "Snippets"
}

fun QuickBarAction.title(de: Boolean): String = when (this) {
    QuickBarAction.WRITING_TOOLS -> if (de) "KI-Schreibhilfe" else "AI Writing Tools"
    QuickBarAction.CLIPBOARD -> if (de) "Zwischenablage" else "Clipboard"
    QuickBarAction.TEXT_EDIT -> if (de) "Cursor & Text-Edit" else "Cursor & Text Edit"
    QuickBarAction.SCAN_TEXT -> if (de) "Text scannen (OCR)" else "Scan Text (OCR)"
    QuickBarAction.SETTINGS -> if (de) "Einstellungen" else "Settings"
    QuickBarAction.RESIZE -> if (de) "Tastaturgröße ändern" else "Resize keyboard"
    QuickBarAction.EMOJI -> if (de) "Emoji-Auswahl" else "Emoji picker"
    QuickBarAction.ONE_HANDED -> if (de) "Einhandmodus" else "One-handed mode"
    QuickBarAction.INCOGNITO -> if (de) "Inkognito-Modus" else "Incognito mode"
    QuickBarAction.QUICK_SEARCH -> if (de) "Web-Schnellsuche" else "Quick web search"
    QuickBarAction.APPLE_MODE -> if (de) "Glas-Modus (Durchscheinend)" else "Glass Mode (Translucent)"
    QuickBarAction.LANGUAGE -> if (de) "Sprache wechseln (DE/EN)" else "Switch language (DE/EN)"
    QuickBarAction.UNDO -> if (de) "Letzte Änderung rückgängig" else "Undo last change"
    QuickBarAction.SNIPPETS -> if (de) "Textbausteine mit Platzhaltern" else "Multi-line text snippets"
}

fun QuickBarAction.description(de: Boolean): String = when (this) {
    QuickBarAction.WRITING_TOOLS -> if (de) "Texte prüfen, umschreiben & Tonfall anpassen" else "Proofread and rewrite text"
    QuickBarAction.CLIPBOARD -> if (de) "Kopierte Texte & angeheftete Clips" else "Copied text history & pins"
    QuickBarAction.TEXT_EDIT -> if (de) "Steuerkreuz, Markieren, Kopieren & Einfügen" else "D-pad cursor, select, copy & paste"
    QuickBarAction.SCAN_TEXT -> if (de) "Text per Kamera direkt einfügen" else "Capture text with camera OCR"
    QuickBarAction.SETTINGS -> if (de) "Tapwing-Optionen direkt öffnen" else "Open Tapwing settings"
    QuickBarAction.RESIZE -> if (de) "Höhe & unteren Abstand live einstellen" else "Adjust height and bottom offset live"
    QuickBarAction.EMOJI -> if (de) "Emoji-Übersicht öffnen" else "Open emoji keyboard"
    QuickBarAction.ONE_HANDED -> if (de) "Zwischen Aus, Rechts und Links wechseln" else "Cycle off, right, and left"
    QuickBarAction.INCOGNITO -> if (de) "Lernen & Zwischenablage pausieren" else "Pause learning and clipboard history"
    QuickBarAction.QUICK_SEARCH -> if (de) "Im Browser suchen oder Suchlink einfügen" else "Search web or insert link"
    QuickBarAction.APPLE_MODE -> if (de) "Glas-Tasten umschalten" else "Toggle Glass keyboard"
    QuickBarAction.LANGUAGE -> if (de) "Zwischen Deutsch & Englisch umschalten" else "Toggle German & English layout"
    QuickBarAction.UNDO -> if (de) "Letzte Autokorrektur oder KI-Edit zurücknehmen" else "Revert last autocorrect or AI edit"
    QuickBarAction.SNIPPETS -> if (de) "Mehrzeilige Vorlagen mit {DATUM}, {NAME} & Co. einfügen" else "Insert multi-line templates with dynamic placeholders"
}

fun QuickBarAction.isHighlighted(prefs: TapwingPrefs): Boolean = when (this) {
    QuickBarAction.WRITING_TOOLS -> true
    QuickBarAction.ONE_HANDED -> prefs.oneHandedMode != OneHandedMode.OFF
    QuickBarAction.INCOGNITO -> prefs.incognitoMode
    QuickBarAction.APPLE_MODE -> prefs.appleModeEnabled
    else -> false
}
