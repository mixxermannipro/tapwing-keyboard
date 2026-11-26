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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.ui.keyboard.title

@Composable
internal fun AppearanceScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val themeCards = listOf(
        Triple(
            ThemeMode.SLATE,
            if (de) "Schiefer" else "Slate",
            Triple(Color(0xFF1F2124), Color(0xFF33363B), Color(0xFF8AB4F8))
        ),
        Triple(
            ThemeMode.DYNAMIC,
            "Material You",
            Triple(scheme.surfaceVariant, scheme.surface, scheme.primary)
        ),
        Triple(
            ThemeMode.COBALT,
            if (de) "Kobaltblau" else "Cobalt Blue",
            Triple(Color(0xFF11151C), Color(0xFF232934), Color(0xFF8AB4F8))
        ),
        Triple(
            ThemeMode.EXPRESSIVE_LAVENDER,
            if (de) "Lavendel" else "Lavender",
            Triple(Color(0xFF1E1A26), Color(0xFF302A3C), Color(0xFFD0BCFF))
        ),
        Triple(
            ThemeMode.AMOLED_DARK,
            "AMOLED Dark",
            Triple(Color(0xFF000000), Color(0xFF191920), Color(0xFF9A82DB))
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Visual Gboard-inspired Theme Gallery
        item {
            Column {
                AppleSectionHeader(if (de) "Farbschema" else "Colors")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    themeCards.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (mode, label, palette) ->
                                ExpressiveThemeTile(
                                    title = label,
                                    boardColor = palette.first,
                                    keyColor = palette.second,
                                    accentColor = palette.third,
                                    selected = prefs.themeMode == mode,
                                    onClick = { onUpdatePrefs { it.copy(themeMode = mode) } },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Light / Dark / System
        item {
            Column {
                AppleSectionHeader(if (de) "Erscheinungsbild" else "Appearance")
                AppleGroupedSurface {
                    val darkOptions = listOf(
                        DropdownOption(DarkModePreference.SYSTEM, if (de) "Systemstandard" else "System auto"),
                        DropdownOption(DarkModePreference.LIGHT, if (de) "Hell" else "Light"),
                        DropdownOption(DarkModePreference.DARK, if (de) "Dunkel" else "Dark")
                    )
                    MinimalDropdownRow(
                        title = if (de) "Modus" else "Mode",
                        selectedValue = prefs.darkMode,
                        options = darkOptions,
                        onSelect = { mode -> onUpdatePrefs { it.copy(darkMode = mode) } }
                    )
                }
            }
        }

        // Key borders, Pill keys, Accessibility contrast, and Tapwing's signature Liquid Glass mode
        item {
            Column {
                AppleSectionHeader(if (de) "Tasten, Stil & Kontrast" else "Key style & contrast")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Tastenumrandung" else "Key borders",
                        checked = prefs.showKeyBorders
                    ) { v -> onUpdatePrefs { it.copy(showKeyBorders = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Abgerundete Pill-Tasten" else "Expressive pill keycaps",
                        checked = prefs.roundedExpressiveKeys
                    ) { v -> onUpdatePrefs { it.copy(roundedExpressiveKeys = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Glas-Modus (Durchscheinend)" else "Glass mode (Translucent)",
                        checked = prefs.appleModeEnabled
                    ) { v -> onUpdatePrefs { it.copy(appleModeEnabled = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Hoher Kontrast (WCAG AAA)" else "High-contrast keys (WCAG AAA)",
                        checked = prefs.highContrastKeys
                    ) { v -> onUpdatePrefs { it.copy(highContrastKeys = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Große Tastenbeschriftung" else "Large key labels",
                        checked = prefs.largeKeyLabels
                    ) { v -> onUpdatePrefs { it.copy(largeKeyLabels = v) } }
                }
                AppleSectionFooter(
                    if (de) "Der Glas-Modus aktiviert durchscheinende Tasten. Hoher Kontrast & große Beschriftung verbessern die Lesbarkeit bei Sonne und Sehschwäche."
                    else "Glass mode enables translucent glass keycaps. High contrast & large labels improve outdoor and low-vision readability."
                )
            }
        }
    }
}
