package com.tapwing.keyboard.ui.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SnippetPlaceholderResolver
import com.tapwing.keyboard.dictionary.TextSnippet
import com.tapwing.keyboard.emoji.EmojiCatalog
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        ToolTileSpec(if (de) "Apple Glas" else "Liquid Glass", Icons.Rounded.Smartphone, active = prefs.appleModeEnabled, onClick = onToggleAppleMode),
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
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (tile.active) kbColors.actionSurface.copy(alpha = 0.28f) else kbColors.cardSurface,
                    border = if (tile.active) BorderStroke(1.dp, kbColors.actionSurface) else null,
                    modifier = Modifier.clickable(onClick = tile.onClick)
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

private data class ToolTileSpec(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Dynamic live keyboard resizer modeled on PixelBoard's resize frame:
 * lets the user drag the top handle OR use the fine slider & bottom-lift buttons while watching
 * the actual KeyboardGrid scale smoothly in real time.
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

        // Draggable resize border around the live KeyboardGrid
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
                                    // Dragging UP (negative dy) increases keyboard height
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
 * Precision Cursor & Selection editing panel addressing one of the top Gboard user requests.
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
            // Left: 4-way precision cursor D-pad with hold-to-repeat
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

            // Right: Select All, Copy, Cut, Paste, Line Start/End, Undo
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

/**
 * In-keyboard Web Search panel (restores one of the most missed Gboard features on Android).
 */
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
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    val actions = listOf(
        WritingAction.PROOFREAD to stringResource(R.string.action_proofread),
        WritingAction.PROFESSIONAL to stringResource(R.string.action_professional),
        WritingAction.CASUAL to stringResource(R.string.action_casual),
        WritingAction.CONCISE to stringResource(R.string.action_concise),
        WritingAction.EMOTIVE to stringResource(R.string.action_emotive),
        WritingAction.REPHRASE to stringResource(R.string.action_rephrase),
        WritingAction.SUMMARIZE to stringResource(R.string.action_summarize),
        WritingAction.TRANSLATE to stringResource(R.string.action_translate)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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
                        color = kbColors.keyText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

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
                        tint = kbColors.keyText,
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
                                text = stringResource(R.string.btn_rewriting),
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
                    tint = kbColors.keyText,
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
    onPasteClipOcr: (ClipboardClip) -> Unit = {},
    onTogglePin: (ClipboardClip) -> Unit,
    onDeleteClip: (ClipboardClip) -> Unit,
    onClearUnpinned: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    var filterMode by remember { mutableStateOf(0) } // 0=All, 1=Pinned, 2=Images, 3=Text
    val hasPinned = remember(clips) { clips.any { it.pinned } }
    val hasImages = remember(clips) { clips.any { it.isImage } }
    val filteredClips = remember(clips, filterMode) {
        when (filterMode) {
            1 -> clips.filter { it.pinned }
            2 -> clips.filter { it.isImage }
            3 -> clips.filter { !it.isImage }
            else -> clips
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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
                text = "${stringResource(R.string.cd_clipboard).substringBefore(' ')} (${clips.size}/${prefs.clipboardMaxItems})",
                color = kbColors.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (clips.any { !it.pinned }) {
                TextButton(onClick = {
                    if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClearUnpinned()
                }) {
                    Text(stringResource(R.string.clipboard_clear), fontSize = 12.sp)
                }
            }
        }

        if (clips.isNotEmpty() && (hasPinned || hasImages)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = buildList {
                    add(0 to if (de) "Alle" else "All")
                    if (hasPinned) add(1 to if (de) "Angepinnt" else "Pinned")
                    if (hasImages) add(2 to if (de) "Bilder" else "Images")
                    if (hasImages) add(3 to "Text")
                }
                tabs.forEach { (id, label) ->
                    val selected = filterMode == id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) kbColors.actionSurface.copy(alpha = 0.28f) else kbColors.cardSurface,
                        border = if (selected) BorderStroke(1.dp, kbColors.actionSurface) else null,
                        modifier = Modifier.clickable {
                            if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            filterMode = id
                        }
                    ) {
                        Text(
                            text = label,
                            color = kbColors.keyText,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (filteredClips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.clipboard_empty),
                    color = kbColors.keyHintText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
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
                items(filteredClips, key = { it.id }) { clip ->
                    val thumb by produceState<Bitmap?>(initialValue = null, clip.imagePath) {
                        val path = clip.imagePath
                        value = if (path != null) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                    BitmapFactory.decodeFile(path, opts)
                                }.getOrNull()
                            }
                        } else null
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (clip.pinned) kbColors.cardAltSurface else kbColors.cardSurface,
                        modifier = Modifier.clickable {
                            if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPasteClip(clip)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (clip.isImage && thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(),
                                    contentDescription = clip.text,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Text(
                                    text = clip.text,
                                    color = kbColors.keyText,
                                    fontSize = 13.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (clip.isImage) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = kbColors.actionSurface.copy(alpha = 0.18f),
                                        modifier = Modifier.clickable {
                                            if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onPasteClipOcr(clip)
                                        }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.strip_screenshot_ocr),
                                            color = kbColors.keyText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = null,
                                        tint = if (clip.pinned) kbColors.actionSurface else kbColors.keyHintText,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                onTogglePin(clip)
                                            }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = kbColors.keyHintText,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onDeleteClip(clip)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnippetsPanel(
    prefs: TapwingPrefs,
    snippets: List<TextSnippet>,
    latestClipboardText: String,
    onBack: () -> Unit,
    onInsertSnippet: (TextSnippet, String) -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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
                text = if (de) "Textbausteine (${snippets.size})" else "Text Snippets (${snippets.size})",
                color = kbColors.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (snippets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (de) "Erstelle Bausteine in Einstellungen → Wörterbuch."
                    else "Create snippets in Settings → Dictionary.",
                    color = kbColors.keyHintText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
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
                items(snippets, key = { it.id }) { snippet ->
                    val resolved = remember(snippet, prefs.activeLocale, latestClipboardText, prefs.snippetUserName, prefs.snippetUserEmail) {
                        SnippetPlaceholderResolver.resolve(
                            template = snippet.body,
                            locale = prefs.activeLocale,
                            latestClipboardText = latestClipboardText,
                            userName = prefs.snippetUserName,
                            userEmail = prefs.snippetUserEmail
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = kbColors.cardSurface,
                        modifier = Modifier.clickable { onInsertSnippet(snippet, resolved) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = kbColors.actionSurface.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = snippet.trigger,
                                        color = kbColors.keyText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = snippet.title,
                                    color = kbColors.keyText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = resolved,
                                color = kbColors.keyHintText,
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
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
    recentEmojis: List<String> = emptyList(),
    onBack: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val de = KeyboardLocales.baseLang(prefs.activeLocale) == "de"
    val allCategories = remember(recentEmojis) {
        if (recentEmojis.isNotEmpty()) {
            listOf(
                com.tapwing.keyboard.emoji.EmojiCategory(
                    id = "recent",
                    icon = "🕒",
                    titleDe = "Zuletzt",
                    titleEn = "Recent",
                    emojis = recentEmojis.map { it to "" }
                )
            ) + EmojiCatalog.categories
        } else {
            EmojiCatalog.categories
        }
    }
    // Keep category stable even when recentEmojis transitions from empty -> non-empty
    var selectedCategoryId by remember {
        mutableStateOf(if (recentEmojis.isNotEmpty()) "recent" else EmojiCatalog.categories.first().id)
    }
    var activeSearchTag by remember { mutableStateOf<String?>(null) }
    val activeCategory = remember(selectedCategoryId, allCategories) {
        allCategories.firstOrNull { it.id == selectedCategoryId } ?: allCategories.first()
    }
    val displayedEmojis = remember(activeSearchTag, activeCategory) {
        val tag = activeSearchTag
        if (tag.isNullOrBlank()) {
            activeCategory.emojis.map { it.first }
        } else {
            EmojiCatalog.search(tag)
        }
    }
    val quickTags = if (de) EmojiCatalog.quickSearchTagsDe else EmojiCatalog.quickSearchTagsEn
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    val currentHaptic by rememberUpdatedState(prefs.hapticFeedback)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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
                allCategories.forEach { cat ->
                    val selected = activeSearchTag == null && cat.id == activeCategory.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) kbColors.actionSurface.copy(alpha = 0.28f) else kbColors.boardBackground,
                        modifier = Modifier.clickable {
                            if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            activeSearchTag = null
                            selectedCategoryId = cat.id
                        }
                    ) {
                        Text(
                            text = cat.icon,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            // Hold-to-repeat backspace inside Emoji panel
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pid = down.id
                            if (currentHaptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentOnBackspace()
                            val repeatJob = scope.launch {
                                delay(320L)
                                while (true) {
                                    currentOnBackspace()
                                    if (currentHaptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    delay(55L)
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
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.cd_backspace), tint = kbColors.keyText, modifier = Modifier.size(20.dp))
            }
        }

        // Quick 1-tap mood/keyword filter strip powered by EmojiCatalog.search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickTags.forEach { (icon, keyword) ->
                val isSelected = activeSearchTag == keyword
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) kbColors.actionSurface.copy(alpha = 0.28f) else kbColors.cardSurface,
                    border = if (isSelected) BorderStroke(1.dp, kbColors.actionSurface) else null,
                    modifier = Modifier.clickable {
                        if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        activeSearchTag = if (isSelected) null else keyword
                    }
                ) {
                    Text(
                        text = "$icon $keyword",
                        color = kbColors.keyText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(displayedEmojis) { emoji ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onInsertEmoji(emoji)
                        }
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

        // Interactive live preview strip supporting horizontal drag-swap and tap-to-select
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
                        actions.forEachIndexed { idx, action ->
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

        // Compact Move Bar for selected item
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

        // All 13 tools grid
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
