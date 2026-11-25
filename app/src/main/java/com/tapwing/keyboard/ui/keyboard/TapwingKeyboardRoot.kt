package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.KeyCenter
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.dictionary.SwipePoint
import com.tapwing.keyboard.dictionary.TextSnippet
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

    clipboardPreview: String?,
    clipboardTimestamp: Long?,
    clipboardVerificationCode: Boolean,
    onPasteClipboard: (Long) -> Unit,
    onDismissClipboard: (Long) -> Unit,
    inlineAutofillViews: List<View> = emptyList(),
    snippets: List<TextSnippet> = emptyList(),
    recentEmojis: List<String> = emptyList(),
    voiceState: VoiceUiState,
    writingSourceText: String,
    writingOutput: WritingToolsOutput?,
    writingLoading: Boolean,
    writingAction: WritingAction?,
    clips: List<ClipboardClip>,
    textEditSelectionActive: Boolean,
    quickSearchInitial: String,
    onKeyPress: (KeySpec, String) -> Unit,
    onSwipeWord: (List<SwipePoint>, List<KeyCenter>) -> Unit,
    onCursorMove: (Int, Int) -> Unit,
    onDeletePreviousWord: () -> Unit,
    onSwipeBackspaceEnd: () -> Unit = {},
    onSwitchLanguage: () -> Unit,
    onToggleOneHandedSide: () -> Unit,
    onExitOneHanded: () -> Unit,
    onSelectSuggestion: (SuggestionChip) -> Unit,
    onTriggerUndo: () -> Unit,
    onEditorUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onTapScreenshotChip: (RecentScreenshot) -> Unit,
    onTapScreenshotOcr: (RecentScreenshot) -> Unit,
    onDismissScreenshotChip: () -> Unit,


    onOpenToolsHub: () -> Unit,
    onOpenCustomizeBar: () -> Unit,
    onQuickAction: (QuickBarAction) -> Unit,
    onUpdateQuickBar: (List<QuickBarAction>) -> Unit,
    onResetQuickBar: () -> Unit,
    onCloseSubPanel: () -> Unit,
    onOpenWritingTools: () -> Unit,
    onWritingOnboardingContinue: () -> Unit,
    onRunWritingAction: (WritingAction) -> Unit,
    onApplyWritingResult: (String) -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenSnippets: () -> Unit = {},
    onInsertSnippet: (TextSnippet, String) -> Unit = { _, _ -> },
    onPasteClip: (ClipboardClip) -> Unit,
    onPasteClipOcr: (ClipboardClip) -> Unit,
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
    onOpenSearch: (String) -> Unit,
    onToggleAppleMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoice: () -> Unit,
    onVoiceOnboardingGetStarted: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleVoiceMinimize: () -> Unit,
    onToggleVoicePause: () -> Unit = {},
    onToggleVoiceMultilingual: () -> Unit = {},
    onQuickVoicePolish: (WritingAction) -> Unit = {},
    onDismissVoiceError: () -> Unit = {},
    onHideKeyboard: () -> Unit
) {
    val baseCtx = LocalContext.current
    val localizedCtx = remember(baseCtx, prefs.appUiLocale) {
        KeyboardLocales.localizedContext(baseCtx, prefs.appUiLocale)
    }

    CompositionLocalProvider(LocalContext provides localizedCtx) {
        TapwingTheme(
            themeMode = prefs.themeMode,
            darkModePreference = prefs.darkMode,
            appleModeEnabled = prefs.appleModeEnabled
        ) {
            val kbColors = LocalTapwingKeyboardColors.current
            val view = LocalView.current
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val footerHeight = maxOf(navBarBottom, 44.dp)
            val density = LocalDensity.current
            var keysHeightPx by remember { mutableIntStateOf(0) }
            val panelHeight = if (keysHeightPx > 0) with(density) { keysHeightPx.toDp() } else 270.dp

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
                        val enter = fadeIn(tween(190, delayMillis = 30, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.97f,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            ) +
                            slideInVertically(
                                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                            ) { it / 28 }
                        val exit = fadeOut(tween(130, easing = FastOutSlowInEasing))
                        (enter togetherWith exit).using(
                            SizeTransform(clip = false) { _, _ ->
                                spring<IntSize>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                            }
                        )
                    },
                    label = "keyboardPanelTransition"
                ) { mode ->
                    val lockedModifier = if (
                        mode == KeyboardPanelMode.KEYS ||
                        mode == KeyboardPanelMode.VOICE_ONBOARDING ||
                        mode == KeyboardPanelMode.WRITING_ONBOARDING ||
                        mode == KeyboardPanelMode.RESIZE ||
                        mode == KeyboardPanelMode.QUICK_SEARCH
                    ) Modifier else Modifier.fillMaxWidth().height(panelHeight)
                    Box(modifier = lockedModifier) {
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
                                    onOpenCustomizeBar = onOpenCustomizeBar,
                                    onOpenWritingTools = onOpenWritingTools,
                                    onOpenClipboard = onOpenClipboard,
                                    onOpenSnippets = onOpenSnippets,
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
                            KeyboardPanelMode.CUSTOMIZE_BAR -> {
                                QuickBarCustomizePanel(
                                    prefs = prefs,
                                    onUpdateQuickBar = onUpdateQuickBar,
                                    onResetQuickBar = onResetQuickBar,
                                    onDone = onCloseSubPanel
                                )
                            }
                            KeyboardPanelMode.CLIPBOARD -> {
                                ClipboardPanel(
                                    prefs = prefs,
                                    clips = clips,
                                    onBack = onCloseSubPanel,
                                    onPasteClip = onPasteClip,
                                    onPasteClipOcr = onPasteClipOcr,
                                    onTogglePin = onTogglePinClip,
                                    onDeleteClip = onDeleteClip,
                                    onClearUnpinned = onClearUnpinnedClips
                                )
                            }
                            KeyboardPanelMode.SNIPPETS -> {
                                val latestClipText = clips.firstOrNull { !it.isImage }?.text.orEmpty()
                                SnippetsPanel(
                                    prefs = prefs,
                                    snippets = snippets,
                                    latestClipboardText = latestClipText,
                                    onBack = onCloseSubPanel,
                                    onInsertSnippet = onInsertSnippet
                                )
                            }
                            KeyboardPanelMode.EMOJI -> {
                                EmojiPanel(
                                    prefs = prefs,
                                    recentEmojis = recentEmojis,
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
                                    onUndo = onEditorUndo
                                )
                            }
                            KeyboardPanelMode.QUICK_SEARCH -> {
                                QuickSearchPanel(
                                    prefs = prefs,
                                    initialQuery = quickSearchInitial,
                                    onBack = onCloseSubPanel,
                                    onInsertSearchLink = onInsertSearchLink,
                                    onOpenSearch = onOpenSearch,
                                    onSwitchLanguage = onSwitchLanguage
                                )
                            }
                            KeyboardPanelMode.KEYS -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged {
                                            if (!((voiceState.isListening || voiceState.isProcessing) && voiceState.isMinimized)) {
                                                keysHeightPx = it.height
                                            }
                                        }
                                ) {
                                    SuggestionStrip(
                                        prefs = prefs,
                                        suggestions = suggestions,
                                        proactiveUndo = proactiveUndo,
                                        recentScreenshot = recentScreenshot,

                                        clipboardPreview = clipboardPreview,
                                        clipboardTimestamp = clipboardTimestamp,
                                        clipboardVerificationCode = clipboardVerificationCode,
                                        onPasteClipboard = onPasteClipboard,
                                        onDismissClipboard = onDismissClipboard,
                                        inlineAutofillViews = inlineAutofillViews,
                                        voiceState = voiceState,
                                        isTyping = isTyping,
                                        onOpenToolsHub = onOpenToolsHub,
                                        onQuickAction = onQuickAction,
                                        onLongPressQuickBar = onOpenCustomizeBar,
                                        onSelectSuggestion = onSelectSuggestion,
                                        onTriggerUndo = onTriggerUndo,
                                        onDismissUndo = onDismissUndo,
                                        onTapScreenshotChip = onTapScreenshotChip,
                                        onTapScreenshotOcr = onTapScreenshotOcr,
                                        onDismissScreenshotChip = onDismissScreenshotChip,


                                        onStartVoice = onStartVoice,
                                        onFinishVoice = onFinishVoice,
                                        onCancelVoice = onCancelVoice,
                                        onToggleVoiceMinimize = onToggleVoiceMinimize,
                                        onToggleVoicePause = onToggleVoicePause,
                                        onToggleVoiceMultilingual = onToggleVoiceMultilingual,
                                        onQuickVoicePolish = onQuickVoicePolish,
                                        onDismissVoiceError = onDismissVoiceError
                                    )

                                    if (!((voiceState.isListening || voiceState.isProcessing) && voiceState.isMinimized)) {
                                        KeyboardGrid(
                                            prefs = prefs,
                                            layoutPage = layoutPage,
                                            shiftState = shiftState,
                                            imeActionLabel = imeActionLabel,
                                            onKeyPress = onKeyPress,
                                            onSwipeWord = onSwipeWord,
                                            onCursorMove = onCursorMove,
                                            onDeletePreviousWord = onDeletePreviousWord,
                                            onSwipeBackspaceEnd = onSwipeBackspaceEnd,
                                            onSwitchLanguage = onSwitchLanguage,
                                            onToggleOneHandedSide = onToggleOneHandedSide,
                                            onExitOneHanded = onExitOneHanded
                                        )
                                    }
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(footerHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 22.dp, top = 4.dp)
                            .width(52.dp)
                            .height(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 24.dp),
                                onClick = {
                                    if (prefs.hapticFeedback) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    onSwitchLanguage()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = stringResource(R.string.cd_switch_language),
                            tint = kbColors.keyHintText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
}
