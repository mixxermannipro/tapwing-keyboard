// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.SplitKeyboardMode
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun PreferencesScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onOpenQuickBar: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Gboard Preferences → Layout section
        item {
            Column {
                AppleSectionHeader(
                    text = if (de) "Layout & Größe" else "Layout",
                    trailingActionText = if (de) "Zurücksetzen" else "Reset",
                    onTrailingAction = {
                        onUpdatePrefs { it.copy(keyboardScalePercent = 100, keyboardBottomLiftDp = 0) }
                    }
                )
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Tastaturhöhe" else "Keyboard height", fontSize = 15.sp)
                            Text("${prefs.keyboardScalePercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.keyboardScalePercent.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(keyboardScalePercent = v.roundToInt()) } },
                            valueRange = 75f..135f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Abstand unten" else "Bottom offset", fontSize = 15.sp)
                            Text("${prefs.keyboardBottomLiftDp} dp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.keyboardBottomLiftDp.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(keyboardBottomLiftDp = v.roundToInt()) } },
                            valueRange = 0f..48f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Unten rechts immer neue Zeile" else "Always insert newline on bottom-right",
                        checked = prefs.enterKeyAlwaysNewline
                    ) { v -> onUpdatePrefs { it.copy(enterKeyAlwaysNewline = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalNavRow(
                        icon = Icons.Rounded.DashboardCustomize,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        title = if (de) "Schnellleiste anpassen" else "Customize Quick Bar",
                        value = "${prefs.quickBarActions.size}",
                        onClick = onOpenQuickBar
                    )
                }
                AppleSectionFooter(
                    if (de) "Aus (Empfohlen): Passt die Enter-Taste automatisch an (Suchen, Go oder Zeilenumbruch)."
                    else "Off (Recommended): Adapts the bottom-right key automatically (Search, Go, or newline)."
                )
            }
        }

        // Gboard Preferences → Key press section
        item {
            Column {
                AppleSectionHeader(if (de) "Tastendruck" else "Key press")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Haptisches Feedback bei Tastendruck" else "Haptic feedback on keypress",
                        checked = prefs.hapticFeedback
                    ) { v -> onUpdatePrefs { it.copy(hapticFeedback = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Ton bei Tastendruck" else "Sound on keypress",
                        checked = prefs.soundOnKeypress
                    ) { v -> onUpdatePrefs { it.copy(soundOnKeypress = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Verzögerung bei Gedrückthalten" else "Key long press delay", fontSize = 15.sp)
                            Text("${prefs.longPressDelayMs} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.longPressDelayMs.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(longPressDelayMs = v.roundToInt()) } },
                            valueRange = 150f..500f
                        )
                    }
                }
            }
        }

        // Ergonomics: One-handed & Split keyboard dropdowns
        item {
            Column {
                AppleSectionHeader(if (de) "Ergonomie & Modus" else "Ergonomics & layout modes")
                AppleGroupedSurface {
                    val oneHandedOptions = OneHandedMode.entries.map { mode ->
                        val label = when (mode) {
                            OneHandedMode.OFF -> if (de) "Aus" else "Off"
                            OneHandedMode.RIGHT -> if (de) "Rechtshändig" else "Right-handed"
                            OneHandedMode.LEFT -> if (de) "Linkshändig" else "Left-handed"
                        }
                        DropdownOption(mode, label)
                    }
                    MinimalDropdownRow(
                        title = if (de) "Einhandmodus" else "One-handed mode",
                        selectedValue = prefs.oneHandedMode,
                        options = oneHandedOptions,
                        onSelect = { mode -> onUpdatePrefs { it.copy(oneHandedMode = mode) } }
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    val splitOptions = SplitKeyboardMode.entries.map { mode ->
                        val (label, sub) = when (mode) {
                            SplitKeyboardMode.OFF -> (if (de) "Aus" else "Off") to null
                            SplitKeyboardMode.AUTO -> (if (de) "Automatisch" else "Auto") to
                                (if (de) "Tablets & Foldables ab 600dp" else "Tablets & foldables ≥ 600dp")
                            SplitKeyboardMode.ALWAYS -> (if (de) "Immer geteilt" else "Always split") to null
                        }
                        DropdownOption(mode, label, sub)
                    }
                    MinimalDropdownRow(
                        title = if (de) "Geteilte Tastatur" else "Split keyboard",
                        selectedValue = prefs.splitKeyboardMode,
                        options = splitOptions,
                        onSelect = { mode -> onUpdatePrefs { it.copy(splitKeyboardMode = mode) } }
                    )
                }
                AppleSectionFooter(
                    if (de) "Teilt die Tastenreihen ergonomisch für Daumen auf Tablets, Foldables oder im Querformat."
                    else "Splits key rows ergonomically for thumbs on tablets, foldables, or landscape screens."
                )
            }
        }

        // Accessibility (Barrierefreiheit)
        item {
            Column {
                AppleSectionHeader(if (de) "Barrierefreiheit" else "Accessibility")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Große Tastenbeschriftung" else "Large key labels",
                        checked = prefs.largeKeyLabels
                    ) { v -> onUpdatePrefs { it.copy(largeKeyLabels = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Hoher Kontrast (WCAG AAA)" else "High-contrast keys (WCAG AAA)",
                        checked = prefs.highContrastKeys
                    ) { v -> onUpdatePrefs { it.copy(highContrastKeys = v) } }
                }
                AppleSectionFooter(
                    if (de) "Verstärkt Tastenkontraste, Umrandungen und Schriftgrößen. TalkBack erkennt alle Tasten, Menüs und KI-Chips."
                    else "Boosts key contrast, borders, and label size. Full TalkBack semantics are enabled across keys, menus, and AI chips."
                )
            }
        }
    }
}
@Composable
internal fun LanguagesScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Aktive Sprache & Menü" else "Active language & UI")
                AppleGroupedSurface {
                    val activeCandidates = KeyboardLocales.allTypingLocales.filter { it.code in prefs.enabledLocales }
                        .ifEmpty { listOf(KeyboardLocales.allTypingLocales.first()) }
                    MinimalDropdownRow(
                        title = if (de) "Aktive Tastatur-Sprache" else "Active keyboard language",
                        selectedValue = prefs.activeLocale,
                        options = activeCandidates.map { spec ->
                            DropdownOption(
                                value = spec.code,
                                label = spec.nativeLabel,
                                subtitle = spec.layoutLabel
                            )
                        },
                        onSelect = { code -> onUpdatePrefs { it.copy(activeLocale = code) } }
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    val appUiOptions = KeyboardLocales.appUiLanguages.map { lang ->
                        val label = if (lang.code == "system") {
                            if (de) "System (${KeyboardLocales.systemLanguageDisplayName()})"
                            else "System (${KeyboardLocales.systemLanguageDisplayName()})"
                        } else {
                            lang.nativeLabel
                        }
                        DropdownOption(lang.code, label)
                    }
                    MinimalDropdownRow(
                        title = if (de) "App-Sprache (Menü)" else "App UI language",
                        selectedValue = prefs.appUiLocale,
                        options = appUiOptions,
                        onSelect = { code -> onUpdatePrefs { it.copy(appUiLocale = code) } }
                    )
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Aktivierte Sprachen & Länder" else "Enabled languages & countries")
                AppleGroupedSurface {
                    KeyboardLocales.allTypingLocales.forEachIndexed { idx, spec ->
                        val enabled = spec.code in prefs.enabledLocales
                        MinimalToggleRow(
                            title = "${spec.nativeLabel} · ${spec.layoutLabel}",
                            checked = enabled
                        ) { checked ->
                            onUpdatePrefs { current ->
                                val next = if (checked) {
                                    (current.enabledLocales + spec.code).distinct()
                                } else {
                                    val filtered = current.enabledLocales.filterNot { it == spec.code }
                                    if (filtered.isEmpty()) current.enabledLocales else filtered
                                }
                                val nextActive = if (current.activeLocale in next) current.activeLocale else next.first()
                                current.copy(enabledLocales = next, activeLocale = nextActive)
                            }
                        }
                        if (idx < KeyboardLocales.allTypingLocales.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
                AppleSectionFooter(
                    if (de) "Die Globus-Taste und Leertaste (gedrückt halten) wechseln zwischen deinen aktivierten Sprachen."
                    else "The globe key and spacebar long-press cycle through your enabled languages."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Tastenbelegung & Mehrsprachigkeit" else "Layout & Multilingual")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Mehrsprachige Vorschläge" else "Multilingual predictions",
                        checked = prefs.multilingualPredictions
                    ) { v -> onUpdatePrefs { it.copy(multilingualPredictions = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Zahlenreihe anzeigen" else "Number row",
                        checked = prefs.showNumberRow
                    ) { v -> onUpdatePrefs { it.copy(showNumberRow = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Direkte Umlaut-Tasten (Ä, Ö, Ü)" else "Direct German umlauts",
                        checked = prefs.showGermanUmlautKeys
                    ) { v -> onUpdatePrefs { it.copy(showGermanUmlautKeys = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Sprache auf Leertaste" else "Language label on spacebar",
                        checked = prefs.showSpacebarLanguageLabel
                    ) { v -> onUpdatePrefs { it.copy(showSpacebarLanguageLabel = v) } }
                }
                AppleSectionFooter(
                    if (de) "Mehrsprachige Vorschläge erkennen Wörter aus deinen anderen aktivierten Sprachen ohne manuelles Umschalten."
                    else "Multilingual predictions recognize words from your other enabled languages without manual switching."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader("System")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Android Tastatur-Einstellungen" else "Android keyboard settings",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenImeSystemSettings
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Standard-Tastatur wählen" else "Choose input method",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onShowImePicker
                    )
                }
            }
        }
    }
}
@Composable
internal fun EmojiSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Größe" else "Size")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (de) "Emoji-Größe" else "Emoji size", fontSize = 15.sp)
                            Text("${prefs.emojiScale} sp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Text(
                            text = "😀  🔥  ✨  🙏  🚀",
                            fontSize = prefs.emojiScale.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Slider(
                            value = prefs.emojiScale.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(emojiScale = v.roundToInt()) } },
                            valueRange = 20f..34f
                        )
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                MinimalToggleRow(
                    title = if (de) "Emoji-Vorschläge beim Tippen" else "Emoji suggestions while typing",
                    checked = prefs.candidateEmojiSuggestions
                ) { v -> onUpdatePrefs { it.copy(candidateEmojiSuggestions = v) } }
            }
        }
    }
}
