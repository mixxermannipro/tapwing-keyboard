// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.ui.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsOutput
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors

@Composable
fun WritingToolsPanel(
    prefs: TapwingPrefs,
    sourceText: String,
    output: WritingToolsOutput?,
    isLoading: Boolean,
    selectedAction: WritingAction?,
    activeCloudLabel: String? = null,
    onBack: () -> Unit,
    onRunAction: (WritingAction) -> Unit,
    onApplyResult: (String) -> Unit,
    onUndoApplied: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current

    val actions = listOf(
        WritingAction.PROOFREAD to stringResource(R.string.action_proofread),
        WritingAction.BULLET_LIST to stringResource(R.string.action_bullet_list),
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
            if (!activeCloudLabel.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = kbColors.actionSurface.copy(alpha = 0.28f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(13.dp))
                        Text(
                            text = activeCloudLabel,
                            color = kbColors.keyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = kbColors.keyHintText
                    )
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
