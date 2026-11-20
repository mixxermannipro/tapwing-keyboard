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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
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

    var altPopup by remember { mutableStateOf<AlternatePopup?>(null) }
    var lastAltPopup by remember { mutableStateOf<AlternatePopup?>(null) }
    var previewChar by remember { mutableStateOf<String?>(null) }
    var gridLeftRoot by remember { mutableFloatStateOf(0f) }
    var gridWidthPx by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    val density = LocalDensity.current
    val itemWidthPx = with(density) { 44.dp.toPx() }
    val edgePx = with(density) { 6.dp.toPx() }
    val deadZonePx = with(density) { 14.dp.toPx() }
    val popupRaisePx = with(density) { 48.dp.roundToPx() }
    if (altPopup != null) lastAltPopup = altPopup

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

        Box(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned {
                    gridLeftRoot = it.positionInRoot().x
                    gridWidthPx = it.size.width.toFloat()
                }
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
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
                                altPopup = null
                                onKeyPress(key, resolvedOutput)
                            },
                            onLongPress = { centerX ->
                                when {
                                    key.type == KeyActionType.SPACE -> onSwitchLanguage()
                                    key.alternates.size == 1 -> {
                                        // Instant 1-symbol long-press input (matching Gboard)
                                        onKeyPress(key, applyShiftCase(key.alternates.first(), shiftState))
                                    }
                                    key.alternates.size > 1 -> {
                                        // Letters (ö, é, ß …) first, so holding a key selects the umlaut right away.
                                        val sorted = key.alternates.sortedBy { alt ->
                                            if (alt.firstOrNull()?.isLetter() == true) 0 else 1
                                        }
                                        val isRightHalf = centerX > gridLeftRoot + gridWidthPx * 0.55f
                                        val items = if (isRightHalf) sorted.reversed() else sorted
                                        val defaultIndex = if (isRightHalf) items.lastIndex else 0
                                        val total = items.size * itemWidthPx
                                        val minLeft = gridLeftRoot + edgePx
                                        val maxLeft = (gridLeftRoot + gridWidthPx - total - edgePx).coerceAtLeast(minLeft)
                                        val rawLeft = if (isRightHalf) {
                                            centerX - total + itemWidthPx / 2f
                                        } else {
                                            centerX - itemWidthPx / 2f
                                        }
                                        val left = rawLeft.coerceIn(minLeft, maxLeft)
                                        altPopup = AlternatePopup(
                                            items = items,
                                            origin = key,
                                            selected = defaultIndex,
                                            leftRootPx = left,
                                            startXPx = centerX
                                        )
                                    }
                                }
                            },
                            onAlternateDrag = { x ->
                                val p = altPopup
                                if (p != null) {
                                    val idx = if (kotlin.math.abs(x - p.startXPx) < deadZonePx) {
                                        p.selected
                                    } else {
                                        ((x - p.leftRootPx) / itemWidthPx).toInt().coerceIn(0, p.items.lastIndex)
                                    }
                                    if (idx != p.selected) {
                                        if (prefs.hapticFeedback) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        altPopup = p.copy(selected = idx)
                                    }
                                }
                            },
                            onAlternateRelease = {
                                val p = altPopup
                                altPopup = null
                                if (p != null) {
                                    onKeyPress(p.origin, applyShiftCase(p.items[p.selected], shiftState))
                                }
                            },
                            onCursorMoveSteps = onCursorMoveSteps,
                            onSwipeBackspaceWord = onDeletePreviousWord
                        )
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = altPopup != null,
            enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.offset {
                IntOffset(
                    ((lastAltPopup?.leftRootPx ?: 0f) - gridLeftRoot).roundToInt(),
                    -popupRaisePx
                )
            }
        ) {
            lastAltPopup?.let { pop ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = kbColors.cardAltSurface,
                    shadowElevation = 10.dp
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        pop.items.forEachIndexed { i, alt ->
                            val sel = i == pop.selected
                            val bg by animateColorAsState(
                                if (sel) kbColors.actionSurface else Color.Transparent,
                                animationSpec = tween(110),
                                label = "altItemBg"
                            )
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(40.dp)
                                    .clip(CircleShape)
                                    .background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = applyShiftCase(alt, shiftState),
                                    color = if (sel) kbColors.actionText else kbColors.keyText,
                                    fontSize = 20.sp,
                                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = previewChar != null && prefs.appleModeEnabled && altPopup == null,
            enter = fadeIn(tween(90)) + scaleIn(initialScale = 0.85f, animationSpec = tween(120)),
            exit = fadeOut(tween(90)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-44).dp)
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
    onLongPress: (Float) -> Unit,
    onAlternateDrag: (Float) -> Unit,
    onAlternateRelease: () -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onSwipeBackspaceWord: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var keyLeftRoot by remember { mutableFloatStateOf(0f) }
    var keyWidthPx by remember { mutableFloatStateOf(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) (if (kbColors.appleLiquidGlass) 0.95f else 0.97f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
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

    val bgTarget = when {
        isActionKey -> kbColors.actionSurface
        key.type == KeyActionType.SHIFT && shiftState != ShiftState.OFF -> kbColors.actionSurface.copy(alpha = 0.85f)
        pressed -> kbColors.keyPressed
        key.type == KeyActionType.SPACE -> kbColors.spacebarSurface
        isModifierKey -> kbColors.modifierSurface
        else -> kbColors.keySurface
    }

    val bgColor by animateColorAsState(
        targetValue = bgTarget,
        animationSpec = tween(if (pressed) 60 else 220, easing = FastOutSlowInEasing),
        label = "keyBg"
    )

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
                        onLongPress(keyLeftRoot + keyWidthPx / 2f)
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
                        onPressStateChanged(false)
                        if (prefs.hapticFeedback) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        onLongPress(keyLeftRoot + keyWidthPx / 2f)
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    if (triggeredLong && change != null && change.pressed) {
                        onAlternateDrag(keyLeftRoot + change.position.x)
                        change.consume()
                    }
                    if (event.changes.all { !it.pressed }) break
                }
                holdJob.cancel()
                pressed = false
                onPressStateChanged(false)
                if (triggeredLong) {
                    onAlternateRelease()
                } else {
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
            .onGloballyPositioned {
                keyLeftRoot = it.positionInRoot().x
                keyWidthPx = it.size.width.toFloat()
            }
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

private data class AlternatePopup(
    val items: List<String>,
    val origin: KeySpec,
    val selected: Int,
    val leftRootPx: Float,
    val startXPx: Float
)
