package com.tapwing.keyboard.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapwing.keyboard.R
import kotlin.math.abs

internal enum class SettingsTab(@get:StringRes val label: Int, val icon: ImageVector, val routes: Set<SettingsRoute>) {
    START(R.string.settings_tab_start, Icons.Rounded.Home, setOf(SettingsRoute.STATS, SettingsRoute.ABOUT)),
    WRITING(R.string.settings_tab_writing, Icons.Rounded.Edit, setOf(
        SettingsRoute.LANGUAGES, SettingsRoute.CORRECTIONS, SettingsRoute.DICTIONARY, SettingsRoute.VOICE
    )),
    KEYBOARD(R.string.settings_tab_keyboard, Icons.Rounded.Keyboard, setOf(
        SettingsRoute.APPEARANCE, SettingsRoute.QUICK_BAR, SettingsRoute.EMOJI
    )),
    TOOLS(R.string.settings_tab_tools, Icons.Rounded.Build, setOf(
        SettingsRoute.QUICK_SEARCH, SettingsRoute.CLIPBOARD, SettingsRoute.AUTOFILL,
        SettingsRoute.WRITING_TOOLS, SettingsRoute.API_KEYS
    ));

    companion object {
        fun forRoute(route: SettingsRoute): SettingsTab =
            entries.firstOrNull { route in it.routes } ?: START
    }
}

internal class SettingsBarScroll {
    private var travel = 0f

    fun update(visible: Boolean, listDelta: Float, leftoverDelta: Float, threshold: Float): Boolean? {
        // Only hide when the list is actually moving. Allow reveal even when it reaches its start.
        val gesture = listDelta + leftoverDelta
        if (gesture == 0f) return null
        if ((visible && gesture > 0f) || (!visible && gesture < 0f)) {
            travel = 0f
            return null
        }
        travel += if (visible) abs(listDelta.coerceAtMost(0f)) else gesture
        if (travel < threshold) return null
        travel = 0f
        return !visible
    }
}

private val barHeight = 68.dp
private val bottomGap = 10.dp
internal val settingsBarSpace = barHeight + bottomGap + 8.dp
internal const val settingsBarMotionMillis = 260

@Composable
internal fun SettingsBottomBar(
    visible: Boolean,
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(settingsBarMotionMillis, easing = FastOutSlowInEasing)) { it } +
            fadeIn(tween(190)),
        exit = slideOutVertically(tween(settingsBarMotionMillis, easing = FastOutSlowInEasing)) { it } +
            fadeOut(tween(180))
    ) {
        Surface(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottomGap)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(barHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 5.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val itemWidth = maxWidth / SettingsTab.entries.size
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selected.ordinal,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "settingsTabIndicator"
                )
                Box(
                    Modifier.offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                )
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    SettingsTab.entries.forEach { tab ->
                        val active = tab == selected
                        val tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                                .padding(horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = tint)
                            Text(
                                stringResource(tab.label),
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
