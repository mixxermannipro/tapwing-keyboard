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
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsEngine
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.clipboard.ClipboardStore
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.TypingStatsStore
import com.tapwing.keyboard.data.VoiceDictationMode
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
import com.tapwing.keyboard.voice.NaturalVoiceCleaner
import com.tapwing.keyboard.voice.TapwingVoiceController
import com.tapwing.keyboard.voice.VoiceCommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

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
    private var imeActionLabel by mutableStateOf(KeyActionLabel.ENTER)
    private var suggestions by mutableStateOf<List<SuggestionChip>>(emptyList())
    private var isTyping by mutableStateOf(false)
    private var proactiveUndoState by mutableStateOf<UndoSnapshot?>(null)
    private var recentScreenshot by mutableStateOf<RecentScreenshot?>(null)
    private var otpChipText by mutableStateOf<String?>(null)
    private var otpClipAddedAtMs: Long = 0L
    private var inlineAutofillViews by mutableStateOf<List<View>>(emptyList())
    private var snippetsSnapshot by mutableStateOf<List<TextSnippet>>(emptyList())
    private var currentTargetPackage: String = ""

    private var writingSourceText by mutableStateOf("")
    private var writingOutput by mutableStateOf<WritingToolsOutput?>(null)
    private var writingLoading by mutableStateOf(false)
    private var writingAction by mutableStateOf<WritingAction?>(null)

    private var textEditSelectionActive by mutableStateOf(false)
    private var quickSearchInitial by mutableStateOf("")

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
        if (!currentPrefs.clipboardEnabled || currentPrefs.incognitoMode) return@OnPrimaryClipChangedListener
        val cm = getSystemService(ClipboardManager::class.java) ?: return@OnPrimaryClipChangedListener
        val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount <= 0) return@OnPrimaryClipChangedListener

        val item = clip.getItemAt(0)
        val isImage = clip.description.hasMimeType("image/*") && item.uri != null
        if (isImage) {
            val uri = item.uri!!
            if (ScreenshotMonitor.isSelfCopiedEvent(uri)) return@OnPrimaryClipChangedListener
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
            return@OnPrimaryClipChangedListener
        }

        val text = item.coerceToText(this)?.toString().orEmpty()
        if (text.isNotBlank()) {
            clipboardStore.addClip(
                rawText = text,
                retention = currentPrefs.clipboardRetention,
                maxUnpinnedItems = currentPrefs.clipboardMaxItems
            )
            val otp = SuggestionEngine.extractOtpFromClipboard(text)
            if (otp != null) {
                otpChipText = otp
                otpClipAddedAtMs = System.currentTimeMillis()
            }
        }
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
        writingToolsEngine = WritingToolsEngine(secureKeyStorage, dictionaryStore, modelManager)
        clipboardStore = ClipboardStore.createForContext(this)
        snippetsSnapshot = dictionaryStore.listSnippets()
        voiceController = TapwingVoiceController(
            context = this,
            modelManager = modelManager,
            onTranscriptReady = { raw -> handleVoiceTranscript(raw) },
            onNeedAudioPermission = { PermissionBridgeActivity.requestRecordAudio(this) },
            initialPromptProvider = { "" }
        )

        serviceScope.launch {
            prefsRepo.prefsFlow.collect { prefs ->
                currentPrefs = prefs
                offlinePackManager.preloadLocaleAsync(prefs.activeLocale)
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
                    recentScreenshot = recentScreenshot,
                    otpChipText = otpChipText,
                    inlineAutofillViews = inlineAutofillViews,
                    snippets = snippetsSnapshot,
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
                    onTapOtpChip = { code ->
                        commitTextWithSpaceSafety(code)
                        otpChipText = null
                    },
                    onDismissOtpChip = { otpChipText = null },
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
                    onOpenClipboard = { panelMode = KeyboardPanelMode.CLIPBOARD },
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
                        voiceController.startListening(
                            locale = currentPrefs.activeLocale,
                            strictLanguageLock = currentPrefs.voiceStrictLanguageLock,
                            engineMode = currentPrefs.voiceEngine,
                            whisperModelId = currentPrefs.selectedWhisperModelId
                        )
                    },
                    onFinishVoice = { voiceController.finishAndCommit() },
                    onCancelVoice = { voiceController.cancel() },
                    onToggleVoiceMinimize = { voiceController.toggleMinimized() },
                    onDismissVoiceError = { voiceController.clearError() },
                    onHideKeyboard = ::hideKeyboard
                )
            }
        }
        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        dictionaryStore.reloadIfModifiedOnDisk()
        snippetsSnapshot = dictionaryStore.listSnippets()
        offlinePackManager.refreshStates()
        offlinePackManager.preloadLocaleAsync(currentPrefs.activeLocale)
        modelManager.refreshStates()
        ensureScreenshotObserverIfPermitted()

        currentTargetPackage = info?.packageName.orEmpty()
        inlineAutofillViews = emptyList()
        val variation = (info?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
        isEmailField = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        isUrlField = variation == InputType.TYPE_TEXT_VARIATION_URI

        imeActionLabel = resolveImeAction(info)
        layoutPage = KeyboardLayoutPage.LETTERS
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        pendingSmartUndoPair = null
        pendingSuggestionTrailingSpace = false

        if (otpChipText != null && System.currentTimeMillis() - otpClipAddedAtMs > 90_000L) {
            otpChipText = null
        }

        consumePendingOcrText()
        refreshContextAndSuggestions()
        checkRecentScreenshot()
        voiceController.resumeIfWithinGrace(
            graceMs = currentPrefs.voiceRotationGraceMs,
            strictLanguageLock = currentPrefs.voiceStrictLanguageLock
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inlineAutofillViews = emptyList()
        if (!finishingInput) {
            voiceController.markConfigurationPause()
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
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
        val minW = (96f * density).toInt()
        val minH = (36f * density).toInt()
        val maxW = (280f * density).toInt()
        val maxH = (44f * density).toInt()

        val chipBg = android.graphics.Color.valueOf(0xFF2B2F3A.toInt()).toArgb()
        val textColor = android.graphics.Color.valueOf(0xFFF3F5FA.toInt()).toArgb()

        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(this, R.drawable.bg_autofill_chip))
                    .setBackgroundColor(chipBg)
                    .setPadding((10f * density).toInt(), (4f * density).toInt(), (10f * density).toInt(), (4f * density).toInt())
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(textColor)
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(0xFFB4BAC8.toInt())
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setMaxWidth((18f * density).toInt())
                    .setMaxHeight((18f * density).toInt())
                    .build()
            )
            .build()

        val stylesBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()

        val spec = InlinePresentationSpec.Builder(Size(minW, minH), Size(maxW, maxH))
            .setStyle(stylesBundle)
            .build()

        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(4)
            .setSupportedLocales(LocaleList.getDefault())
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!currentPrefs.inlineAutofillEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            inlineAutofillViews = emptyList()
            return false
        }
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            inlineAutofillViews = emptyList()
            return false
        }
        val density = resources.displayMetrics.density
        val size = Size(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            (40f * density).toInt()
        )
        val executor = ContextCompat.getMainExecutor(this)
        val collected = ArrayList<View>(suggestions.size)
        val expected = suggestions.take(4).size
        suggestions.take(4).forEach { inlineSuggestion ->
            inlineSuggestion.inflate(this, size, executor) { contentView ->
                if (contentView != null) {
                    collected.add(contentView)
                    inlineAutofillViews = collected.toList()
                } else if (collected.size >= expected) {
                    inlineAutofillViews = collected.toList()
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

    private fun resolveImeAction(info: EditorInfo?): KeyActionLabel {
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> KeyActionLabel.SEARCH
            EditorInfo.IME_ACTION_SEND -> KeyActionLabel.SEND
            EditorInfo.IME_ACTION_DONE -> KeyActionLabel.DONE
            EditorInfo.IME_ACTION_NEXT -> KeyActionLabel.NEXT
            else -> KeyActionLabel.ENTER
        }
    }

    private fun secondaryLocales(): List<String> {
        return if (currentPrefs.multilingualPredictions) {
            currentPrefs.enabledLocales.filter { it != currentPrefs.activeLocale }
        } else {
            emptyList()
        }
    }

    private fun handleKeyPress(key: KeySpec, output: String) {
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
                layoutPage = if (layoutPage == KeyboardLayoutPage.LETTERS) {
                    KeyboardLayoutPage.SYMBOLS
                } else {
                    KeyboardLayoutPage.LETTERS
                }
            }
            KeyActionType.SYMBOLS_EXT_TOGGLE -> {
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
                val handled = sendDefaultEditorAction(true)
                if (!handled) {
                    ic.commitText("\n", 1)
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
            dictionaryStore = dictionaryStore,
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
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        isTyping = trailing.isNotEmpty()
        val latestClipText = clipboardStore.items.value.firstOrNull { !it.isImage }?.text.orEmpty()
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
        voiceController.startListening(
            locale = currentPrefs.activeLocale,
            strictLanguageLock = currentPrefs.voiceStrictLanguageLock,
            engineMode = currentPrefs.voiceEngine,
            whisperModelId = currentPrefs.selectedWhisperModelId
        )
    }

    private fun handleVoiceTranscript(rawTranscript: String) {
        if (currentPrefs.voiceMode == VoiceDictationMode.STANDARD) {
            commitTextWithSpaceSafety(rawTranscript)
            statsStore.recordVoiceDictation(rawTranscript.length, currentPrefs.incognitoMode)
            voiceController.finishExternalProcessing()
            return
        }

        val parsed = NaturalVoiceCleaner.parseCommandOrClean(
            rawTranscript = rawTranscript,
            locale = currentPrefs.activeLocale,
            removeFillers = currentPrefs.voiceRemoveFillers,
            autoPunctuate = currentPrefs.voiceAutoPunctuate,
            dictionaryStore = dictionaryStore
        )

        when (parsed) {
            is VoiceCommandResult.DeleteLastWord -> {
                deletePreviousWord()
                voiceController.finishExternalProcessing()
            }
            is VoiceCommandResult.RewriteAction -> {
                val fieldText = readWholeFieldText()
                if (fieldText.isNotBlank()) {
                    val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
                    voiceController.beginExternalProcessing(
                        if (de) "Überarbeite Text mit KI…" else "Rewriting with AI…"
                    )
                    serviceScope.launch {
                        try {
                            val out = writingToolsEngine.runAction(fieldText, parsed.action, currentPrefs)
                            if (out.rewrittenText.isNotBlank()) {
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
                            voiceController.finishExternalProcessing()
                        }
                    }
                } else {
                    voiceController.finishExternalProcessing()
                }
            }
            is VoiceCommandResult.Text -> {
                val de = KeyboardLocales.baseLang(currentPrefs.activeLocale) == "de"
                val willRunLlmOrCloud = currentPrefs.voiceAiPolish &&
                    (currentPrefs.aiProvider != com.tapwing.keyboard.data.AiProviderType.LOCAL_RULES)
                if (willRunLlmOrCloud) {
                    voiceController.beginExternalProcessing(
                        if (de) "Bereinige mit KI…" else "Cleaning up with AI…"
                    )
                }
                serviceScope.launch {
                    try {
                        val out = writingToolsEngine.cleanupVoiceTranscript(rawTranscript, currentPrefs)
                        val finalText = out.rewrittenText.ifBlank { parsed.cleanedText }
                        if (finalText.isNotBlank()) {
                            commitTextWithSpaceSafety(finalText)
                            statsStore.recordVoiceDictation(finalText.length, currentPrefs.incognitoMode)
                            undoManager.record(
                                beforeText = "",
                                afterText = finalText,
                                reason = UndoReason.VOICE_INSERT,
                                label = if (de) "Diktat rückgängig" else "Undo dictation"
                            )
                            proactiveUndoState = undoManager.activeBanner()
                        }
                    } finally {
                        voiceController.finishExternalProcessing()
                    }
                }
            }
        }
    }

    private fun deletePreviousWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val match = Regex("""\s*\S+\s*$""").find(before) ?: return
        ic.deleteSurroundingText(match.value.length, 0)
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

    private fun commitTextWithSpaceSafety(text: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace() && !text.first().isWhitespace()
        val finalCommit = if (needsLeadingSpace) " $text" else text
        ic.commitText(finalCommit, 1)
        refreshContextAndSuggestions()
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
            QuickBarAction.CLIPBOARD -> panelMode = KeyboardPanelMode.CLIPBOARD
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
