// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TextEditAction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    LINE_START,
    LINE_END,
    SELECT_ALL,
    COPY,
    CUT,
    PASTE
}

/**
 * Dynamic live keyboard resizer with a draggable top handle, fine slider, and bottom-lift controls.
 */
@Composable
fun KeyboardResizePanel(
    prefs: TapwingPrefs,
    layoutPage: KeyboardLayoutPage,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    onScaleChange: (Int) -> Unit,
    onBottomLiftChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = kbColors.cardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (de) "Tastaturgröße: ${prefs.keyboardScalePercent}%"
                        else "Keyboard height: ${prefs.keyboardScalePercent}%",
                        color = kbColors.keyText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("100%", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onDone,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (de) "Fertig" else "Done", fontSize = 12.sp)
                        }
                    }
                }

                Slider(
                    value = prefs.keyboardScalePercent.toFloat(),
                    onValueChange = { onScaleChange(it.roundToInt()) },
                    valueRange = 75f..135f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (de) "Abstand unten: ${prefs.keyboardBottomLiftDp} dp"
                        else "Bottom offset: ${prefs.keyboardBottomLiftDp} dp",
                        color = kbColors.keyHintText,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onBottomLiftChange((prefs.keyboardBottomLiftDp - 6).coerceAtLeast(0)) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("−", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = { onBottomLiftChange((prefs.keyboardBottomLiftDp + 6).coerceAtMost(48)) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("+", fontSize = 14.sp) }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = kbColors.boardBackground,
            border = BorderStroke(1.5.dp, kbColors.actionSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentScale by rememberUpdatedState(prefs.keyboardScalePercent)
                val currentOnScaleChange by rememberUpdatedState(onScaleChange)
                val resizeStepPx = with(LocalDensity.current) { 5.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .pointerInput(resizeStepPx) {
                            detectVerticalDragGestures(
                                onDragStart = { dragAccumulator = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    dragAccumulator -= dragAmount
                                    var updated = currentScale
                                    while (dragAccumulator >= resizeStepPx) {
                                        updated = (updated + 1).coerceIn(75, 135)
                                        dragAccumulator -= resizeStepPx
                                    }
                                    while (dragAccumulator <= -resizeStepPx) {
                                        updated = (updated - 1).coerceIn(75, 135)
                                        dragAccumulator += resizeStepPx
                                    }
                                    if (updated != currentScale) {
                                        currentOnScaleChange(updated)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = null,
                        tint = kbColors.keyText
                    )
                }

                KeyboardGrid(
                    prefs = prefs,
                    layoutPage = layoutPage,
                    shiftState = shiftState,
                    imeActionLabel = imeActionLabel,
                    onKeyPress = { _, _ -> },
                    onCursorMove = { _, _ -> },
                    onDeletePreviousWord = {},
                    onSwitchLanguage = {}
                )
            }
        }
    }
}

/**
 * Precision Cursor & Selection editing panel with hold-to-repeat D-pad and quick clipboard actions.
 */
@Composable
fun TextEditPanel(
    prefs: TapwingPrefs,
    selectionActive: Boolean,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
    onAction: (TextEditAction) -> Unit,
    onUndo: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = kbColors.keyText)
            }
            Text(
                text = if (de) "Cursor & Textbearbeitung" else "Cursor & Text Edit",
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectionActive,
                onClick = onToggleSelection,
                label = { Text(if (de) "Auswählen" else "Select", fontSize = 12.sp) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditPadButton(Icons.Rounded.KeyboardArrowUp, if (de) "Hoch" else "Up", prefs.hapticFeedback) {
                    onAction(TextEditAction.UP)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditPadButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, if (de) "Links" else "Left", prefs.hapticFeedback) {
                        onAction(TextEditAction.LEFT)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectionActive) kbColors.actionSurface else kbColors.cardSurface,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(onClick = onToggleSelection)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "SEL",
                                color = if (selectionActive) kbColors.actionText else kbColors.keyText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    EditPadButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (de) "Rechts" else "Right", prefs.hapticFeedback) {
                        onAction(TextEditAction.RIGHT)
                    }
                }
                EditPadButton(Icons.Rounded.KeyboardArrowDown, if (de) "Runter" else "Down", prefs.hapticFeedback) {
                    onAction(TextEditAction.DOWN)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EditActionTile(Modifier.weight(1f), Icons.Rounded.SelectAll, if (de) "Alles" else "All") {
                        onAction(TextEditAction.SELECT_ALL)
                    }
                    EditActionTile(Modifier.weight(1f), Icons.Rounded.ContentCopy, if (de) "Kopieren" else "Copy") {
                        onAction(TextEditAction.COPY)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EditActionTile(Modifier.weight(1f), Icons.Rounded.ContentCut, if (de) "Aussch." else "Cut") {
                        onAction(TextEditAction.CUT)
                    }
                    EditActionTile(Modifier.weight(1f), Icons.Rounded.ContentPaste, if (de) "Einfügen" else "Paste") {
                        onAction(TextEditAction.PASTE)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    EditActionTile(Modifier.weight(1f), Icons.AutoMirrored.Rounded.KeyboardArrowLeft, if (de) "Anfang" else "Home") {
                        onAction(TextEditAction.LINE_START)
                    }
                    EditActionTile(Modifier.weight(1f), Icons.AutoMirrored.Rounded.Undo, if (de) "Zurück" else "Undo", onClick = onUndo)
                    EditActionTile(Modifier.weight(1f), Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (de) "Ende" else "End") {
                        onAction(TextEditAction.LINE_END)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPadButton(
    icon: ImageVector,
    contentDescription: String,
    hapticEnabled: Boolean,
    onStep: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentOnStep by rememberUpdatedState(onStep)
    val currentHaptic by rememberUpdatedState(hapticEnabled)
    var pressed by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (pressed) kbColors.keyPressed else kbColors.modifierSurface,
        modifier = Modifier
            .size(52.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pid = down.id
                    pressed = true
                    if (currentHaptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentOnStep()
                    val repeatJob = scope.launch {
                        delay(300L)
                        var ticks = 0
                        while (true) {
                            currentOnStep()
                            if (currentHaptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            ticks++
                            delay(if (ticks > 8) 45L else 75L)
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pid } ?: break
                            if (!change.pressed) break
                        }
                    } finally {
                        repeatJob.cancel()
                        pressed = false
                    }
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = kbColors.modifierText, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun EditActionTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = kbColors.cardSurface,
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(18.dp))
            Text(label, color = kbColors.keyText, fontSize = 11.sp, maxLines = 1)
        }
    }
}
