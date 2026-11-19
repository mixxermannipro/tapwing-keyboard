package com.tapwing.keyboard.ime

import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsEngine
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardStore
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.dictionary.SuggestionEngine
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

    private var writingSourceText by mutableStateOf("")
    private var writingOutput by mutableStateOf<WritingToolsOutput?>(null)
    private var writingLoading by mutableStateOf(false)
    private var writingAction by mutableStateOf<WritingAction?>(null)

    private var textEditSelectionActive by mutableStateOf(false)
    private var quickSearchInitial by mutableStateOf("")

    private var lastSpaceTapMs: Long = 0L

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!currentPrefs.clipboardEnabled || currentPrefs.incognitoMode) return@OnPrimaryClipChangedListener
        val cm = getSystemService(ClipboardManager::class.java) ?: return@OnPrimaryClipChangedListener
        val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) {
                clipboardStore.addClip(
                    rawText = text,
                    retention = currentPrefs.clipboardRetention,
                    maxUnpinnedItems = currentPrefs.clipboardMaxItems
                )
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
        writingToolsEngine = WritingToolsEngine(secureKeyStorage, dictionaryStore)
        clipboardStore = ClipboardStore.createForContext(this)
        voiceController = TapwingVoiceController(
            context = this,
            onTranscriptReady = { raw -> handleVoiceTranscript(raw) },
            onNeedAudioPermission = { PermissionBridgeActivity.requestRecordAudio(this) }
        )

        serviceScope.launch {
            prefsRepo.prefsFlow.collect { prefs ->
                currentPrefs = prefs
                clipboardStore.prune(prefs.clipboardRetention, prefs.clipboardMaxItems)
            }
        }

        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.addPrimaryClipChangedListener(clipboardListener)
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
                    voiceState = voiceUiState,
                    writingSourceText = writingSourceText,
                    writingOutput = writingOutput,
                    writingLoading = writingLoading,
                    writingAction = writingAction,
                    clips = clips,
                    textEditSelectionActive = textEditSelectionActive,
                    quickSearchInitial = quickSearchInitial,
                    onKeyPress = ::handleKeyPress,
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
                    onTapScreenshotChip = ::handleScreenshotChip,
                    onOpenToolsHub = { panelMode = KeyboardPanelMode.TOOLS_HUB },
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
                    onPasteClip = { clip ->
                        commitTextWithSpaceSafety(clip.text)
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
                        currentInputConnection?.commitText(emoji, 1)
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
                            strictLanguageLock = currentPrefs.voiceStrictLanguageLock
                        )
                    },
                    onFinishVoice = { voiceController.finishAndCommit() },
                    onCancelVoice = { voiceController.cancel() },
                    onToggleVoiceMinimize = { voiceController.toggleMinimized() },
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
        imeActionLabel = resolveImeAction(info)
        layoutPage = KeyboardLayoutPage.LETTERS
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
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
        if (!finishingInput) {
            voiceController.markConfigurationPause()
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        voiceController.markConfigurationPause()
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.removePrimaryClipChangedListener(clipboardListener)
        }
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
                panelMode = KeyboardPanelMode.EMOJI
            }
            KeyActionType.LANGUAGE_SWITCH -> toggleLocale()
            KeyActionType.ENTER -> {
                learnCompletedTrailingWord()
                val handled = sendDefaultEditorAction(true)
                if (!handled) {
                    ic.commitText("\n", 1)
                }
                refreshContextAndSuggestions()
            }
            KeyActionType.SPACE -> handleSpaceKey()
            KeyActionType.COMMA, KeyActionType.PERIOD, KeyActionType.CHAR -> {
                if (key.type == KeyActionType.PERIOD || key.type == KeyActionType.COMMA) {
                    maybeAutoCorrectTrailingWord()
                    learnCompletedTrailingWord()
                }
                ic.commitText(output, 1)
                undoManager.notifyUnrelatedTyping()
                proactiveUndoState = undoManager.activeBanner()
                if (shiftState == ShiftState.ONCE) {
                    shiftState = ShiftState.OFF
                }
                refreshContextAndSuggestions()
            }
        }
    }

    private fun handleSpaceKey() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()

        if (currentPrefs.doubleSpacePeriod && (now - lastSpaceTapMs) <= 320L &&
            before.endsWith(" ") && before.length >= 2 && before[before.length - 2].isLetterOrDigit()
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTapMs = 0L
            refreshContextAndSuggestions()
            return
        }

        lastSpaceTapMs = now
        val corrected = maybeAutoCorrectTrailingWord()
        learnCompletedTrailingWord()
        ic.commitText(" ", 1)
        if (!corrected) {
            undoManager.notifyUnrelatedTyping()
            proactiveUndoState = undoManager.activeBanner()
        }
        refreshContextAndSuggestions()
    }

    private fun learnCompletedTrailingWord() {
        if (!currentPrefs.learningEnabled || currentPrefs.incognitoMode) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        if (trailing.isEmpty()) return

        val prefixBeforeTrailing = before.dropLast(trailing.length).trimEnd()
        val prevWord = SuggestionEngine.extractTrailingWord(prefixBeforeTrailing).takeIf { it.isNotEmpty() }
        dictionaryStore.recordTypedWord(
            word = trailing,
            previousWord = prevWord,
            learningEnabled = currentPrefs.learningEnabled,
            incognito = currentPrefs.incognitoMode
        )
    }

    private fun maybeAutoCorrectTrailingWord(): Boolean {
        if (!currentPrefs.autoCorrect) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        if (trailing.isBlank()) return false
        val correction = SuggestionEngine.autoCorrectionFor(trailing, currentPrefs.activeLocale, dictionaryStore)
        if (correction != null && correction != trailing) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(trailing.length, 0)
            ic.commitText(correction, 1)
            ic.endBatchEdit()
            undoManager.record(
                beforeText = trailing,
                afterText = correction,
                reason = UndoReason.AUTOCORRECT,
                label = if (currentPrefs.activeLocale == "de") "Rückgängig: $trailing" else "Undo: $trailing"
            )
            proactiveUndoState = undoManager.activeBanner()
            return true
        }
        return false
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        refreshContextAndSuggestions()
    }

    private fun deletePreviousWordWithUndo() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val match = Regex("""\s*\S+\s*$""").find(before) ?: return
        val deleted = match.value
        ic.deleteSurroundingText(deleted.length, 0)
        undoManager.record(
            beforeText = deleted,
            afterText = "",
            reason = UndoReason.BULK_DELETE,
            label = if (currentPrefs.activeLocale == "de") "Wort wiederherstellen" else "Restore word"
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
            val now = android.os.SystemClock.uptimeMillis()
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
            chip.isShortcut -> "${chip.text} "
            else -> "${chip.text} "
        }
        ic.commitText(insertion, 1)
        ic.endBatchEdit()

        // Learn chosen suggestion cleanly
        if (!chip.isEmoji && !chip.isMath && !chip.isShortcut) {
            val cleanWord = chip.text.trim()
            val fullBefore = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
            val prev = SuggestionEngine.extractPreviousWord(fullBefore.dropLast(insertion.length))
            dictionaryStore.recordTypedWord(
                word = cleanWord,
                previousWord = prev.takeIf { it.isNotEmpty() },
                learningEnabled = currentPrefs.learningEnabled,
                incognito = currentPrefs.incognitoMode
            )
        }

        if (before.isNotEmpty() && (chip.isCorrection || chip.isShortcut)) {
            undoManager.record(
                beforeText = before,
                afterText = insertion,
                reason = UndoReason.AUTOCORRECT,
                label = if (currentPrefs.activeLocale == "de") "Rückgängig: $before" else "Undo: $before"
            )
            proactiveUndoState = undoManager.activeBanner()
        }
        refreshContextAndSuggestions()
    }

    private fun performUndo() {
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
            ic.commitText(snapshot.beforeText, 1)
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
        val before = ic?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val trailing = SuggestionEngine.extractTrailingWord(before)
        isTyping = trailing.isNotEmpty()
        suggestions = SuggestionEngine.suggestionsFor(
            textBeforeCursor = before,
            locale = currentPrefs.activeLocale,
            includeEmoji = currentPrefs.candidateEmojiSuggestions,
            includeGrammarChips = currentPrefs.spellGrammarChips,
            includeMath = currentPrefs.inlineMathEnabled,
            dictionaryStore = dictionaryStore
        )

        if (layoutPage == KeyboardLayoutPage.LETTERS && shiftState != ShiftState.CAPS_LOCK) {
            val shouldCap = before.isEmpty() ||
                before.endsWith("\n") ||
                Regex("""[.!?]\s+$""").containsMatchIn(before)
            shiftState = if (shouldCap) ShiftState.ONCE else ShiftState.OFF
        }
    }

    private fun handleVoiceMicClicked() {
        if (!currentPrefs.voiceOnboardingSeen && currentPrefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP) {
            panelMode = KeyboardPanelMode.VOICE_ONBOARDING
            return
        }
        voiceController.startListening(
            locale = currentPrefs.activeLocale,
            strictLanguageLock = currentPrefs.voiceStrictLanguageLock
        )
    }

    private fun handleVoiceTranscript(rawTranscript: String) {
        if (currentPrefs.voiceMode == VoiceDictationMode.STANDARD) {
            commitTextWithSpaceSafety(rawTranscript)
            return
        }
        when (
            val result = NaturalVoiceCleaner.parseCommandOrClean(
                rawTranscript = rawTranscript,
                locale = currentPrefs.activeLocale,
                removeFillers = currentPrefs.voiceRemoveFillers,
                autoPunctuate = currentPrefs.voiceAutoPunctuate,
                dictionaryStore = dictionaryStore
            )
        ) {
            is VoiceCommandResult.DeleteLastWord -> deletePreviousWord()
            is VoiceCommandResult.RewriteAction -> {
                val fieldText = readWholeFieldText()
                if (fieldText.isNotBlank()) {
                    serviceScope.launch {
                        val out = writingToolsEngine.runAction(fieldText, result.action, currentPrefs)
                        if (out.rewrittenText.isNotBlank()) {
                            replaceWholeFieldText(out.rewrittenText)
                            undoManager.record(
                                beforeText = fieldText,
                                afterText = out.rewrittenText,
                                reason = UndoReason.AI_REWRITE,
                                label = if (currentPrefs.activeLocale == "de") "KI rückgängig" else "Undo AI edit"
                            )
                            proactiveUndoState = undoManager.activeBanner()
                        }
                    }
                }
            }
            is VoiceCommandResult.Text -> {
                if (result.cleanedText.isNotBlank()) {
                    commitTextWithSpaceSafety(result.cleanedText)
                    undoManager.record(
                        beforeText = "",
                        afterText = result.cleanedText,
                        reason = UndoReason.VOICE_INSERT,
                        label = if (currentPrefs.activeLocale == "de") "Diktat rückgängig" else "Undo dictation"
                    )
                    proactiveUndoState = undoManager.activeBanner()
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
        serviceScope.launch {
            val out = writingToolsEngine.runAction(currentSource, action, currentPrefs)
            writingLoading = false
            writingOutput = out
        }
    }

    private fun applyWritingToolsResult(rewritten: String) {
        val before = readWholeFieldText().ifBlank { writingSourceText }
        replaceWholeFieldText(rewritten)
        undoManager.record(
            beforeText = before,
            afterText = rewritten,
            reason = UndoReason.AI_REWRITE,
            label = if (currentPrefs.activeLocale == "de") "Schreibhilfe rückgängig" else "Undo rewrite"
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
        undoManager.record(
            beforeText = "",
            afterText = pending,
            reason = UndoReason.OCR_INSERT,
            label = if (currentPrefs.activeLocale == "de") "OCR rückgängig" else "Undo OCR"
        )
        proactiveUndoState = undoManager.activeBanner()
    }

    private fun checkRecentScreenshot() {
        if (!currentPrefs.screenshotSuggestionChip) {
            recentScreenshot = null
            return
        }
        serviceScope.launch {
            recentScreenshot = ScreenshotMonitor.findRecentScreenshot(this@TapwingInputMethodService)
        }
    }

    private fun handleScreenshotChip(shot: RecentScreenshot) {
        serviceScope.launch {
            val ocrText = ScreenshotMonitor.extractTextFromImageUri(this@TapwingInputMethodService, shot.uri)
            if (ocrText.isNotBlank()) {
                commitTextWithSpaceSafety(ocrText)
                if (!currentPrefs.incognitoMode) {
                    clipboardStore.addClip(
                        rawText = ocrText,
                        retention = currentPrefs.clipboardRetention,
                        maxUnpinnedItems = currentPrefs.clipboardMaxItems
                    )
                }
            }
            recentScreenshot = null
        }
    }

    private fun toggleLocale() {
        val next = if (currentPrefs.activeLocale == "de") "en" else "de"
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

    private fun hideKeyboard() {
        panelMode = KeyboardPanelMode.KEYS
        textEditSelectionActive = false
        voiceController.cancel()
        requestHideSelf(0)
    }
}
