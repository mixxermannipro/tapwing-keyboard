// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.isHighlighted
import com.tapwing.keyboard.ui.keyboard.title

@Composable
internal fun GlideTypingScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Glide Typing" else "Glide typing")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Glide Typing aktivieren" else "Enable glide typing",
                        checked = prefs.glideTypingEnabled
                    ) { v -> onUpdatePrefs { it.copy(glideTypingEnabled = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Wisch-Löschen aktivieren (⌫)" else "Enable gesture delete",
                        checked = prefs.swipeBackspaceDeleteWord
                    ) { v -> onUpdatePrefs { it.copy(swipeBackspaceDeleteWord = v) } }
                }
                AppleSectionFooter(
                    if (de) "Ziehe deinen Finger über die Buchstaben, um Wörter zu schreiben, oder wische auf der Löschtaste nach links, um ganze Wörter zu entfernen."
                    else "Input words by sliding through letters, or slide left from the delete key to erase whole words."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Cursor-Steuerung (2D-Trackpad)" else "Gesture cursor control (2D Trackpad)")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AppleIconBadge(
                            icon = Icons.Rounded.SpaceBar,
                            tintColor = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (de) "Leertaste gedrückt halten" else "Hold spacebar to move cursor",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (de) "Bewege den Cursor frei nach links, rechts, oben und unten — wie auf einem Trackpad."
                                else "Slide left, right, up, or down to position the cursor smoothly in 2D.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun QuickBarSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val rowStepPx = remember(density) { with(density) { 48.dp.toPx() } }

    val actions = prefs.quickBarActions
    val currentActions by rememberUpdatedState(actions)
    val availableActions = remember(actions) {
        QuickBarAction.entries.filterNot { it in actions }
    }
    var draggedAction by remember { mutableStateOf<QuickBarAction?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Vorschau" else "Preview")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        if (actions.isEmpty()) {
                            Text(
                                text = if (de) "Leer" else "Empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                actions.forEach { action ->
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (action.isHighlighted(prefs)) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = action.icon(),
                                            contentDescription = action.title(de),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(
                    if (de) "Aktiv (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})"
                    else "Pinned (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})"
                )
                AppleGroupedSurface {
                    if (actions.isEmpty()) {
                        Text(
                            text = if (de) "Keine Symbole angeheftet" else "No pinned icons",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        actions.forEachIndexed { index, action ->
                            key(action) {
                                val isDragged = draggedAction == action
                                val canMoveUp = index > 0
                                val canMoveDown = index < actions.lastIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragOffsetY else 0f
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(action, rowStepPx) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedAction = action
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggedAction = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedAction = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { _, dragAmount ->
                                                        dragOffsetY += dragAmount.y
                                                        val list = currentActions
                                                        val curIdx = list.indexOf(action)
                                                        if (curIdx >= 0) {
                                                            if (dragOffsetY > rowStepPx && curIdx < list.lastIndex) {
                                                                onUpdatePrefs {
                                                                    it.copy(
                                                                        quickBarActions = QuickBarAction.move(
                                                                            list,
                                                                            curIdx,
                                                                            curIdx + 1
                                                                        )
                                                                    )
                                                                }
                                                                dragOffsetY -= rowStepPx
                                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                            } else if (dragOffsetY < -rowStepPx && curIdx > 0) {
                                                                onUpdatePrefs {
                                                                    it.copy(
                                                                        quickBarActions = QuickBarAction.move(
                                                                            list,
                                                                            curIdx,
                                                                            curIdx - 1
                                                                        )
                                                                    )
                                                                }
                                                                dragOffsetY += rowStepPx
                                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = if (de) "Verschieben" else "Reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = action.icon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )

                                    Text(
                                        text = action.title(de),
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.move(it.quickBarActions, index, index - 1))
                                            }
                                        },
                                        enabled = canMoveUp,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = if (de) "${action.title(de)} hoch" else "Move ${action.title(de)} up",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.move(it.quickBarActions, index, index + 1))
                                            }
                                        },
                                        enabled = canMoveDown,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = if (de) "${action.title(de)} runter" else "Move ${action.title(de)} down",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.remove(it.quickBarActions, action))
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = if (de) "${action.title(de)} entfernen" else "Remove ${action.title(de)}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            if (index < actions.lastIndex) {
                                AppleHairlineDivider(startIndent = 44.dp)
                            }
                        }
                    }
                }
            }
        }

        if (availableActions.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Weitere Werkzeuge" else "Available tools")
                    AppleGroupedSurface {
                        val canAddMore = actions.size < QuickBarAction.MAX_PINNED_ACTIONS
                        availableActions.forEachIndexed { idx, action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable(enabled = canAddMore) {
                                        onUpdatePrefs {
                                            it.copy(quickBarActions = QuickBarAction.add(it.quickBarActions, action))
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = action.icon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = action.title(de),
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = if (de) "${action.title(de)} hinzufügen" else "Add ${action.title(de)}",
                                    tint = if (canAddMore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (idx < availableActions.lastIndex) {
                                AppleHairlineDivider(startIndent = 48.dp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Standard-5 wiederherstellen" else "Restore default 5",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        onUpdatePrefs { it.copy(quickBarActions = QuickBarAction.DEFAULT_ACTIONS) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Am Griff (≡) ziehen oder direkt auf ein Symbol in der Tastatur-Leiste lange drücken."
                    else "Drag handle (≡) to reorder, or long-press any icon on the keyboard strip."
                )
            }
        }
    }
}
@Composable
internal fun CorrectionsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    @Suppress("UNUSED_PARAMETER") onOpenQuickBar: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Korrekturen" else "Corrections")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Autokorrektur" else "Auto-correction", prefs.autoCorrect) { v ->
                        onUpdatePrefs { it.copy(autoCorrect = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Autokorrektur mit ⌫ rückgängig" else "Undo auto-correct on backspace", prefs.smartBackspaceUndo) { v ->
                        onUpdatePrefs { it.copy(smartBackspaceUndo = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Doppeltipp-Punkt" else "Double-space period", prefs.doubleSpacePeriod) { v ->
                        onUpdatePrefs { it.copy(doubleSpacePeriod = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Rechtschreib- & Grammatik-Chips" else "Spell & grammar chips", prefs.spellGrammarChips) { v ->
                        onUpdatePrefs { it.copy(spellGrammarChips = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Rückgängig-Button anbieten" else "Show undo shortcut", prefs.proactiveUndo) { v ->
                        onUpdatePrefs { it.copy(proactiveUndo = v) }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Vorschläge & Lernen" else "Suggestions & learning")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Adaptives Lernen" else "Adaptive learning", prefs.learningEnabled) { v ->
                        onUpdatePrefs { it.copy(learningEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Pro-App Schreibstil & Kontext lernen" else "Learn style & words per app", prefs.perAppLearningEnabled) { v ->
                        onUpdatePrefs { it.copy(perAppLearningEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Inline-Taschenrechner" else "Inline math calculator", prefs.inlineMathEnabled) { v ->
                        onUpdatePrefs { it.copy(inlineMathEnabled = v) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Gelernte Wörter und Kontext-Vorschläge bleiben zu 100 % lokal auf deinem Gerät."
                    else "Learned words and context suggestions stay 100% on your device."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Datenschutz" else "Privacy")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Inkognito-Modus" else "Incognito mode", prefs.incognitoMode) { v ->
                        onUpdatePrefs { it.copy(incognitoMode = v) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Pausiert das Lernen neuer Wörter und die Schreibstatistik."
                    else "Pauses learning new words and typing statistics."
                )
            }
        }
    }
}
