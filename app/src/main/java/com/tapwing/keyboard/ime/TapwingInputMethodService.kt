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
import com.tapwing.keyboard.clipboard.SENSITIVE_CLIP_EXTRA
import com.tapwing.keyboard.data.openQuickSearch
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
import com.tapwing.keyboard.undo.UndoAnchor
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
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_INLINE_SUGGESTIONS = 6
private const val PASTE_CHIP_LIFETIME_MS = 20_000L
private const val VERIFICATION_CODE_LIFETIME_MS = 90_000L
private const val SCREENSHOT_LIFETIME_SEC = 180L

private val PASTE_DISMISS_KEYS = setOf(
    KeyActionType.CHAR,
    KeyActionType.SPACE,
    KeyActionType.ENTER,
    KeyActionType.BACKSPACE,
    KeyActionType.COMMA,
    KeyActionType.PERIOD
)
private val PUNCTUATION_OUTPUTS = setOf(".", ",", "!", "?", ":", ";")
private val WORD_DELETE_REGEX = Regex("""\s*\S+\s*$""")

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
    private var clipboardStore by mutableStateOf(ClipboardStore(storageFile = null))
    private var clipboardReady = false
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
    private var lastSyncedClipKey: String? = null

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
    private var privateEditor by mutableStateOf(true)
    private var editorField = EditorField.UNKNOWN
    private val languageToolsAllowed: Boolean
        get() = editorField.allowsLearning(currentEditorInfo?.imeOptions ?: 0)
    private val isEmailField: Boolean get() = editorField == EditorField.EMAIL
    private val isUrlField: Boolean get() = editorField == EditorField.URL
    private var voicePrefs: TapwingPrefs? = null
    private var voiceEditJob: Job? = null
    private var voiceEditGeneration = 0
    private var lastVoiceInsertedWords: Set<String> = emptySet()

    private data class CopiedText(val text: String, val timestamp: Long, val verificationCode: Boolean)

    private var lastSpaceTapMs: Long = 0L
    private var lastHandledScreenshotUri: String? = null
    private var screenshotsAllowedSinceSec = 0L
    private var screenshotObserver: ContentObserver? = null

    // Smart typing trackers: 1-tap Backspace reverts autocorrect, and punctuation cleans chip space.
    private var pendingSmartUndoPair: Pair<String, String>? = null
    private var pendingSuggestionSpace: UndoAnchor? = null
    private var editorGeneration = 0L
    private var writingJob: Job? = null
    private var writingTarget: WritingTarget? = null
    private var lastWordBeforeEmoji: String = ""
    private var lastSuggestionKey: String = ""
    private val pendingSwipeDeletedChunks = mutableListOf<String>()

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        syncSystemClipboard(updateStrip = inputViewActive)
    }

    private fun syncSystemClipboard(updateStrip: Boolean = true) {
        if (!inputViewActive || !clipboardReady || !prefsLoaded) return
        val clip = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull()
        if (updateStrip) updatePasteSuggestion(clip)
        if (clip == null || clip.itemCount == 0) return
        val first = clip.getItemAt(0)
        val key = "${clip.description.timestamp}_${clip.itemCount}_${first.uri ?: first.text?.hashCode()}"
        if (key == lastSyncedClipKey) return
        lastSyncedClipKey = key
        if (!clipboardAllowed() || clip.description.isSensitive()) {
            dismissedCopyTimestamp = clip.description.timestamp
            return
        }

        val uri = first.uri
        if (uri != null && clip.description.hasMimeType("image/*")) {
            if (ScreenshotMonitor.isSelfCopiedEvent(uri)) return
            val generation = editorGeneration
            serviceScope.launch {
                val image = ScreenshotMonitor.cacheImageLocally(this@TapwingInputMethodService, uri)
                    ?: return@launch
                if (!isCurrentEditor(generation) || !clipboardAllowed()) {
                    image.file.delete()
                    if (lastSyncedClipKey == key) lastSyncedClipKey = null
                    return@launch
                }
                val saved = saveClipboardImage(image, getString(R.string.clipboard_copied_image)) ?: return@launch
                val ageSec = (System.currentTimeMillis() - clip.description.timestamp) / 1000L
                if (key == lastSyncedClipKey && currentPrefs.screenshotSuggestionChip &&
                    ageSec in 0..SCREENSHOT_LIFETIME_SEC
                ) {
                    val file = File(requireNotNull(saved.imagePath))
                    recentScreenshot = RecentScreenshot(
                        uri = ScreenshotMonitor.fileProviderUri(this@TapwingInputMethodService, file),
                        displayName = saved.text,
                        dateAddedSec = clip.description.timestamp / 1000L,
                        localImagePath = file.absolutePath,
                        ocrText = saved.ocrText
                    )
                }
            }
            return
        }

        val parts = (0 until clip.itemCount).mapNotNull { idx ->
            clip.getItemAt(idx).text?.toString()
        }
        val text = parts.joinToString("\n")
        if (text.isNotBlank()) {
            clipboardStore.addClip(text, currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
        }
    }

    private fun saveClipboardImage(image: ScreenshotMonitor.CachedImage, label: String): ClipboardClip? {
        val saved = clipboardStore.addImageClip(
            imagePath = image.file.path,
            displayLabel = label,
            retention = currentPrefs.clipboardRetention,
            maxUnpinnedItems = currentPrefs.clipboardMaxItems,
            imageDigest = image.digest
        )
        if (saved == null) image.file.delete()
        return saved
    }

    private fun isCurrentEditor(generation: Long): Boolean =
        generation == editorGeneration && inputViewActive && currentInputConnection != null

    private fun clipboardAllowed() =
        clipboardReady && prefsLoaded && currentPrefs.clipboardEnabled && !currentPrefs.incognitoMode && !privateEditor

    private fun ClipDescription.isSensitive() =
        extras?.getBoolean(SENSITIVE_CLIP_EXTRA) == true

    private fun updatePasteSuggestion(
        clip: android.content.ClipData? = if (clipboardAllowed()) {
            runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull()
        } else null
    ) {
        pasteExpiryJob?.cancel()
        pasteExpiryJob = null
        pasteSuggestion = if (clipboardAllowed() && inputViewActive && clip != null &&
            clip.itemCount > 0 && !clip.description.isSensitive() && !clip.description.hasMimeType("image/*") &&
            clip.description.timestamp != dismissedCopyTimestamp
        ) {
            val timestamp = clip.description.timestamp
            val item = clip.getItemAt(0)
            val rawText = item.text?.toString()
            rawText?.takeIf { it.isNotBlank() }?.let { text ->
                val code = SuggestionEngine.extractOtpFromClipboard(text)
                when {
                    code == null && System.currentTimeMillis() - timestamp in 0 until PASTE_CHIP_LIFETIME_MS ->
                        CopiedText(text, timestamp, verificationCode = false)
                    code != null && System.currentTimeMillis() - timestamp in 0 until VERIFICATION_CODE_LIFETIME_MS ->
                        CopiedText(code, timestamp, verificationCode = true)
                    else -> null
                }
            }
        } else null
        val shown = pasteSuggestion
        if (shown != null) {
            val lifetime = if (shown.verificationCode) VERIFICATION_CODE_LIFETIME_MS else PASTE_CHIP_LIFETIME_MS
            pasteExpiryJob = serviceScope.launch {
                delay((shown.timestamp + lifetime - System.currentTimeMillis()).coerceAtLeast(0))
                if (pasteSuggestion == shown) pasteSuggestion = null
            }
        }
    }

    private fun pasteFromSuggestion(timestamp: Long) {
        val shown = pasteSuggestion?.takeIf { it.timestamp == timestamp } ?: return
        updatePasteSuggestion()
        if (shown != pasteSuggestion) return
        if (commitTextWithUndo(shown.text, addSpace = false)) dismissPasteSuggestion(timestamp)
    }

    private fun dismissPasteSuggestion(timestamp: Long) {
        if (pasteSuggestion?.timestamp != timestamp) return
        dismissedCopyTimestamp = timestamp
        pasteExpiryJob?.cancel()
        pasteSuggestion = null
    }

    private fun editorFieldFor(info: EditorInfo?): EditorField =
        EditorField.detect(info?.inputType ?: InputType.TYPE_NULL, info?.fieldName, info?.hintText?.toString())

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
        serviceScope.launch {
            clipboardStore = withContext(Dispatchers.IO) {
                ClipboardStore.createForContext(this@TapwingInputMethodService)
            }
            clipboardReady = true
            if (prefsLoaded) clipboardStore.prune(currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
            if (inputViewActive) {
                syncSystemClipboard()
                checkRecentScreenshot()
            }
        }
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
                val previous = currentPrefs
                val hadPrefs = prefsLoaded
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
                if (inputViewActive && clipboardWasAllowed != clipboardAllowed()) {
                    if (clipboardAllowed()) {
                        syncSystemClipboard()
                    } else {
                        updatePasteSuggestion()
                    }
                }
                if (hadPrefs && previous.incognitoMode != prefs.incognitoMode) {
                    resetWritingRequest()
                    cancelVoice()
                    recentScreenshot = null
                    if (!prefs.incognitoMode) screenshotsAllowedSinceSec = System.currentTimeMillis() / 1000L + 1
                }
                if (inputViewActive) refreshContextAndSuggestions()
                if (!prefs.inlineAutofillEnabled) clearAutofillSuggestions()
                val languageChanged = !hadPrefs || previous.activeLocale != prefs.activeLocale
                if (languageChanged) offlinePackManager.preloadLocaleAsync(prefs.activeLocale)
                if ((languageChanged || previous.voiceEngine != prefs.voiceEngine) &&
                    prefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST
                ) voiceController.prepareOfflineSpeech(prefs.activeLocale)
                if (clipboardReady && (!hadPrefs || previous.clipboardRetention != prefs.clipboardRetention ||
                        previous.clipboardMaxItems != prefs.clipboardMaxItems)
                ) clipboardStore.prune(prefs.clipboardRetention, prefs.clipboardMaxItems)
                ensureScreenshotObserverIfPermitted()
                if (inputViewActive && (!hadPrefs || previous.autoCopyScreenshots != prefs.autoCopyScreenshots ||
                        previous.screenshotSuggestionChip != prefs.screenshotSuggestionChip)
                ) checkRecentScreenshot()
            }
        }

        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.addPrimaryClipChangedListener(clipboardListener)
        }
        ensureScreenshotObserverIfPermitted()
    }

    private fun ensureScreenshotObserverIfPermitted() {
        if (!prefsLoaded || (!currentPrefs.autoCopyScreenshots && !currentPrefs.screenshotSuggestionChip)) {
            ScreenshotMonitor.unregisterScreenshotObserver(this, screenshotObserver)
            screenshotObserver = null
            return
        }
        if (screenshotObserver != null) return
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
                    clips = clips.takeIf { clipboardAllowed() }.orEmpty(),
                    textEditSelectionActive = textEditSelectionActive,
                    quickSearchInitial = quickSearchInitial,
                    onKeyPress = ::handleKeyPress,
                    onSwipeWord = ::handleSwipeWord,
                    onCursorMove = ::moveCursor2D,
                    onDeletePreviousWord = ::deletePreviousWordSwipeStep,
                    onSwipeBackspaceEnd = ::finishBackspaceSwipe,
                    onSwitchLanguage = ::toggleLocale,
                    onToggleOneHandedSide = ::swapOneHandedSide,
                    onExitOneHanded = { updatePref { it.copy(oneHandedMode = OneHandedMode.OFF) } },
                    onSelectSuggestion = ::applySuggestionChip,
                    onTriggerUndo = { performUndo() },
                    onEditorUndo = { performUndo(allowHostUndo = true) },
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
                        if (clipboardAllowed()) {
                            syncSystemClipboard(updateStrip = false)
                            panelMode = KeyboardPanelMode.CLIPBOARD
                        }
                    },
                    onOpenSnippets = {
                        snippetsSnapshot = dictionaryStore.listSnippets()
                        panelMode = KeyboardPanelMode.SNIPPETS
                    },
                    onInsertSnippet = { _, resolvedText ->
                        if (languageToolsAllowed && commitTextWithUndo(resolvedText)) {
                            statsStore.recordSnippetInserted(resolvedText.length, currentPrefs.incognitoMode)
                            panelMode = KeyboardPanelMode.KEYS
                        }
                    },
                    onPasteClip = paste@{ clip ->
                        if (!clipboardAllowed()) return@paste
                        if (clip.isImage) {
                            pasteImageClip(clip)
                        } else {
                            commitTextWithUndo(clip.text, addSpace = false)
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
                        if (languageToolsAllowed && lastWordBeforeEmoji.isNotEmpty()) {
                            dictionaryStore.recordEmojiAfterWord(
                                word = lastWordBeforeEmoji,
                                emoji = emoji,
                                learningEnabled = currentPrefs.learningEnabled,
                                incognito = currentPrefs.incognitoMode
                            )
                        }
                        dictionaryStore.recordRecentEmoji(
                            emoji = emoji,
                            learningEnabled = languageToolsAllowed && currentPrefs.learningEnabled,
                            incognito = currentPrefs.incognitoMode
                        )
                        recentEmojisSnapshot = dictionaryStore.listRecentEmojis()
                        currentInputConnection?.commitText(emoji, 1)
                        pendingSmartUndoPair = null
                        pendingSuggestionSpace = null
                        refreshContextAndSuggestions()
                    },
                    onBackspace = { handleBackspace() },
                    onCycleOneHanded = ::cycleOneHanded,
                    onToggleIncognito = {
                        updatePref { it.copy(incognitoMode = !it.incognitoMode) }
                        panelMode = KeyboardPanelMode.KEYS
                    },
                    onOpenQuickSearch = ::openQuickSearchPanel,
                    onInsertSearchLink = { query ->
                        currentPrefs.quickSearchEngine.url(query)?.let { link ->
                            if (commitTextWithUndo(link)) panelMode = KeyboardPanelMode.KEYS
                        }
                    },
                    onOpenSearch = { query ->
                        if (openQuickSearch(this@TapwingInputMethodService, currentPrefs, query)) panelMode = KeyboardPanelMode.KEYS
                        else android.widget.Toast.makeText(this@TapwingInputMethodService, R.string.search_no_browser, android.widget.Toast.LENGTH_SHORT).show()
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
                    onToggleVoicePause = { voiceController.togglePause() },
                    onToggleVoiceMultilingual = { toggleVoiceMultilingual() },
                    onQuickVoicePolish = ::runQuickVoicePolish,
                    onDismissVoiceError = { voiceController.clearError() },
                    onHideKeyboard = ::hideKeyboard
                )
            }
        }
        return composeView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        val nextField = editorFieldFor(info)
        val nextPrivate = nextField.isPrivate(info?.imeOptions ?: 0)
        val changedEditor = !restarting || currentEditorInfo?.packageName != info?.packageName ||
            currentEditorInfo?.fieldId != info?.fieldId || privateEditor != nextPrivate
        if (changedEditor) {
            editorGeneration++
            resetWritingRequest()
            if (nextPrivate) recentScreenshot = null
            cancelVoice()
            undoManager.clear()
            proactiveUndoState = null
            lastSuggestionKey = ""
            lastVoiceInsertedWords = emptySet()
            clearAutofillSuggestions()
        }
        if (currentEditorInfo != null && privateEditor && !nextPrivate) {
            screenshotsAllowedSinceSec = System.currentTimeMillis() / 1000L + 1
        }
        currentEditorInfo = info
        currentTargetPackage = info?.packageName.orEmpty()
        editorField = nextField
        privateEditor = nextPrivate
        if (inputViewActive) {
            updatePasteSuggestion()
            refreshContextAndSuggestions()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        editorGeneration++
        resetWritingRequest()
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
        if (currentPrefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST ||
            currentPrefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER
        ) {
            voiceController.warmInstalledLocalModel(
                locale = currentPrefs.activeLocale,
                preferredModelId = currentPrefs.selectedWhisperModelId,
                preferMultilingual = currentPrefs.voiceMultilingualCodeSwitching
            )
        }

        currentEditorInfo = info
        currentTargetPackage = info?.packageName.orEmpty()
        editorField = editorFieldFor(info)
        privateEditor = editorField.isPrivate(info?.imeOptions ?: 0)
        inputViewActive = true
        if (clipboardReady && prefsLoaded) {
            clipboardStore.prune(currentPrefs.clipboardRetention, currentPrefs.clipboardMaxItems)
        }
        syncSystemClipboard(updateStrip = true)

        resolvedImeAction = resolveImeAction(info)
        imeActionLabel = resolvedImeAction.label
        layoutPage = SmartLayoutBehavior.initialPageForInputType(info?.inputType ?: InputType.TYPE_CLASS_TEXT)
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        pendingSmartUndoPair = null
        pendingSuggestionSpace = null
        pendingSwipeDeletedChunks.clear()
        lastSpaceTapMs = 0L
        lastSuggestionKey = ""

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
        if (::dictionaryStore.isInitialized) dictionaryStore.flush()
        if (::statsStore.isInitialized) statsStore.flush()
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
        val minW = (36f * density).toInt()
        val minH = (36f * density).toInt()
        val maxW = (resources.configuration.screenWidthDp - 88).coerceIn(96, 280).let { (it * density).toInt() }
        val maxH = (40f * density).toInt()

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
        return InlineSuggestionsRequest.Builder(listOf(chipSpec))
            .setMaxSuggestionCount(MAX_INLINE_SUGGESTIONS)
            .setSupportedLocales(LocaleList.getDefault())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInlineTooltipPresentationSpec(chipSpec)
                }
            }.build()
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
            .sortedByDescending { it.info.isPinned }
            .take(MAX_INLINE_SUGGESTIONS)
        if (suggestions.isEmpty()) return false

        val generation = inlineRequestGeneration
        val executor = ContextCompat.getMainExecutor(this)
        val rendered = arrayOfNulls<View>(suggestions.size)
        var requested = 0
        suggestions.forEachIndexed { index, suggestion ->
            val wrap = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            // InlineContentView's default surface order is required for provider touch events.
            val request = runCatching {
                suggestion.inflate(this, Size(wrap, wrap), executor) { view ->
                    if (generation == inlineRequestGeneration && currentPrefs.inlineAutofillEnabled && view != null) {
                        rendered[index] = view
                        inlineAutofillViews = rendered.filterNotNull()
                    }
                }
            }
            if (request.isSuccess) requested++
        }
        return requested > 0
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
            if (proactiveUndoState != null &&
                currentInputConnection?.readUndoAnchor()?.let(undoManager::matchingUndo) == null
            ) {
                undoManager.dismissBanner()
                proactiveUndoState = null
            }
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
        if (::dictionaryStore.isInitialized) dictionaryStore.flush()
        if (::statsStore.isInitialized) statsStore.flush()
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
        if (key.type in PASTE_DISMISS_KEYS) {
            pasteSuggestion?.takeUnless { it.verificationCode }?.let { dismissPasteSuggestion(it.timestamp) }
        }
        when (key.type) {
            KeyActionType.SHIFT -> {
                if (tryCycleSelectedTextCase(ic)) return
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
                pendingSuggestionSpace = null
                layoutPage = if (layoutPage == KeyboardLayoutPage.LETTERS) {
                    KeyboardLayoutPage.SYMBOLS
                } else {
                    KeyboardLayoutPage.LETTERS
                }
                if (layoutPage == KeyboardLayoutPage.LETTERS) {
                    refreshContextAndSuggestions(force = true)
                }
            }
            KeyActionType.SYMBOLS_EXT_TOGGLE -> {
                ic.finishComposingText()
                pendingSmartUndoPair = null
                pendingSuggestionSpace = null
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
                pendingSuggestionSpace = null
                learnCompletedTrailingWord()
                performEditorEnterAction(ic)
                refreshContextAndSuggestions()
            }
            KeyActionType.SPACE -> handleSpaceKey()
            KeyActionType.COMMA, KeyActionType.PERIOD, KeyActionType.CHAR -> {
                pendingSmartUndoPair = null
                var correction: Pair<String, String>? = null
                if (key.type == KeyActionType.PERIOD || key.type == KeyActionType.COMMA ||
                    output in PUNCTUATION_OUTPUTS
                ) {
                    // Remove only the space inserted by Tapwing at this caret position.
                    val spaceAnchor = pendingSuggestionSpace
                    if (spaceAnchor != null && spaceAnchor == ic.readUndoAnchor() && spaceAnchor.before.endsWith(" ")) {
                        ic.deleteSurroundingText(1, 0)
                    }
                    pendingSuggestionSpace = null
                    correction = maybeAutoCorrectTrailingWord()
                    learnCompletedTrailingWord()
                } else {
                    pendingSuggestionSpace = null
                }
                val committed = ic.commitText(output, 1)
                if (correction != null) recordAutocorrection(correction, if (committed) output else "")
                else undoManager.dismissBanner()
                statsStore.recordKeystroke(output.length, currentPrefs.incognitoMode)
                proactiveUndoState = undoManager.activeBanner()
                if (shiftState == ShiftState.ONCE) {
                    shiftState = ShiftState.OFF
                }
                refreshContextAndSuggestions()
            }
        }
    }

    private fun performEditorEnterAction(ic: android.view.inputmethod.InputConnection) {
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
    }

    private fun tryCycleSelectedTextCase(ic: android.view.inputmethod.InputConnection): Boolean {
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        if (selected.isEmpty() || selected.none(Char::isLetter)) return false
        val cycled = SelectedTextCaseCycler.cycle(selected, currentPrefs.activeLocale)
        if (cycled == selected) return false

        val selectionStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ic.getSurroundingText(0, 0, 0)?.let { s ->
                if (s.offset >= 0 && s.selectionStart >= 0) s.offset + s.selectionStart else null
            }
        } else null

        ic.beginBatchEdit()
        try {
            if (!ic.commitText(cycled, 1)) return false
            if (selectionStart != null) {
                ic.setSelection(selectionStart, selectionStart + cycled.length)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    private fun handleSwipeWord(points: List<SwipePoint>, keyCenters: List<KeyCenter>) {
        if (!languageToolsAllowed || !currentPrefs.glideTypingEnabled) return
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(80, 0)?.toString().orEmpty() }.getOrDefault("")
        val atStart = SuggestionEngine.isAtSentenceStart(before, "")
        val (p1, p2) = SuggestionEngine.extractPreviousTwoWords(before)
        val appPkg = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null

        val candidates = SwipeGestureDecoder.decodeSwipe(
            points = points,
            keyCenters = keyCenters,
            locale = currentPrefs.activeLocale,
            previousWord = p1,
            dictionaryStore = dictionaryStore.takeUnless { currentPrefs.incognitoMode },
            secondaryLocales = secondaryLocales(),
            atSentenceStart = atStart,
            offlinePack = offlinePackManager.getCachedPack(currentPrefs.activeLocale),
            previousTwoWord = p2.takeIf { it.isNotEmpty() },
            appPackage = appPkg
        )
        val top = candidates.firstOrNull() ?: return
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val insertion = (if (needsSpace) " " else "") + top + " "
        val selected = runCatching { ic.getSelectedText(0)?.toString().orEmpty() }.getOrDefault("")
        if (!runCatching { ic.commitText(insertion, 1) }.getOrDefault(false)) return
        statsStore.recordSwipeWord(top.length, currentPrefs.incognitoMode)
        pendingSuggestionSpace = ic.readUndoAnchor()
        pendingSmartUndoPair = null

        dictionaryStore.recordTypedWord(
            word = top,
            previousWord = p1.takeIf { it.isNotEmpty() },
            learningEnabled = currentPrefs.learningEnabled,
            incognito = currentPrefs.incognitoMode,
            previousTwoWord = p2.takeIf { it.isNotEmpty() },
            wasSentenceStart = atStart,
            locale = currentPrefs.activeLocale,
            appPackage = appPkg
        )

        recordEditorUndo(
            beforeText = selected,
            afterText = insertion,
            reason = UndoReason.INSERT,
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
        val before = runCatching { ic.getTextBeforeCursor(48, 0)?.toString().orEmpty() }.getOrDefault("")
        pendingSuggestionSpace = null

        if (languageToolsAllowed && currentPrefs.doubleSpacePeriod && (now - lastSpaceTapMs) <= 320L &&
            before.endsWith(" ") && before.length >= 2 && before[before.length - 2].isLetterOrDigit() &&
            ic.getSelectedText(0).isNullOrEmpty() && ic.replaceEditorText(1, 0, " ", ". ")
        ) {
            lastSpaceTapMs = 0L
            pendingSmartUndoPair = null
            recordEditorUndo("  ", ". ", UndoReason.INSERT, getString(R.string.btn_undo))
            refreshContextAndSuggestions()
            return
        }

        lastSpaceTapMs = now
        val correctedPair = maybeAutoCorrectTrailingWord()
        learnCompletedTrailingWord()
        val committed = runCatching { ic.commitText(" ", 1) }.getOrDefault(false)
        if (committed && SmartLayoutBehavior.shouldAutoReturnToLettersAfterSpace(layoutPage, before)) {
            layoutPage = KeyboardLayoutPage.LETTERS
        }
        if (correctedPair != null) {
            recordAutocorrection(correctedPair, if (committed) " " else "")
            pendingSmartUndoPair = correctedPair.takeIf { committed }
        } else {
            pendingSmartUndoPair = null
            undoManager.notifyUnrelatedTyping()
            proactiveUndoState = undoManager.activeBanner()
        }
        refreshContextAndSuggestions()
    }

    private fun recordAutocorrection(correction: Pair<String, String>, delimiter: String) {
        val (original, corrected) = correction
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        recordEditorUndo(
            original + delimiter, corrected + delimiter, UndoReason.AUTOCORRECT,
            if (de) "Rückgängig: $original" else "Undo: $original",
            originalCorrectionWord = original
        )
    }

    private fun learnCompletedTrailingWord() {
        if (!languageToolsAllowed) return
        if (!currentPrefs.learningEnabled || currentPrefs.incognitoMode) return
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(140, 0)?.toString().orEmpty() }.getOrDefault("")
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
        if (!languageToolsAllowed || !currentPrefs.autoCorrect) return null
        val ic = currentInputConnection ?: return null
        val before = runCatching { ic.getTextBeforeCursor(96, 0)?.toString().orEmpty() }.getOrDefault("")
        val trailing = SuggestionEngine.extractTrailingWord(before)
        if (trailing.isBlank()) return null

        val prefixBeforeTrailing = before.dropLast(trailing.length)
        val atStart = SuggestionEngine.isAtSentenceStart(before, trailing)
        val (prev1, prev2) = SuggestionEngine.extractPreviousTwoWords(prefixBeforeTrailing)
        val correction = SuggestionEngine.autoCorrectionFor(
            word = trailing,
            locale = currentPrefs.activeLocale,
            dictionaryStore = dictionaryStore.takeUnless { currentPrefs.incognitoMode },
            atSentenceStart = atStart,
            secondaryLocales = secondaryLocales(),
            offlinePack = offlinePackManager.getCachedPack(currentPrefs.activeLocale),
            previousWord = prev1.takeIf { it.isNotEmpty() },
            previousTwoWord = prev2.takeIf { it.isNotEmpty() },
            appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null
        )
        if (correction != null && correction != trailing &&
            ic.getSelectedText(0).isNullOrEmpty() &&
            ic.replaceEditorText(trailing.length, 0, trailing, correction)
        ) {
            statsStore.recordAutocorrect(currentPrefs.incognitoMode)
            return trailing to correction
        }
        return null
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        pendingSuggestionSpace = null

        val selected = runCatching { ic.getSelectedText(0) }.getOrNull()
        if (!selected.isNullOrEmpty()) {
            pendingSmartUndoPair = null
            runCatching { ic.commitText("", 1) }
            refreshContextAndSuggestions()
            return
        }

        if (currentPrefs.smartBackspaceUndo && performUndo(smartBackspace = true)) return
        pendingSmartUndoPair = null

        val now = SystemClock.uptimeMillis()
        runCatching {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, 0))
        }
        refreshContextAndSuggestions()
    }

    private fun deletePreviousWordSwipeStep() {
        pendingSmartUndoPair = null
        pendingSuggestionSpace = null
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(60, 0)?.toString().orEmpty() }.getOrDefault("")
        val match = WORD_DELETE_REGEX.find(before) ?: return
        val deleted = match.value
        if (!ic.getSelectedText(0).isNullOrEmpty() || !runCatching { ic.deleteSurroundingText(deleted.length, 0) }.getOrDefault(false)) return
        pendingSwipeDeletedChunks.add(0, deleted)
    }

    private fun finishBackspaceSwipe() {
        if (pendingSwipeDeletedChunks.isEmpty()) return
        val count = pendingSwipeDeletedChunks.size
        val combinedDeleted = pendingSwipeDeletedChunks.joinToString(separator = "")
        pendingSwipeDeletedChunks.clear()
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        val label = when {
            count == 1 && de -> "Wort wiederherstellen"
            count == 1 -> "Restore word"
            de -> "$count Wörter wiederherstellen"
            else -> "Restore $count words"
        }
        recordEditorUndo(
            beforeText = combinedDeleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = label
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions(force = true)
    }

    private fun deletePreviousWordWithUndo() {
        pendingSmartUndoPair = null
        pendingSuggestionSpace = null
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(60, 0)?.toString().orEmpty() }.getOrDefault("")
        val match = WORD_DELETE_REGEX.find(before) ?: return
        val deleted = match.value
        if (!ic.getSelectedText(0).isNullOrEmpty() || !runCatching { ic.deleteSurroundingText(deleted.length, 0) }.getOrDefault(false)) return
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        recordEditorUndo(
            beforeText = deleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = if (de) "Wort wiederherstellen" else "Restore word"
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions()
    }

    private fun moveCursor2D(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val ic = currentInputConnection ?: return
        pendingSmartUndoPair = null
        pendingSuggestionSpace = null
        fun sendDpad(code: Int, count: Int) {
            repeat(count) {
                val now = SystemClock.uptimeMillis()
                runCatching {
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, 0))
                }
            }
        }
        if (dx != 0) {
            val hCode = if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            sendDpad(hCode, kotlin.math.abs(dx))
        }
        if (dy != 0) {
            val vCode = if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            sendDpad(vCode, kotlin.math.abs(dy))
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
        if (!ic.getSelectedText(0).isNullOrEmpty()) return
        pendingSmartUndoPair = null
        val before = if (chip.replaceCharCount > 0) {
            ic.getTextBeforeCursor(chip.replaceCharCount, 0)?.toString() ?: return
        } else ""
        if (before.length != chip.replaceCharCount) return
        val contextBefore = if (languageToolsAllowed) {
            ic.getTextBeforeCursor(160, 0)?.toString().orEmpty().dropLast(before.length)
        } else ""
        val insertion = if (chip.appendSpace && !chip.isEmoji && !chip.isMath) "${chip.text} " else chip.text
        if (!ic.replaceEditorText(before.length, 0, before, insertion)) return

        pendingSuggestionSpace = if (insertion.endsWith(" ") && !chip.isEmoji && !chip.isMath) ic.readUndoAnchor() else null
        val saved = (chip.text.length - before.length).coerceAtLeast(0)
        if (chip.isShortcut) {
            statsStore.recordSnippetInserted(chip.text.length, currentPrefs.incognitoMode)
        } else if (!chip.isEmoji && !chip.isMath) {
            statsStore.recordSuggestionAccepted(saved, currentPrefs.incognitoMode)
        }

        if (languageToolsAllowed && !chip.isEmoji && !chip.isMath && !chip.isShortcut && !chip.isGrammarFix) {
            val cleanWord = chip.text.trim()
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

        if (languageToolsAllowed && before.isNotEmpty() && chip.isCorrection) {
            dictionaryStore.recordChosenCorrection(
                mistypedWord = before,
                chosenWord = chip.text,
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
            if (before.lowercase() in lastVoiceInsertedWords) {
                dictionaryStore.recordVoiceCorrection(
                    heardWord = before,
                    correctedWord = chip.text,
                    learningEnabled = currentPrefs.learningEnabled,
                    incognito = currentPrefs.incognitoMode
                )
            }
        }

        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        recordEditorUndo(before, insertion, if (chip.isCorrection) UndoReason.AUTOCORRECT else UndoReason.INSERT,
            if (de) "Vorschlag rückgängig" else "Undo suggestion")
        refreshContextAndSuggestions()
    }

    private fun recordEditorUndo(
        beforeText: String,
        afterText: String,
        reason: UndoReason,
        label: String,
        originalCorrectionWord: String? = null
    ) {
        if (privateEditor) return
        val anchor = currentInputConnection?.readUndoAnchor() ?: return
        if (anchor.selected.isNotEmpty()) return
        undoManager.record(beforeText, afterText, reason, label, anchor = anchor,
            originalCorrectionWord = originalCorrectionWord)
        proactiveUndoState = undoManager.activeBanner()
    }

    private fun performUndo(smartBackspace: Boolean = false, allowHostUndo: Boolean = false): Boolean {
        if (privateEditor || (smartBackspace && pendingSmartUndoPair == null)) return false
        val ic = currentInputConnection ?: return false
        val snapshot = if (undoManager.canUndo()) ic.readUndoAnchor()?.let(undoManager::matchingUndo) else null
        if (snapshot == null) {
            if (smartBackspace) return false
            undoManager.dismissBanner()
            proactiveUndoState = null
            val undone = allowHostUndo && ic.performContextMenuAction(android.R.id.undo)
            if (undone) refreshContextAndSuggestions()
            return undone
        }
        if (smartBackspace && (snapshot.reason != UndoReason.AUTOCORRECT ||
                pendingSmartUndoPair == null || !snapshot.afterText.endsWith(" "))) return false
        val actual = if (snapshot.afterText.isEmpty()) "" else {
            ic.getTextBeforeCursor(snapshot.afterText.length, 0)?.toString()
        }
        if (actual != snapshot.afterText) return false
        val original = if (smartBackspace) snapshot.beforeText.removeSuffix(" ") else snapshot.beforeText
        if (!ic.replaceEditorText(snapshot.afterText.length, 0, snapshot.afterText, original)) return false

        undoManager.popUndo()
        proactiveUndoState = undoManager.activeBanner()
        pendingSmartUndoPair = null
        pendingSuggestionSpace = null
        lastSpaceTapMs = 0L
        if (snapshot.reason == UndoReason.AUTOCORRECT && languageToolsAllowed) {
            dictionaryStore.recordCorrectionRejected(
                originalWord = snapshot.originalCorrectionWord ?: snapshot.beforeText.trim(),
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
        }
        if (snapshot.reason == UndoReason.AI_REWRITE) resetWritingRequest()
        refreshContextAndSuggestions()
        return true
    }

    private fun refreshContextAndSuggestions(force: Boolean = false) {
        val ic = currentInputConnection
        if (privateEditor || (!languageToolsAllowed && !isEmailField && !isUrlField) ||
            !ic?.getSelectedText(0).isNullOrEmpty()
        ) {
            lastSuggestionKey = ""
            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
            isTyping = false
            suggestions = emptyList()
            return
        }
        val before = ic?.getTextBeforeCursor(160, 0)?.toString().orEmpty()
        val contextKey = "${currentPrefs.activeLocale}|${currentPrefs.incognitoMode}|$isEmailField|$isUrlField|$before"
        if (!force && contextKey == lastSuggestionKey) return
        lastSuggestionKey = contextKey

        val trailing = SuggestionEngine.extractTrailingWord(before)
        isTyping = trailing.isNotEmpty() || isEmailField || isUrlField
        val latestClipText = if (clipboardAllowed()) {
            clipboardStore.items.value.firstOrNull { !it.isImage }?.text.orEmpty()
        } else ""
        suggestions = SuggestionEngine.suggestionsFor(
            textBeforeCursor = before,
            locale = currentPrefs.activeLocale,
            includeEmoji = currentPrefs.candidateEmojiSuggestions,
            includeGrammarChips = currentPrefs.spellGrammarChips,
            includeMath = currentPrefs.inlineMathEnabled,
            dictionaryStore = dictionaryStore.takeUnless { currentPrefs.incognitoMode },
            secondaryLocales = secondaryLocales(),
            isEmailField = isEmailField,
            isUrlField = isUrlField,
            offlinePack = offlinePackManager.getCachedPack(currentPrefs.activeLocale),
            appPackage = if (currentPrefs.perAppLearningEnabled) currentTargetPackage else null,
            latestClipboardText = latestClipText,
            userName = currentPrefs.snippetUserName,
            userEmail = currentPrefs.snippetUserEmail
        )

        if (layoutPage == KeyboardLayoutPage.LETTERS && shiftState != ShiftState.CAPS_LOCK) {
            val shouldCap = languageToolsAllowed && SuggestionEngine.isAtSentenceStart(before) &&
                trailing.isEmpty()
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

    private fun resolveMultilingualLanguages(prefs: TapwingPrefs): Pair<List<String>, String?> {
        if (!prefs.voiceMultilingualCodeSwitching) return emptyList<String>() to null
        val primarySpec = KeyboardLocales.specFor(prefs.activeLocale)
        val primaryBase = primarySpec.baseLanguage
        val tags = mutableListOf(primarySpec.bcp47Tag)
        val shortCodes = mutableListOf(primaryBase.uppercase())
        val enabled = prefs.enabledLocales
            .map { KeyboardLocales.specFor(it) }
            .filter { it.baseLanguage != primaryBase }
        enabled.firstOrNull()?.let { sec ->
            tags += sec.bcp47Tag
            shortCodes += sec.baseLanguage.uppercase()
        }
        // Always ensure at least a bilingual pair (add English when primary != en, else German)
        if (tags.size < 2) {
            val fallbackLocale = if (primaryBase == "en") "de" else "en"
            val sec = KeyboardLocales.specFor(fallbackLocale)
            tags += sec.bcp47Tag
            shortCodes += sec.baseLanguage.uppercase()
        }
        return tags to shortCodes.distinct().take(2).joinToString("+")
    }

    private fun toggleVoiceMultilingual() {
        val next = !currentPrefs.voiceMultilingualCodeSwitching
        updatePref {
            it.copy(
                voiceMultilingualCodeSwitching = next,
                voiceStrictLanguageLock = if (next) false else it.voiceStrictLanguageLock
            )
        }
        // If actively listening, restart seamlessly with the updated language mode
        if (voiceController.state.value.isListening) {
            cancelVoice()
            serviceScope.launch {
                kotlinx.coroutines.delay(90L)
                if (inputViewActive) startVoice()
            }
        }
    }

    private fun runQuickVoicePolish(action: WritingAction) {
        if (!languageToolsAllowed || !currentPrefs.aiWritingToolsEnabled) return
        val target = currentInputConnection?.readWritingTarget() ?: return
        val fieldText = target.text
        if (fieldText.isBlank()) return
        val prefs = currentPrefs
        val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
        val cloudSet = WritingToolsEngine.CLOUD_PROVIDERS
        val processingPrefs = if (prefs.incognitoMode) {
            prefs.copy(
                aiProvider = prefs.aiProvider.takeUnless { it in cloudSet } ?: AiProviderType.LOCAL_RULES,
                aiFallbackProvider = prefs.aiFallbackProvider.takeUnless { it in cloudSet } ?: AiProviderType.LOCAL_RULES
            )
        } else prefs
        voiceEditGeneration++
        val generation = voiceEditGeneration
        voiceEditJob?.cancel()
        voiceController.beginExternalProcessing(
            when (action) {
                WritingAction.BULLET_LIST -> if (de) "Erstelle Stichpunkte …" else "Formatting bullets…"
                WritingAction.CONCISE -> if (de) "Kürze Text …" else "Shortening…"
                WritingAction.PROFESSIONAL -> if (de) "Formuliere formell …" else "Polishing tone…"
                else -> if (de) "Überarbeite Text …" else "Rewriting…"
            }
        )
        voiceEditJob = serviceScope.launch {
            try {
                val appPkg = if (prefs.perAppLearningEnabled) currentTargetPackage else null
                val out = writingToolsEngine.runAction(fieldText, action, processingPrefs, appPackage = appPkg)
                val connection = currentInputConnection
                if (generation == voiceEditGeneration && inputViewActive && connection != null &&
                    out.rewrittenText.isNotBlank() && target.replace(connection, out.rewrittenText)
                ) {
                    recordEditorUndo(
                        beforeText = fieldText,
                        afterText = out.rewrittenText,
                        reason = UndoReason.AI_REWRITE,
                        label = if (de) "KI rückgängig" else "Undo AI edit"
                    )
                    proactiveUndoState = undoManager.activeBanner()
                    refreshContextAndSuggestions()
                }
            } finally {
                if (generation == voiceEditGeneration) voiceController.finishExternalProcessing()
            }
        }
    }

    private fun startVoice() {
        if (!languageToolsAllowed) return
        val prefs = currentPrefs
        voicePrefs = prefs
        val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
        if (prefs.voiceEngine.usesCloudAudio && prefs.incognitoMode) {
            voiceController.finishExternalProcessing(
                if (de) "Cloud-Diktat ist in privaten Feldern aus." else "Cloud dictation is off in private fields."
            )
            return
        }
        val natural = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
        val appPkg = if (prefs.perAppLearningEnabled) currentTargetPackage else null
        val customVocab = if (prefs.incognitoMode) emptySet()
        else dictionaryStore.topVocabularyForVoice(appPackage = appPkg, limit = 30)
        val vocabHint = customVocab
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 2..36 && it.any(Char::isLetter) }
            .take(20)
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
        val (multilingualTags, multilingualBadge) = resolveMultilingualLanguages(prefs)
        val isMultilingual = prefs.voiceMultilingualCodeSwitching && multilingualTags.size >= 2

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
                model = effectiveModel,
                multilingualLanguages = if (isMultilingual) multilingualTags else emptyList(),
                autoBulletLists = prefs.voiceAutoBulletLists
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
        val live = if (geminiKey != null) {
            GeminiLiveConfig(
                apiKey = geminiKey,
                smartFormatting = natural && prefs.voiceRemoveFillers && prefs.voiceAutoPunctuate,
                languageCode = KeyboardLocales.specFor(prefs.activeLocale).bcp47Tag.takeIf {
                    prefs.voiceStrictLanguageLock && !isMultilingual
                },
                languageCodes = if (isMultilingual) multilingualTags else emptyList()
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
                model = prefs.dictationModel.trim().takeIf {
                    prefs.dictationProvider == AiProviderType.GEMINI_API && it.isNotEmpty()
                } ?: CloudDictation.GEMINI_MODEL,
                multilingualLanguages = if (isMultilingual) multilingualTags else emptyList(),
                autoBulletLists = prefs.voiceAutoBulletLists
            )
        } else null
        voiceController.startListening(
            locale = prefs.activeLocale,
            strictLanguageLock = prefs.voiceStrictLanguageLock && !isMultilingual,
            engineMode = prefs.voiceEngine,
            offlineOnly = prefs.incognitoMode,
            whisperModelId = prefs.selectedWhisperModelId,
            formatText = natural && prefs.voiceAutoPunctuate,
            cloud = cloud,
            live = live,
            fallbackCloud = fallbackCloud,
            vocabularyHint = vocabHint,
            multilingual = isMultilingual,
            multilingualLabel = multilingualBadge
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
            dictionaryStore = dictionaryStore.takeUnless { prefs.incognitoMode },
            multilingual = prefs.voiceMultilingualCodeSwitching,
            autoBulletLists = prefs.voiceAutoBulletLists
        )
        val generation = voiceEditGeneration
        val appPkg = if (prefs.perAppLearningEnabled) currentTargetPackage else null
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
            VoiceCommandResult.DeleteLastWord -> deletePreviousWordWithUndo()
            VoiceCommandResult.DeleteLastSentence -> deletePreviousSentence(de)
            VoiceCommandResult.ClearAll -> clearWholeFieldWithUndo(de)
            VoiceCommandResult.UndoLast -> { performUndo() }
            VoiceCommandResult.SelectAll -> {
                currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            }
            VoiceCommandResult.CopyAll -> {
                currentInputConnection?.let { ic ->
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.performContextMenuAction(android.R.id.copy)
                }
            }
            VoiceCommandResult.PasteClipboard -> {
                currentInputConnection?.performContextMenuAction(android.R.id.paste)
                refreshContextAndSuggestions()
            }
            VoiceCommandResult.SendAction -> {
                val ic = currentInputConnection ?: return
                performEditorEnterAction(ic)
                refreshContextAndSuggestions()
            }
            is VoiceCommandResult.RewriteAction -> {
                val target = currentInputConnection?.readWritingTarget() ?: return
                val fieldText = target.text
                if (fieldText.isBlank()) return
                voiceController.beginExternalProcessing(if (de) "Überarbeite Text …" else "Rewriting text…")
                voiceEditJob = serviceScope.launch {
                    try {
                        val out = writingToolsEngine.runAction(fieldText, parsed.action, processingPrefs, appPackage = appPkg)
                        val connection = currentInputConnection
                        if (generation == voiceEditGeneration && inputViewActive && connection != null &&
                            out.rewrittenText.isNotBlank() && target.replace(connection, out.rewrittenText)
                        ) {
                            recordEditorUndo(
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
                val isLocalLlm = processingPrefs.cleanupProvider == AiProviderType.LOCAL_LLM
                voiceController.beginExternalProcessing(
                    when {
                        isLocalLlm && de -> "Lokale KI bereinigt …"
                        isLocalLlm -> "Local AI polishing…"
                        de -> "Glätte Text …"
                        else -> "Polishing text…"
                    }
                )
                voiceEditJob = serviceScope.launch {
                    try {
                        val text = writingToolsEngine.cleanupVoiceTranscript(
                            rawTranscript = raw,
                            prefs = processingPrefs,
                            ruleCleaned = fallback,
                            appPackage = appPkg
                        )
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
        if (!commitTextWithUndo(text, UndoReason.VOICE_INSERT, if (de) "Diktat rückgängig" else "Undo dictation")) return
        voiceController.recordInsertionTiming()
        statsStore.recordVoiceDictation(text.length, prefs.incognitoMode)
        lastVoiceInsertedWords = Regex("""[\p{L}]{2,}""")
            .findAll(text)
            .map { it.value.lowercase() }
            .toSet()
        dictionaryStore.recordTextBatch(
            text = text,
            learningEnabled = prefs.learningEnabled,
            incognito = prefs.incognitoMode,
            locale = prefs.activeLocale,
            appPackage = if (prefs.perAppLearningEnabled) currentTargetPackage else null
        )
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
        if (!ic.getSelectedText(0).isNullOrEmpty() || !ic.deleteSurroundingText(deleted.length, 0)) return
        recordEditorUndo(
            beforeText = deleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = if (de) "Satz wiederherstellen" else "Restore sentence"
        )
        proactiveUndoState = undoManager.activeBanner()
        refreshContextAndSuggestions()
    }

    private fun clearWholeFieldWithUndo(de: Boolean) {
        val connection = currentInputConnection ?: return
        val target = connection.readWritingTarget(useSelection = false) ?: return
        if (target.text.isEmpty() || !target.replace(connection, "")) return
        recordEditorUndo(target.text, "", UndoReason.BULK_DELETE,
            if (de) "Text wiederherstellen" else "Restore text")
        refreshContextAndSuggestions()
    }

    private fun openWritingTools() {
        if (!languageToolsAllowed || !currentPrefs.aiWritingToolsEnabled) return
        if (!currentPrefs.aiOnboardingSeen) {
            panelMode = KeyboardPanelMode.WRITING_ONBOARDING
        } else {
            openWritingToolsScreen()
        }
    }

    private fun resetWritingRequest() {
        writingJob?.cancel()
        writingJob = null
        writingTarget = null
        writingLoading = false
        writingOutput = null
        writingSourceText = ""
    }

    private fun openWritingToolsScreen() {
        if (!languageToolsAllowed) return
        resetWritingRequest()
        writingSourceText = readWholeFieldText()
        writingAction = null
        panelMode = KeyboardPanelMode.WRITING_TOOLS
    }

    private fun runWritingAction(action: WritingAction) {
        if (!languageToolsAllowed) return
        val target = currentInputConnection?.readWritingTarget() ?: return
        if (target.text.isBlank()) return
        resetWritingRequest()
        writingTarget = target
        writingSourceText = target.text
        writingAction = action
        writingLoading = true
        val generation = editorGeneration
        val prefs = currentPrefs
        val appPkg = if (prefs.perAppLearningEnabled) currentTargetPackage else null
        val cloud = WritingToolsEngine.CLOUD_PROVIDERS
        val processingPrefs = if (prefs.incognitoMode) prefs.copy(
            aiProvider = prefs.aiProvider.takeUnless { it in cloud } ?: AiProviderType.LOCAL_RULES,
            aiFallbackProvider = prefs.aiFallbackProvider.takeUnless { it in cloud } ?: AiProviderType.LOCAL_RULES
        ) else prefs

        if (prefs.perAppLearningEnabled) {
            dictionaryStore.recordAppWritingAction(currentTargetPackage, action, prefs.learningEnabled, prefs.incognitoMode)
        }
        writingJob = serviceScope.launch {
            try {
                val out = writingToolsEngine.runAction(target.text, action, processingPrefs, appPackage = appPkg)
                if (generation == editorGeneration && writingTarget === target &&
                    currentInputConnection?.readWritingTarget() == target
                ) writingOutput = out
            } finally {
                if (writingTarget === target) writingLoading = false
            }
        }
    }

    private fun applyWritingToolsResult(rewritten: String) {
        if (!languageToolsAllowed || writingOutput?.rewrittenText != rewritten) return
        val connection = currentInputConnection ?: return
        val target = writingTarget ?: return
        if (!target.replace(connection, rewritten)) {
            openWritingToolsScreen()
            return
        }
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        recordEditorUndo(target.text, rewritten, UndoReason.AI_REWRITE,
            if (de) "Schreibhilfe rückgängig" else "Undo rewrite")
        resetWritingRequest()
        panelMode = KeyboardPanelMode.KEYS
        refreshContextAndSuggestions()
    }

    private fun readWholeFieldText(): String =
        currentInputConnection?.readWritingTarget()?.text.orEmpty()

    private fun commitTextWithUndo(
        text: String,
        reason: UndoReason = UndoReason.INSERT,
        label: String = getString(R.string.btn_undo),
        addSpace: Boolean = true
    ): Boolean {
        if (text.isEmpty()) return false
        val connection = currentInputConnection ?: return false
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val before = connection.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val needsSpace = addSpace && selected.isEmpty() && before.isNotEmpty() &&
            !before.last().isWhitespace() && !text.first().isWhitespace()
        val insertion = if (needsSpace) " $text" else text
        if (!connection.commitText(insertion, 1)) return false
        recordEditorUndo(selected, insertion, reason, label)
        refreshContextAndSuggestions()
        return true
    }

    private fun consumePendingOcrText() {
        val pending = OcrResultBus.consume() ?: return
        if (!languageToolsAllowed) return
        val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
        commitTextWithUndo(pending, UndoReason.OCR_INSERT, if (de) "OCR rückgängig" else "Undo OCR")
    }

    private fun checkRecentScreenshot() {
        if (!currentPrefs.screenshotSuggestionChip && !currentPrefs.autoCopyScreenshots) {
            recentScreenshot = null
            return
        }
        if (recentScreenshot?.let { System.currentTimeMillis() / 1000L - it.dateAddedSec > SCREENSHOT_LIFETIME_SEC } == true) {
            recentScreenshot = null
        }
        if (!prefsLoaded || !clipboardReady || !inputViewActive || privateEditor || currentPrefs.incognitoMode ||
            !ScreenshotMonitor.hasMediaPermission(this)) return
        val generation = editorGeneration
        val originalClipKey = lastSyncedClipKey
        serviceScope.launch {
            val found = ScreenshotMonitor.findRecentScreenshot(
                this@TapwingInputMethodService, maxAgeSec = SCREENSHOT_LIFETIME_SEC
            ) ?: return@launch
            if (!isCurrentEditor(generation) || privateEditor || currentPrefs.incognitoMode ||
                found.dateAddedSec < screenshotsAllowedSinceSec
            ) return@launch
            val key = "${found.uri}_${found.dateAddedSec}"
            if (key == lastHandledScreenshotUri) return@launch
            lastHandledScreenshotUri = key
            val image = if (currentPrefs.clipboardEnabled || currentPrefs.autoCopyScreenshots) {
                ScreenshotMonitor.cacheImageLocally(this@TapwingInputMethodService, found.uri)
            } else null
            if (!isCurrentEditor(generation) || privateEditor || currentPrefs.incognitoMode ||
                key != lastHandledScreenshotUri
            ) {
                image?.file?.delete()
                if (lastHandledScreenshotUri == key) lastHandledScreenshotUri = null
                return@launch
            }
            val saved = image?.takeIf { clipboardAllowed() }?.let { saveClipboardImage(it, found.displayName) }
            val local = saved?.imagePath?.let(::File)
            val copiedAt = runCatching {
                getSystemService(ClipboardManager::class.java)?.primaryClipDescription?.timestamp
            }.getOrNull()
            if (currentPrefs.autoCopyScreenshots && originalClipKey == lastSyncedClipKey &&
                (copiedAt == null || copiedAt < found.dateAddedSec * 1000L)
            ) {
                val copy = local ?: image?.file?.takeIf { it.isFile }
                if (copy != null) ScreenshotMonitor.copyImageToSystemClipboard(this@TapwingInputMethodService, copy)
            } else if (local == null) {
                image?.file?.delete()
            }
            if (currentPrefs.screenshotSuggestionChip) {
                recentScreenshot = found.copy(
                    uri = local?.let { ScreenshotMonitor.fileProviderUri(this@TapwingInputMethodService, it) } ?: found.uri,
                    localImagePath = local?.path,
                    ocrText = saved?.ocrText
                )
            }
        }
    }

    private fun handleScreenshotPasteImage(shot: RecentScreenshot) {
        if (privateEditor || currentPrefs.incognitoMode) return
        val mime = if (shot.localImagePath != null) "image/png" else contentResolver.getType(shot.uri) ?: "image/png"
        if (commitImageToEditor(shot.localImagePath, shot.uri, mime)) {
            if (recentScreenshot == shot) recentScreenshot = null
        } else {
            handleScreenshotPasteOcr(shot)
        }
    }

    private fun handleScreenshotPasteOcr(shot: RecentScreenshot) {
        if (privateEditor || currentPrefs.incognitoMode) return
        val generation = editorGeneration
        serviceScope.launch {
            val ocr = shot.ocrText ?: ScreenshotMonitor.extractTextFromImageUri(
                this@TapwingInputMethodService, shot.uri
            )
            if (isCurrentEditor(generation) && !privateEditor && !currentPrefs.incognitoMode && ocr.isNotBlank()) {
                commitTextWithUndo(ocr, UndoReason.OCR_INSERT)
                if (recentScreenshot == shot) recentScreenshot = null
            }
        }
    }

    private fun pasteImageClip(clip: ClipboardClip) {
        if (!clipboardAllowed()) return
        val path = clip.imagePath ?: return
        val file = File(path)
        if (!file.isFile) return
        val uri = ScreenshotMonitor.fileProviderUri(this, file)
        if (!commitImageToEditor(path, uri, clip.mimeType ?: "image/png")) {
            ScreenshotMonitor.copyImageToSystemClipboard(this, file)
            if (!clip.ocrText.isNullOrBlank()) commitTextWithUndo(clip.ocrText, UndoReason.OCR_INSERT)
        }
    }

    private fun handleClipOcrPaste(clip: ClipboardClip) {
        if (!clipboardAllowed()) return
        val cachedOcr = clip.ocrText
        if (!cachedOcr.isNullOrBlank()) {
            commitTextWithUndo(cachedOcr, UndoReason.OCR_INSERT)
            return
        }
        val path = clip.imagePath ?: return
        val generation = editorGeneration
        serviceScope.launch {
            val ocr = ScreenshotMonitor.extractTextFromFile(this@TapwingInputMethodService, File(path))
            if (isCurrentEditor(generation) && clipboardAllowed() && ocr.isNotBlank()) {
                clipboardStore.updateClipOcrText(
                    id = clip.id,
                    ocrText = ocr,
                    retention = currentPrefs.clipboardRetention,
                    maxUnpinnedItems = currentPrefs.clipboardMaxItems
                )
                commitTextWithUndo(ocr, UndoReason.OCR_INSERT)
            }
        }
    }

    private fun commitImageToEditor(localPath: String?, contentUri: Uri, mimeType: String): Boolean {
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        val mimes = EditorInfoCompat.getContentMimeTypes(info)
        val supportsImages = mimes.any { ClipDescription.compareMimeTypes(mimeType, it) }
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
        if (!languageToolsAllowed) return
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
                if (clipboardAllowed()) {
                    syncSystemClipboard(updateStrip = false)
                    panelMode = KeyboardPanelMode.CLIPBOARD
                }
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
            QuickBarAction.QUICK_SEARCH -> openQuickSearchPanel()
            QuickBarAction.APPLE_MODE -> updatePref { it.copy(appleModeEnabled = !it.appleModeEnabled) }
            QuickBarAction.LANGUAGE -> toggleLocale()
            QuickBarAction.UNDO -> performUndo(allowHostUndo = true)
            QuickBarAction.SNIPPETS -> {
                snippetsSnapshot = dictionaryStore.listSnippets()
                panelMode = KeyboardPanelMode.SNIPPETS
            }
        }
    }

    private fun openQuickSearchPanel() {
        quickSearchInitial = if (privateEditor || currentPrefs.incognitoMode) "" else {
            currentInputConnection?.getSelectedText(0)?.toString().orEmpty().take(120)
        }
        panelMode = KeyboardPanelMode.QUICK_SEARCH
    }

    private fun hideKeyboard() {
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        voiceController.cancel()
        requestHideSelf(0)
    }
}
