// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.CloudProviderDisclosure
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.ModelInstallState
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.title
import java.io.File
import kotlin.math.roundToInt

internal fun Context.openSettingsLink(url: String) {
    if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
        Toast.makeText(this, R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
    }
}
@Composable
internal fun LocalModelCatalogSection(
    title: String,
    recommendationNote: String,
    models: List<LocalModelSpec>,
    selectedModelId: String,
    states: Map<String, ModelInstallState>,
    de: Boolean,
    onSelectModel: (LocalModelSpec) -> Unit,
    onDownloadModel: (LocalModelSpec) -> Unit,
    onCancelDownload: (LocalModelSpec) -> Unit,
    onDeleteModel: (LocalModelSpec) -> Unit,
    onImportModel: (LocalModelSpec) -> Unit,
    onOpenUrl: (String) -> Unit,
    showTechnicalDetails: Boolean = true
) {
    Column {
        AppleSectionHeader(title)
        AppleGroupedSurface {
            Text(
                text = recommendationNote,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            AppleHairlineDivider(startIndent = 16.dp)
            LocalModelCatalogListContent(
                models = models,
                selectedModelId = selectedModelId,
                states = states,
                de = de,
                onSelectModel = onSelectModel,
                onDownloadModel = onDownloadModel,
                onCancelDownload = onCancelDownload,
                onDeleteModel = onDeleteModel,
                onImportModel = onImportModel,
                onOpenUrl = onOpenUrl,
                showTechnicalDetails = showTechnicalDetails
            )
        }
    }
}
@Composable
internal fun LocalModelCatalogListContent(
    models: List<LocalModelSpec>,
    selectedModelId: String,
    states: Map<String, ModelInstallState>,
    de: Boolean,
    onSelectModel: (LocalModelSpec) -> Unit,
    onDownloadModel: (LocalModelSpec) -> Unit,
    onCancelDownload: (LocalModelSpec) -> Unit,
    onDeleteModel: (LocalModelSpec) -> Unit,
    onImportModel: (LocalModelSpec) -> Unit,
    onOpenUrl: (String) -> Unit,
    showTechnicalDetails: Boolean = true
) {
    models.forEachIndexed { idx, spec ->
        if (idx > 0) AppleHairlineDivider(startIndent = 16.dp)
        val state = states[spec.id] ?: ModelInstallState.NotInstalled
        val isSelected = selectedModelId == spec.id
        val interaction = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .springPressScale(interaction, enabled = state is ModelInstallState.Installed, pressedScale = 0.986f)
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    enabled = state is ModelInstallState.Installed
                ) { onSelectModel(spec) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (showTechnicalDetails) spec.displayName else spec.displayName.substringBefore(" ("),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val tag = if (de) spec.highlightTagDe else spec.highlightTagEn
                        if (tag.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (spec.isRecommended) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                }
                            ) {
                                Text(
                                    text = tag,
                                    color = if (spec.isRecommended) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val speedBadge = if (de) spec.speedBadgeDe else spec.speedBadgeEn
                    val metaText = listOfNotNull(
                        speedBadge.takeIf { it.isNotEmpty() },
                        "${spec.approxSizeMb} MB",
                        "${spec.minRamGb}+ GB RAM".takeIf { showTechnicalDetails },
                        spec.licenseName.takeIf { it.isNotEmpty() }
                    ).joinToString(" · ")
                    Text(
                        text = metaText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showTechnicalDetails && (spec.attributionNotice.isNotEmpty() || spec.licenseUrl.isNotEmpty())) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (spec.attributionNotice.isNotEmpty()) {
                                Text(
                                    text = spec.attributionNotice,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (spec.licenseUrl.isNotEmpty()) {
                                Text(
                                    text = if (de) "Lizenz ↗" else "License ↗",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onOpenUrl(spec.licenseUrl) }
                                )
                            }
                        }
                    }
                }
                if (isSelected && state is ModelInstallState.Installed) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (showTechnicalDetails) {
                Text(
                    text = if (de) spec.summaryDe else spec.summaryEn,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            when (state) {
                is ModelInstallState.Downloading -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "modelDlProgress"
                    )
                    val pct = (state.progress * 100f).roundToInt()
                    val dlMb = state.downloadedBytes / 1_000_000L
                    val totMb = (state.totalBytes / 1_000_000L).coerceAtLeast(1L)
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$pct% ($dlMb / $totMb MB)", fontSize = 12.sp)
                        TextButton(onClick = { onCancelDownload(spec) }) {
                            Text(if (de) "Abbrechen" else "Cancel", fontSize = 12.sp)
                        }
                    }
                }
                is ModelInstallState.Extracting -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "modelExtractProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    )
                    Text(
                        text = if (de) "Entpacke ONNX-Modell…" else "Extracting ONNX model…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is ModelInstallState.Installed -> if (showTechnicalDetails || !isSelected) {
                    val mb = state.sizeBytes / 1_000_000L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { onSelectModel(spec) },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isSelected) (if (de) "Aktiv ✓" else "Active ✓")
                                else (if (de) "Auswählen ($mb MB)" else "Use ($mb MB)"),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { onDeleteModel(spec) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is ModelInstallState.Error -> {
                    Text(
                        text = state.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDownloadModel(spec) },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "Erneut laden" else "Retry", fontSize = 12.sp)
                        }
                        if (showTechnicalDetails) TextButton(onClick = { onOpenUrl(spec.repoUrl) }) {
                            Text("Repo", fontSize = 12.sp)
                        }
                    }
                }
                ModelInstallState.NotInstalled -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDownloadModel(spec) },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (de) "Laden" else "Download", fontSize = 12.sp)
                        }
                        if (showTechnicalDetails) FilledTonalButton(
                            onClick = { onImportModel(spec) },
                            shape = CircleShape
                        ) {
                            Text(if (de) "Datei" else "File", fontSize = 12.sp)
                        }
                        if (showTechnicalDetails) TextButton(onClick = { onOpenUrl(spec.repoUrl) }) {
                            Text("Info", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun CloudConsentDialog(
    disclosure: CloudProviderDisclosure,
    de: Boolean,
    onOpenPrivacyUrl: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (de) "Cloud-Datenübertragung erlauben?" else "Allow cloud data transmission?",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = disclosure.displayName,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (de) disclosure.summaryDe else disclosure.summaryEn,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = if (de) {
                        "Es werden niemals Hintergrund-Tippdaten übertragen. Du kannst diese Einwilligung jederzeit unter „Datenschutz & Cloud-Einwilligung“ widerrufen."
                    } else {
                        "Background typing is never transmitted. You can revoke this consent anytime under Privacy & Cloud Consent."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { onOpenPrivacyUrl(disclosure.privacyPolicyUrl) }
                ) {
                    Text(if (de) "Datenschutz des Anbieters öffnen ↗" else "Open provider privacy policy ↗")
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (de) "Einverstanden" else "Agree & Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (de) "Abbrechen" else "Cancel")
            }
        }
    )
}

@Composable
internal fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
internal fun MinimalNavRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .springPressScale(interaction, pressedScale = 0.985f)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppleIconBadge(icon = icon, tintColor = badgeColor)
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(19.dp)
        )
    }
}
@Composable
internal fun MinimalToggleRow(
    title: String,
    checked: Boolean,
    icon: ImageVector? = null,
    badgeColor: Color = Color(0xFF007AFF),
    onCheckedChange: (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .springPressScale(interaction, pressedScale = 0.988f)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = if (icon != null) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            AppleIconBadge(icon = icon, tintColor = badgeColor)
        }
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AppleSwitch(checked = checked)
    }
}
@Composable
internal fun SettingsDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "disclosureChevron"
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .springPressScale(interaction, pressedScale = 0.988f)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClickLabel = if (expanded) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                role = Role.Button,
                onClick = onToggle
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
internal data class DropdownOption<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null
)
@Composable
internal fun <T> MinimalDropdownRow(
    title: String,
    selectedValue: T,
    options: List<DropdownOption<T>>,
    subtitle: String? = null,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val selectedOption = options.firstOrNull { it.value == selectedValue }
    val selectedLabel = selectedOption?.label ?: ""
    val displaySubtitle = subtitle ?: selectedOption?.subtitle

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .springPressScale(interaction, pressedScale = 0.988f)
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    stateDescription = selectedLabel
                }
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    onClick = { expanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!displaySubtitle.isNullOrBlank()) {
                    Text(
                        text = displaySubtitle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 155.dp)
                    )
                    Icon(
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(min = 220.dp, max = 330.dp)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = option.label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (!option.subtitle.isNullOrBlank()) {
                                Text(
                                    text = option.subtitle,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        if (option.value != selectedValue) {
                            onSelect(option.value)
                        }
                    }
                )
            }
        }
    }
}
@Composable
internal fun MinimalValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
internal fun MinimalActionRow(
    title: String,
    textColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .springPressScale(interaction, pressedScale = 0.986f)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
