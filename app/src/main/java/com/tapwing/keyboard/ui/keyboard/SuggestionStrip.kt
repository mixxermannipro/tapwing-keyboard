package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
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

    val voiceActive = voiceState.isListening || voiceState.isProcessing
    val showUndo = proactiveUndo != null && prefs.proactiveUndo
    val showShot = !showUndo && recentScreenshot != null && prefs.screenshotSuggestionChip && !isTyping
    val showSuggestions = !showUndo && !showShot && suggestions.isNotEmpty() &&
        (isTyping || suggestions.any { it.isMath || it.isShortcut || it.isGrammarFix })
    // 0 = quick bar, 1 = undo / screenshot chip, 2 = Gboard-style suggestion cells
    val middleKind = when {
        showUndo || showShot -> 1
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
                // Left: flat tools-hub button (Gboard style)
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

                // Right: flat mic button
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
                        text = if (de) "Leiste anpassen" else "Customize bar",
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
    val statusText = when {
        voiceState.partialText.isNotBlank() -> voiceState.partialText
        voiceState.isProcessing -> if (prefs.activeLocale == "de") "Wird bereinigt…" else "Tidying up…"
        else -> stringResource(R.string.voice_hint_finish)
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
