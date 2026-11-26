// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.TapwingPrefs

private const val ONLINE_PRIVACY_URL = "https://tapwing-keyboard.mixxermannipropvp.workers.dev/privacy"

@Composable
internal fun PrivacySettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit,
    onOpenLicenses: () -> Unit
) {
    val context = LocalContext.current
    val consentedList = prefs.consentedCloudProviders
        .mapNotNull { runCatching { AiProviderType.valueOf(it) }.getOrNull() }
        .filter { CloudProviderPolicy.isCloudProvider(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Schutz & Lernmodus" else "Protection & Learning")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Inkognito-Modus (kein Lernen, keine Zwischenablage)" else "Incognito mode (no learning, no clipboard)",
                        checked = prefs.incognitoMode
                    ) { v -> onUpdatePrefs { it.copy(incognitoMode = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Wortvorschläge lokal lernen" else "Learn word suggestions locally",
                        checked = prefs.learningEnabled
                    ) { v -> onUpdatePrefs { it.copy(learningEnabled = v) } }
                }
                AppleSectionFooter(
                    if (de) {
                        "Passwort-, Kreditkarten-, OTP-/TAN- und Als-Privat-markierte Felder (No Personalized Learning) werden automatisch ignoriert. Persönliche Daten, Zwischenablage, Wörterbücher und API-Schlüssel liegen verschlüsselt im entsperrten Gerätespeicher (CE-Storage)."
                    } else {
                        "Password, credit card, OTP/PIN, and No-Personalized-Learning fields are always ignored. Personal profile data, clipboard history, adaptive dictionaries, and API keys are stored only in user-unlocked Credential-Encrypted (CE) storage."
                    }
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Cloud-KI Einwilligungen" else "Cloud AI Consents")
                if (consentedList.isEmpty()) {
                    AppleGroupedSurface {
                        MinimalValueRow(
                            title = if (de) "Aktive Cloud-Freigaben" else "Active cloud permissions",
                            value = if (de) "Keine (100 % Offline)" else "None (100% offline)"
                        )
                    }
                } else {
                    AppleGroupedSurface {
                        consentedList.forEachIndexed { index, provider ->
                            if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                            val disclosure = CloudProviderPolicy.disclosureFor(provider)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = disclosure?.displayName ?: provider.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (de) "Cloud-Übertragung bei aktivem Abruf erlaubt" else "Cloud transmission consented on explicit use",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    onUpdatePrefs { current ->
                                        current.copy(consentedCloudProviders = current.consentedCloudProviders - provider.name)
                                    }
                                }) {
                                    Text(if (de) "Widerrufen" else "Revoke", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            title = if (de) "Alle Cloud-Einwilligungen widerrufen" else "Revoke all cloud consents",
                            textColor = MaterialTheme.colorScheme.error
                        ) {
                            onUpdatePrefs { it.copy(consentedCloudProviders = emptySet()) }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) {
                        "Cloud-Diktat und Cloud-Schreibhilfe bleiben gesperrt, bis du dem jeweiligen Anbieter ausdrücklich zustimmst. Ein Widerruf wirkt sofort."
                    } else {
                        "Cloud dictation and Cloud writing tools stay blocked until you explicitly consent per provider. Revocation takes effect immediately."
                    }
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Dokumentation" else "Documentation")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Datenschutzerklärung online öffnen" else "Open Privacy Policy online",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        context.openSettingsLink(ONLINE_PRIVACY_URL)
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Open-Source- & Modell-Lizenzen anzeigen" else "View Open-Source & Model Licenses",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenLicenses
                    )
                }
            }
        }
    }
}
