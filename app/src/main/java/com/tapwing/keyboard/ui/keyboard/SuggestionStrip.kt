package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.clipboard.RecentScreenshot
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SuggestionChip
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import com.tapwing.keyboard.undo.UndoSnapshot
import com.tapwing.keyboard.voice.VoiceUiState

@Composable
fun SuggestionStrip(
    prefs: TapwingPrefs,
    suggestions: List<SuggestionChip>,
    proactiveUndo: UndoSnapshot?,
    recentScreenshot: RecentScreenshot?,
    voiceState: VoiceUiState,
    isTyping: Boolean,
    onOpenToolsHub: () -> Unit,
    onQuickAction: (QuickBarAction) -> Unit,
    onLongPressQuickBar: () -> Unit,
    onSelectSuggestion: (SuggestionChip) -> Unit,
    onTriggerUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onTapScreenshotChip: (RecentScreenshot) -> Unit,
    onStartVoice: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onToggleVoiceMinimize: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val de = prefs.activeLocale == "de"

    if (voiceState.isListening || voiceState.isProcessing) {
        VoiceToolbarRow(
            prefs = prefs,
            voiceState = voiceState,
            onFinishVoice = onFinishVoice,
            onCancelVoice = onCancelVoice,
            onToggleMinimize = onToggleVoiceMinimize
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left 4-square Tools Hub button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(kbColors.modifierSurface)
                .clickable(onClick = onOpenToolsHub),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (prefs.incognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.cd_tools_hub),
                tint = kbColors.modifierText,
                modifier = Modifier.size(20.dp)
            )
        }

        val hasScrollableChips = (proactiveUndo != null && prefs.proactiveUndo) ||
            (recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping) ||
            (isTyping && suggestions.isNotEmpty()) ||
            prefs.incognitoMode

        if (hasScrollableChips && (isTyping || proactiveUndo != null || recentScreenshot != null)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (proactiveUndo != null && prefs.proactiveUndo) {
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
                } else if (recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = kbColors.stripChipSurface,
                        modifier = Modifier.clickable { onTapScreenshotChip(recentScreenshot) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Screenshot,
                                contentDescription = null,
                                tint = kbColors.keyText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (de) "Screenshot-Text" else "Scan screenshot",
                                color = kbColors.stripChipText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (suggestions.isNotEmpty() && (isTyping || suggestions.any { it.isMath || it.isShortcut || it.isGrammarFix })) {
                    if (prefs.aiWritingToolsEnabled && QuickBarAction.WRITING_TOOLS in prefs.quickBarActions) {
                        QuickIconPill(
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_writing_tools),
                            highlighted = true,
                            onClick = { onQuickAction(QuickBarAction.WRITING_TOOLS) },
                            onLongClick = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                onLongPressQuickBar()
                            }
                        )
                    }
                    suggestions.forEach { chip ->
                        val isEmphasized = chip.isCorrection || chip.isGrammarFix || chip.isShortcut || chip.isMath
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isEmphasized) {
                                kbColors.actionSurface.copy(alpha = 0.25f)
                            } else {
                                kbColors.stripChipSurface
                            },
                            modifier = Modifier.clickable { onSelectSuggestion(chip) }
                        ) {
                            val label = when {
                                chip.isShortcut -> "⚡ ${chip.text}"
                                chip.isMath -> "🧮 ${chip.text}"
                                else -> chip.text
                            }
                            Text(
                                text = label,
                                color = kbColors.stripChipText,
                                fontSize = 14.sp,
                                fontWeight = if (isEmphasized) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val pinned = prefs.quickBarActions
            if (pinned.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
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
                                text = if (de) "Leiste anpassen" else "Customize bar",
                                color = kbColors.modifierText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (pinned.size <= 5) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pinned.forEach { action ->
                        QuickIconPill(
                            icon = action.icon(),
                            contentDescription = action.title(de),
                            highlighted = action.isHighlighted(prefs),
                            onClick = { onQuickAction(action) },
                            onLongClick = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                onLongPressQuickBar()
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pinned.forEach { action ->
                        QuickIconPill(
                            icon = action.icon(),
                            contentDescription = action.title(de),
                            highlighted = action.isHighlighted(prefs),
                            onClick = { onQuickAction(action) },
                            onLongClick = {
                                if (prefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                onLongPressQuickBar()
                            }
                        )
                    }
                }
            }
        }

        // Right circular Mic button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(kbColors.modifierSurface)
                .clickable(onClick = onStartVoice),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.cd_voice_mic),
                tint = kbColors.modifierText,
                modifier = Modifier.size(20.dp)
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
    val statusText = when {
        voiceState.partialText.isNotBlank() -> voiceState.partialText
        voiceState.isProcessing -> if (prefs.activeLocale == "de") "Wird bereinigt…" else "Tidying up…"
        else -> stringResource(R.string.voice_hint_finish)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
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
