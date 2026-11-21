package com.tapwing.keyboard.ime

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.text.InputType
import android.util.Size
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsEngine
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.clipboard.ClipboardStore
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.TypingStatsStore
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.data.VoiceEngineMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.KeyCenter
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import com.tapwing.keyboard.dictionary.SnippetPlaceholderResolver
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.dictionary.SuggestionEngine
import com.tapwing.keyboard.dictionary.SwipeGestureDecoder
import com.tapwing.keyboard.dictionary.SwipePoint
import com.tapwing.keyboard.dictionary.TextSnippet
import com.tapwing.keyboard.ocr.OcrResultBus
import com.tapwing.keyboard.ocr.ScanTextActivity
import com.tapwing.keyboard.settings.SettingsActivity
import com.tapwing.keyboard.ui.keyboard.KeyActionLabel
import com.tapwing.keyboard.ui.keyboard.KeyActionType
import com.tapwing.keyboard.ui.keyboard.KeySpec
import com.tapwing.keyboard.ui.keyboard.KeyboardLayoutPage
import com.tapwing.keyboard.ui.keyboard.KeyboardPanelMode
import com.tapwing.keyboard.ui.keyboard.ShiftState
import com.tapwing.keyboard.ui.keyboard.TapwingKeyboardRoot
import com.tapwing.keyboard.ui.keyboard.TextEditAction
import com.tapwing.keyboard.undo.UndoManager
import com.tapwing.keyboard.undo.UndoReason
import com.tapwing.keyboard.undo.UndoSnapshot
import com.tapwing.keyboard.voice.CloudDictation
import com.tapwing.keyboard.voice.CloudSpeechProvider
import com.tapwing.keyboard.voice.GeminiLiveConfig
import com.tapwing.keyboard.voice.NaturalVoiceCleaner
import com.tapwing.keyboard.voice.TapwingVoiceController
import com.tapwing.keyboard.voice.VoiceCommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val VERIFICATION_CODE_LIFETIME_MS = 90_000L

class TapwingInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var prefsRepo: TapwingPreferencesRepository
    private lateinit var secureKeyStorage: SecureKeyStorage
    private lateinit var dictionaryStore: AdaptiveDictionaryStore
    private lateinit var offlinePackManager: OfflineDictionaryPackManager
    private lateinit var statsStore: TypingStatsStore
    private lateinit var modelManager: LocalModelManager
    private lateinit var writingToolsEngine: WritingToolsEngine
    private lateinit var clipboardStore: ClipboardStore
    private lateinit var voiceController: TapwingVoiceController
    private val undoManager = UndoManager()

    private var currentPrefs by mutableStateOf(TapwingPrefs())
    private var panelMode by mutableStateOf(KeyboardPanelMode.KEYS)
    private var layoutPage by mutableStateOf(KeyboardLayoutPage.LETTERS)
    private var shiftState by mutableStateOf(ShiftState.ONCE)
    private var currentEditorInfo: EditorInfo? = null
    private var resolvedImeAction by mutableStateOf(ResolvedImeAction(KeyActionLabel.ENTER, insertNewlineDirectly = true))
    private var imeActionLabel by mutableStateOf(KeyActionLabel.ENTER)
    private var suggestions by mutableStateOf<List<SuggestionChip>>(emptyList())
    private var isTyping by mutableStateOf(false)
    private var proactiveUndoState by mutableStateOf<UndoSnapshot?>(null)
    private var recentScreenshot by mutableStateOf<RecentScreenshot?>(null)
    private var inlineAutofillViews by mutableStateOf<List<View>>(emptyList())
    private var inlineRequestGeneration = 0
    private var snippetsSnapshot by mutableStateOf<List<TextSnippet>>(emptyList())
    private var recentEmojisSnapshot by mutableStateOf<List<String>>(emptyList())
    private var currentTargetPackage: String = ""
    private var lastSyncedClipKey: String = ""

    private var writingSourceText by mutableStateOf("")
    private var writingOutput by mutableStateOf<WritingToolsOutput?>(null)
    private var writingLoading by mutableStateOf(false)
    private var writingAction by mutableStateOf<WritingAction?>(null)

    private var textEditSelectionActive by mutableStateOf(false)
    private var quickSearchInitial by mutableStateOf("")

    private var pasteSuggestion by mutableStateOf<CopiedText?>(null)
    private var dismissedCopyTimestamp = -1L
    private var pasteExpiryJob: Job? = null
    private var inputViewActive = false
    private var prefsLoaded = false
    private var privateEditor by mutableStateOf(false)
    private var voicePrefs: TapwingPrefs? = null
    private var voiceEditJob: Job? = null
    private var voiceEditGeneration = 0

    private data class CopiedText(val text: String, val timestamp: Long, val verificationCode: Boolean)

    private var lastSpaceTapMs: Long = 0L
    private var isEmailField: Boolean = false
    private var isUrlField: Boolean = false
    private var lastHandledScreenshotUri: String? = null
    private var screenshotObserver: ContentObserver? = null

    // Smart typing trackers: 1-tap Backspace reverts autocorrect, and punctuation cleans chip space.
    private var pendingSmartUndoPair: Pair<String, String>? = null
    private var pendingSuggestionTrailingSpace: Boolean = false
    private var lastWordBeforeEmoji: String = ""

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        syncSystemClipboard(updateStrip = inputViewActive)
    }

    private fun syncSystemClipboard(updateStrip: Boolean = true) {
        if (!currentPrefs.clipboardEnabled || currentPrefs.incognitoMode) return
        val clip = runCatching {
            getSystemService(ClipboardManager::class.java)?.primaryClip
        }.getOrNull()
        if (updateStrip && inputViewActive) updatePasteSuggestion(clip)
        if (clip == null || clip.itemCount == 0) return

        val firstItem = clip.getItemAt(0)
        val firstDigest = firstItem.uri?.toString()
            ?: firstItem.text?.toString()?.hashCode()?.toString()
            ?: firstItem.coerceToText(this)?.toString()?.hashCode()?.toString()
            ?: "0"
        val clipKey = "${clip.description.timestamp}_${clip.itemCount}_$firstDigest"
        if (clipKey == lastSyncedClipKey) return
        lastSyncedClipKey = clipKey

        val isImage = clip.description.hasMimeType("image/*") && firstItem.uri != null
        if (isImage) {
            val uri = firstItem.uri!!
            if (ScreenshotMonitor.isSelfCopiedEvent(uri)) return
            serviceScope.launch {
                val localFile = ScreenshotMonitor.cacheImageLocally(this@TapwingInputMethodService, uri)
                if (localFile != null) {
                    val saved = clipboardStore.addImageClip(
                        imagePath = localFile.absolutePath,
                        mimeType = "image/png",
                        displayLabel = "Screenshot",
                        retention = currentPrefs.clipboardRetention,
                        maxUnpinnedItems = currentPrefs.clipboardMaxItems
                    )
                    val shot = RecentScreenshot(
                        uri = ScreenshotMonitor.fileProviderUri(this@TapwingInputMethodService, localFile),
                        displayName = localFile.name,
                        dateAddedSec = System.currentTimeMillis() / 1000L,
                        localImagePath = localFile.absolutePath
                    )
                    recentScreenshot = shot
                    val ocr = ScreenshotMonitor.extractTextFromFile(this@TapwingInputMethodService, localFile)
                    if (ocr.isNotBlank() && saved != null) {
                        clipboardStore.updateClipOcrText(
                            id = saved.id,
                            ocrText = ocr,
                            retention = currentPrefs.clipboardRetention,
                            maxUnpinnedItems = currentPrefs.clipboardMaxItems
                        )
                        if (recentScreenshot?.localImagePath == localFile.absolutePath) {
                            recentScreenshot = recentScreenshot?.copy(ocrText = ocr)
                        }
                    }
                }
            }
            return
        }

        val parts = (0 until clip.itemCount).mapNotNull { idx ->
            val item = clip.getItemAt(idx)
            val raw = item.text?.toString() ?: item.coerceToText(this)?.toString()
            raw?.trim()?.takeIf { it.isNotEmpty() }
        }
        val text = parts.joinToString("\n")
        if (text.isNotBlank()) {
            clipboardStore.addClip(
                rawText = text,
                retention = currentPrefs.clipboardRetention,
                maxUnpinnedItems = currentPrefs.clipboardMaxItems
            )
        }
    }

    private fun clipboardAllowed() =
        currentPrefs.clipboardEnabled && !currentPrefs.incognitoMode && !privateEditor

    private fun ClipDescription.isSensitive() =
        extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private fun updatePasteSuggestion(
        clip: android.content.ClipData? = if (clipboardAllowed()) {
            runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull()
        } else null
    ) {
        pasteExpiryJob?.cancel()
        pasteExpiryJob = null
        pasteSuggestion = if (clipboardAllowed() && inputViewActive && clip != null &&
            clip.itemCount > 0 && !clip.description.isSensitive() &&
            clip.description.timestamp != dismissedCopyTimestamp
        ) {
            val timestamp = clip.description.timestamp
            val item = clip.getItemAt(0)
            val rawText = item.text?.toString() ?: item.coerceToText(this)?.toString()
            rawText?.takeIf { it.isNotBlank() }?.let { text ->
                val code = SuggestionEngine.extractOtpFromClipboard(text)
                when {
                    code == null -> CopiedText(text, timestamp, verificationCode = false)
                    System.currentTimeMillis() - timestamp in 0 until VERIFICATION_CODE_LIFETIME_MS ->
                        CopiedText(code, timestamp, verificationCode = true)
                    else -> null
                }
            }
        } else null
        val shown = pasteSuggestion
        if (shown?.verificationCode == true) {
            pasteExpiryJob = serviceScope.launch {
                delay((shown.timestamp + VERIFICATION_CODE_LIFETIME_MS - System.currentTimeMillis()).coerceAtLeast(0))
                if (pasteSuggestion == shown) pasteSuggestion = null
            }
        }
    }

    private fun pasteFromSuggestion(timestamp: Long) {
        val shown = pasteSuggestion?.takeIf { it.timestamp == timestamp } ?: return
        updatePasteSuggestion()
        if (shown != pasteSuggestion) return
        val connection = currentInputConnection ?: return
        if (connection.commitText(shown.text, 1)) {
            dismissPasteSuggestion(timestamp)
            refreshContextAndSuggestions()
        }
    }

    private fun dismissPasteSuggestion(timestamp: Long) {
        if (pasteSuggestion?.timestamp != timestamp) return
        dismissedCopyTimestamp = timestamp
        pasteExpiryJob?.cancel()
        pasteSuggestion = null
    }

    private fun isPrivateEditor(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return true
        val type = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return inputType == InputType.TYPE_NULL ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
            (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            (type == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ))
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefsRepo = TapwingPreferencesRepository(this)
        secureKeyStorage = SecureKeyStorage(this)
        dictionaryStore = AdaptiveDictionaryStore.createForContext(this)
        offlinePackManager = OfflineDictionaryPackManager(this)
        statsStore = TypingStatsStore.createForContext(this)
        modelManager = LocalModelManager(this)
        writingToolsEngine = WritingToolsEngine(secureKeyStorage, dictionaryStore, modelManager, this)
        clipboardStore = ClipboardStore.createForContext(this)
        snippetsSnapshot = dictionaryStore.listSnippets()
        recentEmojisSnapshot = dictionaryStore.listRecentEmojis()
        voiceController = TapwingVoiceController(
            context = this,
            modelManager = modelManager,
            onTranscriptReady = ::handleVoiceTranscript,
            onNeedAudioPermission = { PermissionBridgeActivity.requestRecordAudio(this) }
        )

        serviceScope.launch {
            prefsRepo.prefsFlow.collect { prefs ->
                val clipboardWasAllowed = clipboardAllowed()
                currentPrefs = prefs
                prefsLoaded = true
                if (inputViewActive) {
                    val resolved = ImeActionResolver.resolve(
                        imeOptions = currentEditorInfo?.imeOptions ?: 0,
                        inputType = currentEditorInfo?.inputType ?: 0,
                        alwaysNewline = prefs.enterKeyAlwaysNewline,
                        packageName = currentEditorInfo?.packageName.orEmpty()
                    )
                    resolvedImeAction = resolved
                    imeActionLabel = resolved.label
                }
                if (inputViewActive && clipboardWasAllowed != clipboardAllowed()) updatePasteSuggestion()
                if (!prefs.inlineAutofillEnabled) clearAutofillSuggestions()
                offlinePackManager.preloadLocaleAsync(prefs.activeLocale)
                if (prefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST) {
                    voiceController.prepareOfflineSpeech(prefs.activeLocale)
                }
                clipboardStore.prune(prefs.clipboardRetention, prefs.clipboardMaxItems)
                ensureScreenshotObserverIfPermitted()
            }
        }

        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.addPrimaryClipChangedListener(clipboardListener)
        }
        ensureScreenshotObserverIfPermitted()
    }

    private fun ensureScreenshotObserverIfPermitted() {
        if (screenshotObserver != null) return
        if (!currentPrefs.autoCopyScreenshots && !currentPrefs.screenshotSuggestionChip) return
        if (!ScreenshotMonitor.hasMediaPermission(this)) return
        screenshotObserver = ScreenshotMonitor.registerScreenshotObserver(this) {
            checkRecentScreenshot()
        }
    }

    override fun onCreateInputView(): View {
        attachTreeOwners(window?.window?.decorView)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            attachTreeOwners(this)
            setContent {
                val voiceUiState by voiceController.state.collectAsState()
                val clips by clipboardStore.items.collectAsState()

                TapwingKeyboardRoot(
                    prefs = currentPrefs,
                    panelMode = panelMode,
                    layoutPage = layoutPage,
                    shiftState = shiftState,
                    imeActionLabel = imeActionLabel,
                    suggestions = suggestions,
                    isTyping = isTyping,
                    proactiveUndo = proactiveUndoState,
                    recentScreenshot = recentScreenshot.takeIf { !privateEditor && !currentPrefs.incognitoMode },
                    clipboardPreview = pasteSuggestion?.text?.take(160)?.replace(Regex("""\s+"""), " "),
                    clipboardTimestamp = pasteSuggestion?.timestamp,
                    clipboardVerificationCode = pasteSuggestion?.verificationCode == true,
                    onPasteClipboard = ::pasteFromSuggestion,
                    onDismissClipboard = ::dismissPasteSuggestion,
                    inlineAutofillViews = inlineAutofillViews,
                    snippets = snippetsSnapshot,
                    recentEmojis = recentEmojisSnapshot,
                    voiceState = voiceUiState,
                    writingSourceText = writingSourceText,
                    writingOutput = writingOutput,
                    writingLoading = writingLoading,
                    writingAction = writingAction,
                    clips = clips,
                    textEditSelectionActive = textEditSelectionActive,
                    quickSearchInitial = quickSearchInitial,
                    onKeyPress = ::handleKeyPress,
                    onSwipeWord = ::handleSwipeWord,
                    onCursorMoveSteps = ::moveCursorBy,
                    onDeletePreviousWord = ::deletePreviousWordWithUndo,
                    onSwitchLanguage = ::toggleLocale,
                    onToggleOneHandedSide = ::swapOneHandedSide,
                    onExitOneHanded = { updatePref { it.copy(oneHandedMode = OneHandedMode.OFF) } },
                    onSelectSuggestion = ::applySuggestionChip,
                    onTriggerUndo = ::performUndo,
                    onDismissUndo = {
                        undoManager.dismissBanner()
                        proactiveUndoState = null
                    },
                    onTapScreenshotChip = ::handleScreenshotPasteImage,
                    onTapScreenshotOcr = ::handleScreenshotPasteOcr,
                    onDismissScreenshotChip = { recentScreenshot = null },
                    onOpenToolsHub = { panelMode = KeyboardPanelMode.TOOLS_HUB },
                    onOpenCustomizeBar = { panelMode = KeyboardPanelMode.CUSTOMIZE_BAR },
                    onQuickAction = ::handleQuickBarAction,
                    onUpdateQuickBar = { list -> updatePref { it.copy(quickBarActions = list) } },
                    onResetQuickBar = { updatePref { it.copy(quickBarActions = QuickBarAction.DEFAULT_ACTIONS) } },
                    onCloseSubPanel = {
                        textEditSelectionActive = false
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onOpenWritingTools = ::openWritingTools,
                    onWritingOnboardingContinue = {
                        updatePref { it.copy(aiOnboardingSeen = true) }
                        openWritingToolsScreen()
                    },
                    onRunWritingAction = ::runWritingAction,
                    onApplyWritingResult = ::applyWritingToolsResult,
                    onOpenClipboard = {
                        syncSystemClipboard(updateStrip = false)
                        panelMode = KeyboardPanelMode.CLIPBOARD
                    },
                    onOpenSnippets = {
                        snippetsSnapshot = dictionaryStore.listSnippets()
                        panelMode = KeyboardPanelMode.SNIPPETS
                    },
                    onInsertSnippet = { _, resolvedText ->
                        commitTextWithSpaceSafety(resolvedText)
                        statsStore.recordSnippetInserted(resolvedText.length, currentPrefs.incognitoMode)
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onPasteClip = { clip ->
                        if (clip.isImage) {
                            pasteImageClip(clip)
                        } else {
                            commitTextWithSpaceSafety(clip.text)
                        }
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onPasteClipOcr = { clip ->
                        handleClipOcrPaste(clip)
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onTogglePinClip = { clip ->
                        clipboardStore.togglePin(clip.id, currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
                    },
                    onDeleteClip = { clip ->
                        clipboardStore.deleteClip(clip.id, currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
                    },
                    onClearUnpinnedClips = {
                        clipboardStore.clearUnpinned(currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
                    },
                    onOpenResize = { panelMode = KeyboardPanelMode.RESIZE },
                    onUpdateScalePercent = { pct -> updatePref { it.copy(keyboardScalePercent = pct) } },
                    onUpdateBottomLiftDp = { dp -> updatePref { it.copy(keyboardBottomLiftDp = dp) } },
                    onResetResize = {
                        updatePref { it.copy(keyboardScalePercent = 100, keyboardBottomLiftDp = 0) }
                    },
                    onOpenTextEdit = { panelMode = KeyboardPanelMode.TEXT_EDIT },
                    onToggleTextEditSelection = { textEditSelectionActive = !textEditSelectionActive },
                    onTextEditAction = ::performTextEditAction,
                    onOpenScanText = ::launchScanTextActivity,
                    onOpenEmoji = { panelMode = KeyboardPanelMode.EMOJI },
                    onInsertEmoji = { emoji ->
                        if (lastWordBeforeEmoji.isNotEmpty()) {
                            dictionaryStore.recordEmojiAfterWord(
                                word = lastWordBeforeEmoji,
                                emoji = emoji,
                                learningEnabled = currentPrefs.learningEnabled,
                                incognito = currentPrefs.incognitoMode
                            )
                        }
                        dictionaryStore.recordRecentEmoji(
                            emoji = emoji,
                            learningEnabled = currentPrefs.learningEnabled,
                            incognito = currentPrefs.incognitoMode
                        )
                        recentEmojisSnapshot = dictionaryStore.listRecentEmojis()
                        currentInputConnection?.commitText(emoji, 1)
                        pendingSmartUndoPair = null
                        pendingSuggestionTrailingSpace = false
                        refreshContextAndSuggestions()
                    },
                    onBackspace = { handleBackspace() },
                    onCycleOneHanded = ::cycleOneHanded,
                    onToggleIncognito = {
                        updatePref { it.copy(incognitoMode = !it.incognitoMode) }
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onOpenQuickSearch = {
                        quickSearchInitial = readWholeFieldText().take(120)
                        panelMode = KeyboardPanelMode.QUICK_SEARCH
                    },
                    onInsertSearchLink = { q ->
                        val url = "https://www.google.com/search?q=${Uri.encode(q.trim())}"
                        commitTextWithSpaceSafety(url)
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onOpenSearchInBrowser = { q ->
                        val url = "https://www.google.com/search?q=${Uri.encode(q.trim())}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { startActivity(intent) }
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onToggleAppleMode = {
                        updatePref { it.copy(appleModeEnabled = !it.appleModeEnabled) }
                    },
                    onOpenSettings = ::launchSettingsActivity,
                    onStartVoice = ::handleVoiceMicClicked,
                    onVoiceOnboardingGetStarted = {
                        updatePref { it.copy(voiceOnboardingSeen = true) }
                        panelMode = KeyboardPanelMode.KEYS
                        startVoice()
                    },
                    onFinishVoice = { voiceController.finishAndCommit() },
                    onCancelVoice = { cancelVoice() },
                    onToggleVoiceMinimize = { voiceController.toggleMinimized() },
                    onDismissVoiceError = { voiceController.clearError() },
                    onHideKeyboard = ::hideKeyboard
                )
            }
        }
        return composeView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        val nextPrivate = isPrivateEditor(info)
        if (!restarting || (nextPrivate && !privateEditor)) {
            cancelVoice()
            undoManager.clear()
            proactiveUndoState = null
            clearAutofillSuggestions()
        }
        privateEditor = nextPrivate
        if (inputViewActive) updatePasteSuggestion()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clearAutofillSuggestions()
        cancelVoice()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        dictionaryStore.reloadIfModifiedOnDisk()
        snippetsSnapshot = dictionaryStore.listSnippets()
        recentEmojisSnapshot = dictionaryStore.listRecentEmojis()
        offlinePackManager.refreshStates()
        offlinePackManager.preloadLocaleAsync(currentPrefs.activeLocale)
        modelManager.refreshStates()
        ensureScreenshotObserverIfPermitted()
        if (currentPrefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST) {
            voiceController.prepareOfflineSpeech(currentPrefs.activeLocale)
        }

        currentEditorInfo = info
        currentTargetPackage = info?.packageName.orEmpty()
        privateEditor = isPrivateEditor(info)
        inputViewActive = true
        syncSystemClipboard(updateStrip = true)
        val variation = (info?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
        isEmailField = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        isUrlField = variation == InputType.TYPE_TEXT_VARIATION_URI

        resolvedImeAction = resolveImeAction(info)
        imeActionLabel = resolvedImeAction.label
        layoutPage = KeyboardLayoutPage.LETTERS
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        pendingSmartUndoPair = null
        pendingSuggestionTrailingSpace = false

        consumePendingOcrText()
        refreshContextAndSuggestions()
        checkRecentScreenshot()
        voiceController.resumeIfWithinGrace(
            graceMs = currentPrefs.voiceRotationGraceMs,
            strictLanguageLock = currentPrefs.voiceStrictLanguageLock
        )
    }

    override fun onWindowShown() {
        super.onWindowShown()
        syncSystemClipboard(updateStrip = inputViewActive)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inputViewActive = false
        pasteExpiryJob?.cancel()
        pasteSuggestion = null
        if (finishingInput) clearAutofillSuggestions()
        cancelVoice(keepRotationResume = !finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    private fun isKeyboardDark(): Boolean {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (currentPrefs.darkMode) {
            DarkModePreference.SYSTEM -> systemDark || currentPrefs.themeMode == ThemeMode.AMOLED_DARK
            DarkModePreference.LIGHT -> false
            DarkModePreference.DARK -> true
        }
    }

    // ViewStyle.BaseBuilder carries a library restriction annotation in androidx.autofill 1.3.0
    // even though ViewStyle.Builder is the documented public builder API for IME inline chips.
    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!currentPrefs.inlineAutofillEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        val density = resources.displayMetrics.density
        val minW = (44f * density).toInt()
        val minH = (36f * density).toInt()
        val maxW = (260f * density).toInt()
        val maxH = (40f * density).toInt()

        val iconMinW = (36f * density).toInt()
        val iconMaxW = (44f * density).toInt()

        val dark = isKeyboardDark()
        val chipDrawable = if (dark) R.drawable.bg_autofill_chip_dark else R.drawable.bg_autofill_chip_light
        val titleColor = if (dark) 0xFFF1F3F4.toInt() else 0xFF1F1F1F.toInt()
        val subtitleColor = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        val padH = (12f * density).toInt()
        val padV = (4f * density).toInt()
        val marginH = (3f * density).toInt()
        val marginV = (2f * density).toInt()

        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(this, chipDrawable))
                    .setPadding(padH, padV, padH, padV)
                    .setLayoutMargin(marginH, marginV, marginH, marginV)
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(titleColor)
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(subtitleColor)
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setMaxWidth((20f * density).toInt())
                    .setMaxHeight((20f * density).toInt())
                    .setLayoutMargin(0, 0, (6f * density).toInt(), 0)
                    .build()
            )
            .setEndIconStyle(
                ImageViewStyle.Builder()
                    .setMaxWidth((16f * density).toInt())
                    .setMaxHeight((16f * density).toInt())
                    .setLayoutMargin((6f * density).toInt(), 0, 0, 0)
                    .build()
            )
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(this, chipDrawable))
                    .setPadding((8f * density).toInt(), padV, (8f * density).toInt(), padV)
                    .setLayoutMargin(marginH, marginV, marginH, marginV)
                    .build()
            )
            .setSingleIconChipIconStyle(
                ImageViewStyle.Builder()
                    .setMaxWidth((20f * density).toInt())
                    .setMaxHeight((20f * density).toInt())
                    .build()
            )
            .build()

        val stylesBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()

        val chipSpec = InlinePresentationSpec.Builder(Size(minW, minH), Size(maxW, maxH))
            .setStyle(stylesBundle)
            .build()
        val pinnedSpec = InlinePresentationSpec.Builder(Size(iconMinW, minH), Size(iconMaxW, maxH))
            .setStyle(stylesBundle)
            .build()

        return InlineSuggestionsRequest.Builder(listOf(chipSpec, pinnedSpec))
            .setMaxSuggestionCount(6)
            .setSupportedLocales(LocaleList.getDefault())
            .build()
    }

    private fun clearAutofillSuggestions() {
        inlineRequestGeneration++
        inlineAutofillViews = emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        clearAutofillSuggestions()
        if (!currentPrefs.inlineAutofillEnabled || currentInputConnection == null) return false
        val suggestions = response.inlineSuggestions
            .take(6)
            .sortedBy { it.info.isPinned }
        if (suggestions.isEmpty()) return false

        val generation = inlineRequestGeneration
        val density = resources.displayMetrics.density
        val chipHeightPx = (38f * density).toInt()
        val executor = ContextCompat.getMainExecutor(this)
        val rendered = arrayOfNulls<View>(suggestions.size)
        suggestions.forEachIndexed { index, suggestion ->
            val size = if (suggestion.info.isPinned) {
                Size(chipHeightPx, chipHeightPx)
            } else {
                Size(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, chipHeightPx)
            }
            suggestion.inflate(this, size, executor) { view ->
                if (generation == inlineRequestGeneration && currentPrefs.inlineAutofillEnabled && view != null) {
                    view.setZOrderedOnTop(false)
                    rendered[index] = view
                    inlineAutofillViews = rendered.filterNotNull()
                }
            }
        }
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        voiceController.markConfigurationPause()
        super.onConfigurationChanged(newConfig)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (panelMode == KeyboardPanelMode.KEYS && (oldSelStart != newSelStart || oldSelEnd != newSelEnd)) {
            refreshContextAndSuggestions()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            LocalInferenceEngine.unloadAll()
        } else if (level >= TRIM_MEMORY_UI_HIDDEN) {
            LocalInferenceEngine.unloadWhisper()
        }
    }

    override fun onDestroy() {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.removePrimaryClipChangedListener(clipboardListener)
        }
        ScreenshotMonitor.unregisterScreenshotObserver(this, screenshotObserver)
        screenshotObserver = null
        LocalInferenceEngine.unloadAll()
        voiceController.destroy()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun updatePref(transform: (TapwingPrefs) -> TapwingPrefs) {
        serviceScope.launch {
            prefsRepo.update(transform)
        }
    }

    private fun attachTreeOwners(view: View?) {
        view ?: return
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    private fun resolveImeAction(info: EditorInfo?): ResolvedImeAction =
        ImeActionResolver.resolve(
            imeOptions = info?.imeOptions ?: 0,
            inputType = info?.inputType ?: 0,
            alwaysNewline = currentPrefs.enterKeyAlwaysNewline,
            packageName = info?.packageName.orEmpty()
        )

    private fun secondaryLocales(): List<String> {
        return if (currentPrefs.multilingualPredictions) {
            currentPrefs.enabledLocales.filter { it != currentPrefs.activeLocale }
        } else {
            emptyList()
        }
    }

    private fun handleKeyPress(key: KeySpec, output: String) {
        voiceController.clearError()
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyActionType.SHIFT -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.ONCE
                    ShiftState.ONCE -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
            }
            KeyActionType.BACKSPACE -> handleBackspace()
            KeyActionType.SYMBOLS_TOGGLE -> {
                ic.finishComposingText()
                pendingSmartUndoPair = null
                pendingSuggestionTrailingSpace = false
                layoutPage = if (layoutPage == KeyboardLayoutPage.LETTERS) {
                    KeyboardLayoutPage.SYMBOLS
                } else {
                    KeyboardLayoutPage.LETTERS
                }
                if (layoutPage == KeyboardLayoutPage.LETTERS) {
                    refreshContextAndSuggestions()
                }
            }
            KeyActionType.SYMBOLS_EXT_TOGGLE -> {
                ic.finishComposingText()
                pendingSmartUndoPair = null
                pendingSuggestionTrailingSpace = false
                layoutPage = if (layoutPage == KeyboardLayoutPage.SYMBOLS_EXT) {
                    KeyboardLayoutPage.SYMBOLS
                } else {
                    KeyboardLayoutPage.SYMBOLS_EXT
                }
            }
            KeyActionType.EMOJI_PANEL -> {
                val before = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty()
                lastWordBeforeEmoji = SuggestionEngine.extractPreviousWord(before)
                panelMode = KeyboardPanelMode.EMOJI
            }
            KeyActionType.LANGUAGE_SWITCH -> toggleLocale()
            KeyActionType.ENTER -> {
                pendingSmartUndoPair = null
                pendingSuggestionTrailingSpace = false
                learnCompletedTrailingWord()
                if (resolvedImeAction.insertNewlineDirectly) {
                    ic.commitText("\n", 1)
                } else {
                    val performed = (resolvedImeAction.editorActionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
                        ic.performEditorAction(resolvedImeAction.editorActionId)) ||
                        sendDefaultEditorAction(true)
                    if (!performed) {
                        if (resolvedImeAction.sendEnterKeyFallback) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                        } else {
                            ic.commitText("\n", 1)
                        }
                    }
                }
                refreshContextAndSuggestions()
            }
            KeyActionType.SPACE -> handleSpaceKey()
            KeyActionType.COMMA, KeyActionType.PERIOD, KeyActionType.CHAR -> {
                pendingSmartUndoPair = null
                if (key.type == KeyActionType.PERIOD || key.type == KeyActionType.COMMA ||
                    output in listOf(".", ",", "!", "?", ":", ";")
                ) {
                    // Clean up trailing space added automatically by a suggestion chip
                    if (pendingSuggestionTrailingSpace) {
                        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                        if (before.endsWith(" ")) {
                            ic.deleteSurroundingText(1, 0)
                        }
                    }
                    pendingSuggestionTrailingSpace = false
                    maybeAutoCorrectTrailingWord()
                    learnCompletedTrailingWord()
                } else {
                    pendingSuggestionTrailingSpace = false
                }
                ic.commitText(output, 1)
                statsStore.recordKeystroke(output.length, currentPrefs.incognitoMode)
                undoManager.notifyUnrelatedTyping()
                proactiveUndoState = undoManager.activeBanner()
                if (shiftState == ShiftState.ONCE) {
                    shiftState = ShiftState.OFF
                }
                refreshContextAndSuggestions()
            }
        }
    }

    private fun handleSwipeWord(points: List<SwipePoint>, keyCenters: List<KeyCenter>) {
        if (!currentPrefs.glideTypingEnabled) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val atStart = SuggestionEngine.isAtSentenceStart(before, "")
        val prevWord = SuggestionEngine.extractPreviousWord(before)

        val candidates = SwipeGestureDecoder.decodeSwipe(
            points = points,
            keyCenters = keyCenters,
            locale = currentPrefs.activeLocale,
            previousWord = prevWord,
            dictionaryStore = dictionaryStore.takeUnless { currentPrefs.incognitoMode },
            secondaryLocales = secondaryLocales(),
            atSentenceStart = atStart,
            offlinePack = offlinePackManager.getLoadedPack(currentPrefs.activeLocale)
        )
        val top = candidates.firstOrNull() ?: return
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val insertion = (if (needsSpace) " " else "") + top + " "
        ic.commitText(insertion, 1)
        statsStore.recordSwipeWord(top.length, currentPrefs.incognitoMode)
        pendingSuggestionTrailingSpace = true
        pendingSmartUndoPair = null

        val (p1, p2) = SuggestionEngine.extractPreviousTwoWords(before)
        dictionaryStore.recordTypedWord(
            word = top,
            previousWord = p1.takeIf { it.isNotEmpty() },
            learningEnabled = currentPrefs.learningEnabled,
            incognito = currentPrefs.incognitoMode,
            previousTwoWord = p2.takeIf { it.isNotEmpty() },
            wasSentenceStart = atStart,
            locale = currentPrefs.activeLocale,
            appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null
        )

        undoManager.record(
            beforeText = "",
            afterText = insertion,
            reason = UndoReason.BULK_DELETE,
            label = if (KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de") {
                "Wischwort rückgängig"
            } else {
                "Undo swipe"
            }
        )
        proactiveUndoState = undoManager.activeBanner()
        if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF

        refreshContextAndSuggestions()
        // Surface alternative swipe candidates so user can swap with 1 tap!
        if (candidates.size > 1) {
            suggestions = candidates.drop(1).map { alt ->
                SuggestionChip(
                    text = alt,
                    isCorrection = false,
                    replaceCharCount = top.length + 1
                )
            }
            isTyping = true
        }
    }

    private fun handleSpaceKey() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        pendingSuggestionTrailingSpace = false

        if (currentPrefs.doubleSpacePeriod && (now - lastSpaceTapMs) <= 320L &&
            before.endsWith(" ") && before.length >= 2 && before[before.length - 2].isLetterOrDigit()
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTapMs = 0L
            pendingSmartUndoPair = null
            refreshContextAndSuggestions()
            return
        }

        lastSpaceTapMs = now
        val correctedPair = maybeAutoCorrectTrailingWord()
        learnCompletedTrailingWord()
        ic.commitText(" ", 1)
        if (correctedPair != null) {
            pendingSmartUndoPair = correctedPair
        } else {
            pendingSmartUndoPair = null
            undoManager.notifyUnrelatedTyping()
            proactiveUndoState = undoManager.activeBanner()
        }
        refreshContextAndSuggestions()
    }

    private fun learnCompletedTrailingWord() {
        if (!currentPrefs.learningEnabled || currentPrefs.incognitoMode) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(140, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        if (trailing.isEmpty()) return

        val prefixBeforeTrailing = before.dropLast(trailing.length)
        val atSentenceStart = SuggestionEngine.isAtSentenceStart(prefixBeforeTrailing, "")
        val (prev1, prev2) = SuggestionEngine.extractPreviousTwoWords(prefixBeforeTrailing)
        statsStore.recordWordCompleted(currentPrefs.incognitoMode)
        dictionaryStore.recordTypedWord(
            word = trailing,
            previousWord = prev1.takeIf { it.isNotEmpty() },
            learningEnabled = currentPrefs.learningEnabled,
            incognito = currentPrefs.incognitoMode,
            previousTwoWord = prev2.takeIf { it.isNotEmpty() },
            wasSentenceStart = atSentenceStart,
            locale = currentPrefs.activeLocale,
            appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null
        )
    }

    private fun maybeAutoCorrectTrailingWord(): Pair<String, String>? {
        if (!currentPrefs.autoCorrect) return null
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        if (trailing.isBlank()) return null

        val atStart = SuggestionEngine.isAtSentenceStart(before, trailing)
        val correction = SuggestionEngine.autoCorrectionFor(
            word = trailing,
            locale = currentPrefs.activeLocale,
            dictionaryStore = dictionaryStore,
            atSentenceStart = atStart,
            secondaryLocales = secondaryLocales(),
            offlinePack = offlinePackManager.getLoadedPack(currentPrefs.activeLocale)
        )
        if (correction != null && correction != trailing) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(trailing.length, 0)
            ic.commitText(correction, 1)
            ic.endBatchEdit()
            statsStore.recordAutocorrect(currentPrefs.incognitoMode)
            val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
            undoManager.record(
                beforeText = trailing,
                afterText = correction,
                reason = UndoReason.AUTOCORRECT,
                label = if (de) "Rückgängig: $trailing" else "Undo: $trailing"
            )
            proactiveUndoState = undoManager.activeBanner()
            return trailing to correction
        }
        return null
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        pendingSuggestionTrailingSpace = false

        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            pendingSmartUndoPair = null
            ic.commitText("", 1)
            refreshContextAndSuggestions()
            return
        }

        // Smart Backspace-Undo: tapping Backspace once right after autocorrect reverts to original!
        val smartPair = pendingSmartUndoPair
        if (currentPrefs.smartBackspaceUndo && smartPair != null) {
            val (original, corrected) = smartPair
            val expectedSuffix = "$corrected "
            val before = ic.getTextBeforeCursor(expectedSuffix.length + 2, 0)?.toString().orEmpty()
            if (before.endsWith(expectedSuffix)) {
                pendingSmartUndoPair = null
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expectedSuffix.length, 0)
                ic.commitText(original, 1)
                ic.endBatchEdit()
                dictionaryStore.recordCorrectionRejected(
                    originalWord = original,
                    learningEnabled = currentPrefs.learningEnabled,
                    incognito = currentPrefs.incognitoMode
                )
                undoManager.dismissBanner()
                proactiveUndoState = null
                refreshContextAndSuggestions()
                return
            }
        }
        pendingSmartUndoPair = null

        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        refreshContextAndSuggestions()
    }

    private fun deletePreviousWordWithUndo() {
        pendingSmartUndoPair = null
        pendingSuggestionTrailingSpace = false
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val match = Regex("""\s*\S+\s*$""").find(before) ?: return
        val deleted = match.value
        ic.deleteSurroundingText(deleted.length, 0)
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        undoManager.record(
            beforeText = deleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = if (de) "Wort wiederherstellen" else "Restore word"
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions()
    }

    private fun moveCursorBy(steps: Int) {
        if (steps == 0) return
        val ic = currentInputConnection ?: return
        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun performTextEditAction(action: TextEditAction) {
        val ic = currentInputConnection ?: return
        val metaShift = if (textEditSelectionActive) KeyEvent.META_SHIFT_ON else 0
        fun sendNav(code: Int, meta: Int = metaShift) {
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        }

        when (action) {
            TextEditAction.LEFT -> sendNav(KeyEvent.KEYCODE_DPAD_LEFT)
            TextEditAction.RIGHT -> sendNav(KeyEvent.KEYCODE_DPAD_RIGHT)
            TextEditAction.UP -> sendNav(KeyEvent.KEYCODE_DPAD_UP)
            TextEditAction.DOWN -> sendNav(KeyEvent.KEYCODE_DPAD_DOWN)
            TextEditAction.LINE_START -> sendNav(KeyEvent.KEYCODE_MOVE_HOME)
            TextEditAction.LINE_END -> sendNav(KeyEvent.KEYCODE_MOVE_END)
            TextEditAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            TextEditAction.COPY -> ic.performContextMenuAction(android.R.id.copy)
            TextEditAction.CUT -> ic.performContextMenuAction(android.R.id.cut)
            TextEditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
        }
        refreshContextAndSuggestions()
    }

    private fun applySuggestionChip(chip: SuggestionChip) {
        if (privateEditor || chip !in suggestions) return
        val ic = currentInputConnection ?: return
        pendingSmartUndoPair = null
        val before = if (chip.replaceCharCount > 0) {
            ic.getTextBeforeCursor(chip.replaceCharCount, 0)?.toString().orEmpty()
        } else {
            ""
        }
        ic.beginBatchEdit()
        if (chip.replaceCharCount > 0) {
            ic.deleteSurroundingText(chip.replaceCharCount, 0)
        }
        val insertion = when {
            chip.isEmoji || chip.isMath -> chip.text
            else -> "${chip.text} "
        }
        ic.commitText(insertion, 1)
        ic.endBatchEdit()

        pendingSuggestionTrailingSpace = insertion.endsWith(" ") && !chip.isEmoji && !chip.isMath
        val saved = (chip.text.length - before.length).coerceAtLeast(0)
        if (chip.isShortcut) {
            statsStore.recordSnippetInserted(chip.text.length, currentPrefs.incognitoMode)
        } else if (!chip.isEmoji && !chip.isMath) {
            statsStore.recordSuggestionAccepted(saved, currentPrefs.incognitoMode)
        }

        if (!chip.isEmoji && !chip.isMath && !chip.isShortcut) {
            val cleanWord = chip.text.trim()
            val fullBefore = ic.getTextBeforeCursor(140, 0)?.toString().orEmpty()
            val contextBefore = fullBefore.dropLast(insertion.length)
            val atStart = SuggestionEngine.isAtSentenceStart(contextBefore, "")
            val (prev1, prev2) = SuggestionEngine.extractPreviousTwoWords(contextBefore)
            dictionaryStore.recordTypedWord(
                word = cleanWord,
                previousWord = prev1.takeIf { it.isNotEmpty() },
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode,
                previousTwoWord = prev2.takeIf { it.isNotEmpty() },
                wasSentenceStart = atStart,
                locale = currentPrefs.activeLocale,
                appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null
            )
        }

        if (before.isNotEmpty() && chip.isCorrection) {
            dictionaryStore.recordChosenCorrection(
                mistypedWord = before,
                chosenWord = chip.text,
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
        }

        if (before.isNotEmpty() && (chip.isCorrection || chip.isShortcut)) {
            val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
            undoManager.record(
                beforeText = before,
                afterText = insertion,
                reason = UndoReason.AUTOCORRECT,
                label = if (de) "Rückgängig: $before" else "Undo: $before"
            )
            proactiveUndoState = undoManager.activeBanner()
        }
        refreshContextAndSuggestions()
    }

    private fun performUndo() {
        pendingSmartUndoPair = null
        pendingSuggestionTrailingSpace = false
        val snapshot = undoManager.popUndo() ?: return
        proactiveUndoState = undoManager.activeBanner()
        val ic = currentInputConnection ?: return

        if (snapshot.reason == UndoReason.AUTOCORRECT) {
            dictionaryStore.recordCorrectionRejected(
                originalWord = snapshot.beforeText,
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
        }

        if (snapshot.reason == UndoReason.AI_REWRITE) {
            replaceWholeFieldText(snapshot.beforeText)
            writingOutput = null
            writingSourceText = snapshot.beforeText
            refreshContextAndSuggestions()
            return
        }

        if (snapshot.reason == UndoReason.BULK_DELETE) {
            if (snapshot.beforeText.isEmpty() && snapshot.afterText.isNotEmpty()) {
                val beforeCursor = ic.getTextBeforeCursor(snapshot.afterText.length + 4, 0)?.toString().orEmpty()
                if (beforeCursor.endsWith(snapshot.afterText)) {
                    ic.deleteSurroundingText(snapshot.afterText.length, 0)
                }
            } else {
                ic.commitText(snapshot.beforeText, 1)
            }
            refreshContextAndSuggestions()
            return
        }

        val beforeCursor = ic.getTextBeforeCursor(snapshot.afterText.length + 4, 0)?.toString().orEmpty()
        if (beforeCursor.endsWith(snapshot.afterText)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(snapshot.afterText.length, 0)
            ic.commitText(snapshot.beforeText, 1)
            ic.endBatchEdit()
        } else if (beforeCursor.endsWith("${snapshot.afterText} ")) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(snapshot.afterText.length + 1, 0)
            ic.commitText("${snapshot.beforeText} ", 1)
            ic.endBatchEdit()
        }
        refreshContextAndSuggestions()
    }

    private fun refreshContextAndSuggestions() {
        if (privateEditor) {
            isTyping = false
            suggestions = emptyList()
            return
        }
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        isTyping = trailing.isNotEmpty()
        val latestClipText = if (clipboardAllowed()) {
            clipboardStore.items.value.firstOrNull { !it.isImage }?.text.orEmpty()
        } else ""
        suggestions = SuggestionEngine.suggestionsFor(
            textBeforeCursor = before,
            locale = currentPrefs.activeLocale,
            includeEmoji = currentPrefs.candidateEmojiSuggestions,
            includeGrammarChips = currentPrefs.spellGrammarChips,
            includeMath = currentPrefs.inlineMathEnabled,
            dictionaryStore = dictionaryStore,
            secondaryLocales = secondaryLocales(),
            isEmailField = isEmailField,
            isUrlField = isUrlField,
            offlinePack = offlinePackManager.getLoadedPack(currentPrefs.activeLocale),
            appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null,
            latestClipboardText = latestClipText,
            userName = currentPrefs.snippetUserName,
            userEmail = currentPrefs.snippetUserEmail
        )

        if (layoutPage == KeyboardLayoutPage.LETTERS && shiftState != ShiftState.CAPS_LOCK) {
            val shouldCap = before.isEmpty() ||
                before.endsWith("\n") ||
                Regex("""[.!?]\s+$""").containsMatchIn(before)
            shiftState = if (shouldCap) ShiftState.ONCE else ShiftState.OFF
        }
    }

    private fun handleVoiceMicClicked() {
        val vs = voiceController.state.value
        if (vs.isListening || vs.isProcessing) {
            voiceController.finishAndCommit()
            return
        }
        if (!currentPrefs.voiceOnboardingSeen && currentPrefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP) {
            panelMode = KeyboardPanelMode.VOICE_ONBOARDING
            return
        }
        panelMode = KeyboardPanelMode.KEYS
        startVoice()
    }

    private fun startVoice() {
        val prefs = if (privateEditor) currentPrefs.copy(incognitoMode = true) else currentPrefs
        voicePrefs = prefs
        val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
        if (prefs.voiceEngine.usesCloudAudio && prefs.incognitoMode) {
            voiceController.finishExternalProcessing(
                if (de) "Cloud-Diktat ist in privaten Feldern aus." else "Cloud dictation is off in private fields."
            )
            return
        }
        val natural = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
        val customVocab = if (prefs.incognitoMode) emptySet() else dictionaryStore.allKnownVocabularyWords()
        val vocabHint = customVocab
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 2..32 && it.any(Char::isLetter) }
            .take(12)
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }

        fun buildCloudDictation(providerType: AiProviderType, modelId: String): CloudDictation? {
            val speechProv = CloudSpeechProvider.from(providerType, prefs.aiCustomEndpoint) ?: return null
            val key = secureKeyStorage.readKey(providerType).ifBlank {
                secureKeyStorage.readDictationKey(speechProv.name)
            }
            if (key.isBlank()) return null
            val effectiveModel = modelId.trim().ifEmpty { AiModelCatalog.defaultAudioModelFor(providerType) }
            return CloudDictation(
                provider = speechProv,
                apiKey = key,
                natural = natural,
                removeFillers = prefs.voiceRemoveFillers,
                autoPunctuate = prefs.voiceAutoPunctuate,
                customVocabulary = customVocab,
                model = effectiveModel
            )
        }

        val cloud = if (prefs.voiceEngine == VoiceEngineMode.CLOUD) {
            buildCloudDictation(prefs.dictationProvider, prefs.dictationModel)
        } else null
        val geminiKey = if (prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE) {
            secureKeyStorage.readKey(AiProviderType.GEMINI_API)
                .ifBlank { secureKeyStorage.readDictationKey(CloudSpeechProvider.GEMINI.name) }
                .takeIf(SecureKeyStorage::isValidApiKey)
        } else null
        val live = if (geminiKey != null && prefs.dictationProvider == AiProviderType.GEMINI_API) {
            GeminiLiveConfig(
                apiKey = geminiKey,
                smartFormatting = natural && prefs.voiceRemoveFillers && prefs.voiceAutoPunctuate,
                languageCode = KeyboardLocales.specFor(prefs.activeLocale).bcp47Tag.takeIf { prefs.voiceStrictLanguageLock }
            )
        } else null
        val explicitFallbackCloud = buildCloudDictation(
            prefs.dictationFallbackProvider,
            prefs.dictationFallbackModel
        )
        val fallbackCloud = explicitFallbackCloud ?: if (live != null) {
            CloudDictation(
                provider = CloudSpeechProvider.GEMINI,
                apiKey = geminiKey!!,
                natural = natural,
                removeFillers = prefs.voiceRemoveFillers,
                autoPunctuate = prefs.voiceAutoPunctuate,
                customVocabulary = customVocab,
                model = prefs.dictationModel.trim().ifEmpty { CloudDictation.GEMINI_MODEL }
            )
        } else null
        voiceController.startListening(
            locale = prefs.activeLocale,
            strictLanguageLock = prefs.voiceStrictLanguageLock,
            engineMode = prefs.voiceEngine,
            offlineOnly = prefs.incognitoMode,
            whisperModelId = prefs.selectedWhisperModelId,
            formatText = natural && prefs.voiceAutoPunctuate,
            cloud = cloud,
            live = live,
            fallbackCloud = fallbackCloud,
            vocabularyHint = vocabHint
        )
    }

    private fun cancelVoice(keepRotationResume: Boolean = false) {
        voiceEditGeneration++
        voiceEditJob?.cancel()
        voiceEditJob = null
        if (!keepRotationResume) voicePrefs = null
        if (::voiceController.isInitialized) voiceController.cancel(keepRotationResume)
    }

    private fun handleVoiceTranscript(raw: String, alreadyFormatted: Boolean) {
        val prefs = voicePrefs ?: return
        if (!inputViewActive) return
        val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
        if (prefs.voiceMode == VoiceDictationMode.STANDARD) {
            insertVoiceText(raw, prefs)
            return
        }
        val parsed = NaturalVoiceCleaner.parseCommandOrClean(
            rawTranscript = raw,
            locale = prefs.activeLocale,
            removeFillers = prefs.voiceRemoveFillers,
            autoPunctuate = prefs.voiceAutoPunctuate,
            dictionaryStore = dictionaryStore.takeUnless { prefs.incognitoMode }
        )
        val generation = voiceEditGeneration
        val cloudSet = com.tapwing.keyboard.ai.WritingToolsEngine.CLOUD_PROVIDERS
        val processingPrefs = if (prefs.incognitoMode) {
            prefs.copy(
                aiProvider = if (prefs.aiProvider in cloudSet) AiProviderType.LOCAL_RULES else prefs.aiProvider,
                aiFallbackProvider = if (prefs.aiFallbackProvider in cloudSet) AiProviderType.LOCAL_RULES else prefs.aiFallbackProvider,
                cleanupProvider = if (prefs.cleanupProvider in cloudSet) AiProviderType.LOCAL_RULES else prefs.cleanupProvider,
                cleanupFallbackProvider = if (prefs.cleanupFallbackProvider in cloudSet) AiProviderType.LOCAL_RULES else prefs.cleanupFallbackProvider
            )
        } else prefs

        when (parsed) {
            VoiceCommandResult.DeleteLastWord -> deletePreviousWord()
            VoiceCommandResult.DeleteLastSentence -> deletePreviousSentence(de)
            VoiceCommandResult.ClearAll -> clearWholeFieldWithUndo(de)
            VoiceCommandResult.UndoLast -> performUndo()
            is VoiceCommandResult.RewriteAction -> {
                val fieldText = readWholeFieldText()
                if (fieldText.isBlank()) return
                voiceController.beginExternalProcessing(if (de) "Überarbeite Text …" else "Rewriting text…")
                voiceEditJob = serviceScope.launch {
                    try {
                        val out = writingToolsEngine.runAction(fieldText, parsed.action, processingPrefs)
                        if (generation == voiceEditGeneration && inputViewActive &&
                            readWholeFieldText() == fieldText && out.rewrittenText.isNotBlank()
                        ) {
                            replaceWholeFieldText(out.rewrittenText)
                            undoManager.record(
                                beforeText = fieldText,
                                afterText = out.rewrittenText,
                                reason = UndoReason.AI_REWRITE,
                                label = if (de) "KI rückgängig" else "Undo AI edit"
                            )
                            proactiveUndoState = undoManager.activeBanner()
                        }
                    } finally {
                        if (generation == voiceEditGeneration) voiceController.finishExternalProcessing()
                    }
                }
            }
            is VoiceCommandResult.Text -> {
                val fallback = if (alreadyFormatted) raw else parsed.cleanedText
                val noAiCleanup = processingPrefs.cleanupProvider == AiProviderType.LOCAL_RULES &&
                    processingPrefs.cleanupFallbackProvider == AiProviderType.LOCAL_RULES
                if (!prefs.voiceAiPolish ||
                    noAiCleanup ||
                    alreadyFormatted ||
                    WritingToolsEngine.shouldSkipCloudPolish(raw, fallback)
                ) {
                    insertVoiceText(fallback, prefs)
                    return
                }
                voiceController.beginExternalProcessing(if (de) "Glätte Text …" else "Polishing text…")
                voiceEditJob = serviceScope.launch {
                    try {
                        val text = writingToolsEngine.cleanupVoiceTranscript(raw, processingPrefs, fallback)
                        if (generation == voiceEditGeneration && inputViewActive) {
                            insertVoiceText(text.rewrittenText, prefs)
                        }
                    } finally {
                        if (generation == voiceEditGeneration) voiceController.finishExternalProcessing()
                    }
                }
            }
        }
    }

    private fun insertVoiceText(text: String, prefs: TapwingPrefs) {
        if (text.isBlank()) return
        val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
        val inserted = commitTextWithSpaceSafety(text) ?: return
        voiceController.recordInsertionTiming()
        statsStore.recordVoiceDictation(text.length, prefs.incognitoMode)
        dictionaryStore.recordTextBatch(
            text = text,
            learningEnabled = prefs.learningEnabled,
            incognito = prefs.incognitoMode,
            locale = prefs.activeLocale,
            appPackage = if (prefs.perAppLearningEnabled) currentTargetPackage else null
        )
        undoManager.record(
            beforeText = "",
            afterText = inserted,
            reason = UndoReason.VOICE_INSERT,
            label = if (de) "Diktat rückgängig" else "Undo dictation"
        )
        proactiveUndoState = undoManager.activeBanner()
    }

    private fun deletePreviousWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val match = Regex("""\s*\S+\s*$""").find(before) ?: return
        ic.deleteSurroundingText(match.value.length, 0)
        refreshContextAndSuggestions()
    }

    private fun deletePreviousSentence(de: Boolean) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(600, 0)?.toString().orEmpty()
        if (before.isBlank()) return
        val trimmedEnd = before.trimEnd()
        if (trimmedEnd.isEmpty()) return
        val boundarySearchEnd = (trimmedEnd.length - 1).coerceAtLeast(0)
        var cutIdx = -1
        for (i in boundarySearchEnd downTo 0) {
            val ch = trimmedEnd[i]
            if (ch == '\n' || (ch in ".!?" && i < trimmedEnd.lastIndex && trimmedEnd[i + 1].isWhitespace())) {
                cutIdx = i + 1
                break
            }
        }
        val deleteFrom = if (cutIdx >= 0) cutIdx else 0
        val deleted = before.substring(deleteFrom)
        if (deleted.isEmpty()) return
        ic.deleteSurroundingText(deleted.length, 0)
        undoManager.record(
            beforeText = deleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = if (de) "Satz wiederherstellen" else "Restore sentence"
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions()
    }

    private fun clearWholeFieldWithUndo(de: Boolean) {
        val fullText = readWholeFieldText()
        if (fullText.isBlank()) return
        replaceWholeFieldText("")
        undoManager.record(
            beforeText = fullText,
            afterText = "",
            reason = UndoReason.AI_REWRITE,
            label = if (de) "Text wiederherstellen" else "Restore text"
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions()
    }

    private fun openWritingTools() {
        if (!currentPrefs.aiOnboardingSeen) {
            panelMode = KeyboardPanelMode.WRITING_ONBOARDING
        } else {
            openWritingToolsScreen()
        }
    }

    private fun openWritingToolsScreen() {
        writingSourceText = readWholeFieldText()
        writingOutput = null
        writingAction = null
        panelMode = KeyboardPanelMode.WRITING_TOOLS
    }

    private fun runWritingAction(action: WritingAction) {
        val currentSource = readWholeFieldText().ifBlank { writingSourceText }
        writingSourceText = currentSource
        if (currentSource.isBlank()) return

        writingAction = action
        writingLoading = true
        if (currentPrefs.perAppLearningEnabled) {
            dictionaryStore.recordAppWritingAction(
                appPackage = currentTargetPackage,
                action = action,
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
        }
        serviceScope.launch {
            val out = writingToolsEngine.runAction(currentSource, action, currentPrefs)
            writingLoading = false
            writingOutput = out
        }
    }

    private fun applyWritingToolsResult(rewritten: String) {
        val before = readWholeFieldText().ifBlank { writingSourceText }
        replaceWholeFieldText(rewritten)
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        undoManager.record(
            beforeText = before,
            afterText = rewritten,
            reason = UndoReason.AI_REWRITE,
            label = if (de) "Schreibhilfe rückgängig" else "Undo rewrite"
        )
        proactiveUndoState = undoManager.activeBanner()
        writingSourceText = rewritten
        panelMode = KeyboardPanelMode.KEYS
        refreshContextAndSuggestions()
    }

    private fun readWholeFieldText(): String {
        val ic = currentInputConnection ?: return ""
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) return selected
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
        if (!extracted.isNullOrBlank()) return extracted
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        return before + after
    }

    private fun replaceWholeFieldText(newText: String) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText(newText, 1)
            return
        }
        ic.beginBatchEdit()
        val beforeLen = ic.getTextBeforeCursor(4000, 0)?.length ?: 0
        val afterLen = ic.getTextAfterCursor(4000, 0)?.length ?: 0
        if (beforeLen > 0 || afterLen > 0) {
            ic.deleteSurroundingText(beforeLen, afterLen)
        }
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    private fun commitTextWithSpaceSafety(text: String): String? {
        if (text.isEmpty()) return null
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace() && !text.first().isWhitespace()
        val finalCommit = if (needsLeadingSpace) " $text" else text
        if (!ic.commitText(finalCommit, 1)) return null
        refreshContextAndSuggestions()
        return finalCommit
    }

    private fun consumePendingOcrText() {
        val pending = OcrResultBus.consume() ?: return
        commitTextWithSpaceSafety(pending)
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        undoManager.record(
            beforeText = "",
            afterText = pending,
            reason = UndoReason.OCR_INSERT,
            label = if (de) "OCR rückgängig" else "Undo OCR"
        )
        proactiveUndoState = undoManager.activeBanner()
    }

    private fun checkRecentScreenshot() {
        if (!currentPrefs.screenshotSuggestionChip && !currentPrefs.autoCopyScreenshots) {
            recentScreenshot = null
            return
        }
        if (!ScreenshotMonitor.hasMediaPermission(this)) return
        serviceScope.launch {
            val found = ScreenshotMonitor.findRecentScreenshot(this@TapwingInputMethodService) ?: return@launch
            val uriKey = "${found.uri}_${found.dateAddedSec}"
            if (uriKey == lastHandledScreenshotUri) {
                if (currentPrefs.screenshotSuggestionChip && recentScreenshot == null) {
                    recentScreenshot = found
                }
                return@launch
            }
            lastHandledScreenshotUri = uriKey

            val localFile = ScreenshotMonitor.cacheImageLocally(this@TapwingInputMethodService, found.uri)
            val contentUri = if (localFile != null) {
                if (currentPrefs.autoCopyScreenshots && !currentPrefs.incognitoMode) {
                    ScreenshotMonitor.copyImageToSystemClipboard(this@TapwingInputMethodService, localFile)
                }
                ScreenshotMonitor.fileProviderUri(this@TapwingInputMethodService, localFile)
            } else {
                found.uri
            }

            val ocrText = if (localFile != null) {
                ScreenshotMonitor.extractTextFromFile(this@TapwingInputMethodService, localFile)
            } else {
                ScreenshotMonitor.extractTextFromImageUri(this@TapwingInputMethodService, found.uri)
            }

            if (localFile != null && currentPrefs.clipboardEnabled && !currentPrefs.incognitoMode) {
                clipboardStore.addImageClip(
                    imagePath = localFile.absolutePath,
                    mimeType = "image/png",
                    displayLabel = found.displayName,
                    ocrText = ocrText,
                    retention = currentPrefs.clipboardRetention,
                    maxUnpinnedItems = currentPrefs.clipboardMaxItems
                )
            }

            if (currentPrefs.screenshotSuggestionChip) {
                recentScreenshot = found.copy(
                    uri = contentUri,
                    localImagePath = localFile?.absolutePath,
                    ocrText = ocrText.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    private fun handleScreenshotPasteImage(shot: RecentScreenshot) {
        serviceScope.launch {
            val committed = commitImageToEditor(shot.localImagePath, shot.uri, "image/png")
            if (!committed) {
                val ocr = shot.ocrText ?: ScreenshotMonitor.extractTextFromImageUri(this@TapwingInputMethodService, shot.uri)
                if (ocr.isNotBlank()) {
                    commitTextWithSpaceSafety(ocr)
                }
            }
            recentScreenshot = null
        }
    }

    private fun handleScreenshotPasteOcr(shot: RecentScreenshot) {
        serviceScope.launch {
            val ocr = shot.ocrText ?: if (!shot.localImagePath.isNullOrBlank()) {
                ScreenshotMonitor.extractTextFromFile(this@TapwingInputMethodService, File(shot.localImagePath))
            } else {
                ScreenshotMonitor.extractTextFromImageUri(this@TapwingInputMethodService, shot.uri)
            }
            if (ocr.isNotBlank()) {
                commitTextWithSpaceSafety(ocr)
            }
            recentScreenshot = null
        }
    }

    private fun pasteImageClip(clip: ClipboardClip) {
        val path = clip.imagePath ?: return
        val file = File(path)
        if (!file.isFile) return
        val uri = ScreenshotMonitor.fileProviderUri(this, file)
        ScreenshotMonitor.copyImageToSystemClipboard(this, file)
        val committed = commitImageToEditor(path, uri, clip.mimeType ?: "image/png")
        if (!committed && !clip.ocrText.isNullOrBlank()) {
            commitTextWithSpaceSafety(clip.ocrText)
        }
    }

    private fun handleClipOcrPaste(clip: ClipboardClip) {
        val cachedOcr = clip.ocrText
        if (!cachedOcr.isNullOrBlank()) {
            commitTextWithSpaceSafety(cachedOcr)
            return
        }
        val path = clip.imagePath ?: return
        serviceScope.launch {
            val ocr = ScreenshotMonitor.extractTextFromFile(this@TapwingInputMethodService, File(path))
            if (ocr.isNotBlank()) {
                clipboardStore.updateClipOcrText(
                    id = clip.id,
                    ocrText = ocr,
                    retention = currentPrefs.clipboardRetention,
                    maxUnpinnedItems = currentPrefs.clipboardMaxItems
                )
                commitTextWithSpaceSafety(ocr)
            }
        }
    }

    private fun commitImageToEditor(localPath: String?, contentUri: Uri, mimeType: String): Boolean {
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        val mimes = EditorInfoCompat.getContentMimeTypes(info)
        val supportsImages = mimes.any {
            ClipDescription.compareMimeTypes(it, mimeType) ||
                ClipDescription.compareMimeTypes(it, "image/*")
        }
        val targetUri = if (!localPath.isNullOrBlank() && File(localPath).isFile) {
            ScreenshotMonitor.fileProviderUri(this, File(localPath))
        } else {
            contentUri
        }
        if (!supportsImages) return false

        val desc = ClipDescription("Tapwing Screenshot", arrayOf(mimeType))
        val contentInfo = InputContentInfoCompat(targetUri, desc, null)
        return runCatching {
            InputConnectionCompat.commitContent(
                ic,
                info,
                contentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
        }.getOrDefault(false)
    }

    private fun toggleLocale() {
        val next = KeyboardLocales.nextEnabledLocale(currentPrefs.activeLocale, currentPrefs.enabledLocales)
        updatePref { it.copy(activeLocale = next) }
    }

    private fun cycleOneHanded() {
        val next = when (currentPrefs.oneHandedMode) {
            OneHandedMode.OFF -> OneHandedMode.RIGHT
            OneHandedMode.RIGHT -> OneHandedMode.LEFT
            OneHandedMode.LEFT -> OneHandedMode.OFF
        }
        updatePref { it.copy(oneHandedMode = next) }
        panelMode = KeyboardPanelMode.KEYS
    }

    private fun swapOneHandedSide() {
        val next = if (currentPrefs.oneHandedMode == OneHandedMode.RIGHT) {
            OneHandedMode.LEFT
        } else {
            OneHandedMode.RIGHT
        }
        updatePref { it.copy(oneHandedMode = next) }
    }

    private fun launchScanTextActivity() {
        val intent = Intent(this, ScanTextActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun launchSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun handleQuickBarAction(action: QuickBarAction) {
        when (action) {
            QuickBarAction.WRITING_TOOLS -> openWritingTools()
            QuickBarAction.CLIPBOARD -> {
                syncSystemClipboard(updateStrip = false)
                panelMode = KeyboardPanelMode.CLIPBOARD
            }
            QuickBarAction.TEXT_EDIT -> panelMode = KeyboardPanelMode.TEXT_EDIT
            QuickBarAction.SCAN_TEXT -> launchScanTextActivity()
            QuickBarAction.SETTINGS -> launchSettingsActivity()
            QuickBarAction.RESIZE -> panelMode = KeyboardPanelMode.RESIZE
            QuickBarAction.EMOJI -> panelMode = KeyboardPanelMode.EMOJI
            QuickBarAction.ONE_HANDED -> cycleOneHanded()
            QuickBarAction.INCOGNITO -> {
                updatePref { it.copy(incognitoMode = !it.incognitoMode) }
                panelMode = KeyboardPanelMode.KEYS
            }
            QuickBarAction.QUICK_SEARCH -> {
                quickSearchInitial = readWholeFieldText().take(120)
                panelMode = KeyboardPanelMode.QUICK_SEARCH
            }
            QuickBarAction.APPLE_MODE -> updatePref { it.copy(appleModeEnabled = !it.appleModeEnabled) }
            QuickBarAction.LANGUAGE -> toggleLocale()
            QuickBarAction.UNDO -> performUndo()
            QuickBarAction.SNIPPETS -> {
                snippetsSnapshot = dictionaryStore.listSnippets()
                panelMode = KeyboardPanelMode.SNIPPETS
            }
        }
    }

    private fun hideKeyboard() {
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        voiceController.cancel()
        requestHideSelf(0)
    }
}
