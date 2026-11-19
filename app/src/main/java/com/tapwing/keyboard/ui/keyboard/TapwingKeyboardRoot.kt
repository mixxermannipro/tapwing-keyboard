package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tapwing.keyboard.R
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
    textEditSelectionActive: Boolean,
    quickSearchInitial: String,
    onKeyPress: (KeySpec, String) -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onDeletePreviousWord: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onToggleOneHandedSide: () -> Unit,
    onExitOneHanded: () -> Unit,
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
    onOpenResize: () -> Unit,
    onUpdateScalePercent: (Int) -> Unit,
    onUpdateBottomLiftDp: (Int) -> Unit,
    onResetResize: () -> Unit,
    onOpenTextEdit: () -> Unit,
    onToggleTextEditSelection: () -> Unit,
    onTextEditAction: (TextEditAction) -> Unit,
    onOpenScanText: () -> Unit,
    onOpenEmoji: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onCycleOneHanded: () -> Unit,
    onToggleIncognito: () -> Unit,
    onOpenQuickSearch: () -> Unit,
    onInsertSearchLink: (String) -> Unit,
    onOpenSearchInBrowser: (String) -> Unit,
    onToggleAppleMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoice: () -> Unit,
    onVoiceOnboardingGetStarted: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleVoiceMinimize: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    TapwingTheme(
        themeMode = prefs.themeMode,
        darkModePreference = prefs.darkMode,
        appleModeEnabled = prefs.appleModeEnabled
    ) {
        val kbColors = LocalTapwingKeyboardColors.current
        val view = LocalView.current
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val footerHeight = maxOf(navBarBottom, 44.dp)

        val backgroundModifier = if (kbColors.appleLiquidGlass) {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(kbColors.boardBackground, kbColors.boardGradientBottom)
                )
            )
        } else {
            Modifier.background(kbColors.boardBackground)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
        ) {
            if (kbColors.appleLiquidGlass) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(kbColors.keyGlassHighlight)
                )
            }

            AnimatedContent(
                targetState = panelMode,
                transitionSpec = {
                    val enter = fadeIn(tween(170)) + slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { it / 14 }
                    val exit = fadeOut(tween(120))
                    enter togetherWith exit
                },
                label = "keyboardPanelTransition"
            ) { mode ->
                when (mode) {
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
                            onOpenResize = onOpenResize,
                            onOpenTextEdit = onOpenTextEdit,
                            onOpenScanText = onOpenScanText,
                            onStartVoice = onStartVoice,
                            onOpenEmoji = onOpenEmoji,
                            onCycleOneHanded = onCycleOneHanded,
                            onToggleIncognito = onToggleIncognito,
                            onOpenQuickSearch = onOpenQuickSearch,
                            onToggleAppleMode = onToggleAppleMode,
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
                    KeyboardPanelMode.RESIZE -> {
                        KeyboardResizePanel(
                            prefs = prefs,
                            layoutPage = layoutPage,
                            shiftState = shiftState,
                            imeActionLabel = imeActionLabel,
                            onScaleChange = onUpdateScalePercent,
                            onBottomLiftChange = onUpdateBottomLiftDp,
                            onReset = onResetResize,
                            onDone = onCloseSubPanel
                        )
                    }
                    KeyboardPanelMode.TEXT_EDIT -> {
                        TextEditPanel(
                            prefs = prefs,
                            selectionActive = textEditSelectionActive,
                            onBack = onCloseSubPanel,
                            onToggleSelection = onToggleTextEditSelection,
                            onAction = onTextEditAction,
                            onUndo = onTriggerUndo
                        )
                    }
                    KeyboardPanelMode.QUICK_SEARCH -> {
                        QuickSearchPanel(
                            prefs = prefs,
                            initialQuery = quickSearchInitial,
                            onBack = onCloseSubPanel,
                            onInsertSearchLink = onInsertSearchLink,
                            onOpenInBrowser = onOpenSearchInBrowser
                        )
                    }
                    KeyboardPanelMode.KEYS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                onOpenTextEdit = onOpenTextEdit,
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
                                    onDeletePreviousWord = onDeletePreviousWord,
                                    onSwitchLanguage = onSwitchLanguage,
                                    onToggleOneHandedSide = onToggleOneHandedSide,
                                    onExitOneHanded = onExitOneHanded
                                )
                            }
                        }
                    }
                }
            }

            if (prefs.keyboardBottomLiftDp > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(prefs.keyboardBottomLiftDp.dp)
                )
            }

            // Bottom system inset bar holding the borderless, larger, lower-left-shifted dismiss chevron
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 30.dp, top = 4.dp)
                        .width(52.dp)
                        .height(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp),
                            onClick = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                onHideKeyboard()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_hide_keyboard),
                        tint = kbColors.keyHintText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
