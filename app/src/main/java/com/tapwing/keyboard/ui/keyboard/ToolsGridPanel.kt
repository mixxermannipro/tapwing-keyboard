// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.settings.springPressScale
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors

private data class ToolTileSpec(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ToolsHubPanel(
    prefs: TapwingPrefs,
    onClose: () -> Unit,
    onOpenCustomizeBar: () -> Unit,
    onOpenWritingTools: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenSnippets: () -> Unit = {},
    onOpenResize: () -> Unit,
    onOpenTextEdit: () -> Unit,
    onOpenScanText: () -> Unit,
    onStartVoice: () -> Unit,
    onOpenEmoji: () -> Unit,
    onCycleOneHanded: () -> Unit,
    onToggleIncognito: () -> Unit,
    onOpenQuickSearch: () -> Unit,
    onToggleAppleMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchLanguage: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    val oneHandedLabel = when (prefs.oneHandedMode) {
        OneHandedMode.OFF -> if (de) "Einhändig" else "One-hand"
        OneHandedMode.RIGHT -> if (de) "Rechts" else "Right"
        OneHandedMode.LEFT -> if (de) "Links" else "Left"
    }

    val items = listOf(
        ToolTileSpec(if (de) "Schreibhilfe" else "Writing", Icons.Rounded.AutoAwesome, onClick = onOpenWritingTools),
        ToolTileSpec(if (de) "Ablage" else "Clipboard", Icons.Rounded.ContentPaste, onClick = onOpenClipboard),
        ToolTileSpec(if (de) "Bausteine" else "Snippets", Icons.Rounded.Description, onClick = onOpenSnippets),
        ToolTileSpec(if (de) "Größe" else "Resize", Icons.Rounded.Height, onClick = onOpenResize),
        ToolTileSpec(if (de) "Text-Edit" else "Text edit", Icons.Rounded.OpenWith, onClick = onOpenTextEdit),
        ToolTileSpec(if (de) "Scannen" else "Scan OCR", Icons.Rounded.DocumentScanner, onClick = onOpenScanText),
        ToolTileSpec(if (de) "Diktat" else "Voice", Icons.Rounded.Mic, onClick = onStartVoice),
        ToolTileSpec("Emoji", Icons.Rounded.EmojiEmotions, onClick = onOpenEmoji),
        ToolTileSpec(oneHandedLabel, Icons.Rounded.PhoneAndroid, active = prefs.oneHandedMode != OneHandedMode.OFF, onClick = onCycleOneHanded),
        ToolTileSpec(if (de) "Inkognito" else "Incognito", Icons.Rounded.VisibilityOff, active = prefs.incognitoMode, onClick = onToggleIncognito),
        ToolTileSpec(if (de) "Websuche" else "Search", Icons.Rounded.Search, onClick = onOpenQuickSearch),
        ToolTileSpec(if (de) "Glas" else "Glass", Icons.Rounded.Smartphone, active = prefs.appleModeEnabled, onClick = onToggleAppleMode),
        ToolTileSpec(if (de) "Optionen" else "Settings", Icons.Rounded.Settings, onClick = onOpenSettings),
        ToolTileSpec(if (de) "Sprache (${prefs.activeLocale.uppercase()})" else "Lang (${prefs.activeLocale.uppercase()})", Icons.Rounded.Language, onClick = onSwitchLanguage)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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
                text = stringResource(R.string.tools_title),
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = kbColors.cardAltSurface,
                modifier = Modifier.clickable(onClick = onOpenCustomizeBar)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(15.dp))
                    Text(
                        text = stringResource(R.string.tools_edit_bar),
                        color = kbColors.keyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { tile ->
                val interactionSource = remember { MutableInteractionSource() }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (tile.active) kbColors.actionSurface.copy(alpha = 0.28f) else kbColors.cardSurface,
                    border = if (tile.active) BorderStroke(1.dp, kbColors.actionSurface) else null,
                    modifier = Modifier
                        .springPressScale(interactionSource, pressedScale = 0.95f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = tile.onClick
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(kbColors.cardAltSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(tile.icon, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(18.dp))
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

/**
 * In-keyboard editor for picking and reordering shortcuts shown on the top suggestion strip.
 */
@Composable
fun QuickBarCustomizePanel(
    prefs: TapwingPrefs,
    onUpdateQuickBar: (List<QuickBarAction>) -> Unit,
    onResetQuickBar: () -> Unit,
    onDone: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    val view = LocalView.current
    val density = LocalDensity.current
    val swapStepPx = remember(density) { with(density) { 42.dp.toPx() } }

    val actions = prefs.quickBarActions
    val currentActions by rememberUpdatedState(actions)
    var selectedAction by remember { mutableStateOf(actions.firstOrNull()) }

    LaunchedEffect(actions) {
        if (selectedAction == null || selectedAction !in actions) {
            selectedAction = actions.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Text(
                text = if (de) "Schnellleiste anpassen" else "Customize toolbar",
                color = kbColors.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onResetQuickBar,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Reset", fontSize = 12.sp)
            }
            Button(
                onClick = onDone,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (de) "Fertig" else "Done", fontSize = 12.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = kbColors.cardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(kbColors.modifierSurface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GridView, contentDescription = null, tint = kbColors.keyHintText, modifier = Modifier.size(15.dp))
                }

                if (actions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (de) "Tippe unten auf Werkzeuge zum Anheften" else "Tap tools below to pin",
                            color = kbColors.keyHintText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions.forEach { action ->
                            val isSelected = action == selectedAction
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) kbColors.actionSurface.copy(alpha = 0.34f) else kbColors.cardAltSurface,
                                border = if (isSelected) BorderStroke(1.5.dp, kbColors.actionSurface) else null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { selectedAction = action }
                                    .pointerInput(action, swapStepPx) {
                                        var dragAccum = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                dragAccum = 0f
                                                selectedAction = action
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                dragAccum += dragAmount
                                                val list = currentActions
                                                val curIdx = list.indexOf(action)
                                                if (curIdx >= 0) {
                                                    if (dragAccum >= swapStepPx && curIdx < list.lastIndex) {
                                                        onUpdateQuickBar(QuickBarAction.move(list, curIdx, curIdx + 1))
                                                        dragAccum -= swapStepPx
                                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    } else if (dragAccum <= -swapStepPx && curIdx > 0) {
                                                        onUpdateQuickBar(QuickBarAction.move(list, curIdx, curIdx - 1))
                                                        dragAccum += swapStepPx
                                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = action.icon(),
                                        contentDescription = action.title(de),
                                        tint = kbColors.keyText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(kbColors.modifierSurface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = kbColors.keyHintText, modifier = Modifier.size(15.dp))
                }
            }
        }

        val activeSelected = selectedAction?.takeIf { it in actions }
        val selectedIdx = activeSelected?.let { actions.indexOf(it) } ?: -1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (activeSelected != null && selectedIdx >= 0) {
                Text(
                    text = "${selectedIdx + 1}. ${activeSelected.shortLabel(de, prefs)} (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})",
                    color = kbColors.keyText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = kbColors.cardSurface,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(enabled = selectedIdx > 0) {
                            onUpdateQuickBar(QuickBarAction.move(actions, selectedIdx, selectedIdx - 1))
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = if (de) "Nach links" else "Move left",
                            tint = if (selectedIdx > 0) kbColors.keyText else kbColors.keyHintText.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = kbColors.cardSurface,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(enabled = selectedIdx < actions.lastIndex) {
                            onUpdateQuickBar(QuickBarAction.move(actions, selectedIdx, selectedIdx + 1))
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = if (de) "Nach rechts" else "Move right",
                            tint = if (selectedIdx < actions.lastIndex) kbColors.keyText else kbColors.keyHintText.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = kbColors.cardAltSurface,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable {
                            val next = QuickBarAction.remove(actions, activeSelected)
                            selectedAction = next.getOrNull(selectedIdx.coerceAtMost(next.lastIndex.coerceAtLeast(0)))
                            onUpdateQuickBar(next)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = if (de) "Entfernen" else "Remove",
                            tint = kbColors.keyText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = if (de) "Ziehen oder antippen zum Sortieren (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})"
                    else "Drag or tap items to reorder (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})",
                    color = kbColors.keyHintText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(QuickBarAction.entries) { action ->
                val pinnedIdx = actions.indexOf(action)
                val isPinned = pinnedIdx >= 0
                val isSelected = action == activeSelected
                val canAddMore = isPinned || actions.size < QuickBarAction.MAX_PINNED_ACTIONS
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isSelected -> kbColors.actionSurface.copy(alpha = 0.32f)
                        isPinned -> kbColors.cardAltSurface
                        else -> kbColors.cardSurface
                    },
                    border = if (isPinned) BorderStroke(1.dp, kbColors.actionSurface.copy(alpha = 0.7f)) else null,
                    modifier = Modifier.clickable(enabled = canAddMore) {
                        if (isPinned) {
                            selectedAction = action
                        } else {
                            onUpdateQuickBar(QuickBarAction.add(actions, action))
                            selectedAction = action
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (isPinned) "#${pinnedIdx + 1}" else "+",
                            color = if (isPinned) kbColors.keyText else kbColors.keyHintText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 3.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = action.icon(),
                                contentDescription = null,
                                tint = kbColors.keyText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = action.shortLabel(de, prefs),
                                color = kbColors.keyText,
                                fontSize = 10.sp,
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
}
