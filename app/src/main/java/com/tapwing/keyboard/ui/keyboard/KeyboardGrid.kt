package com.tapwing.keyboard.ui.keyboard

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.SplitKeyboardMode
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.KeyCenter
import com.tapwing.keyboard.dictionary.SwipePoint
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun KeyboardGrid(
    prefs: TapwingPrefs,
    layoutPage: KeyboardLayoutPage,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    onKeyPress: (KeySpec, String) -> Unit,
    onSwipeWord: (List<SwipePoint>, List<KeyCenter>) -> Unit = { _, _ -> },
    onCursorMoveSteps: (Int) -> Unit,
    onDeletePreviousWord: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onToggleOneHandedSide: () -> Unit = {},
    onExitOneHanded: () -> Unit = {}
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

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
    var activePreview by remember { mutableStateOf<KeyPreviewBubble?>(null) }
    var gridLeftRoot by remember { mutableFloatStateOf(0f) }
    var gridTopRoot by remember { mutableFloatStateOf(0f) }
    var gridWidthPx by remember { mutableFloatStateOf(0f) }
    val keyCenterMap = remember { mutableStateMapOf<String, KeyCenter>() }
    val swipeTrail = remember { mutableStateListOf<SwipePoint>() }

    val view = LocalView.current
    val density = LocalDensity.current
    val itemWidthPx = with(density) { 44.dp.toPx() }
    val edgePx = with(density) { 6.dp.toPx() }
    val deadZonePx = with(density) { 14.dp.toPx() }
    val popupRaisePx = with(density) { 48.dp.roundToPx() }
    val swipeSlopPx = with(density) { 26.dp.toPx() }
    if (altPopup != null) lastAltPopup = altPopup

    val currentLayoutPage by rememberUpdatedState(layoutPage)
    val currentShiftState by rememberUpdatedState(shiftState)
    val currentPrefs by rememberUpdatedState(prefs)
    val currentOnSwipeWord by rememberUpdatedState(onSwipeWord)

    // Continuous dynamic height scaling (75%..135%) applied smoothly on top of keyboardHeight preset
    val baseDp = if (prefs.showNumberRow) 46f else prefs.keyboardHeight.rowHeightDp.toFloat()
    val scaleFactor = (prefs.keyboardScalePercent / 100f) * (prefs.keyboardHeight.rowHeightDp / 48f)
    val rowHeight = (baseDp * scaleFactor).coerceIn(33f, 70f).dp
    val rowGap = if (prefs.showNumberRow) 5.dp else 6.dp
    val halfRowGap = rowGap / 2

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isSplitActive = prefs.oneHandedMode == OneHandedMode.OFF && when (prefs.splitKeyboardMode) {
        SplitKeyboardMode.OFF -> false
        SplitKeyboardMode.ALWAYS -> true
        SplitKeyboardMode.AUTO -> screenWidthDp >= 600
    }
    val splitCenterGap = if (screenWidthDp >= 720) 108.dp else 72.dp

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
                    val pos = it.positionInRoot()
                    gridLeftRoot = pos.x
                    gridTopRoot = pos.y
                    gridWidthPx = it.size.width.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.5.dp, vertical = 1.dp)
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val isIndentedAlphaRow = !isSplitActive && row.size == 9 && row.first().type == KeyActionType.CHAR
                    val renderRowKeys = remember(row, isSplitActive) {
                        if (isSplitActive) splitKeyboardRow(row) else listOf(row)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isIndentedAlphaRow) 16.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        renderRowKeys.forEachIndexed { halfIdx, halfKeys ->
                            if (halfIdx > 0) {
                                Spacer(Modifier.width(splitCenterGap))
                            }
                            Row(
                                modifier = if (isSplitActive) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                halfKeys.forEachIndexed { keyIdx, key ->
                                    val isLetterCharKey = currentLayoutPage == KeyboardLayoutPage.LETTERS &&
                                        key.type == KeyActionType.CHAR &&
                                        key.label.length == 1 &&
                                        key.label[0].isLetter()
                                    val keyIdentity = "${layoutPage.name}_${rowIndex}_${halfIdx}_${keyIdx}_${key.type.name}_${key.label}"

                                    ExpressiveKeyCap(
                                        modifier = Modifier
                                            .weight(key.weight)
                                            .height(rowHeight + rowGap),
                                        innerPaddingHorizontal = 2.5.dp,
                                        innerPaddingVertical = halfRowGap,
                                        keyIdentity = keyIdentity,
                                        key = key,
                                        prefs = prefs,
                                        shiftState = shiftState,
                                        imeActionLabel = imeActionLabel,
                                        canSwipeFromKey = isLetterCharKey && prefs.glideTypingEnabled,
                                        swipeSlopPx = swipeSlopPx,
                                        onCenterMeasured = { cx, cy ->
                                            if (isLetterCharKey) {
                                                keyCenterMap[key.label] = KeyCenter(
                                                    char = key.label[0],
                                                    x = cx - gridLeftRoot,
                                                    y = cy - gridTopRoot
                                                )
                                            }
                                        },
                                        onPressStateChanged = { isDown, cx, topY ->
                                            if (isDown && key.type == KeyActionType.CHAR) {
                                                activePreview = KeyPreviewBubble(
                                                    keyId = keyIdentity,
                                                    char = applyShiftCase(key.label, currentShiftState),
                                                    centerXRoot = cx,
                                                    topYRoot = topY
                                                )
                                            } else if (!isDown && activePreview?.keyId == keyIdentity) {
                                                activePreview = null
                                            }
                                        },
                                        onKeySound = {
                                            if (currentPrefs.soundOnKeypress) {
                                                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
                                            }
                                        },
                                        onPress = { resolvedOutput ->
                                            altPopup = null
                                            onKeyPress(key, resolvedOutput)
                                        },
                                        onLongPress = { centerX ->
                                            when {
                                                key.type == KeyActionType.SPACE -> onSwitchLanguage()
                                                key.alternates.size == 1 -> {
                                                    onKeyPress(key, applyShiftCase(key.alternates.first(), currentShiftState))
                                                }
                                                key.alternates.size > 1 -> {
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
                                                    if (currentPrefs.hapticFeedback) {
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
                                                onKeyPress(p.origin, applyShiftCase(p.items[p.selected], currentShiftState))
                                            }
                                        },
                                        onSwipeStart = { rx, ry ->
                                            activePreview = null
                                            swipeTrail.clear()
                                            swipeTrail.add(SwipePoint(rx - gridLeftRoot, ry - gridTopRoot))
                                        },
                                        onSwipeMove = { rx, ry ->
                                            val lx = rx - gridLeftRoot
                                            val ly = ry - gridTopRoot
                                            val last = swipeTrail.lastOrNull()
                                            if (last == null || hypot(lx - last.x, ly - last.y) >= 6f) {
                                                swipeTrail.add(SwipePoint(lx, ly))
                                            }
                                        },
                                        onSwipeFinish = {
                                            val pts = swipeTrail.toList()
                                            swipeTrail.clear()
                                            if (pts.size >= 2) {
                                                currentOnSwipeWord(pts, keyCenterMap.values.toList())
                                            }
                                        },
                                        onSwipeCancel = { swipeTrail.clear() },
                                        onCursorMoveSteps = onCursorMoveSteps,
                                        onSwipeBackspaceWord = onDeletePreviousWord
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Smooth Glide/Swipe trail overlay
            if (swipeTrail.size >= 2) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val path = Path().apply {
                        moveTo(swipeTrail.first().x, swipeTrail.first().y)
                        for (i in 1 until swipeTrail.size) {
                            lineTo(swipeTrail[i].x, swipeTrail[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = kbColors.actionSurface.copy(alpha = 0.78f),
                        style = Stroke(
                            width = 7.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // Alternate-character popup
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

            // Per-key positioned floating preview bubble
            val preview = activePreview
            androidx.compose.animation.AnimatedVisibility(
                visible = preview != null && altPopup == null && swipeTrail.isEmpty(),
                enter = fadeIn(tween(60)) + scaleIn(initialScale = 0.88f, animationSpec = tween(80)),
                exit = fadeOut(tween(70)),
                modifier = Modifier.offset {
                    val bubbleHalfPx = 22.dp.toPx()
                    val x = ((preview?.centerXRoot ?: gridLeftRoot) - gridLeftRoot - bubbleHalfPx)
                        .coerceIn(0f, (gridWidthPx - bubbleHalfPx * 2f).coerceAtLeast(0f))
                        .roundToInt()
                    val y = ((preview?.topYRoot ?: gridTopRoot) - gridTopRoot - 46.dp.toPx()).roundToInt()
                    IntOffset(x, y)
                }
            ) {
                if (preview != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = kbColors.cardSurface,
                        border = BorderStroke(0.8.dp, kbColors.keyGlassHighlight),
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier.width(44.dp).height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preview.char,
                                color = kbColors.keyText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
    innerPaddingHorizontal: androidx.compose.ui.unit.Dp,
    innerPaddingVertical: androidx.compose.ui.unit.Dp,
    keyIdentity: String,
    key: KeySpec,
    prefs: TapwingPrefs,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    canSwipeFromKey: Boolean,
    swipeSlopPx: Float,
    onCenterMeasured: (Float, Float) -> Unit,
    onPressStateChanged: (Boolean, Float, Float) -> Unit,
    onKeySound: () -> Unit,
    onPress: (String) -> Unit,
    onLongPress: (Float) -> Unit,
    onAlternateDrag: (Float) -> Unit,
    onAlternateRelease: () -> Unit,
    onSwipeStart: (Float, Float) -> Unit,
    onSwipeMove: (Float, Float) -> Unit,
    onSwipeFinish: () -> Unit,
    onSwipeCancel: () -> Unit,
    onCursorMoveSteps: (Int) -> Unit,
    onSwipeBackspaceWord: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var keyLeftRoot by remember { mutableFloatStateOf(0f) }
    var keyTopRoot by remember { mutableFloatStateOf(0f) }
    var keyWidthPx by remember { mutableFloatStateOf(0f) }
    var keyHeightPx by remember { mutableFloatStateOf(0f) }

    // Keep fresh lambdas & state inside rememberUpdatedState so pointerInput NEVER restarts mid-touch!
    val currentKey by rememberUpdatedState(key)
    val currentPrefs by rememberUpdatedState(prefs)
    val currentShift by rememberUpdatedState(shiftState)
    val currentCanSwipe by rememberUpdatedState(canSwipeFromKey)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnAltDrag by rememberUpdatedState(onAlternateDrag)
    val currentOnAltRelease by rememberUpdatedState(onAlternateRelease)
    val currentOnPressChanged by rememberUpdatedState(onPressStateChanged)
    val currentOnKeySound by rememberUpdatedState(onKeySound)
    val currentOnSwipeStart by rememberUpdatedState(onSwipeStart)
    val currentOnSwipeMove by rememberUpdatedState(onSwipeMove)
    val currentOnSwipeFinish by rememberUpdatedState(onSwipeFinish)
    val currentOnSwipeCancel by rememberUpdatedState(onSwipeCancel)
    val currentOnCursorMoveSteps by rememberUpdatedState(onCursorMoveSteps)
    val currentOnSwipeBackspaceWord by rememberUpdatedState(onSwipeBackspaceWord)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) (if (kbColors.appleLiquidGlass) 0.95f else 0.97f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
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

    val cornerShape: Shape = when {
        kbColors.appleLiquidGlass -> RoundedCornerShape(percent = 50)
        !prefs.roundedExpressiveKeys -> RoundedCornerShape(7.dp)
        isActionKey -> RoundedCornerShape(19.dp)
        key.type == KeyActionType.SPACE -> RoundedCornerShape(11.dp)
        isModifierKey -> RoundedCornerShape(15.dp)
        else -> RoundedCornerShape(11.dp)
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
        animationSpec = tween(if (pressed) 20 else 95, easing = FastOutSlowInEasing),
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

    // Key pointerInput ONLY on keyIdentity (never on shiftState or prefs!), so taps never get dropped or stuck!
    val touchModifier = when (key.type) {
        KeyActionType.SPACE -> Modifier.pointerInput(keyIdentity) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pid: PointerId = down.id
                pressed = true
                if (currentPrefs.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                currentOnKeySound()
                var triggeredLong = false
                var cursorSwiping = false
                var dragAccum = 0f
                var lastX = down.position.x
                val stepPx = 22f

                val longJob: Job = scope.launch {
                    delay(currentPrefs.longPressDelayMs.toLong() + 140L)
                    if (!cursorSwiping) {
                        triggeredLong = true
                        if (currentPrefs.hapticFeedback) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        currentOnLongPress(keyLeftRoot + keyWidthPx / 2f)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pid } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        if (!triggeredLong) {
                            dragAccum += dx
                            if (!cursorSwiping && kotlin.math.abs(change.position.x - down.position.x) > 16f) {
                                cursorSwiping = true
                                longJob.cancel()
                            }
                            if (cursorSwiping) {
                                while (dragAccum >= stepPx) {
                                    currentOnCursorMoveSteps(1)
                                    dragAccum -= stepPx
                                }
                                while (dragAccum <= -stepPx) {
                                    currentOnCursorMoveSteps(-1)
                                    dragAccum += stepPx
                                }
                                change.consume()
                            }
                        }
                    }
                    if (!triggeredLong && !cursorSwiping) {
                        currentOnPress(" ")
                    }
                } finally {
                    longJob.cancel()
                    pressed = false
                }
            }
        }

        KeyActionType.BACKSPACE -> Modifier.pointerInput(keyIdentity) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pid: PointerId = down.id
                pressed = true
                if (currentPrefs.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                currentOnKeySound()
                var triggeredRepeatOrSwipe = false
                var anchorX = down.position.x
                val holdJob: Job = scope.launch {
                    delay(320L)
                    triggeredRepeatOrSwipe = true
                    var repeats = 0
                    while (true) {
                        currentOnPress("")
                        repeats++
                        delay(if (repeats > 10) 34L else 55L)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pid } ?: break
                        if (!change.pressed) break
                        if (currentPrefs.swipeBackspaceDeleteWord) {
                            val dx = change.position.x - anchorX
                            if (dx <= -42f) {
                                holdJob.cancel()
                                triggeredRepeatOrSwipe = true
                                anchorX = change.position.x
                                currentOnSwipeBackspaceWord()
                            }
                        }
                    }
                    if (!triggeredRepeatOrSwipe) {
                        currentOnPress("")
                    }
                } finally {
                    holdJob.cancel()
                    pressed = false
                }
            }
        }

        else -> Modifier.pointerInput(keyIdentity) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pid: PointerId = down.id
                val startRootX = keyLeftRoot + down.position.x
                val startRootY = keyTopRoot + down.position.y
                pressed = true
                currentOnPressChanged(true, keyLeftRoot + keyWidthPx / 2f, keyTopRoot)
                if (currentPrefs.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                currentOnKeySound()

                var triggeredLong = false
                var isSwiping = false
                var swipeFinished = false
                val holdJob: Job = scope.launch {
                    if (currentKey.alternates.isNotEmpty()) {
                        delay(currentPrefs.longPressDelayMs.toLong())
                        if (!isSwiping) {
                            triggeredLong = true
                            currentOnPressChanged(false, 0f, 0f)
                            if (currentPrefs.hapticFeedback) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            currentOnLongPress(keyLeftRoot + keyWidthPx / 2f)
                        }
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pid } ?: break
                        if (!change.pressed) break
                        val curRootX = keyLeftRoot + change.position.x
                        val curRootY = keyTopRoot + change.position.y

                        if (triggeredLong) {
                            currentOnAltDrag(curRootX)
                            change.consume()
                        } else if (currentCanSwipe) {
                            val dist = hypot(curRootX - startRootX, curRootY - startRootY)
                            if (!isSwiping && dist > swipeSlopPx) {
                                isSwiping = true
                                holdJob.cancel()
                                currentOnPressChanged(false, 0f, 0f)
                                currentOnSwipeStart(startRootX, startRootY)
                            }
                            if (isSwiping) {
                                currentOnSwipeMove(curRootX, curRootY)
                                change.consume()
                            }
                        }
                    }
                    when {
                        isSwiping -> {
                            swipeFinished = true
                            currentOnSwipeFinish()
                        }
                        triggeredLong -> currentOnAltRelease()
                        else -> {
                            val output = if (currentKey.type == KeyActionType.CHAR) {
                                applyShiftCase(currentKey.output, currentShift)
                            } else {
                                currentKey.output
                            }
                            currentOnPress(output)
                        }
                    }
                } finally {
                    holdJob.cancel()
                    pressed = false
                    currentOnPressChanged(false, 0f, 0f)
                    if (isSwiping && !swipeFinished) {
                        currentOnSwipeCancel()
                    }
                }
            }
        }
    }

    val shadowMod = if (kbColors.appleLiquidGlass) {
        Modifier.shadow(if (pressed) 1.dp else 3.dp, cornerShape, clip = false)
    } else if (!isModifierKey && !isActionKey) {
        Modifier.shadow(0.5.dp, cornerShape)
    } else Modifier

    // Gapless hitbox wrapper: pointerInput covers the full cell including spacing so fast taps between keys never miss!
    Box(
        modifier = modifier
            .onGloballyPositioned {
                val pos = it.positionInRoot()
                keyLeftRoot = pos.x
                keyTopRoot = pos.y
                keyWidthPx = it.size.width.toFloat()
                keyHeightPx = it.size.height.toFloat()
                onCenterMeasured(pos.x + it.size.width / 2f, pos.y + it.size.height / 2f)
            }
            .then(touchModifier)
            .padding(horizontal = innerPaddingHorizontal, vertical = innerPaddingVertical)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
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
                        if (prefs.showSpacebarLanguageLabel) {
                            Text(
                                text = displayText,
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
}

internal fun splitKeyboardRow(row: List<KeySpec>): List<List<KeySpec>> {
    val spaceIdx = row.indexOfFirst { it.type == KeyActionType.SPACE }
    if (spaceIdx >= 0) {
        val spaceKey = row[spaceIdx]
        val left = row.subList(0, spaceIdx) + spaceKey.copy(weight = 1.95f)
        val right = listOf(spaceKey.copy(weight = 1.95f, label = " ")) + row.subList(spaceIdx + 1, row.size)
        return listOf(left, right)
    }
    val splitAt = when {
        row.size >= 10 -> 5
        row.size == 9 && row.first().type == KeyActionType.SHIFT -> 4
        row.size == 9 -> 5
        else -> row.size / 2
    }
    return listOf(row.subList(0, splitAt), row.subList(splitAt, row.size))
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

private data class KeyPreviewBubble(
    val keyId: String,
    val char: String,
    val centerXRoot: Float,
    val topYRoot: Float
)
