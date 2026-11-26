// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.clipboard.SENSITIVE_CLIP_EXTRA
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import kotlinx.coroutines.launch

@Composable
internal fun ApiKeysVaultScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    secureKeyStorage: SecureKeyStorage,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vaultRevision by remember { mutableIntStateOf(0) }
    var globalNotice by remember { mutableStateOf<String?>(null) }
    var pendingTestConsent by remember { mutableStateOf<Pair<AiProviderType, () -> Unit>?>(null) }

    pendingTestConsent?.let { (prov, onConfirmed) ->
        CloudProviderPolicy.disclosureFor(prov)?.let { disclosure ->
            CloudConsentDialog(
                disclosure = disclosure,
                de = de,
                onOpenPrivacyUrl = { url -> context.openSettingsLink(url) },
                onConfirm = {
                    pendingTestConsent = null
                    onUpdatePrefs { it.copy(consentedCloudProviders = it.consentedCloudProviders + prov.name) }
                    onConfirmed()
                },
                onDismiss = { pendingTestConsent = null }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (globalNotice != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = globalNotice!!,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        item {
            AppleSectionFooter(
                if (de) {
                    "Alle Schlüssel liegen Hardware-verschlüsselt im Android Keystore. Du kannst Anbieter beliebig mischen (z. B. Gemini fürs Diktat + Groq/Grok für Text) und Schlüssel jederzeit anzeigen, kopieren oder testen."
                } else {
                    "All API keys are encrypted via hardware Android Keystore. Mix providers freely across dictation, cleanup, and writing tools, or view, copy, and test keys anytime."
                }
            )
        }

        SecureKeyStorage.CLOUD_PROVIDERS.forEach { provider ->
            item(key = "${provider.name}_$vaultRevision") {
                var savedKey by remember(vaultRevision) { mutableStateOf(secureKeyStorage.readKey(provider)) }
                var keyInput by remember { mutableStateOf("") }
                var revealKey by remember { mutableStateOf(false) }
                var testing by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<String?>(null) }

                val isFreeProvider = provider in setOf(
                    AiProviderType.GEMINI_API,
                    AiProviderType.GROQ_API,
                    AiProviderType.OPENROUTER_API
                )
                val signupUrl = AiModelCatalog.signupUrl(provider)

                Column {
                    AppleSectionHeader(
                        text = AiModelCatalog.displayName(provider, de),
                        trailingActionText = if (signupUrl.isNotEmpty()) (if (de) "Key holen ↗" else "Get key ↗") else null,
                        onTrailingAction = if (signupUrl.isNotEmpty()) {
                            { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl))) } }
                        } else null
                    )
                    AppleGroupedSurface {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AiModelReference(provider)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (savedKey.isNotBlank()) {
                                        if (de) "Gespeichert ✓" else "Saved ✓"
                                    } else {
                                        if (de) "Nicht hinterlegt" else "Not configured"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (savedKey.isNotBlank()) Color(0xFF34C759)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isFreeProvider) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Free Tier",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = AiModelCatalog.freeTierSummary(provider, de),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (savedKey.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (revealKey) savedKey else secureKeyStorage.maskedPreview(provider),
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { revealKey = !revealKey },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (de) "Anzeigen" else "Reveal",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clip = ClipData.newPlainText("Tapwing API Key", savedKey).apply {
                                                    description.extras = PersistableBundle().apply {
                                                        putBoolean(SENSITIVE_CLIP_EXTRA, true)
                                                    }
                                                }
                                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                                                globalNotice = if (de) {
                                                    "${AiModelCatalog.displayName(provider, true)}-Schlüssel kopiert"
                                                } else {
                                                    "Copied ${AiModelCatalog.displayName(provider, false)} key"
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = if (de) "Kopieren" else "Copy",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                secureKeyStorage.deleteKey(provider)
                                                savedKey = ""
                                                revealKey = false
                                                testResult = if (de) "Schlüssel entfernt." else "Key removed."
                                                vaultRevision++
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = if (de) "Löschen" else "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (provider == AiProviderType.OPENAI_COMPATIBLE) {
                                AppleInlineField(
                                    value = prefs.aiCustomEndpoint,
                                    onValueChange = { ep -> onUpdatePrefs { it.copy(aiCustomEndpoint = ep) } },
                                    placeholder = "https://api.openai.com/v1/chat/completions"
                                )
                            }

                            AppleInlineField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                placeholder = if (savedKey.isNotBlank()) {
                                    if (de) "Neuen Schlüssel einfügen zum Ersetzen" else "Paste new key to replace"
                                } else {
                                    if (de) "API-Schlüssel einfügen" else "Paste API key"
                                },
                                isPassword = true
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (!SecureKeyStorage.isValidApiKey(keyInput)) {
                                            testResult = if (de) "Ungültige Zeichen oder Leerzeichen im Schlüssel."
                                            else "Invalid characters or spaces in key."
                                        } else {
                                            secureKeyStorage.saveKey(provider, keyInput)
                                            savedKey = secureKeyStorage.readKey(provider)
                                            keyInput = ""
                                            testResult = if (de) "Sicher im Tresor gespeichert." else "Saved securely in vault."
                                            vaultRevision++
                                        }
                                    },
                                    enabled = keyInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (de) "Speichern" else "Save", fontSize = 13.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val keyToTest = savedKey
                                        if (keyToTest.isBlank()) return@FilledTonalButton
                                        val runConnectionTest = {
                                            testing = true
                                            testResult = null
                                            scope.launch {
                                                val modelToTest = when (provider) {
                                                    prefs.aiProvider -> prefs.aiModel
                                                    prefs.cleanupProvider -> prefs.cleanupModel
                                                    else -> AiModelCatalog.defaultTextModelFor(provider)
                                                }.ifBlank { AiModelCatalog.defaultTextModelFor(provider) }
                                                val res = CloudAiClient.testConnection(
                                                    provider = provider,
                                                    apiKey = keyToTest,
                                                    model = modelToTest,
                                                    customEndpoint = prefs.aiCustomEndpoint,
                                                    locale = prefs.activeLocale
                                                )
                                                testing = false
                                                testResult = when (res) {
                                                    is CloudAiResult.Success -> "✓ ${res.providerLabel}: ${res.text}"
                                                    is CloudAiResult.Failure -> "✗ ${res.message}"
                                                }
                                            }
                                        }
                                        if (CloudProviderPolicy.hasConsent(prefs.consentedCloudProviders, provider)) {
                                            runConnectionTest()
                                        } else {
                                            pendingTestConsent = provider to { runConnectionTest() }
                                        }
                                    },
                                    enabled = savedKey.isNotBlank() && !testing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(if (de) "Testen" else "Test", fontSize = 13.sp)
                                    }
                                }
                            }

                            if (testResult != null) {
                                Text(
                                    text = testResult!!,
                                    fontSize = 12.sp,
                                    color = if (testResult!!.startsWith("✗")) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
