package com.tapwing.keyboard.ui.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import com.tapwing.keyboard.undo.UndoSnapshot
import com.tapwing.keyboard.voice.VoiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onDismissVoiceError: () -> Unit = {}
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    val voiceActive = voiceState.isListening || voiceState.isProcessing
    val showVoiceError = !voiceActive && !voiceState.errorMessage.isNullOrBlank()
    val showAutofill = !showVoiceError && inlineAutofillViews.isNotEmpty()
    val showUndo = !showVoiceError && !showAutofill && proactiveUndo != null && prefs.proactiveUndo
    val showClipboard = !showVoiceError && !showAutofill && !showUndo && !clipboardPreview.isNullOrBlank()
    val showShot = !showClipboard && !showVoiceError && !showUndo && !showAutofill &&
        recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping
    val showSuggestions = !showVoiceError && !showUndo && !showShot && !showAutofill && suggestions.isNotEmpty() &&
        (isTyping || suggestions.any { it.isMath || it.isShortcut || it.isGrammarFix })
    val middleKind = when {
        showVoiceError || showUndo || showShot -> 1
        showClipboard -> 4
        showAutofill -> 3
        showSuggestions -> 2
        else -> 0
    }

    AnimatedContent(
        targetState = voiceActive,
        transitionSpec = {
            fadeIn(tween(200, delayMillis = 40, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(120, easing = FastOutSlowInEasing))
        },
        label = "stripVoiceSwitch"
    ) { voice ->
        if (voice) {
            VoiceToolbarRow(
                prefs = prefs,
                voiceState = voiceState,
                onFinishVoice = onFinishVoice,
                onCancelVoice = onCancelVoice,
                onToggleMinimize = onToggleVoiceMinimize
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatStripButton(
                    icon = if (prefs.incognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.GridView,
                    contentDescription = stringResource(R.string.cd_tools_hub),
                    onClick = onOpenToolsHub
                )

                AnimatedContent(
                    targetState = middleKind,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        fadeIn(tween(180, delayMillis = 30, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(110, easing = FastOutSlowInEasing))
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
                                        onClick = { onPasteClipboard(clipboardTimestamp) }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp).height(40.dp),
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
                                    onClick = { onDismissClipboard(clipboardTimestamp) }
                                )
                            }
                        }
                        3 -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            inlineAutofillViews.forEach { autofillView ->
                                AndroidView(
                                    factory = {
                                        (autofillView.parent as? ViewGroup)?.removeView(autofillView)
                                        autofillView
                                    },
                                    update = { v ->
                                        v.requestLayout()
                                    },
                                    modifier = Modifier.height(40.dp)
                                )
                            }
                        }
                        2 -> SuggestionCells(
                            suggestions = suggestions.take(3),
                            onSelect = onSelectSuggestion
                        )
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
                                    modifier = Modifier.clickable(onClick = onDismissVoiceError)
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
                                    modifier = Modifier.clickable(onClick = onTriggerUndo)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                                            contentDescription = stringResource(R.string.btn_undo),
                                            tint = kbColors.actionText,
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
                                                .clickable(onClick = onDismissUndo)
                                        )
                                    }
                                }
                            } else if (recentScreenshot != null && showShot) {
                                ScreenshotPreviewStripChip(
                                    shot = recentScreenshot,
                                    onPasteImage = { onTapScreenshotChip(recentScreenshot) },
                                    onPasteOcr = { onTapScreenshotOcr(recentScreenshot) },
                                    onDismiss = onDismissScreenshotChip
                                )
                            }
                        }
                        else -> QuickBarRow(
                            prefs = prefs,
                            de = de,
                            onQuickAction = onQuickAction,
                            onLongPressQuickBar = onLongPressQuickBar,
                            onHaptic = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                        )
                    }
                }

                FlatStripButton(
                    icon = Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.cd_voice_mic),
                    onClick = onStartVoice
                )
            }
        }
    }
}

@Composable
private fun ScreenshotPreviewStripChip(
    shot: RecentScreenshot,
    onPasteImage: () -> Unit,
    onPasteOcr: () -> Unit,
    onDismiss: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val context = LocalContext.current
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
            modifier = Modifier.clickable(onClick = onPasteImage)
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
                            .size(28.dp)
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
            modifier = Modifier.clickable(onClick = onPasteOcr)
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
                .clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun FlatStripButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = kbColors.modifierText,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Gboard-style: up to three equal-width text cells separated by thin dividers. */
@Composable
private fun SuggestionCells(
    suggestions: List<SuggestionChip>,
    onSelect: (SuggestionChip) -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEachIndexed { index, chip ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(kbColors.keyBorder)
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
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(chip) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = kbColors.stripChipText,
                    fontSize = 16.sp,
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
private fun VoiceToolbarRow(
    prefs: TapwingPrefs,
    voiceState: VoiceUiState,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleMinimize: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val pulseBg by animateColorAsState(
        targetValue = if (voiceState.isListening) kbColors.actionSurface else kbColors.modifierSurface,
        label = "voicePulseBg"
    )
    val timerText = if ((voiceState.isLocalWhisper || voiceState.isCloud) && voiceState.isListening) {
        val m = voiceState.elapsedSeconds / 60
        val s = voiceState.elapsedSeconds % 60
        "%02d:%02d · ".format(m, s)
    } else {
        ""
    }
    val statusText = when {
        !voiceState.processingLabel.isNullOrBlank() -> voiceState.processingLabel
        voiceState.partialText.isNotBlank() -> voiceState.partialText
        voiceState.isProcessing && voiceState.isLocalWhisper -> stringResource(R.string.voice_processing_local)
        voiceState.isProcessing -> stringResource(R.string.voice_tidying_up)
        else -> "$timerText${stringResource(R.string.voice_hint_finish)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(kbColors.keySurface)
                .clickable(onClick = onCancelVoice),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_voice_cancel),
                tint = kbColors.keyText,
                modifier = Modifier.size(20.dp)
            )
        }

        if (voiceState.isLocalWhisper || voiceState.isCloud || voiceState.isAndroidOffline) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = kbColors.actionSurface.copy(alpha = 0.22f)
            ) {
                Text(
                    text = if (voiceState.isCloud) "Cloud" else "Offline",
                    color = kbColors.keyText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = statusText,
            color = kbColors.keyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (prefs.voiceMinimizeButton) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(kbColors.modifierSurface)
                    .clickable(onClick = onToggleMinimize),
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(pulseBg)
                .clickable(onClick = onFinishVoice),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_voice_done),
                tint = kbColors.actionText,
                modifier = Modifier.size(22.dp)
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
            .size(36.dp)
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
            modifier = Modifier.size(19.dp)
        )
    }
}
