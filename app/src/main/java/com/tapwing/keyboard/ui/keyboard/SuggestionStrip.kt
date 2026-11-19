package com.tapwing.keyboard.ui.keyboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.clipboard.RecentScreenshot
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
    onOpenWritingTools: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenScanText: () -> Unit,
    onOpenSettings: () -> Unit,
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
                imageVector = Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.cd_tools_hub),
                tint = kbColors.modifierText,
                modifier = Modifier.size(20.dp)
            )
        }

        // Center area: Proactive Undo OR Screenshot OCR chip OR Typing Suggestions OR Quick Actions
        val hasScrollableChips = (proactiveUndo != null && prefs.proactiveUndo) ||
            (recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping) ||
            (isTyping && suggestions.isNotEmpty())

        if (hasScrollableChips) {
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
                        color = kbColors.actionSurface.copy(alpha = 0.14f),
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
                                tint = kbColors.actionSurface,
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
                                tint = kbColors.actionSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (prefs.activeLocale == "de") "Screenshot-Text" else "Scan screenshot",
                                color = kbColors.stripChipText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (isTyping && suggestions.isNotEmpty()) {
                    if (prefs.aiWritingToolsEnabled && prefs.showWritingToolsIcon) {
                        QuickIconPill(
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_writing_tools),
                            highlighted = true,
                            onClick = onOpenWritingTools
                        )
                    }
                    suggestions.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (chip.isCorrection || chip.isGrammarFix) {
                                kbColors.actionSurface.copy(alpha = 0.14f)
                            } else {
                                kbColors.stripChipSurface
                            },
                            modifier = Modifier.clickable { onSelectSuggestion(chip) }
                        ) {
                            Text(
                                text = chip.text,
                                color = if (chip.isCorrection || chip.isGrammarFix) {
                                    kbColors.actionSurface
                                } else {
                                    kbColors.stripChipText
                                },
                                fontSize = 14.sp,
                                fontWeight = if (chip.isCorrection || chip.isGrammarFix) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prefs.aiWritingToolsEnabled && prefs.showWritingToolsIcon) {
                    QuickIconPill(
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_writing_tools),
                        highlighted = true,
                        onClick = onOpenWritingTools
                    )
                }
                if (prefs.clipboardEnabled && prefs.showClipboardIcon) {
                    QuickIconPill(
                        icon = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.cd_clipboard),
                        highlighted = false,
                        onClick = onOpenClipboard
                    )
                }
                if (prefs.showScanTextIcon) {
                    QuickIconPill(
                        icon = Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(R.string.cd_scan_text),
                        highlighted = false,
                        onClick = onOpenScanText
                    )
                }
                QuickIconPill(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    highlighted = false,
                    onClick = onOpenSettings
                )
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
        // Cancel X circle
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

        // Center live status / partial transcript
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

        // Primary violet circular confirm / mic button
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

@Composable
private fun QuickIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) kbColors.actionSurface.copy(alpha = 0.14f)
                else kbColors.boardBackground
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) kbColors.actionSurface else kbColors.modifierText,
            modifier = Modifier.size(19.dp)
        )
    }
}
