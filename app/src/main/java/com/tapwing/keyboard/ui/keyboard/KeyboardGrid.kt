package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KeyboardGrid(
    prefs: TapwingPrefs,
    layoutPage: KeyboardLayoutPage,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    onKeyPress: (KeySpec, String) -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onSwitchLanguage: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val rows = remember(layoutPage, prefs.activeLocale, prefs.showGermanUmlautKeys, prefs.showNumberRow) {
        KeyboardLayouts.rowsFor(
            page = layoutPage,
            locale = prefs.activeLocale,
            showGermanUmlauts = prefs.showGermanUmlautKeys,
            showNumberRow = prefs.showNumberRow
        )
    }

    var activeAlternates by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeAlternateOrigin by remember { mutableStateOf<KeySpec?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (activeAlternates.isNotEmpty() && activeAlternateOrigin != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = kbColors.cardSurface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    activeAlternates.forEach { alt ->
                        val casedAlt = applyShiftCase(alt, shiftState)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = kbColors.stripChipSurface,
                            modifier = Modifier
                                .clickable {
                                    onKeyPress(activeAlternateOrigin!!, casedAlt)
                                    activeAlternates = emptyList()
                                    activeAlternateOrigin = null
                                }
                        ) {
                            Text(
                                text = casedAlt,
                                color = kbColors.keyText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        val rowHeight = prefs.keyboardHeight.rowHeightDp.dp
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (rowIndex == 1 && row.size == 9) 14.dp else 0.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    ExpressiveKeyCap(
                        modifier = Modifier
                            .weight(key.weight)
                            .height(rowHeight),
                        key = key,
                        prefs = prefs,
                        shiftState = shiftState,
                        imeActionLabel = imeActionLabel,
                        onPress = { resolvedOutput ->
                            activeAlternates = emptyList()
                            activeAlternateOrigin = null
                            onKeyPress(key, resolvedOutput)
                        },
                        onLongPress = {
                            if (key.type == KeyActionType.SPACE) {
                                onSwitchLanguage()
                            } else if (key.alternates.isNotEmpty()) {
                                activeAlternates = key.alternates
                                activeAlternateOrigin = key
                            }
                        },
                        onCursorMoveSteps = onCursorMoveSteps
                    )
                }
            }
        }
    }
}

enum class KeyActionLabel {
    ENTER,
    SEARCH,
    SEND,
    DONE,
    NEXT
}

@Composable
private fun ExpressiveKeyCap(
    modifier: Modifier,
    key: KeySpec,
    prefs: TapwingPrefs,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    onPress: (String) -> Unit,
    onLongPress: () -> Unit,
    onCursorMoveSteps: (Int) -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val isActionKey = key.type == KeyActionType.ENTER
    val isModifierKey = key.type in setOf(
        KeyActionType.SHIFT,
        KeyActionType.BACKSPACE,
        KeyActionType.SYMBOLS_TOGGLE,
        KeyActionType.SYMBOLS_EXT_TOGGLE,
        KeyActionType.COMMA,
        KeyActionType.EMOJI_PANEL,
        KeyActionType.PERIOD
    )

    val cornerShape: Shape = when {
        isActionKey -> CircleShape
        prefs.roundedExpressiveKeys && isModifierKey -> CircleShape
        prefs.roundedExpressiveKeys -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(7.dp)
    }

    val bgColor = when {
        isActionKey -> kbColors.actionSurface
        key.type == KeyActionType.SHIFT && shiftState != ShiftState.OFF -> kbColors.actionSurface.copy(alpha = 0.25f)
        pressed -> kbColors.keyPressed
        isModifierKey -> kbColors.modifierSurface
        else -> kbColors.keySurface
    }

    val fgColor = when {
        isActionKey -> kbColors.actionText
        isModifierKey -> kbColors.modifierText
        else -> kbColors.keyText
    }

    val border = if (prefs.showKeyBorders && !isActionKey) {
        BorderStroke(0.5.dp, kbColors.keyBorder)
    } else null

    val displayText = if (key.type == KeyActionType.CHAR) {
        applyShiftCase(key.label, shiftState)
    } else {
        key.label
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val interactionModifier = if (key.type == KeyActionType.SPACE) {
        modifier
            .pointerInput(key, prefs.hapticFeedback) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        val stepPx = 22f
                        while (dragAccumulator >= stepPx) {
                            onCursorMoveSteps(1)
                            dragAccumulator -= stepPx
                        }
                        while (dragAccumulator <= -stepPx) {
                            onCursorMoveSteps(-1)
                            dragAccumulator += stepPx
                        }
                    }
                )
            }
            .pointerInput(key, prefs.hapticFeedback) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (prefs.hapticFeedback) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    var longJob: Job? = scope.launch {
                        delay(420L)
                        onLongPress()
                    }
                    val startTime = System.currentTimeMillis()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    longJob?.cancel()
                    longJob = null
                    pressed = false
                    if (System.currentTimeMillis() - startTime < 410L && kotlin.math.abs(dragAccumulator) < 8f) {
                        onPress(" ")
                    }
                    dragAccumulator = 0f
                }
            }
    } else {
        modifier.pointerInput(key, shiftState, prefs.hapticFeedback) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                if (prefs.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                var triggeredRepeatOrLong = false
                val holdJob = scope.launch {
                    if (key.type == KeyActionType.BACKSPACE) {
                        delay(360L)
                        triggeredRepeatOrLong = true
                        while (true) {
                            onPress("")
                            delay(55L)
                        }
                    } else if (key.alternates.isNotEmpty()) {
                        delay(380L)
                        triggeredRepeatOrLong = true
                        onLongPress()
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed }) break
                }
                holdJob.cancel()
                pressed = false
                if (!triggeredRepeatOrLong) {
                    val output = if (key.type == KeyActionType.CHAR) {
                        applyShiftCase(key.output, shiftState)
                    } else {
                        key.output
                    }
                    onPress(output)
                }
            }
        }
    }

    Surface(
        modifier = interactionModifier
            .then(
                if (!isModifierKey && !isActionKey) Modifier.shadow(0.5.dp, cornerShape)
                else Modifier
            ),
        shape = cornerShape,
        color = bgColor,
        border = border
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (key.hint != null && key.type == KeyActionType.CHAR) {
                Text(
                    text = key.hint,
                    color = kbColors.keyHintText,
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 4.dp)
                )
            }

            when (key.type) {
                KeyActionType.SHIFT -> Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = key.label,
                    tint = if (shiftState == ShiftState.CAPS_LOCK) kbColors.actionSurface else fgColor,
                    modifier = Modifier.size(20.dp)
                )
                KeyActionType.BACKSPACE -> Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = key.label,
                    tint = fgColor,
                    modifier = Modifier.size(20.dp)
                )
                KeyActionType.EMOJI_PANEL -> Icon(
                    imageVector = Icons.Rounded.EmojiEmotions,
                    contentDescription = key.label,
                    tint = fgColor,
                    modifier = Modifier.size(20.dp)
                )
                KeyActionType.ENTER -> {
                    val icon = when (imeActionLabel) {
                        KeyActionLabel.SEARCH -> Icons.Rounded.Search
                        KeyActionLabel.SEND -> Icons.AutoMirrored.Rounded.Send
                        KeyActionLabel.DONE -> Icons.Rounded.Check
                        else -> Icons.AutoMirrored.Rounded.KeyboardReturn
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = key.label,
                        tint = fgColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
                KeyActionType.SPACE -> Text(
                    text = displayText,
                    color = kbColors.keyHintText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                KeyActionType.SYMBOLS_TOGGLE, KeyActionType.SYMBOLS_EXT_TOGGLE -> Text(
                    text = displayText,
                    color = fgColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                else -> Text(
                    text = displayText,
                    color = fgColor,
                    fontSize = if (key.hint != null) 19.sp else 21.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = if (key.hint != null) Modifier.padding(top = 5.dp) else Modifier
                )
            }
        }
    }
}

private fun applyShiftCase(raw: String, shiftState: ShiftState): String {
    if (shiftState == ShiftState.OFF) return raw
    return if (raw == "ß") "ẞ" else raw.uppercase()
}
