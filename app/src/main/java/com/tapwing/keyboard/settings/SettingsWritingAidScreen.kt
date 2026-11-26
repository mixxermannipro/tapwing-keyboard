// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.ai.GeminiNanoClient
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.NanoAvailability
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.launch

private enum class WritingAidMode {
    RULES,
    LOCAL_LLM,
    CLOUD
}
@Composable
internal fun WritingToolsSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    secureKeyStorage: SecureKeyStorage,
    modelManager: LocalModelManager,
    onOpenApiKeys: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by modelManager.states.collectAsState()

    var pendingImportSpec by remember { mutableStateOf<LocalModelSpec?>(null) }
    val importLlmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSpec = pendingImportSpec
        pendingImportSpec = null
        if (uri != null && targetSpec != null) {
            scope.launch {
                val ok = modelManager.importModelFromUri(targetSpec, uri)
                if (ok) {
                    onUpdatePrefs {
                        it.copy(
                            selectedLlmModelId = targetSpec.id,
                            aiProvider = AiProviderType.LOCAL_LLM,
                            aiModel = targetSpec.id,
                            cleanupProvider = AiProviderType.LOCAL_LLM,
                            cleanupModel = targetSpec.id
                        )
                    }
                }
            }
        }
    }

    var nanoStatus by remember { mutableStateOf<NanoAvailability?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var moreModelsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    val savedCloudCount = remember(prefs) { secureKeyStorage.configuredCloudProviders().size }
    val bestInstalledLlm = remember(modelStates, prefs.selectedLlmModelId) {
        modelManager.bestInstalledLlmModel(prefs.selectedLlmModelId)
    }
    val activeMode = when (prefs.aiProvider) {
        AiProviderType.LOCAL_LLM -> WritingAidMode.LOCAL_LLM
        AiProviderType.LOCAL_RULES, AiProviderType.GEMINI_NANO -> WritingAidMode.RULES
        else -> WritingAidMode.CLOUD
    }
    val fastLlmModels = remember { LocalModelCatalog.llmModels.filter { !it.isFlagshipTier } }
    val flagshipLlmModels = remember { LocalModelCatalog.llmModels.filter { it.isFlagshipTier } }
    val cloudProviders = remember {
        listOf(
            AiProviderType.GEMINI_API,
            AiProviderType.GROQ_API,
            AiProviderType.OPENROUTER_API,
            AiProviderType.XAI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )
    }

    LaunchedEffect(activeMode, prefs.activeLocale) {
        if (activeMode == WritingAidMode.RULES) {
            nanoStatus = GeminiNanoClient.checkAvailability(context, prefs.activeLocale)
        }
    }

    var pendingConsentAction by remember { mutableStateOf<Pair<AiProviderType, () -> Unit>?>(null) }

    pendingConsentAction?.let { (prov, onConfirmAction) ->
        CloudProviderPolicy.disclosureFor(prov)?.let { disclosure ->
            CloudConsentDialog(
                disclosure = disclosure,
                de = de,
                onOpenPrivacyUrl = { url -> context.openSettingsLink(url) },
                onConfirm = {
                    pendingConsentAction = null
                    onConfirmAction()
                },
                onDismiss = { pendingConsentAction = null }
            )
        }
    }

    fun selectMode(mode: WritingAidMode) {
        when (mode) {
            WritingAidMode.RULES -> onUpdatePrefs {
                it.copy(
                    aiProvider = AiProviderType.LOCAL_RULES,
                    aiModel = "",
                    cleanupProvider = AiProviderType.LOCAL_RULES,
                    cleanupModel = ""
                )
            }
            WritingAidMode.LOCAL_LLM -> {
                val llmId = bestInstalledLlm?.first?.id ?: prefs.selectedLlmModelId
                onUpdatePrefs {
                    it.copy(
                        selectedLlmModelId = llmId,
                        aiProvider = AiProviderType.LOCAL_LLM,
                        aiModel = llmId,
                        cleanupProvider = AiProviderType.LOCAL_LLM,
                        cleanupModel = llmId
                    )
                }
            }
            WritingAidMode.CLOUD -> {
                val targetCloud = secureKeyStorage.configuredCloudProviders().firstOrNull()
                    ?: AiProviderType.GEMINI_API
                val defaultModel = AiModelCatalog.defaultTextModelFor(targetCloud)
                val applyCloud = {
                    onUpdatePrefs {
                        it.copy(
                            aiProvider = targetCloud,
                            aiModel = defaultModel,
                            cleanupProvider = targetCloud,
                            cleanupModel = defaultModel,
                            consentedCloudProviders = it.consentedCloudProviders + targetCloud.name
                        )
                    }
                }
                if (CloudProviderPolicy.hasConsent(prefs.consentedCloudProviders, targetCloud)) {
                    applyCloud()
                } else {
                    pendingConsentAction = targetCloud to applyCloud
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Clean master switch
        item {
            AppleGroupedSurface {
                MinimalToggleRow(
                    title = if (de) "Schreibhilfe aktivieren" else "Enable Writing Aid",
                    checked = prefs.aiWritingToolsEnabled
                ) { enabled ->
                    onUpdatePrefs { it.copy(aiWritingToolsEnabled = enabled) }
                }
            }
        }

        if (prefs.aiWritingToolsEnabled) {
            // 2. Simple, crystal-clear 3-option mode selector
            item {
                Column {
                    AppleSectionHeader(if (de) "Modus" else "Mode")
                    AppleGroupedSurface {
                        val options = listOf(
                            Triple(
                                WritingAidMode.RULES,
                                if (de) "Sofort (Offline)" else "Instant (Offline)",
                                if (de) "0 ms · kein Download" else "0 ms · no download"
                            ),
                            Triple(
                                WritingAidMode.LOCAL_LLM,
                                if (de) "Lokal-KI (Offline)" else "Local AI (Offline)",
                                bestInstalledLlm?.first?.shortEngineLabel
                                    ?: (if (de) "Direkt auf dem Handy" else "Runs on your phone")
                            ),
                            Triple(
                                WritingAidMode.CLOUD,
                                if (de) "Cloud-KI" else "Cloud AI",
                                if (savedCloudCount > 0) AiModelCatalog.displayName(prefs.aiProvider, de)
                                else (if (de) "Mit eigenem API-Key" else "With your own API key")
                            )
                        )
                        options.forEachIndexed { idx, (mode, title, subtitle) ->
                            if (idx > 0) AppleHairlineDivider(startIndent = 16.dp)
                            val selected = activeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectMode(mode) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 15.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Minimal contextual card for the active mode
            when (activeMode) {
                WritingAidMode.RULES -> {
                    if (nanoStatus == NanoAvailability.AVAILABLE || nanoStatus == NanoAvailability.DOWNLOADABLE) {
                        item {
                            AppleGroupedSurface {
                                MinimalToggleRow(
                                    title = if (de) "Google Gemini Nano nutzen" else "Use Google Gemini Nano",
                                    checked = prefs.aiProvider == AiProviderType.GEMINI_NANO
                                ) { enabled ->
                                    val next = if (enabled) AiProviderType.GEMINI_NANO else AiProviderType.LOCAL_RULES
                                    onUpdatePrefs { it.copy(aiProvider = next, cleanupProvider = next) }
                                }
                                if (nanoStatus == NanoAvailability.DOWNLOADABLE) {
                                    AppleHairlineDivider(startIndent = 16.dp)
                                    MinimalActionRow(
                                        title = if (nanoDownloading) (if (de) "Wird geladen…" else "Downloading…")
                                        else (if (de) "Gemini Nano laden" else "Download Gemini Nano"),
                                        textColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        if (nanoDownloading) return@MinimalActionRow
                                        nanoDownloading = true
                                        scope.launch {
                                            GeminiNanoClient.requestDownload(context, prefs.activeLocale)
                                            nanoStatus = GeminiNanoClient.checkAvailability(context, prefs.activeLocale)
                                            nanoDownloading = false
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            AppleSectionFooter(
                                if (de) "Sofort einsatzbereit — korrigiert, kürzt und formatiert komplett offline ohne Speicherbedarf."
                                else "Ready instantly — fixes, shortens, and formats completely offline with zero storage."
                            )
                        }
                    }
                }

                WritingAidMode.LOCAL_LLM -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LocalModelCatalogSection(
                                title = if (de) "Lokales Modell" else "Local model",
                                recommendationNote = if (de) "Tipp: LFM 2.5 1.2B für beste Qualität oder Qwen 3.5 0.8B für maximales Tempo."
                                else "Tip: LFM 2.5 1.2B for best quality or Qwen 3.5 0.8B for fastest replies.",
                                models = fastLlmModels,
                                selectedModelId = bestInstalledLlm?.first?.id ?: prefs.selectedLlmModelId,
                                states = modelStates,
                                de = de,
                                onSelectModel = { spec ->
                                    onUpdatePrefs {
                                        it.copy(
                                            selectedLlmModelId = spec.id,
                                            aiProvider = AiProviderType.LOCAL_LLM,
                                            aiModel = spec.id,
                                            cleanupProvider = AiProviderType.LOCAL_LLM,
                                            cleanupModel = spec.id
                                        )
                                    }
                                },
                                onDownloadModel = { spec ->
                                    onUpdatePrefs {
                                        it.copy(
                                            selectedLlmModelId = spec.id,
                                            aiProvider = AiProviderType.LOCAL_LLM,
                                            aiModel = spec.id,
                                            cleanupProvider = AiProviderType.LOCAL_LLM,
                                            cleanupModel = spec.id
                                        )
                                    }
                                    modelManager.startDownload(spec)
                                },
                                onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                                onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                                onImportModel = { spec ->
                                    pendingImportSpec = spec
                                    importLlmLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                },
                                onOpenUrl = { url -> context.openSettingsLink(url) },
                                showTechnicalDetails = false
                            )

                            if (flagshipLlmModels.isNotEmpty()) {
                                AppleGroupedSurface {
                                    SettingsDisclosure(
                                        title = if (de) "Weitere Modelle (6+ GB RAM)" else "More models (6+ GB RAM)",
                                        expanded = moreModelsExpanded
                                    ) { moreModelsExpanded = !moreModelsExpanded }
                                    AnimatedVisibility(visible = moreModelsExpanded) {
                                        Column {
                                            AppleHairlineDivider(startIndent = 16.dp)
                                            LocalModelCatalogListContent(
                                                models = flagshipLlmModels,
                                                selectedModelId = bestInstalledLlm?.first?.id ?: prefs.selectedLlmModelId,
                                                states = modelStates,
                                                de = de,
                                                onSelectModel = { spec ->
                                                    onUpdatePrefs {
                                                        it.copy(
                                                            selectedLlmModelId = spec.id,
                                                            aiProvider = AiProviderType.LOCAL_LLM,
                                                            aiModel = spec.id,
                                                            cleanupProvider = AiProviderType.LOCAL_LLM,
                                                            cleanupModel = spec.id
                                                        )
                                                    }
                                                },
                                                onDownloadModel = { spec ->
                                                    onUpdatePrefs { it.copy(selectedLlmModelId = spec.id) }
                                                    modelManager.startDownload(spec)
                                                },
                                                onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                                                onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                                                onImportModel = { spec ->
                                                    pendingImportSpec = spec
                                                    importLlmLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                                },
                                                onOpenUrl = { url -> context.openSettingsLink(url) },
                                                showTechnicalDetails = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                WritingAidMode.CLOUD -> {
                    item {
                        Column {
                            AppleSectionHeader(if (de) "Cloud-Konfiguration" else "Cloud setup")
                            AppleGroupedSurface {
                                MinimalDropdownRow(
                                    title = if (de) "Anbieter" else "Provider",
                                    selectedValue = prefs.aiProvider,
                                    options = cloudProviders.map { prov ->
                                        val hasKey = secureKeyStorage.hasKey(prov)
                                        DropdownOption(
                                            value = prov,
                                            label = if (hasKey) AiModelCatalog.displayName(prov, de)
                                            else "${AiModelCatalog.displayName(prov, de)} · 🔑"
                                        )
                                    },
                                    onSelect = { prov ->
                                        val defModel = AiModelCatalog.defaultTextModelFor(prov)
                                        val applyProvider = {
                                            onUpdatePrefs {
                                                it.copy(
                                                    aiProvider = prov,
                                                    aiModel = defModel,
                                                    cleanupProvider = prov,
                                                    cleanupModel = defModel,
                                                    consentedCloudProviders = it.consentedCloudProviders + prov.name
                                                )
                                            }
                                        }
                                        if (CloudProviderPolicy.hasConsent(prefs.consentedCloudProviders, prov)) {
                                            applyProvider()
                                        } else {
                                            pendingConsentAction = prov to applyProvider
                                        }
                                    }
                                )
                                val presets = AiModelCatalog.textModelsFor(prefs.aiProvider)
                                if (presets.isNotEmpty()) {
                                    AppleHairlineDivider(startIndent = 16.dp)
                                    val currentModel = prefs.aiModel.trim().ifBlank { presets.first().id }
                                    val modelOptions = presets.map { spec ->
                                        val tag = if (spec.isFreeTier) "FREE · " else ""
                                        DropdownOption(spec.id, "$tag${spec.label}")
                                    }.let { list ->
                                        if (list.any { it.value == currentModel }) list else list + DropdownOption(currentModel, currentModel)
                                    }
                                    MinimalDropdownRow(
                                        title = if (de) "Modell" else "Model",
                                        selectedValue = currentModel,
                                        options = modelOptions,
                                        onSelect = { id -> onUpdatePrefs { it.copy(aiModel = id, cleanupModel = id) } }
                                    )
                                }
                                AppleHairlineDivider(startIndent = 16.dp)
                                MinimalNavRow(
                                    icon = Icons.Rounded.Key,
                                    badgeColor = Color(0xFFFF375F),
                                    title = if (de) "API-Schlüssel" else "API Keys",
                                    value = if (savedCloudCount > 0) (if (de) "$savedCloudCount aktiv" else "$savedCloudCount active")
                                    else (if (de) "Einrichten" else "Set up"),
                                    onClick = onOpenApiKeys
                                )
                            }
                        }
                    }
                }
            }

            // 4. Tiny, uncluttered Advanced disclosure
            item {
                AppleGroupedSurface {
                    SettingsDisclosure(if (de) "Erweitert" else "Advanced", advancedExpanded) {
                        advancedExpanded = !advancedExpanded
                    }
                    AnimatedVisibility(visible = advancedExpanded) {
                        Column {
                            AppleHairlineDivider(startIndent = 16.dp)
                            MinimalToggleRow(
                                title = if (de) "Alle Tastaturen unterstützen" else "Support all keyboards",
                                checked = prefs.aiSupportAllKeyboards
                            ) { enabled -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = enabled) } }
                            AppleHairlineDivider(startIndent = 16.dp)
                            val cleanupOptions = listOf(
                                DropdownOption(AiProviderType.LOCAL_RULES, if (de) "Sofort (Offline)" else "Instant (Offline)"),
                                DropdownOption(AiProviderType.LOCAL_LLM, if (de) "Lokal-KI (Offline)" else "Local AI (Offline)")
                            ) + cloudProviders.filter { secureKeyStorage.hasKey(it) || it == prefs.cleanupProvider }.map {
                                DropdownOption(it, AiModelCatalog.displayName(it, de))
                            }
                            MinimalDropdownRow(
                                title = if (de) "Diktat-Bereinigung" else "Dictation cleanup",
                                selectedValue = prefs.cleanupProvider,
                                options = cleanupOptions.distinctBy { it.value },
                                onSelect = { prov ->
                                    val defModel = if (prov == AiProviderType.LOCAL_LLM) {
                                        bestInstalledLlm?.first?.id ?: prefs.selectedLlmModelId
                                    } else {
                                        AiModelCatalog.defaultTextModelFor(prov)
                                    }
                                    val applyCleanup = {
                                        onUpdatePrefs {
                                            val updatedConsent = if (CloudProviderPolicy.isCloudProvider(prov)) {
                                                it.consentedCloudProviders + prov.name
                                            } else {
                                                it.consentedCloudProviders
                                            }
                                            it.copy(
                                                cleanupProvider = prov,
                                                cleanupModel = defModel,
                                                consentedCloudProviders = updatedConsent
                                            )
                                        }
                                    }
                                    if (!CloudProviderPolicy.isCloudProvider(prov) ||
                                        CloudProviderPolicy.hasConsent(prefs.consentedCloudProviders, prov)
                                    ) {
                                        applyCleanup()
                                    } else {
                                        pendingConsentAction = prov to applyCleanup
                                    }
                                }
                            )
                            if (activeMode == WritingAidMode.CLOUD) {
                                AppleHairlineDivider(startIndent = 16.dp)
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = if (de) "Eigene Modell-ID (optional)" else "Custom model ID (optional)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    AppleInlineField(
                                        value = prefs.aiModel,
                                        onValueChange = { m -> onUpdatePrefs { it.copy(aiModel = m, cleanupModel = m) } },
                                        placeholder = AiModelCatalog.defaultTextModelFor(prefs.aiProvider)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun AiModelReference(provider: AiProviderType, model: String = "") {
    val url = AiModelCatalog.modelsUrl(provider) ?: return
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { context.openSettingsLink(url) }, modifier = Modifier.weight(1f, fill = false)) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ai_model_catalog))
        }
        if (model.isNotBlank()) {
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Model ID", model.trim()))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, R.string.ai_model_copied, Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ai_model_copy))
            }
        }
    }
}
