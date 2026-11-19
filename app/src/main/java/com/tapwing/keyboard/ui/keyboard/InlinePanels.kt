package com.tapwing.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.emoji.EmojiCatalog
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors

@Composable
fun ToolsHubPanel(
    prefs: TapwingPrefs,
    onClose: () -> Unit,
    onOpenWritingTools: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenScanText: () -> Unit,
    onStartVoice: () -> Unit,
    onOpenEmoji: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchLanguage: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = prefs.activeLocale == "de"

    val items = listOf(
        ToolTileSpec(if (de) "Schreibhilfe" else "Writing", Icons.Rounded.AutoAwesome, onOpenWritingTools),
        ToolTileSpec(if (de) "Ablage" else "Clipboard", Icons.Rounded.ContentPaste, onOpenClipboard),
        ToolTileSpec(if (de) "Scannen" else "Scan OCR", Icons.Rounded.DocumentScanner, onOpenScanText),
        ToolTileSpec(if (de) "Diktat" else "Voice", Icons.Rounded.Mic, onStartVoice),
        ToolTileSpec("Emoji", Icons.Rounded.EmojiEmotions, onOpenEmoji),
        ToolTileSpec(if (de) "Design" else "Theme", Icons.Rounded.Palette, onCycleTheme),
        ToolTileSpec(if (de) "Optionen" else "Settings", Icons.Rounded.Settings, onOpenSettings),
        ToolTileSpec(if (de) "Sprache (${prefs.activeLocale.uppercase()})" else "Lang (${prefs.activeLocale.uppercase()})", Icons.Rounded.Language, onSwitchLanguage)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Text(
                text = if (de) "Tapwing Werkzeuge" else "Tapwing Tools",
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { tile ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = kbColors.cardSurface,
                    modifier = Modifier.clickable(onClick = tile.onClick)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(kbColors.cardAltSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(tile.icon, contentDescription = null, tint = kbColors.actionSurface, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = tile.label,
                            color = kbColors.keyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private data class ToolTileSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun WritingToolsPanel(
    prefs: TapwingPrefs,
    sourceText: String,
    output: WritingToolsOutput?,
    isLoading: Boolean,
    selectedAction: WritingAction?,
    onBack: () -> Unit,
    onRunAction: (WritingAction) -> Unit,
    onApplyResult: (String) -> Unit,
    onUndoApplied: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = prefs.activeLocale == "de"

    val actions = listOf(
        WritingAction.PROOFREAD to (if (de) "Korrigieren" else "Proofread"),
        WritingAction.PROFESSIONAL to (if (de) "Professionell" else "Professional"),
        WritingAction.CASUAL to (if (de) "Locker" else "Casual"),
        WritingAction.CONCISE to (if (de) "Kürzer" else "Concise"),
        WritingAction.EMOTIVE to (if (de) "Ausdrucksstark" else "Emotive"),
        WritingAction.REPHRASE to (if (de) "Umformulieren" else "Rephrase")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(266.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Text(
                text = stringResource(R.string.writing_tools_title),
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (output != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = kbColors.cardAltSurface) {
                    Text(
                        text = output.badge,
                        color = kbColors.actionSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // White rounded main preview card matching showcase.png
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = kbColors.cardSurface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (sourceText.isBlank()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = kbColors.actionSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.writing_tools_empty_title),
                        color = kbColors.keyText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.writing_tools_empty_desc),
                        color = kbColors.keyHintText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = if (de) "Wird überarbeitet…" else "Rewriting…",
                                color = kbColors.keyHintText,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val previewText = output?.rewrittenText?.takeIf { it.isNotBlank() } ?: sourceText
                        Text(
                            text = previewText,
                            color = kbColors.keyText,
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (output?.fallbackNote != null) {
                            Text(
                                text = output.fallbackNote,
                                color = kbColors.keyHintText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (output != null && output.rewrittenText.isNotBlank() && !isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onUndoApplied,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.btn_undo), fontSize = 13.sp)
                            }
                            Button(
                                onClick = { onApplyResult(output.rewrittenText) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_apply), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Action chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions.forEach { (action, label) ->
                FilterChip(
                    selected = selectedAction == action,
                    onClick = { onRunAction(action) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
fun WritingToolsOnboardingCard(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = kbColors.cardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = kbColors.keyHintText)
                }
            }
            Text(
                text = stringResource(R.string.writing_tools_onboarding_title),
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.writing_tools_onboarding_desc),
                color = kbColors.keyHintText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_continue))
            }
        }
    }
}

@Composable
fun NaturalVoiceOnboardingSheet(
    onGetStarted: () -> Unit,
    onDismiss: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = kbColors.cardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = kbColors.actionSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = kbColors.keyHintText)
                }
            }

            Text(
                text = stringResource(R.string.voice_onboarding_title),
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OnboardingFeatureCard(
                icon = Icons.Rounded.AutoFixHigh,
                title = stringResource(R.string.voice_onboarding_f1_title),
                subtitle = stringResource(R.string.voice_onboarding_f1_desc)
            )
            OnboardingFeatureCard(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.voice_onboarding_f2_title),
                subtitle = stringResource(R.string.voice_onboarding_f2_desc)
            )
            OnboardingFeatureCard(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.voice_onboarding_f3_title),
                subtitle = stringResource(R.string.voice_onboarding_f3_desc)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = kbColors.keyHintText, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.voice_onboarding_privacy),
                    color = kbColors.keyHintText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_get_started))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_no_thanks))
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = kbColors.cardAltSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = kbColors.keyText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = kbColors.keyHintText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ClipboardPanel(
    prefs: TapwingPrefs,
    clips: List<ClipboardClip>,
    onBack: () -> Unit,
    onPasteClip: (ClipboardClip) -> Unit,
    onTogglePin: (ClipboardClip) -> Unit,
    onDeleteClip: (ClipboardClip) -> Unit,
    onClearUnpinned: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = prefs.activeLocale == "de"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Text(
                text = if (de) "Zwischenablage (${clips.size}/${prefs.clipboardMaxItems})"
                else "Clipboard (${clips.size}/${prefs.clipboardMaxItems})",
                color = kbColors.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (clips.any { !it.pinned }) {
                TextButton(onClick = onClearUnpinned) {
                    Text(if (de) "Leeren" else "Clear", fontSize = 12.sp)
                }
            }
        }

        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (de) "Kopierte Texte erscheinen hier automatisch."
                    else "Copied text clips will appear here automatically.",
                    color = kbColors.keyHintText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(clips, key = { it.id }) { clip ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (clip.pinned) kbColors.cardAltSurface else kbColors.cardSurface,
                        modifier = Modifier.clickable { onPasteClip(clip) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = clip.text,
                                color = kbColors.keyText,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = null,
                                    tint = if (clip.pinned) kbColors.actionSurface else kbColors.keyHintText,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onTogglePin(clip) }
                                )
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = kbColors.keyHintText,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteClip(clip) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiPanel(
    prefs: TapwingPrefs,
    onBack: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    var selectedCategoryId by remember { mutableStateOf(EmojiCatalog.categories.first().id) }
    val activeCategory = remember(selectedCategoryId) {
        EmojiCatalog.categories.firstOrNull { it.id == selectedCategoryId } ?: EmojiCatalog.categories.first()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EmojiCatalog.categories.forEach { cat ->
                    val selected = cat.id == activeCategory.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) kbColors.actionSurface.copy(alpha = 0.16f) else kbColors.boardBackground,
                        modifier = Modifier.clickable { selectedCategoryId = cat.id }
                    ) {
                        Text(
                            text = cat.icon,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onBackspace, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.cd_backspace), tint = kbColors.keyText)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(activeCategory.emojis) { (emoji, _) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInsertEmoji(emoji) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = prefs.emojiScale.sp
                    )
                }
            }
        }
    }
}
