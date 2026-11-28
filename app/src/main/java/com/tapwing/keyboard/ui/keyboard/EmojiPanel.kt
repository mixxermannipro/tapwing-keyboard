// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.emoji.EmojiCatalog
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
