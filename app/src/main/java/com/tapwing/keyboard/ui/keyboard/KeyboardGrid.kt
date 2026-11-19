package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
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
    onDeletePreviousWord: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onToggleOneHandedSide: () -> Unit = {},
    onExitOneHanded: () -> Unit = {}
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
    var previewChar by remember { mutableStateOf<String?>(null) }

    // Continuous dynamic height scaling (75%..135%) applied smoothly on top of keyboardHeight preset
    val baseDp = if (prefs.showNumberRow) 46f else prefs.keyboardHeight.rowHeightDp.toFloat()
    val scaleFactor = (prefs.keyboardScalePercent / 100f) * (prefs.keyboardHeight.rowHeightDp / 48f)
    val rowHeight = (baseDp * scaleFactor).coerceIn(33f, 70f).dp
    val rowGap = if (prefs.showNumberRow) 5.dp else 6.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefs.oneHandedMode == OneHandedMode.RIGHT) {
            OneHandedSideRail(
                onSwapSide = onToggleOneHandedSide,
                onExpand = onExitOneHanded
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            AnimatedVisibility(
                visible = previewChar != null && prefs.appleModeEnabled && activeAlternates.isEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = kbColors.cardSurface,
                        border = BorderStroke(0.8.dp, kbColors.keyGlassHighlight),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = previewChar.orEmpty(),
                            color = kbColors.keyText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (activeAlternates.isNotEmpty() && activeAlternateOrigin != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = kbColors.cardSurface,
                    border = if (kbColors.appleLiquidGlass) {
                        BorderStroke(0.8.dp, kbColors.keyGlassHighlight)
                    } else null,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
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
                                shape = CircleShape,
                                color = kbColors.stripChipSurface,
                                modifier = Modifier.clickable {
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
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            rows.forEachIndexed { rowIndex, row ->
                val isIndentedAlphaRow = row.size == 9 && row.first().type == KeyActionType.CHAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isIndentedAlphaRow) 16.dp else 0.dp),
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
                            onPressStateChanged = { isDown ->
                                previewChar = if (isDown && key.type == KeyActionType.CHAR) {
                                    applyShiftCase(key.label, shiftState)
                                } else null
                            },
                            onPress = { resolvedOutput ->
                                activeAlternates = emptyList()
                                activeAlternateOrigin = null
                                onKeyPress(key, resolvedOutput)
                            },
                            onLongPress = {
                                when {
                                    key.type == KeyActionType.SPACE -> onSwitchLanguage()
                                    key.alternates.size == 1 -> {
                                        // Instant 1-symbol long-press input (matching PixelBoard!)
                                        onKeyPress(key, applyShiftCase(key.alternates.first(), shiftState))
                                    }
                                    key.alternates.size > 1 -> {
                                        activeAlternates = key.alternates
                                        activeAlternateOrigin = key
                                    }
                                }
                            },
                            onCursorMoveSteps = onCursorMoveSteps,
                            onSwipeBackspaceWord = onDeletePreviousWord
                        )
                    }
                }
            }
        }

        if (prefs.oneHandedMode == OneHandedMode.LEFT) {
            OneHandedSideRail(
                onSwapSide = onToggleOneHandedSide,
                onExpand = onExitOneHanded
            )
        }
    }
}

@Composable
private fun OneHandedSideRail(
    onSwapSide: () -> Unit,
    onExpand: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Column(
        modifier = Modifier
            .width(48.dp)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = kbColors.modifierSurface,
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onExpand)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = kbColors.modifierText, modifier = Modifier.size(18.dp))
            }
        }
        Surface(
            shape = CircleShape,
            color = kbColors.modifierSurface,
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onSwapSide)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = kbColors.modifierText, modifier = Modifier.size(20.dp))
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
    onPressStateChanged: (Boolean) -> Unit,
    onPress: (String) -> Unit,
    onLongPress: () -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onSwipeBackspaceWord: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) (if (kbColors.appleLiquidGlass) 0.91f else 0.96f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "keyPressScale"
    )

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

    // 1:1 PixelBoard capsule pills (RoundedCornerShape 50% vertical capsule)
    val cornerShape: Shape = if (prefs.roundedExpressiveKeys) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(7.dp)
    }

    val bgColor = when {
        isActionKey -> kbColors.actionSurface
        key.type == KeyActionType.SHIFT && shiftState != ShiftState.OFF -> kbColors.actionSurface.copy(alpha = 0.85f)
        pressed -> kbColors.keyPressed
        key.type == KeyActionType.SPACE -> kbColors.spacebarSurface
        isModifierKey -> kbColors.modifierSurface
        else -> kbColors.keySurface
    }

    val fgColor = when {
        isActionKey -> kbColors.actionText
        key.type == KeyActionType.SHIFT && shiftState != ShiftState.OFF -> kbColors.actionText
        isModifierKey -> kbColors.modifierText
        else -> kbColors.keyText
    }

    val border: BorderStroke? = when {
        kbColors.appleLiquidGlass -> BorderStroke(
            width = 0.75.dp,
            brush = Brush.verticalGradient(
                listOf(kbColors.keyGlassHighlight, kbColors.keyBorder)
            )
        )
        prefs.showKeyBorders && !isActionKey -> BorderStroke(0.5.dp, kbColors.keyBorder)
        else -> null
    }

    val displayText = if (key.type == KeyActionType.CHAR) {
        applyShiftCase(key.label, shiftState)
    } else {
        key.label
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val interactionModifier = when (key.type) {
        KeyActionType.SPACE -> modifier
            .pointerInput(key, prefs.hapticFeedback, prefs.longPressDelayMs) {
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
            .pointerInput(key, prefs.hapticFeedback, prefs.longPressDelayMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (prefs.hapticFeedback) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    val longJob = scope.launch {
                        delay(prefs.longPressDelayMs.toLong() + 140L)
                        onLongPress()
                    }
                    val startTime = System.currentTimeMillis()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    longJob.cancel()
                    pressed = false
                    if (System.currentTimeMillis() - startTime < (prefs.longPressDelayMs + 130) &&
                        kotlin.math.abs(dragAccumulator) < 8f
                    ) {
                        onPress(" ")
                    }
                    dragAccumulator = 0f
                }
            }

        KeyActionType.BACKSPACE -> modifier
            .pointerInput(key, prefs.hapticFeedback, prefs.swipeBackspaceDeleteWord) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (prefs.hapticFeedback) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    var triggeredRepeatOrSwipe = false
                    var startX = down.position.x
                    val holdJob = scope.launch {
                        delay(340L)
                        triggeredRepeatOrSwipe = true
                        while (true) {
                            onPress("")
                            delay(55L)
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && prefs.swipeBackspaceDeleteWord) {
                            val dx = change.position.x - startX
                            if (dx <= -42f) {
                                holdJob.cancel()
                                triggeredRepeatOrSwipe = true
                                startX = change.position.x
                                onSwipeBackspaceWord()
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                    holdJob.cancel()
                    pressed = false
                    if (!triggeredRepeatOrSwipe) {
                        onPress("")
                    }
                }
            }

        else -> modifier.pointerInput(key, shiftState, prefs.hapticFeedback, prefs.longPressDelayMs) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                onPressStateChanged(true)
                if (prefs.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                var triggeredLong = false
                val holdJob = scope.launch {
                    if (key.alternates.isNotEmpty()) {
                        delay(prefs.longPressDelayMs.toLong())
                        triggeredLong = true
                        onLongPress()
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed }) break
                }
                holdJob.cancel()
                pressed = false
                onPressStateChanged(false)
                if (!triggeredLong) {
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

    val shadowMod = if (kbColors.appleLiquidGlass) {
        Modifier.shadow(if (pressed) 1.dp else 3.dp, cornerShape, clip = false)
    } else if (!isModifierKey && !isActionKey) {
        Modifier.shadow(0.5.dp, cornerShape)
    } else Modifier

    Surface(
        modifier = interactionModifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(shadowMod),
        shape = cornerShape,
        color = bgColor,
        border = border
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (kbColors.appleLiquidGlass) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (kbColors.isDark) 0.08f else 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1:1 PixelBoard top-center symbol hint
            if (key.hint != null && key.type == KeyActionType.CHAR) {
                Text(
                    text = key.hint,
                    color = kbColors.keyHintText,
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }

            when (key.type) {
                KeyActionType.SHIFT -> Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = key.label,
                    tint = fgColor,
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
                KeyActionType.SPACE -> {
                    if (prefs.showSpacebarLanguageLabel || key.label == " ") {
                        Text(
                            text = if (prefs.showSpacebarLanguageLabel) displayText else "",
                            color = kbColors.keyHintText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                }
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
                    modifier = if (key.hint != null) Modifier.padding(top = 7.dp) else Modifier
                )
            }
        }
    }
}

private fun applyShiftCase(raw: String, shiftState: ShiftState): String {
    if (shiftState == ShiftState.OFF) return raw
    return if (raw == "ß") "ẞ" else raw.uppercase()
}
