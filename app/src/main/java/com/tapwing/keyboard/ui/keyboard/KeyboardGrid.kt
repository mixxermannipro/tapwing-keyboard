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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as composeKey
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.data.KeyboardLocales
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
    onCursorMove: (Int, Int) -> Unit,
    onDeletePreviousWord: () -> Unit,
    onSwipeBackspaceEnd: () -> Unit = {},
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

    val gestureScope = rememberCoroutineScope()
    var altPopup by remember { mutableStateOf<AlternatePopup?>(null) }
    var lastAltPopup by remember { mutableStateOf<AlternatePopup?>(null) }
    var activePreview by remember { mutableStateOf<KeyPreviewBubble?>(null) }
    var trackpadModeActive by remember { mutableStateOf(false) }
    var trackpadLastDir by remember { mutableStateOf(0 to 0) }
    var isSwipingActive by remember { mutableStateOf(false) }
    // Non-snapshot geometry holders so onGloballyPositioned NEVER triggers cascading recompositions
    val gridGeometry = remember { GridGeometry() }
    val keyCenterMap = remember { HashMap<String, KeyCenter>(36) }
    val swipeTrail = remember { mutableStateListOf<SwipePoint>() }

    val view = LocalView.current
    val density = LocalDensity.current
    val itemWidthPx = with(density) { 44.dp.toPx() }
    val edgePx = with(density) { 6.dp.toPx() }
    val deadZonePx = with(density) { 14.dp.toPx() }
    val popupRaisePx = with(density) { 48.dp.roundToPx() }
    val swipeSlopPx = with(density) { 26.dp.toPx() }
    val cursorStepXPx = with(density) { 10.dp.toPx() }
    val cursorStepYPx = with(density) { 22.dp.toPx() }
    val cursorSlopPx = with(density) { 8.dp.toPx() }
    val backspaceSwipeWordPx = with(density) { 28.dp.toPx() }
    if (altPopup != null) lastAltPopup = altPopup

    val currentLayoutPage by rememberUpdatedState(layoutPage)
    val currentShiftState by rememberUpdatedState(shiftState)
    val currentPrefs by rememberUpdatedState(prefs)
    val currentOnSwipeWord by rememberUpdatedState(onSwipeWord)

    val gridKeysAlpha by animateFloatAsState(
        targetValue = if (trackpadModeActive) 0.28f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "trackpadKeysAlpha"
    )

    LaunchedEffect(layoutPage, prefs.activeLocale, prefs.showNumberRow, prefs.showGermanUmlautKeys) {
        activePreview = null
        altPopup = null
        lastAltPopup = null
        trackpadModeActive = false
        trackpadLastDir = 0 to 0
        isSwipingActive = false
        swipeTrail.clear()
        keyCenterMap.clear()
    }

    val isNumpadPage = layoutPage == KeyboardLayoutPage.NUMPAD || layoutPage == KeyboardLayoutPage.PHONE_PAD
    // Continuous dynamic height scaling (75%..135%) applied smoothly on top of keyboardHeight preset
    val baseDp = when {
        isNumpadPage -> 54f
        prefs.showNumberRow -> 46f
        else -> prefs.keyboardHeight.rowHeightDp.toFloat()
    }
    val scaleFactor = (prefs.keyboardScalePercent / 100f) * (prefs.keyboardHeight.rowHeightDp / 48f)
    val rowHeight = (baseDp * scaleFactor).coerceIn(33f, 74f).dp
    val rowGap = if (prefs.showNumberRow && !isNumpadPage) 6.dp else 8.dp
    val halfRowGap = rowGap / 2

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isSplitActive = !isNumpadPage && prefs.oneHandedMode == OneHandedMode.OFF && when (prefs.splitKeyboardMode) {
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
                    gridGeometry.leftRoot = pos.x
                    gridGeometry.topRoot = pos.y
                    gridGeometry.widthPx = it.size.width.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = gridKeysAlpha }
                    .padding(horizontal = 2.5.dp, vertical = 1.dp)
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val isIndentedAlphaRow = !isSplitActive && !isNumpadPage && row.size == 9 && row.first().type == KeyActionType.CHAR
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
                                halfKeys.forEachIndexed { keyIdx, keySpec ->
                                    val isLetterCharKey = currentLayoutPage == KeyboardLayoutPage.LETTERS &&
                                        keySpec.type == KeyActionType.CHAR &&
                                        keySpec.label.length == 1 &&
                                        keySpec.label[0].isLetter()
                                    val keyIdentity = "${layoutPage.name}_${rowIndex}_${halfIdx}_${keyIdx}_${keySpec.type.name}_${keySpec.label}"

                                    composeKey(keyIdentity) {
                                    ExpressiveKeyCap(
                                        modifier = Modifier
                                            .weight(keySpec.weight)
                                            .height(rowHeight + rowGap),
                                        innerPaddingHorizontal = 2.5.dp,
                                        innerPaddingVertical = if (prefs.roundedExpressiveKeys) 3.dp else halfRowGap,
                                        gestureScope = gestureScope,
                                        keyIdentity = keyIdentity,
                                        key = keySpec,
                                        prefs = prefs,
                                        shiftState = shiftState,
                                        imeActionLabel = imeActionLabel,
                                        canSwipeFromKey = isLetterCharKey && prefs.glideTypingEnabled,
                                        swipeSlopPx = swipeSlopPx,
                                        cursorStepXPx = cursorStepXPx,
                                        cursorStepYPx = cursorStepYPx,
                                        cursorSlopPx = cursorSlopPx,
                                        backspaceSwipeWordPx = backspaceSwipeWordPx,
                                        isNumpadKey = isNumpadPage,
                                        onCenterMeasured = { cx, cy ->
                                            if (isLetterCharKey) {
                                                keyCenterMap[keySpec.label] = KeyCenter(
                                                    char = keySpec.label[0],
                                                    x = cx - gridGeometry.leftRoot,
                                                    y = cy - gridGeometry.topRoot
                                                )
                                            }
                                        },
                                        onPressStateChanged = { isDown, cx, topY ->
                                            if (isDown && keySpec.type == KeyActionType.CHAR && !isNumpadPage) {
                                                activePreview = KeyPreviewBubble(
                                                    keyId = keyIdentity,
                                                    char = applyShiftCase(keySpec.label, currentShiftState),
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
                                            activePreview = null
                                            altPopup = null
                                            onKeyPress(keySpec, resolvedOutput)
                                        },
                                        onLongPress = { centerX ->
                                            when {
                                                keySpec.alternates.size == 1 -> {
                                                    onKeyPress(keySpec, applyShiftCase(keySpec.alternates.first(), currentShiftState))
                                                }
                                                keySpec.alternates.size > 1 -> {
                                                    val sorted = keySpec.alternates.sortedBy { alt ->
                                                        if (alt.firstOrNull()?.isLetter() == true) 0 else 1
                                                    }
                                                    val isRightHalf = centerX > gridGeometry.leftRoot + gridGeometry.widthPx * 0.55f
                                                    val items = if (isRightHalf) sorted.reversed() else sorted
                                                    val defaultIndex = if (isRightHalf) items.lastIndex else 0
                                                    val total = items.size * itemWidthPx
                                                    val minLeft = gridGeometry.leftRoot + edgePx
                                                    val maxLeft = (gridGeometry.leftRoot + gridGeometry.widthPx - total - edgePx).coerceAtLeast(minLeft)
                                                    val rawLeft = if (isRightHalf) {
                                                        centerX - total + itemWidthPx / 2f
                                                    } else {
                                                        centerX - itemWidthPx / 2f
                                                    }
                                                    val left = rawLeft.coerceIn(minLeft, maxLeft)
                                                    altPopup = AlternatePopup(
                                                        items = items,
                                                        origin = keySpec,
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
                                            isSwipingActive = true
                                            swipeTrail.clear()
                                            swipeTrail.add(SwipePoint(rx - gridGeometry.leftRoot, ry - gridGeometry.topRoot))
                                        },
                                        onSwipeMove = { rx, ry ->
                                            val lx = rx - gridGeometry.leftRoot
                                            val ly = ry - gridGeometry.topRoot
                                            val last = swipeTrail.lastOrNull()
                                            if (last == null || hypot(lx - last.x, ly - last.y) >= 6f) {
                                                swipeTrail.add(SwipePoint(lx, ly))
                                            }
                                        },
                                        onSwipeFinish = {
                                            val pts = swipeTrail.toList()
                                            swipeTrail.clear()
                                            isSwipingActive = false
                                            if (pts.size >= 2) {
                                                currentOnSwipeWord(pts, keyCenterMap.values.toList())
                                            }
                                        },
                                        onSwipeCancel = {
                                            swipeTrail.clear()
                                            isSwipingActive = false
                                        },
                                        onCursorMove = { dx, dy ->
                                            if (trackpadModeActive) trackpadLastDir = dx to dy
                                            onCursorMove(dx, dy)
                                        },
                                        onTrackpadModeChanged = { active ->
                                            trackpadModeActive = active
                                            if (!active) trackpadLastDir = 0 to 0
                                            if (active) {
                                                activePreview = null
                                                altPopup = null
                                            }
                                        },
                                        onSwipeBackspaceWord = onDeletePreviousWord,
                                        onSwipeBackspaceEnd = onSwipeBackspaceEnd
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2D Spacebar Trackpad overlay badge with live directional highlight
            androidx.compose.animation.AnimatedVisibility(
                visible = trackpadModeActive,
                enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.94f, animationSpec = tween(140)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = kbColors.cardSurface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, kbColors.actionSurface.copy(alpha = 0.45f)),
                    shadowElevation = 8.dp
                ) {
                    val isDe = com.tapwing.keyboard.data.KeyboardLocales.baseLang(prefs.activeLocale) == "de"
                    val (dirX, dirY) = trackpadLastDir
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isDe) "Trackpad" else "Trackpad",
                            color = kbColors.keyText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val arrows = listOf(
                            "←" to (dirX < 0),
                            "↑" to (dirY < 0),
                            "↓" to (dirY > 0),
                            "→" to (dirX > 0)
                        )
                        arrows.forEach { (glyph, lit) ->
                            Text(
                                text = glyph,
                                color = if (lit) kbColors.actionSurface else kbColors.keyHintText,
                                fontSize = if (lit) 15.sp else 13.sp,
                                fontWeight = if (lit) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Isolated smooth Bezier Glide/Swipe trail overlay (never recomposes keys!)
            SwipeTrailOverlay(
                swipeTrail = swipeTrail,
                trailColor = kbColors.actionSurface,
                modifier = Modifier.matchParentSize()
            )

            // Alternate-character popup
            androidx.compose.animation.AnimatedVisibility(
                visible = altPopup != null,
                enter = fadeIn(tween(90)) + scaleIn(
                    initialScale = 0.90f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                ),
                exit = fadeOut(tween(80)),
                modifier = Modifier.offset {
                    IntOffset(
                        ((lastAltPopup?.leftRootPx ?: 0f) - gridGeometry.leftRoot).roundToInt(),
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
                visible = preview != null && altPopup == null && !isSwipingActive && !trackpadModeActive,
                enter = fadeIn(tween(55)) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessHigh)
                ),
                exit = fadeOut(tween(65)),
                modifier = Modifier.offset {
                    val bubbleHalfPx = 22.dp.toPx()
                    val x = ((preview?.centerXRoot ?: gridGeometry.leftRoot) - gridGeometry.leftRoot - bubbleHalfPx)
                        .coerceIn(0f, (gridGeometry.widthPx - bubbleHalfPx * 2f).coerceAtLeast(0f))
                        .roundToInt()
                    val y = ((preview?.topYRoot ?: gridGeometry.topRoot) - gridGeometry.topRoot - 46.dp.toPx()).roundToInt()
                    IntOffset(x, y)
                }
            ) {
                if (preview != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = kbColors.cardSurface,
                        border = BorderStroke(0.8.dp, kbColors.keyGlassHighlight),
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier.width(44.dp).height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preview.char,
                                color = kbColors.keyText,
                                fontSize = 26.sp,
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
                .size(40.dp)
                .clickable(role = Role.Button, onClick = onExpand)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.OpenInFull,
                    contentDescription = "Vollbild-Tastatur / Full-width keyboard",
                    tint = kbColors.modifierText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = kbColors.modifierSurface,
            modifier = Modifier
                .size(40.dp)
                .clickable(role = Role.Button, onClick = onSwapSide)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    contentDescription = "Einhandmodus Seite wechseln / Switch one-handed side",
                    tint = kbColors.modifierText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

enum class KeyActionLabel {
    ENTER,
    SEARCH,
    SEND,
    DONE,
    NEXT,
    GO
}

private val CAPSULE_KEY_SHAPE = RoundedCornerShape(percent = 50)
private val RECT_KEY_SHAPE = RoundedCornerShape(5.dp)
private val NUMPAD_KEY_SHAPE = RoundedCornerShape(18.dp)

private val MODIFIER_KEY_TYPES = setOf(
    KeyActionType.SHIFT,
    KeyActionType.BACKSPACE,
    KeyActionType.SYMBOLS_TOGGLE,
    KeyActionType.SYMBOLS_EXT_TOGGLE,
    KeyActionType.COMMA,
    KeyActionType.EMOJI_PANEL,
    KeyActionType.PERIOD
)

@Composable
private fun SwipeTrailOverlay(
    swipeTrail: List<SwipePoint>,
    trailColor: Color,
    modifier: Modifier = Modifier
) {
    if (swipeTrail.size < 2) return
    val reusablePath = remember { Path() }
    Canvas(modifier = modifier) {
        val count = swipeTrail.size
        // Draw tapered comet ribbon using quadratic Bezier midpoints for buttery Gboard-grade glide feel
        val tailStart = (count - 22).coerceAtLeast(0)
        val visibleCount = count - tailStart
        if (visibleCount < 2) return@Canvas

        // Soft glow under-stroke
        reusablePath.reset()
        reusablePath.moveTo(swipeTrail[tailStart].x, swipeTrail[tailStart].y)
        for (i in tailStart + 1 until count - 1) {
            val midX = (swipeTrail[i].x + swipeTrail[i + 1].x) * 0.5f
            val midY = (swipeTrail[i].y + swipeTrail[i + 1].y) * 0.5f
            reusablePath.quadraticTo(swipeTrail[i].x, swipeTrail[i].y, midX, midY)
        }
        reusablePath.lineTo(swipeTrail[count - 1].x, swipeTrail[count - 1].y)
        drawPath(
            path = reusablePath,
            color = trailColor.copy(alpha = 0.25f),
            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Tapered segments from thin tail to bold tip
        val minW = 2.5.dp.toPx()
        val maxW = 7.5.dp.toPx()
        for (i in tailStart until count - 1) {
            val t = (i - tailStart + 1).toFloat() / visibleCount.toFloat()
            val strokeW = minW + (maxW - minW) * t
            val alpha = (0.25f + 0.65f * t).coerceIn(0.25f, 0.9f)
            drawLine(
                color = trailColor.copy(alpha = alpha),
                start = Offset(swipeTrail[i].x, swipeTrail[i].y),
                end = Offset(swipeTrail[i + 1].x, swipeTrail[i + 1].y),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ExpressiveKeyCap(
    modifier: Modifier,
    innerPaddingHorizontal: androidx.compose.ui.unit.Dp,
    innerPaddingVertical: androidx.compose.ui.unit.Dp,
    gestureScope: kotlinx.coroutines.CoroutineScope,
    keyIdentity: String,
    key: KeySpec,
    prefs: TapwingPrefs,
    shiftState: ShiftState,
    imeActionLabel: KeyActionLabel,
    canSwipeFromKey: Boolean,
    swipeSlopPx: Float,
    cursorStepXPx: Float,
    cursorStepYPx: Float,
    cursorSlopPx: Float,
    backspaceSwipeWordPx: Float,
    isNumpadKey: Boolean = false,
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
    onCursorMove: (Int, Int) -> Unit,
    onTrackpadModeChanged: (Boolean) -> Unit,
    onSwipeBackspaceWord: () -> Unit,
    onSwipeBackspaceEnd: () -> Unit = {}
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = gestureScope
    var pressed by remember(keyIdentity) { mutableStateOf(false) }
    val bounds = remember(keyIdentity) { KeyBounds() }

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
    val currentOnCursorMove by rememberUpdatedState(onCursorMove)
    val currentOnTrackpadModeChanged by rememberUpdatedState(onTrackpadModeChanged)
    val currentOnSwipeBackspaceWord by rememberUpdatedState(onSwipeBackspaceWord)
    val currentOnSwipeBackspaceEnd by rememberUpdatedState(onSwipeBackspaceEnd)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) (if (kbColors.appleLiquidGlass) 0.95f else 0.97f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "keyPressScale"
    )

    val isActionKey = key.type == KeyActionType.ENTER
    val isModifierKey = key.type in MODIFIER_KEY_TYPES

    val cornerShape: Shape = when {
        isNumpadKey -> NUMPAD_KEY_SHAPE
        kbColors.appleLiquidGlass || prefs.roundedExpressiveKeys -> CAPSULE_KEY_SHAPE
        else -> RECT_KEY_SHAPE
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

    val border: BorderStroke? = remember(prefs.highContrastKeys, kbColors.appleLiquidGlass, kbColors.keyGlassHighlight, kbColors.keyBorder, prefs.showKeyBorders, isActionKey) {
        when {
            prefs.highContrastKeys -> BorderStroke(1.25.dp, kbColors.keyBorder)
            kbColors.appleLiquidGlass -> BorderStroke(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    listOf(kbColors.keyGlassHighlight, kbColors.keyBorder)
                )
            )
            prefs.showKeyBorders && !isActionKey -> BorderStroke(0.5.dp, kbColors.keyBorder)
            else -> null
        }
    }
    val glassOverlayBrush = remember(kbColors.appleLiquidGlass, kbColors.isDark) {
        if (kbColors.appleLiquidGlass) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (kbColors.isDark) 0.08f else 0.25f),
                    Color.Transparent
                )
            )
        } else null
    }

    val displayText = if (key.type == KeyActionType.CHAR) {
        applyShiftCase(key.label, shiftState)
    } else {
        key.label
    }

    val isDeLocale = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    val a11yDescription = when (key.type) {
        KeyActionType.SPACE -> if (isDeLocale) "Leertaste" else "Space"
        KeyActionType.SHIFT -> if (isDeLocale) "Umschalttaste" else "Shift"
        KeyActionType.BACKSPACE -> if (isDeLocale) "Löschen" else "Backspace"
        KeyActionType.EMOJI_PANEL -> "Emoji"
        KeyActionType.SYMBOLS_TOGGLE, KeyActionType.SYMBOLS_EXT_TOGGLE ->
            if (isDeLocale) "Symbole $displayText" else "Symbols $displayText"
        KeyActionType.ENTER -> when (imeActionLabel) {
            KeyActionLabel.SEARCH -> if (isDeLocale) "Suchen" else "Search"
            KeyActionLabel.SEND -> if (isDeLocale) "Senden" else "Send"
            KeyActionLabel.DONE -> if (isDeLocale) "Fertig" else "Done"
            KeyActionLabel.NEXT -> if (isDeLocale) "Weiter" else "Next"
            KeyActionLabel.GO -> if (isDeLocale) "Los" else "Go"
            KeyActionLabel.ENTER -> if (isDeLocale) "Eingabe" else "Enter"
        }
        KeyActionType.COMMA -> if (isDeLocale) "Komma" else "Comma"
        KeyActionType.PERIOD -> if (isDeLocale) "Punkt" else "Period"
        else -> displayText
    }
    val a11yStateDescription = if (key.type == KeyActionType.SHIFT) {
        when (shiftState) {
            ShiftState.CAPS_LOCK -> if (isDeLocale) "Feststelltaste aktiv" else "Caps Lock on"
            ShiftState.ONCE -> if (isDeLocale) "Aktiv" else "On"
            ShiftState.OFF -> null
        }
    } else null

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
                val tracker = SpacebarTrackpadTracker(
                    horizontalStepPx = cursorStepXPx,
                    verticalStepPx = cursorStepYPx,
                    activationSlopPx = cursorSlopPx
                )
                var lastX = down.position.x
                var lastY = down.position.y

                val longJob: Job = scope.launch {
                    delay(currentPrefs.longPressDelayMs.toLong() + 80L)
                    if (!tracker.isQuickHorizontalSwiping) {
                        tracker.activate2DTrackpad()
                        currentOnTrackpadModeChanged(true)
                        if (currentPrefs.hapticFeedback) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pid } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - lastX
                        val dy = change.position.y - lastY
                        lastX = change.position.x
                        lastY = change.position.y

                        val step = tracker.onDragDelta(dx, dy)
                        if (tracker.isQuickHorizontalSwiping) {
                            longJob.cancel()
                        }
                        if (step.hasMovement) {
                            currentOnCursorMove(step.dx, step.dy)
                            if (currentPrefs.hapticFeedback) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        if (tracker.is2DTrackpadActive || tracker.isQuickHorizontalSwiping) {
                            change.consume()
                        }
                    }
                    if (!tracker.is2DTrackpadActive && !tracker.isQuickHorizontalSwiping) {
                        currentOnPress(" ")
                    }
                } finally {
                    longJob.cancel()
                    if (tracker.is2DTrackpadActive) {
                        currentOnTrackpadModeChanged(false)
                    }
                    tracker.reset()
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
                var usedWordSwipe = false
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
                            if (dx <= -backspaceSwipeWordPx) {
                                holdJob.cancel()
                                triggeredRepeatOrSwipe = true
                                usedWordSwipe = true
                                anchorX = change.position.x
                                if (currentPrefs.hapticFeedback) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                currentOnSwipeBackspaceWord()
                                change.consume()
                            }
                        }
                    }
                    if (!triggeredRepeatOrSwipe) {
                        currentOnPress("")
                    }
                } finally {
                    holdJob.cancel()
                    if (usedWordSwipe) {
                        currentOnSwipeBackspaceEnd()
                    }
                    pressed = false
                }
            }
        }

        else -> Modifier.pointerInput(keyIdentity) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pid: PointerId = down.id
                val startRootX = bounds.leftRoot + down.position.x
                val startRootY = bounds.topRoot + down.position.y
                pressed = true
                currentOnPressChanged(true, bounds.leftRoot + bounds.widthPx / 2f, bounds.topRoot)
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
                            currentOnLongPress(bounds.leftRoot + bounds.widthPx / 2f)
                        }
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pid } ?: break
                        if (!change.pressed) break
                        val curRootX = bounds.leftRoot + change.position.x
                        val curRootY = bounds.topRoot + change.position.y

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
        Modifier.shadow(if (kbColors.isDark) 0.6.dp else 1.dp, cornerShape)
    } else Modifier

    val largeLabels = prefs.largeKeyLabels
    val iconSize = if (largeLabels) 23.dp else 20.dp

    // Gapless hitbox wrapper: pointerInput covers the full cell including spacing so fast taps between keys never miss!
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yDescription
                if (a11yStateDescription != null) {
                    stateDescription = a11yStateDescription
                }
            }
            .onGloballyPositioned {
                val pos = it.positionInRoot()
                bounds.leftRoot = pos.x
                bounds.topRoot = pos.y
                bounds.widthPx = it.size.width.toFloat()
                bounds.heightPx = it.size.height.toFloat()
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
                        if (glassOverlayBrush != null) {
                            Modifier.background(glassOverlayBrush)
                        } else Modifier
                    )
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (key.hint != null && key.type == KeyActionType.CHAR) {
                    Text(
                        text = key.hint,
                        color = kbColors.keyHintText,
                        fontSize = if (largeLabels) 10.5.sp else 9.sp,
                        lineHeight = if (largeLabels) 11.sp else 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                    )
                }

                when (key.type) {
                    KeyActionType.SHIFT -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(iconSize)
                        )
                        if (shiftState == ShiftState.CAPS_LOCK) {
                            Spacer(Modifier.height(1.5.dp))
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(fgColor)
                            )
                        }
                    }
                    KeyActionType.BACKSPACE -> Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(iconSize)
                    )
                    KeyActionType.EMOJI_PANEL -> Icon(
                        imageVector = Icons.Rounded.EmojiEmotions,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(iconSize)
                    )
                    KeyActionType.ENTER -> {
                        val icon = when (imeActionLabel) {
                            KeyActionLabel.SEARCH -> Icons.Rounded.Search
                            KeyActionLabel.SEND -> Icons.AutoMirrored.Rounded.Send
                            KeyActionLabel.DONE -> Icons.Rounded.Check
                            KeyActionLabel.NEXT, KeyActionLabel.GO -> Icons.AutoMirrored.Rounded.ArrowForward
                            KeyActionLabel.ENTER -> Icons.AutoMirrored.Rounded.KeyboardReturn
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(if (largeLabels) 24.dp else 21.dp)
                        )
                    }
                    KeyActionType.SPACE -> {
                        if (prefs.showSpacebarLanguageLabel) {
                            Text(
                                text = displayText,
                                color = kbColors.keyHintText,
                                fontSize = if (largeLabels) 13.5.sp else 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }
                    }
                    KeyActionType.SYMBOLS_TOGGLE, KeyActionType.SYMBOLS_EXT_TOGGLE -> Text(
                        text = displayText,
                        color = fgColor,
                        fontSize = if (largeLabels) 14.5.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    else -> Text(
                        text = displayText,
                        color = fgColor,
                        fontSize = when {
                            isNumpadKey && largeLabels -> 27.sp
                            isNumpadKey -> 24.sp
                            largeLabels -> 24.sp
                            else -> 21.sp
                        },
                        fontWeight = if (isNumpadKey || largeLabels) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.padding(
                            top = if (key.hint != null && key.type == KeyActionType.CHAR) 7.dp else 0.dp
                        )
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

private class GridGeometry(
    var leftRoot: Float = 0f,
    var topRoot: Float = 0f,
    var widthPx: Float = 0f
)

private class KeyBounds(
    var leftRoot: Float = 0f,
    var topRoot: Float = 0f,
    var widthPx: Float = 0f,
    var heightPx: Float = 0f
)

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
