// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.BuildConfig
import com.tapwing.keyboard.ai.OpenSourceLicensesCatalog
import com.tapwing.keyboard.ai.OpenSourceNotice
import com.tapwing.keyboard.data.TypingStats

@Composable
internal fun AboutSettingsScreen(
    de: Boolean,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Text("Tapwing", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} • Source Available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Column {
                AppleSectionHeader("Details")
                AppleGroupedSurface {
                    MinimalValueRow(if (de) "Paket-ID" else "Package", "com.tapwing.keyboard")
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(if (de) "Verarbeitung" else "Processing", if (de) "Lokal auf Gerät" else "On-device")
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Lizenz" else "License",
                        if (de) "Source Available (Apache-2.0-Basis)" else "Source Available (Apache-2.0 baseline)"
                    )
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Rechtliches & Datenschutz" else "Legal & Privacy")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Datenschutz & Cloud-Einwilligung" else "Privacy & Cloud Consent",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenPrivacy
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Open-Source- & Modell-Lizenzen" else "Open-source & Model Licenses",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenLicenses
                    )
                }
            }
        }

        item {
            Column {
                AppleSectionHeader("System")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Tastatur in System aktivieren" else "Enable in system settings",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenImeSystemSettings
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Eingabemethode wählen" else "Choose input method",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onShowImePicker
                    )
                }
            }
        }
    }
}

@Composable
internal fun LicensesScreen(de: Boolean) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            LicenseNoticeSection(
                title = if (de) "Eingebundene Bibliotheken" else "Bundled Libraries",
                notices = OpenSourceLicensesCatalog.libraries,
                onOpenUrl = { context.openSettingsLink(it) }
            )
        }
        item {
            LicenseNoticeSection(
                title = if (de) "Lokal nachladbare KI- & Sprachmodelle" else "Downloadable On-Device Models",
                notices = OpenSourceLicensesCatalog.downloadableModels,
                onOpenUrl = { context.openSettingsLink(it) }
            )
        }
        item {
            LicenseNoticeSection(
                title = if (de) "Offline-Wörterbuchpakete" else "Offline Dictionary Packs",
                notices = OpenSourceLicensesCatalog.dictionaryPacks,
                onOpenUrl = { context.openSettingsLink(it) }
            )
        }
    }
}

@Composable
private fun LicenseNoticeSection(
    title: String,
    notices: List<OpenSourceNotice>,
    onOpenUrl: (String) -> Unit
) {
    Column {
        AppleSectionHeader(title)
        AppleGroupedSurface {
            notices.forEachIndexed { idx, notice ->
                if (idx > 0) AppleHairlineDivider(startIndent = 16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(notice.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${notice.license} · ${notice.copyrightOrAuthor}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (notice.note.isNotBlank()) {
                        Text(
                            text = notice.note,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (notice.url.isNotBlank()) {
                        MinimalActionRow(
                            title = notice.url,
                            textColor = MaterialTheme.colorScheme.primary,
                            onClick = { onOpenUrl(notice.url) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypingStatsScreen(
    stats: TypingStats,
    de: Boolean,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Geschwindigkeit & Leistung" else "Speed & Productivity")
                AppleGroupedSurface {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBadge(if (de) "Tempo" else "Speed", if (stats.estimatedWpm > 0) "${stats.estimatedWpm} WPM" else "—")
                        StatBadge(if (de) "Wörter" else "Words", stats.wordsTyped.toString())
                        StatBadge(if (de) "Gespart" else "Saved", "${stats.charactersSaved}")
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Getippte Zeichen" else "Characters typed",
                        stats.charactersTyped.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Angenommene Vorschläge" else "Suggestions accepted",
                        stats.suggestionsAccepted.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Autokorrekturen" else "Autocorrections applied",
                        stats.autocorrectionsApplied.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Wisch-Wörter (Glide)" else "Swipe-typed words",
                        stats.swipesDecoded.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Spracheingaben" else "Voice dictations",
                        stats.voiceDictations.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Textbausteine eingefügt" else "Snippets inserted",
                        stats.snippetsInserted.toString()
                    )
                }
                AppleSectionFooter(
                    if (de) "100% lokal auf deinem Gerät gespeichert. Im Inkognito-Modus pausiert die Zählung automatisch."
                    else "Stored 100% locally on your device. Automatically pauses in Incognito mode."
                )
            }
        }

        item {
            AppleGroupedSurface {
                MinimalActionRow(
                    title = if (de) "Statistik zurücksetzen" else "Reset statistics",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = onReset
                )
            }
        }
    }
}
