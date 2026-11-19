package com.tapwing.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import com.tapwing.keyboard.ui.theme.TapwingTheme
import com.tapwing.keyboard.undo.UndoSnapshot
import com.tapwing.keyboard.voice.VoiceUiState

@Composable
fun TapwingKeyboardRoot(
    prefs: TapwingPrefs,
    panelMode: KeyboardPanelMode,
    layoutPage: KeyboardLayoutPage,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    suggestions: List<SuggestionChip>,
    isTyping: Boolean,
    proactiveUndo: UndoSnapshot?,
    recentScreenshot: RecentScreenshot?,
    voiceState: VoiceUiState,
    writingSourceText: String,
    writingOutput: WritingToolsOutput?,
    writingLoading: Boolean,
    writingAction: WritingAction?,
    clips: List<ClipboardClip>,
    onKeyPress: (KeySpec, String) -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onSwitchLanguage: () -> Unit,
    onSelectSuggestion: (SuggestionChip) -> Unit,
    onTriggerUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onTapScreenshotChip: (RecentScreenshot) -> Unit,
    onOpenToolsHub: () -> Unit,
    onCloseSubPanel: () -> Unit,
    onOpenWritingTools: () -> Unit,
    onWritingOnboardingContinue: () -> Unit,
    onRunWritingAction: (WritingAction) -> Unit,
    onApplyWritingResult: (String) -> Unit,
    onOpenClipboard: () -> Unit,
    onPasteClip: (ClipboardClip) -> Unit,
    onTogglePinClip: (ClipboardClip) -> Unit,
    onDeleteClip: (ClipboardClip) -> Unit,
    onClearUnpinnedClips: () -> Unit,
    onOpenScanText: () -> Unit,
    onOpenEmoji: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoice: () -> Unit,
    onVoiceOnboardingGetStarted: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleVoiceMinimize: () -> Unit
) {
    TapwingTheme(
        themeMode = prefs.themeMode,
        darkModePreference = prefs.darkMode
    ) {
        val kbColors = LocalTapwingKeyboardColors.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(kbColors.boardBackground)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 4.dp)
        ) {
            when (panelMode) {
                KeyboardPanelMode.VOICE_ONBOARDING -> {
                    NaturalVoiceOnboardingSheet(
                        onGetStarted = onVoiceOnboardingGetStarted,
                        onDismiss = onCloseSubPanel
                    )
                }
                KeyboardPanelMode.WRITING_ONBOARDING -> {
                    WritingToolsOnboardingCard(
                        onContinue = onWritingOnboardingContinue,
                        onDismiss = onCloseSubPanel
                    )
                }
                KeyboardPanelMode.WRITING_TOOLS -> {
                    WritingToolsPanel(
                        prefs = prefs,
                        sourceText = writingSourceText,
                        output = writingOutput,
                        isLoading = writingLoading,
                        selectedAction = writingAction,
                        onBack = onCloseSubPanel,
                        onRunAction = onRunWritingAction,
                        onApplyResult = onApplyWritingResult,
                        onUndoApplied = onTriggerUndo
                    )
                }
                KeyboardPanelMode.TOOLS_HUB -> {
                    ToolsHubPanel(
                        prefs = prefs,
                        onClose = onCloseSubPanel,
                        onOpenWritingTools = onOpenWritingTools,
                        onOpenClipboard = onOpenClipboard,
                        onOpenScanText = onOpenScanText,
                        onStartVoice = onStartVoice,
                        onOpenEmoji = onOpenEmoji,
                        onCycleTheme = onCycleTheme,
                        onOpenSettings = onOpenSettings,
                        onSwitchLanguage = onSwitchLanguage
                    )
                }
                KeyboardPanelMode.CLIPBOARD -> {
                    ClipboardPanel(
                        prefs = prefs,
                        clips = clips,
                        onBack = onCloseSubPanel,
                        onPasteClip = onPasteClip,
                        onTogglePin = onTogglePinClip,
                        onDeleteClip = onDeleteClip,
                        onClearUnpinned = onClearUnpinnedClips
                    )
                }
                KeyboardPanelMode.EMOJI -> {
                    EmojiPanel(
                        prefs = prefs,
                        onBack = onCloseSubPanel,
                        onInsertEmoji = onInsertEmoji,
                        onBackspace = onBackspace
                    )
                }
                KeyboardPanelMode.KEYS -> {
                    SuggestionStrip(
                        prefs = prefs,
                        suggestions = suggestions,
                        proactiveUndo = proactiveUndo,
                        recentScreenshot = recentScreenshot,
                        voiceState = voiceState,
                        isTyping = isTyping,
                        onOpenToolsHub = onOpenToolsHub,
                        onOpenWritingTools = onOpenWritingTools,
                        onOpenClipboard = onOpenClipboard,
                        onOpenScanText = onOpenScanText,
                        onOpenSettings = onOpenSettings,
                        onSelectSuggestion = onSelectSuggestion,
                        onTriggerUndo = onTriggerUndo,
                        onDismissUndo = onDismissUndo,
                        onTapScreenshotChip = onTapScreenshotChip,
                        onStartVoice = onStartVoice,
                        onFinishVoice = onFinishVoice,
                        onCancelVoice = onCancelVoice,
                        onToggleVoiceMinimize = onToggleVoiceMinimize
                    )

                    if (!((voiceState.isListening || voiceState.isProcessing) && voiceState.isMinimized)) {
                        KeyboardGrid(
                            prefs = prefs,
                            layoutPage = layoutPage,
                            shiftState = shiftState,
                            imeActionLabel = imeActionLabel,
                            onKeyPress = onKeyPress,
                            onCursorMoveSteps = onCursorMoveSteps,
                            onSwitchLanguage = onSwitchLanguage
                        )
                    }
                }
            }
        }
    }
}
