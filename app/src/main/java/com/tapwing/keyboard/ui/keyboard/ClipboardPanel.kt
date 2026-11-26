// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ui.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.clipboard.ClipboardClip
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.SnippetPlaceholderResolver
import com.tapwing.keyboard.dictionary.TextSnippet
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var filterMode by remember { mutableStateOf(0) }
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
                            val bmp = thumb
                            if (clip.isImage && bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
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
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                onTogglePin(clip)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PushPin,
                                            contentDescription = stringResource(
                                                if (clip.pinned) R.string.cd_unpin else R.string.cd_pin
                                            ),
                                            tint = if (clip.pinned) kbColors.actionSurface else kbColors.keyHintText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                if (prefs.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onDeleteClip(clip)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.cd_delete),
                                            tint = kbColors.keyHintText,
                                            modifier = Modifier.size(16.dp)
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
                    val resolved = remember(snippet, prefs.activeLocale, latestClipboardText, prefs.snippetUserName, prefs.snippetUserEmail, prefs.personalProfile) {
                        SnippetPlaceholderResolver.resolve(
                            template = snippet.body,
                            locale = prefs.activeLocale,
                            latestClipboardText = latestClipboardText,
                            userName = prefs.snippetUserName,
                            userEmail = prefs.snippetUserEmail,
                            profile = prefs.personalProfile
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
