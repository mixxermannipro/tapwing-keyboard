package com.tapwing.keyboard.ui.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.settings.springPressScale
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import com.tapwing.keyboard.undo.UndoReason
import com.tapwing.keyboard.undo.UndoSnapshot
import com.tapwing.keyboard.voice.VoiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun View.tapHaptic(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

private fun View.confirmHaptic(enabled: Boolean) {
    if (!enabled) return
    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(flag)
}

@Composable
fun SuggestionStrip(
    prefs: TapwingPrefs,
    suggestions: List<SuggestionChip>,
    proactiveUndo: UndoSnapshot?,
    recentScreenshot: RecentScreenshot?,

    clipboardPreview: String?,
    clipboardTimestamp: Long?,
    clipboardVerificationCode: Boolean,
    onPasteClipboard: (Long) -> Unit,
    onDismissClipboard: (Long) -> Unit,
    inlineAutofillViews: List<View> = emptyList(),
    voiceState: VoiceUiState,
    isTyping: Boolean,
    onOpenToolsHub: () -> Unit,
    onQuickAction: (QuickBarAction) -> Unit,
    onLongPressQuickBar: () -> Unit,
    onSelectSuggestion: (SuggestionChip) -> Unit,
    onTriggerUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onTapScreenshotChip: (RecentScreenshot) -> Unit,
    onTapScreenshotOcr: (RecentScreenshot) -> Unit,
    onDismissScreenshotChip: () -> Unit,

    onStartVoice: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleVoiceMinimize: () -> Unit,
    onToggleVoicePause: () -> Unit = {},
    onToggleVoiceMultilingual: () -> Unit = {},
    onQuickVoicePolish: (WritingAction) -> Unit = {},
    onDismissVoiceError: () -> Unit = {}
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    val voiceActive = voiceState.isListening || voiceState.isProcessing
    val showAutofill = inlineAutofillViews.isNotEmpty()
    val showVoiceError = !showAutofill && !voiceActive && !voiceState.errorMessage.isNullOrBlank()
    val showNextWords = suggestions.any { it.isNextWord }
    val undoAvailable = proactiveUndo != null && prefs.proactiveUndo
    val showUndo = !showVoiceError && !showAutofill && undoAvailable && !showNextWords
    val showClipboard = !showVoiceError && !showAutofill && !undoAvailable && !clipboardPreview.isNullOrBlank()
    val showShot = !showClipboard && !showVoiceError && !undoAvailable && !showAutofill &&
        recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping
    val showSuggestions = !showVoiceError && !showUndo && !showShot && !showAutofill && suggestions.isNotEmpty() &&
        (isTyping || showNextWords || suggestions.any { it.isMath || it.isShortcut || it.isGrammarFix })
    val middleKind = when {
        showVoiceError || showUndo || showShot -> 1
        showClipboard -> 4
        showAutofill -> 3
        showSuggestions -> 2
        else -> 0
    }

    AnimatedContent(
        targetState = voiceActive && !showAutofill,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 25, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMedium)
                )) togetherWith fadeOut(tween(110, easing = FastOutSlowInEasing))
        },
        label = "stripVoiceSwitch"
    ) { voice ->
        if (voice) {
            VoiceToolbarRow(
                prefs = prefs,
                voiceState = voiceState,
                onFinishVoice = onFinishVoice,
                onCancelVoice = onCancelVoice,
                onToggleMinimize = onToggleVoiceMinimize,
                onTogglePause = onToggleVoicePause,
                onToggleMultilingual = onToggleVoiceMultilingual
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatStripButton(
                    icon = if (prefs.incognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.GridView,
                    contentDescription = stringResource(R.string.cd_tools_hub),
                    hapticEnabled = prefs.hapticFeedback,
                    onClick = onOpenToolsHub
                )

                if (showAutofill) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        inlineAutofillViews.forEach { autofillView ->
                            key(autofillView) {
                                AndroidView(
                                    factory = {
                                        (autofillView.parent as? ViewGroup)?.removeView(autofillView)
                                        autofillView
                                    },
                                    modifier = Modifier
                                )
                            }
                        }
                    }
                } else {
                AnimatedContent(
                    targetState = middleKind,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 25, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMedium)
                            )) togetherWith fadeOut(tween(110, easing = FastOutSlowInEasing))
                    },
                    label = "stripMiddle"
                ) { kind ->
                    when (kind) {
                        4 -> if (clipboardPreview != null && clipboardTimestamp != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = kbColors.actionSurface.copy(alpha = 0.24f),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.weight(1f).clickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(R.string.clipboard_paste_suggestion),
                                        onClick = {
                                            view.tapHaptic(prefs.hapticFeedback)
                                            onPasteClipboard(clipboardTimestamp)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp).height(36.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (clipboardVerificationCode) Icons.Rounded.Key else Icons.Rounded.ContentPaste,
                                            contentDescription = null,
                                            tint = kbColors.keyText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (clipboardVerificationCode) {
                                                stringResource(R.string.clipboard_verification_code, clipboardPreview)
                                            } else clipboardPreview,
                                            color = kbColors.keyText,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                FlatStripButton(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clipboard_dismiss_suggestion),
                                    hapticEnabled = prefs.hapticFeedback,
                                    onClick = { onDismissClipboard(clipboardTimestamp) }
                                )
                            }
                        }
                        2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                SuggestionCells(
                                    suggestions = suggestions.take(3),
                                    hapticEnabled = prefs.hapticFeedback,
                                    onSelect = onSelectSuggestion
                                )
                            }
                            if (showNextWords && undoAvailable) {
                                FlatStripButton(
                                    icon = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = proactiveUndo?.label ?: stringResource(R.string.btn_undo),
                                    hapticEnabled = prefs.hapticFeedback,
                                    onClick = onTriggerUndo
                                )
                            }
                        }
                        1 -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (showVoiceError && !voiceState.errorMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.clickable {
                                        view.tapHaptic(prefs.hapticFeedback)
                                        onDismissVoiceError()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = voiceState.errorMessage,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (proactiveUndo != null && showUndo) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = kbColors.actionSurface.copy(alpha = 0.22f),
                                    modifier = Modifier.clickable {
                                        view.tapHaptic(prefs.hapticFeedback)
                                        onTriggerUndo()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                                            contentDescription = stringResource(R.string.btn_undo),
                                            tint = kbColors.keyText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = proactiveUndo.label,
                                            color = kbColors.keyText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = kbColors.keyHintText,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable {
                                                    view.tapHaptic(prefs.hapticFeedback)
                                                    onDismissUndo()
                                                }
                                        )
                                    }
                                }
                                if (proactiveUndo.reason == UndoReason.VOICE_INSERT && prefs.aiWritingToolsEnabled) {
                                    val quickActions = listOf(
                                        WritingAction.BULLET_LIST to (if (de) "• Liste" else "• Bullets"),
                                        WritingAction.CONCISE to (if (de) "Kürzer" else "Shorter"),
                                        WritingAction.PROFESSIONAL to (if (de) "Formell" else "Formal")
                                    )
                                    quickActions.forEach { (action, label) ->
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = kbColors.stripChipSurface,
                                            modifier = Modifier.clickable {
                                                view.tapHaptic(prefs.hapticFeedback)
                                                onQuickVoicePolish(action)
                                            }
                                        ) {
                                            Text(
                                                text = label,
                                                color = kbColors.stripChipText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (recentScreenshot != null && showShot) {
                                ScreenshotPreviewStripChip(
                                    shot = recentScreenshot,
                                    hapticEnabled = prefs.hapticFeedback,
                                    onPasteImage = { onTapScreenshotChip(recentScreenshot) },
                                    onPasteOcr = { onTapScreenshotOcr(recentScreenshot) },
                                    onDismiss = onDismissScreenshotChip
                                )
                            }
                        }
                        else -> QuickBarRow(
                            prefs = prefs,
                            de = de,
                            onQuickAction = { action ->
                                view.tapHaptic(prefs.hapticFeedback)
                                onQuickAction(action)
                            },
                            onLongPressQuickBar = onLongPressQuickBar,
                            onHaptic = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                        )
                    }
                }
                }

                FlatStripButton(
                    icon = Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.cd_voice_mic),
                    hapticEnabled = prefs.hapticFeedback,
                    filled = true,
                    onClick = onStartVoice
                )
            }
        }
    }
}

@Composable
private fun ScreenshotPreviewStripChip(
    shot: RecentScreenshot,
    hapticEnabled: Boolean,
    onPasteImage: () -> Unit,
    onPasteOcr: () -> Unit,
    onDismiss: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val thumb by produceState<Bitmap?>(initialValue = null, shot.localImagePath, shot.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                if (!shot.localImagePath.isNullOrBlank()) {
                    BitmapFactory.decodeFile(shot.localImagePath, opts)
                } else {
                    context.contentResolver.openInputStream(shot.uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
            }.getOrNull()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = kbColors.stripChipSurface,
            modifier = Modifier.clickable {
                view.tapHaptic(hapticEnabled)
                onPasteImage()
            }
        ) {
            Row(
                modifier = Modifier.padding(start = 5.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Screenshot,
                        contentDescription = null,
                        tint = kbColors.keyText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.strip_paste_screenshot),
                    color = kbColors.stripChipText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = kbColors.cardAltSurface,
            modifier = Modifier.clickable {
                view.tapHaptic(hapticEnabled)
                onPasteOcr()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint = kbColors.keyText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.strip_screenshot_ocr),
                    color = kbColors.keyText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = kbColors.keyHintText,
            modifier = Modifier
                .size(16.dp)
                .clickable {
                    view.tapHaptic(hapticEnabled)
                    onDismiss()
                }
        )
    }
}

@Composable
private fun FlatStripButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    hapticEnabled: Boolean,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(kbColors.modifierSurface) else Modifier
            )
            .clickable {
                view.tapHaptic(hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = kbColors.modifierText,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 1:1 Gboard suggestion strip cells with subtle vertical dividers and tactile haptic feedback. */
@Composable
private fun SuggestionCells(
    suggestions: List<SuggestionChip>,
    hapticEnabled: Boolean,
    onSelect: (SuggestionChip) -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEachIndexed { index, chip ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(kbColors.keyHintText.copy(alpha = 0.25f))
                )
            }
            val isEmphasized = chip.isCorrection || chip.isGrammarFix || chip.isShortcut || chip.isMath
            val label = when {
                chip.isShortcut -> "⚡ ${chip.text}"
                chip.isMath -> "🧮 ${chip.text}"
                else -> chip.text
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(if (isEmphasized) 16.dp else 12.dp))
                    .background(
                        if (isEmphasized) kbColors.actionSurface.copy(alpha = 0.24f) else Color.Transparent
                    )
                    .clickable(role = Role.Button) {
                        view.tapHaptic(hapticEnabled)
                        onSelect(chip)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = kbColors.stripChipText,
                    fontSize = 15.5.sp,
                    fontWeight = if (isEmphasized) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickBarRow(
    prefs: TapwingPrefs,
    de: Boolean,
    onQuickAction: (QuickBarAction) -> Unit,
    onLongPressQuickBar: () -> Unit,
    onHaptic: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val pinned = prefs.quickBarActions
    if (pinned.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = kbColors.modifierSurface.copy(alpha = 0.45f),
                modifier = Modifier.clickable(onClick = onLongPressQuickBar)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = kbColors.modifierText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.strip_customize_bar),
                        color = kbColors.modifierText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        return
    }
    val rowModifier = if (pinned.size <= 5) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = if (pinned.size <= 5) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pinned.forEach { action ->
            QuickIconPill(
                icon = action.icon(),
                contentDescription = action.title(de),
                highlighted = action.isHighlighted(prefs),
                onClick = { onQuickAction(action) },
                onLongClick = {
                    onHaptic()
                    onLongPressQuickBar()
                }
            )
        }
    }
}

@Composable
private fun RamblerWaveform(
    rmsDb: Float,
    isListening: Boolean
) {
    val normalized = (rmsDb / 12f).coerceIn(0.08f, 1f)
    val transition = rememberInfiniteTransition(label = "ramblerWave")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )
    val multipliers = listOf(0.45f, 0.72f, 0.96f, 0.68f, 1.0f, 0.76f, 0.48f)
    val waveBrush = Brush.verticalGradient(
        listOf(Color(0xFF4285F4), Color(0xFF9B72CB), Color(0xFFD96570))
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(22.dp)
    ) {
        multipliers.forEachIndexed { idx, mult ->
            val phase = if (idx % 2 == 0) shimmer else 1f - shimmer
            val rawHeight = if (isListening) {
                (0.22f + normalized * mult * (0.65f + 0.35f * phase)).coerceIn(0.2f, 1f)
            } else {
                (0.28f + 0.42f * phase).coerceIn(0.22f, 0.8f)
            }
            val barFraction by animateFloatAsState(
                targetValue = rawHeight,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "waveBar$idx"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(barFraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(waveBrush)
            )
        }
    }
}

@Composable
private fun VoiceToolbarRow(
    prefs: TapwingPrefs,
    voiceState: VoiceUiState,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleMinimize: () -> Unit,
    onTogglePause: () -> Unit = {},
    onToggleMultilingual: () -> Unit = {}
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    val pulseBg by animateColorAsState(
        targetValue = if (voiceState.isListening && !voiceState.isPaused) kbColors.actionSurface else kbColors.modifierSurface,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "voicePulseBg"
    )
    val doneBreathScale = if (voiceState.isListening && !voiceState.isPaused) {
        val pulseTransition = rememberInfiniteTransition(label = "voiceDonePulse")
        val breath by pulseTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.045f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voiceDoneBreath"
        )
        breath
    } else {
        1.0f
    }
    val cancelInteraction = remember { MutableInteractionSource() }
    val pauseInteraction = remember { MutableInteractionSource() }
    val minInteraction = remember { MutableInteractionSource() }
    val doneInteraction = remember { MutableInteractionSource() }
    val previewScroll = rememberScrollState()
    val badgeText = voiceState.engineLabel?.takeIf { it.isNotBlank() }
        ?: if (voiceState.isCloud) "Cloud" else if (de) "Lokal" else "Local"

    val timerText = if (voiceState.isListening) {
        val m = voiceState.elapsedSeconds / 60
        val s = voiceState.elapsedSeconds % 60
        "%02d:%02d · ".format(m, s)
    } else {
        ""
    }
    val statusText = when {
        voiceState.isPaused -> stringResource(R.string.voice_paused)
        !voiceState.processingLabel.isNullOrBlank() -> voiceState.processingLabel
        voiceState.partialText.isNotBlank() -> voiceState.partialText
        voiceState.isProcessing && voiceState.isLocalWhisper -> stringResource(R.string.voice_processing_local)
        voiceState.isProcessing -> stringResource(R.string.voice_tidying_up)
        else -> "$timerText${stringResource(R.string.voice_hint_finish)}"
    }

    LaunchedEffect(statusText, voiceState.partialText) {
        if (voiceState.partialText.isNotBlank()) {
            previewScroll.animateScrollTo(previewScroll.maxValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .springPressScale(cancelInteraction, pressedScale = 0.92f)
                .clip(CircleShape)
                .background(kbColors.keySurface)
                .clickable(interactionSource = cancelInteraction, indication = ripple()) {
                    view.tapHaptic(prefs.hapticFeedback)
                    onCancelVoice()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_voice_cancel),
                tint = kbColors.keyText,
                modifier = Modifier.size(18.dp)
            )
        }

        RamblerWaveform(
            rmsDb = if (voiceState.isPaused) 0f else voiceState.rmsDb,
            isListening = voiceState.isListening && !voiceState.isPaused
        )

        if (voiceState.isLocalWhisper || voiceState.isCloud || voiceState.isAndroidOffline || !voiceState.engineLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = kbColors.actionSurface.copy(alpha = 0.22f)
            ) {
                Text(
                    text = badgeText,
                    color = kbColors.keyText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        val langPillText = if (prefs.voiceMultilingualCodeSwitching) {
            voiceState.multilingualLabel?.takeIf { it.isNotBlank() } ?: "DE+EN"
        } else {
            KeyboardLocales.baseLang(prefs.activeLocale).uppercase()
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (prefs.voiceMultilingualCodeSwitching) {
                kbColors.actionSurface.copy(alpha = 0.30f)
            } else {
                kbColors.modifierSurface.copy(alpha = 0.65f)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    view.tapHaptic(prefs.hapticFeedback)
                    onToggleMultilingual()
                }
        ) {
            Text(
                text = langPillText,
                color = kbColors.keyText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (voiceState.partialText.isNotBlank() && !voiceState.isPaused) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(previewScroll),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = statusText,
                    color = kbColors.keyText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            Text(
                text = statusText,
                color = kbColors.keyText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (voiceState.canPause && voiceState.isListening) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .springPressScale(pauseInteraction, pressedScale = 0.92f)
                    .clip(CircleShape)
                    .background(if (voiceState.isPaused) kbColors.actionSurface.copy(alpha = 0.32f) else kbColors.modifierSurface)
                    .clickable(interactionSource = pauseInteraction, indication = ripple()) {
                        view.tapHaptic(prefs.hapticFeedback)
                        onTogglePause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (voiceState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(
                        if (voiceState.isPaused) R.string.cd_voice_resume else R.string.cd_voice_pause
                    ),
                    tint = kbColors.modifierText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (prefs.voiceMinimizeButton) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .springPressScale(minInteraction, pressedScale = 0.92f)
                    .clip(CircleShape)
                    .background(kbColors.modifierSurface)
                    .clickable(interactionSource = minInteraction, indication = ripple()) {
                        view.tapHaptic(prefs.hapticFeedback)
                        onToggleMinimize()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (voiceState.isMinimized) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = stringResource(R.string.cd_voice_minimize),
                    tint = kbColors.modifierText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 36.dp)
                .graphicsLayer {
                    scaleX = doneBreathScale
                    scaleY = doneBreathScale
                }
                .springPressScale(doneInteraction, pressedScale = 0.92f)
                .clip(RoundedCornerShape(percent = 50))
                .background(pulseBg)
                .clickable(interactionSource = doneInteraction, indication = ripple()) {
                    view.confirmHaptic(prefs.hapticFeedback)
                    onFinishVoice()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_voice_done),
                tint = kbColors.actionText,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) kbColors.actionSurface.copy(alpha = 0.25f)
                else kbColors.modifierSurface.copy(alpha = 0.35f)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) kbColors.keyText else kbColors.modifierText,
            modifier = Modifier.size(18.dp)
        )
    }
}
