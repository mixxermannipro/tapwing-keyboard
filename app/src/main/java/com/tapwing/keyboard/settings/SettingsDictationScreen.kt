// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.CleanupPromptBuilder
import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.ModelInstallState
import com.tapwing.keyboard.voice.NaturalVoiceCleaner
import com.tapwing.keyboard.voice.VoiceSelfTest
import com.tapwing.keyboard.voice.GeminiLiveDictation
import com.tapwing.keyboard.voice.CloudSpeechProvider
import com.tapwing.keyboard.voice.TapwingVoiceController
import com.tapwing.keyboard.voice.WavAudioRecorder
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.data.VoiceEngineMode
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun VoiceSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    modelManager: LocalModelManager,
    secureKeyStorage: SecureKeyStorage,
    onOpenWritingTools: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by modelManager.states.collectAsState()
    var pendingImportSpec by remember { mutableStateOf<LocalModelSpec?>(null) }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSpec = pendingImportSpec
        pendingImportSpec = null
        if (uri != null && targetSpec != null) {
            scope.launch {
                val ok = modelManager.importModelFromUri(targetSpec, uri)
                if (ok) {
                    onUpdatePrefs { it.copy(selectedWhisperModelId = targetSpec.id) }
                }
            }
        }
    }

    var advanced by rememberSaveable { mutableStateOf(false) }
    var whisperModelsExpanded by rememberSaveable { mutableStateOf(false) }
    var diagRunning by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<String?>(null) }
    var pendingCloudVoiceConsent by remember { mutableStateOf<Pair<AiProviderType, VoiceEngineMode>?>(null) }

    pendingCloudVoiceConsent?.let { (targetProv, targetMode) ->
        CloudProviderPolicy.disclosureFor(targetProv)?.let { disclosure ->
            CloudConsentDialog(
                disclosure = disclosure,
                de = de,
                onOpenPrivacyUrl = { url -> context.openSettingsLink(url) },
                onConfirm = {
                    pendingCloudVoiceConsent = null
                    onUpdatePrefs {
                        it.copy(
                            voiceEngine = targetMode,
                            consentedCloudProviders = it.consentedCloudProviders + targetProv.name
                        )
                    }
                },
                onDismiss = { pendingCloudVoiceConsent = null }
            )
        }
    }

    val useLiveCloud = prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE
    val activeDictProviderType = if (useLiveCloud) AiProviderType.GEMINI_API else prefs.dictationProvider
    val dictationProvider = CloudSpeechProvider.from(activeDictProviderType, prefs.aiCustomEndpoint)
    val keySaved = remember(activeDictProviderType) {
        secureKeyStorage.hasKey(activeDictProviderType) ||
            (dictationProvider != null && secureKeyStorage.readDictationKey(dictationProvider.name).isNotBlank())
    }
    val localAndroidAvailable = remember(context) { TapwingVoiceController.isOnDeviceSpeechAvailable(context) }
    val bestInstalled = remember(
        modelStates,
        prefs.selectedWhisperModelId,
        prefs.activeLocale,
        prefs.voiceMultilingualCodeSwitching
    ) {
        modelManager.bestInstalledSpeechModel(
            preferredId = prefs.selectedWhisperModelId,
            locale = prefs.activeLocale,
            preferMultilingual = prefs.voiceMultilingualCodeSwitching
        )
    }
    val recommendedForLocale = remember(prefs.activeLocale, prefs.voiceMultilingualCodeSwitching) {
        LocalModelCatalog.recommendedForLocale(
            locale = prefs.activeLocale,
            multilingual = prefs.voiceMultilingualCodeSwitching
        )
    }
    val cloudNeedsKey = prefs.voiceEngine.usesCloudAudio && (dictationProvider == null || !keySaved)
    val offlineReady = bestInstalled != null || localAndroidAvailable ||
        (!prefs.voiceEngine.usesCloudAudio && android.speech.SpeechRecognizer.isRecognitionAvailable(context))
    val dictationReady = micGranted && !cloudNeedsKey && (prefs.voiceEngine.usesCloudAudio || offlineReady)

    val defaultPromptPreview = remember(prefs.voiceCleanupUseSystemLanguage, prefs.activeLocale) {
        CleanupPromptBuilder.buildDefaultPrompt(
            activeKeyboardLocale = prefs.activeLocale,
            useSystemLanguage = prefs.voiceCleanupUseSystemLanguage
        )
    }
    var promptDraft by remember(prefs.voiceCleanupPrompt) {
        mutableStateOf(prefs.voiceCleanupPrompt)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            AppleGroupedSurface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (dictationReady) Color(0xFF34C759).copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (dictationReady) Icons.Rounded.Check else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (dictationReady) Color(0xFF34C759) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when {
                                !micGranted -> if (de) "Mikrofon noch erlauben" else "Allow microphone access"
                                cloudNeedsKey -> if (de) "Cloud-Schlüssel fehlt" else "Cloud API key needed"
                                bestInstalled == null && prefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER ->
                                    if (de) "Lokal-Modell laden" else "Download offline model"
                                else -> if (de) "Spracheingabe bereit" else "Voice typing ready"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                !micGranted -> if (de) "Ein Tipp genügt, damit Tapwing dich hört." else "Tap once so Tapwing can hear your voice."
                                cloudNeedsKey -> if (de) "API-Schlüssel eintragen oder lokal diktieren." else "Save an API key or switch to local mode."
                                bestInstalled != null -> {
                                    val name = bestInstalled.first.displayName.substringBefore(" (")
                                    val speed = if (de) bestInstalled.first.speedBadgeDe else bestInstalled.first.speedBadgeEn
                                    if (speed.isNotEmpty()) "$name · $speed" else name
                                }
                                else -> if (de) "Empfehlung unten laden für maximales Offline-Tempo." else "Download recommended model below for instant offline speed."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!micGranted) {
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        if (de) "Mikrofon jetzt erlauben" else "Allow microphone now",
                        MaterialTheme.colorScheme.primary
                    ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                } else if (bestInstalled == null) {
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        if (de) "Empfohlenes Modell laden (${recommendedForLocale.approxSizeMb} MB)"
                        else "Download recommended model (${recommendedForLocale.approxSizeMb} MB)",
                        MaterialTheme.colorScheme.primary
                    ) {
                        onUpdatePrefs { it.copy(selectedWhisperModelId = recommendedForLocale.id) }
                        modelManager.startDownload(recommendedForLocale)
                    }
                }
            }
        }

        item {
            Column {
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "Natürlich bereinigen" else "Natural cleanup",
                        prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                    ) { enabled ->
                        val mode = if (enabled) VoiceDictationMode.NATURAL_CLEANUP else VoiceDictationMode.STANDARD
                        onUpdatePrefs { it.copy(voiceMode = mode) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        if (de) "Mehrsprachiges Diktat (DE + EN mischen)" else "Multilingual dictation (mix DE + EN)",
                        prefs.voiceMultilingualCodeSwitching
                    ) { enabled ->
                        onUpdatePrefs {
                            it.copy(
                                voiceMultilingualCodeSwitching = enabled,
                                voiceStrictLanguageLock = if (enabled) false else it.voiceStrictLanguageLock
                            )
                        }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        if (de) "Stichpunkte automatisch erkennen" else "Auto-detect bullet lists",
                        prefs.voiceAutoBulletLists
                    ) { enabled ->
                        onUpdatePrefs { it.copy(voiceAutoBulletLists = enabled) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Entfernt Füllwörter & Stottern, korrigiert Versprecher, formatiert Listen automatisch als • Stichpunkte, mischt Sprachen nahtlos und nutzt dein persönliches Wörterbuch."
                    else "Strips fillers & stutters, resolves self-corrections, auto-formats spoken lists as • bullets, mixes languages seamlessly, and adapts to your personal dictionary."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Verarbeitung" else "Processing")
                AppleGroupedSurface {
                    val engineList = buildList {
                        add(VoiceEngineMode.LOCAL_WHISPER)
                        add(VoiceEngineMode.AUTO_LOCAL_FIRST)
                        add(VoiceEngineMode.GEMINI_LIVE)
                        if (localAndroidAvailable || prefs.voiceEngine == VoiceEngineMode.ON_DEVICE_SPEECH) {
                            add(VoiceEngineMode.ON_DEVICE_SPEECH)
                        }
                        add(VoiceEngineMode.CLOUD)
                        add(VoiceEngineMode.SYSTEM_SPEECH)
                    }
                    val engineOptions = engineList.map { mode ->
                        val (title, description) = when (mode) {
                            VoiceEngineMode.LOCAL_WHISPER -> if (de) {
                                "Lokal & Ultraschnell" to "100 % auf deinem Gerät mit Sherpa-ONNX oder Whisper — blitzschnell & ohne Zeitlimit."
                            } else "On-device & ultra-fast" to "100% private via Sherpa-ONNX or Whisper — instant results & unlimited length."
                            VoiceEngineMode.AUTO_LOCAL_FIRST -> if (de) {
                                "Automatisch" to "Bevorzugt dein schnelles Lokal-Modell und fällt bei Bedarf auf Android-Sprache zurück."
                            } else "Automatic" to "Uses your fast local model first and falls back to Android speech when needed."
                            VoiceEngineMode.GEMINI_LIVE -> if (de) {
                                "Cloud-KI (Live)" to "Gemini Live-Streaming mit automatischem Offline-Schutz."
                            } else "Cloud AI (Live)" to "Gemini Live streaming with automatic offline fallback."
                            VoiceEngineMode.ON_DEVICE_SPEECH -> if (de) {
                                "Android offline · Live" to "System-Sprachpaket auf dem Gerät mit Sofort-Vorschau."
                            } else "Android offline · Live" to "Built-in device language pack with instant preview."
                            VoiceEngineMode.CLOUD -> if (de) {
                                "Cloud nach Stopp" to "Sendet Audio nach dem Stopp an Groq, Gemini oder OpenAI."
                            } else "Cloud after stop" to "Sends audio to Groq, Gemini, or OpenAI after stopping."
                            VoiceEngineMode.SYSTEM_SPEECH -> if (de) {
                                "Android-Sprachdienst" to "Standard-Spracherkennung von Android."
                            } else "Android speech service" to "Standard Android speech recognition."
                        }
                        DropdownOption(mode, title, description)
                    }
                    MinimalDropdownRow(
                        title = if (de) "Sprach-Engine" else "Speech engine",
                        selectedValue = prefs.voiceEngine,
                        options = engineOptions,
                        onSelect = { mode ->
                            if (mode.usesCloudAudio) {
                                val targetProv = if (mode == VoiceEngineMode.GEMINI_LIVE) {
                                    AiProviderType.GEMINI_API
                                } else {
                                    prefs.dictationProvider
                                }
                                if (CloudProviderPolicy.hasConsent(prefs.consentedCloudProviders, targetProv)) {
                                    onUpdatePrefs { it.copy(voiceEngine = mode) }
                                } else {
                                    pendingCloudVoiceConsent = targetProv to mode
                                }
                            } else {
                                onUpdatePrefs { it.copy(voiceEngine = mode) }
                            }
                        }
                    )
                    val installedSpeechModels = LocalModelCatalog.speechModels.filter {
                        modelStates[it.id] is ModelInstallState.Installed
                    }
                    if (!prefs.voiceEngine.usesCloudAudio && installedSpeechModels.size >= 2) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        val activeSpeechId = bestInstalled?.first?.id ?: prefs.selectedWhisperModelId
                        MinimalDropdownRow(
                            title = if (de) "Aktives Sprachmodell" else "Active speech model",
                            selectedValue = activeSpeechId,
                            options = installedSpeechModels.map { spec ->
                                DropdownOption(
                                    value = spec.id,
                                    label = spec.displayName.substringBefore(" ("),
                                    subtitle = if (de) spec.speedBadgeDe else spec.speedBadgeEn
                                )
                            },
                            onSelect = { id -> onUpdatePrefs { it.copy(selectedWhisperModelId = id) } }
                        )
                    }
                }
            }
        }

        if (prefs.voiceEngine.usesCloudAudio) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Cloud einrichten" else "Cloud setup")
                    AppleGroupedSurface {
                        MinimalValueRow(
                            if (de) "Diktat-Anbieter" else "Dictation provider",
                            if (useLiveCloud) "Gemini Live" else AiModelCatalog.displayName(prefs.dictationProvider, de)
                        )
                        val model = if (useLiveCloud) GeminiLiveDictation.MODEL
                        else prefs.dictationModel.ifBlank { AiModelCatalog.defaultAudioModelFor(activeDictProviderType) }
                        AiModelReference(activeDictProviderType, model)
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "API-Schlüssel-Tresor öffnen" else "Open API Keys Vault",
                            MaterialTheme.colorScheme.primary,
                            onClick = onOpenApiKeys
                        )
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "Diktat- & Bereinigungsmodelle wählen" else "Choose dictation & cleanup models",
                            MaterialTheme.colorScheme.primary,
                            onClick = onOpenWritingTools
                        )
                    }
                    AppleSectionFooter(
                        if (!keySaved || dictationProvider == null) {
                            if (de) "Hinterlege einen API-Schlüssel — oder nutze „Lokal & Ultraschnell“ ohne Schlüssel."
                            else "Add an API key — or use “On-device & ultra-fast” without a key."
                        } else {
                            if (de) "Bei Verbindungsabbruch übernimmt automatisch dein lokales Offline-Modell."
                            else "If the connection drops, your installed offline model takes over automatically."
                        }
                    )
                }
            }
        }

        if (prefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER ||
            prefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST ||
            prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LocalModelCatalogSection(
                        title = if (de) "Lokale Sprachmodelle (Sherpa-ONNX)" else "On-device Speech Models (Sherpa-ONNX)",
                        recommendationNote = if (de) {
                            "Empfohlen: Nemotron 3.5 Live (DE + EN mischen in Echtzeit), Parakeet Ultra (25 Sprachen) oder FastConformer DE (103 MB, ultrakompakt)."
                        } else {
                            "Recommended: Nemotron 3.5 Live (real-time DE + EN code-switching), Parakeet Ultra (25 languages), or FastConformer DE (103 MB, ultra-light)."
                        },
                        models = LocalModelCatalog.sherpaModels,
                        selectedModelId = bestInstalled?.first?.id ?: prefs.selectedWhisperModelId,
                        states = modelStates,
                        de = de,
                        onSelectModel = { spec -> onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) } },
                        onDownloadModel = { spec ->
                            onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) }
                            modelManager.startDownload(spec)
                        },
                        onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                        onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                        onImportModel = { spec ->
                            pendingImportSpec = spec
                            importModelLauncher.launch(arrayOf("application/octet-stream", "application/x-bzip2", "*/*"))
                        },
                        onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                        showTechnicalDetails = true
                    )

                    AppleGroupedSurface {
                        SettingsDisclosure(
                            title = if (de) "Weitere Modelle (Whisper · 99 Sprachen)" else "More models (Whisper · 99 languages)",
                            expanded = whisperModelsExpanded
                        ) {
                            whisperModelsExpanded = !whisperModelsExpanded
                        }
                        AnimatedVisibility(
                            visible = whisperModelsExpanded,
                            enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                            exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                        ) {
                            Column {
                                AppleHairlineDivider(startIndent = 16.dp)
                                LocalModelCatalogListContent(
                                    models = LocalModelCatalog.whisperModels,
                                    selectedModelId = bestInstalled?.first?.id ?: prefs.selectedWhisperModelId,
                                    states = modelStates,
                                    de = de,
                                    onSelectModel = { spec -> onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) } },
                                    onDownloadModel = { spec ->
                                        if (bestInstalled == null) onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) }
                                        modelManager.startDownload(spec)
                                    },
                                    onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                                    onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                                    onImportModel = { spec ->
                                        pendingImportSpec = spec
                                        importModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                    },
                                    onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                                    showTechnicalDetails = true
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Tempo- & Bereinigungstest" else "Speed & cleanup check")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (diagRunning) return@FilledTonalButton
                                if (!micGranted) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@FilledTonalButton
                                }
                                val target = bestInstalled
                                if (target == null) {
                                    diagReport = if (de) "Lade zuerst oben ein Modell, um lokal zu testen."
                                    else "Download a model above first to test locally."
                                    return@FilledTonalButton
                                }
                                diagRunning = true
                                diagReport = if (de) "Aufnahme läuft (3 Sek.) — sprich jetzt!" else "Recording (3 sec) — speak now!"
                                scope.launch {
                                    val wav = File(context.cacheDir, "tapwing_diag_voice.wav")
                                    val rec = WavAudioRecorder()
                                    val started = rec.start(wav, {}, {}, { err ->
                                        diagReport = if (de) "Mikrofon-Fehler: $err" else "Mic error: $err"
                                    })
                                    if (!started) {
                                        diagRunning = false
                                        return@launch
                                    }
                                    delay(3000L)
                                    val result = rec.stopAndGetResult()
                                    if (result == null) {
                                        diagRunning = false
                                        diagReport = if (de) "Keine Aufnahme erstellt." else "No audio recorded."
                                        return@launch
                                    }
                                    diagReport = if (de) "Erkenne & bereinige lokal…" else "Recognizing & cleaning locally…"
                                    val t0 = System.currentTimeMillis()
                                    val tr = LocalInferenceEngine.transcribeWav(
                                        wavPath = result.file.absolutePath,
                                        spec = target.first,
                                        modelPath = target.second,
                                        languageCode = if (prefs.voiceMultilingualCodeSwitching) null else KeyboardLocales.baseLang(prefs.activeLocale)
                                    )
                                    val ms = System.currentTimeMillis() - t0
                                    result.file.delete()
                                    diagRunning = false
                                    diagReport = tr.fold(
                                        onSuccess = { rawTxt ->
                                            if (rawTxt.isBlank()) {
                                                if (de) "Keine Sprache gehört (Pegel ${result.peakSampleAbs}). Bitte lauter sprechen."
                                                else "No speech heard (peak ${result.peakSampleAbs}). Try speaking closer."
                                            } else {
                                                val cleaned = NaturalVoiceCleaner.clean(
                                                    rawTranscript = rawTxt,
                                                    locale = prefs.activeLocale,
                                                    removeFillers = prefs.voiceRemoveFillers,
                                                    autoPunctuate = prefs.voiceAutoPunctuate,
                                                    multilingual = prefs.voiceMultilingualCodeSwitching,
                                                    autoBulletLists = prefs.voiceAutoBulletLists
                                                )
                                                "${target.first.shortEngineLabel} · ${ms} ms → „$cleaned“"
                                            }
                                        },
                                        onFailure = { err ->
                                            if (de) "Fehler (${ms} ms): ${err.localizedMessage}" else "Error (${ms} ms): ${err.localizedMessage}"
                                        }
                                    )
                                }
                            },
                            enabled = !diagRunning,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "3s Mikrofon-Test" else "3s Mic Test", fontSize = 13.sp, maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (diagRunning) return@FilledTonalButton
                                val target = bestInstalled
                                if (target == null) {
                                    diagReport = if (de) "Lade zuerst oben ein Modell, um lokal zu testen."
                                    else "Download a model above first to test locally."
                                    return@FilledTonalButton
                                }
                                diagRunning = true
                                diagReport = if (de) "Starte TTS-Benchmark…" else "Running TTS benchmark…"
                                scope.launch {
                                    val wav = File(context.cacheDir, "tapwing_tts_16k.wav")
                                    val synth = VoiceSelfTest.synthesizeGermanWav16k(
                                        context = context,
                                        text = "Hallo, treffen wir uns morgen um fünfzehn Uhr im Büro?",
                                        dest16kWav = wav
                                    )
                                    if (synth.isFailure) {
                                        diagRunning = false
                                        diagReport = "TTS Fehler: ${synth.exceptionOrNull()?.localizedMessage}"
                                        return@launch
                                    }
                                    val (file, wavInfo) = synth.getOrThrow()
                                    val t0 = System.currentTimeMillis()
                                    val tr = LocalInferenceEngine.transcribeWav(
                                        wavPath = file.absolutePath,
                                        spec = target.first,
                                        modelPath = target.second,
                                        languageCode = KeyboardLocales.baseLang(prefs.activeLocale)
                                    )
                                    val ms = System.currentTimeMillis() - t0
                                    file.delete()
                                    diagRunning = false
                                    diagReport = tr.fold(
                                        onSuccess = { rawTxt ->
                                            val cleaned = NaturalVoiceCleaner.clean(
                                                rawTranscript = rawTxt,
                                                locale = prefs.activeLocale,
                                                removeFillers = prefs.voiceRemoveFillers,
                                                autoPunctuate = prefs.voiceAutoPunctuate
                                            )
                                            "${target.first.shortEngineLabel} ($wavInfo) · ${ms} ms → „${cleaned.ifBlank { "(leer)" }}“"
                                        },
                                        onFailure = { err ->
                                            "Fehler (${ms} ms): ${err.localizedMessage}"
                                        }
                                    )
                                }
                            },
                            enabled = !diagRunning,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "TTS-Benchmark" else "TTS Benchmark", fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    if (!diagReport.isNullOrBlank()) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = diagReport!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                SettingsDisclosure(if (de) "Erweiterte Einstellungen" else "Advanced settings", advanced) {
                    advanced = !advanced
                }
                AnimatedVisibility(
                    visible = advanced,
                    enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                ) {
                    Column {
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(if (de) "Füllwörter entfernen" else "Remove filler words", prefs.voiceRemoveFillers) { v ->
                            onUpdatePrefs { it.copy(voiceRemoveFillers = v) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(if (de) "Auto-Zeichensetzung" else "Auto-punctuation", prefs.voiceAutoPunctuate) { v ->
                            onUpdatePrefs { it.copy(voiceAutoPunctuate = v) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(if (de) "Tastatursprache bevorzugen" else "Prefer keyboard language", prefs.voiceStrictLanguageLock) { v ->
                            onUpdatePrefs { it.copy(voiceStrictLanguageLock = v) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(if (de) "Minimieren-Taste" else "Minimize button", prefs.voiceMinimizeButton) { v ->
                            onUpdatePrefs { it.copy(voiceMinimizeButton = v) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(
                            if (de) "Zusätzliche Text-KI-Bereinigung" else "Additional AI cleanup pass",
                            prefs.voiceAiPolish
                        ) { v -> onUpdatePrefs { it.copy(voiceAiPolish = v) } }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalToggleRow(
                            if (de) "Sprache = Systemsprache (${KeyboardLocales.systemLanguageDisplayName()})"
                            else "Language = System (${KeyboardLocales.systemLanguageDisplayName()})",
                            prefs.voiceCleanupUseSystemLanguage
                        ) { v -> onUpdatePrefs { it.copy(voiceCleanupUseSystemLanguage = v) } }
                        AppleHairlineDivider(startIndent = 16.dp)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = promptDraft,
                                onValueChange = { promptDraft = it },
                                label = {
                                    Text(
                                        if (de) "Eigene Bereinigungs-Anweisung (optional)"
                                        else "Custom cleanup instructions (optional)",
                                        fontSize = 12.sp
                                    )
                                },
                                placeholder = {
                                    Text(defaultPromptPreview.take(140) + "…", fontSize = 12.sp)
                                },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onUpdatePrefs { it.copy(voiceCleanupPrompt = promptDraft.trim()) }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (de) "Prompt speichern" else "Save prompt", fontSize = 13.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        promptDraft = defaultPromptPreview
                                        onUpdatePrefs { it.copy(voiceCleanupPrompt = "") }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (de) "Standard" else "Default", fontSize = 13.sp)
                                }
                            }
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
                                Text(if (de) "Diktat nach Drehung fortsetzen" else "Resume after rotation", fontSize = 15.sp)
                                Text("${prefs.voiceRotationGraceMs} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                            }
                            Slider(
                                value = prefs.voiceRotationGraceMs.toFloat(),
                                onValueChange = { value ->
                                    onUpdatePrefs { it.copy(voiceRotationGraceMs = value.roundToInt().toLong()) }
                                },
                                valueRange = 500f..3500f
                            )
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalValueRow(
                            if (de) "Aktives Modell" else "Active model",
                            bestInstalled?.first?.displayName ?: (if (de) "Keines geladen" else "None")
                        )
                    }
                }
            }
        }
    }
}
