// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.ocr.ScanTextActivity
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ClipboardSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ocrResultNotice by remember { mutableStateOf<String?>(null) }

    val photoOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = ScreenshotMonitor.extractTextFromImageUri(context, uri)
                if (text.isNotBlank()) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Tapwing OCR", text))
                    ocrResultNotice = if (de) {
                        "In Zwischenablage kopiert: ${text.take(64)}"
                    } else {
                        "Copied to clipboard: ${text.take(64)}"
                    }
                } else {
                    ocrResultNotice = if (de) "Kein Text im Bild gefunden." else "No text found in image."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AppleGroupedSurface {
                MinimalToggleRow(if (de) "Verlauf und Einfügevorschläge" else "History and paste suggestions", prefs.clipboardEnabled) { v ->
                    onUpdatePrefs { it.copy(clipboardEnabled = v) }
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalToggleRow(if (de) "Kopierte Bilder in Vorschlagsleiste" else "Copied image chip in strip", prefs.screenshotSuggestionChip) { v ->
                    onUpdatePrefs { it.copy(screenshotSuggestionChip = v) }
                }
            }
            AppleSectionFooter(
                if (de) "Kopierte Texte und explizit in die Zwischenablage kopierte Bilder erscheinen in der Leiste. Kein automatischer Zugriff auf deine Fotomediathek."
                else "Copied text and explicitly copied images appear in the suggestion strip. Tapwing never scans your photo library in the background."
            )
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Aufbewahrung" else "Retention")
                AppleGroupedSurface {
                    val retentionOptions = listOf(
                        DropdownOption(ClipboardRetention.HOUR_1, if (de) "1 Stunde" else "1 hour"),
                        DropdownOption(ClipboardRetention.HOURS_24, if (de) "24 Stunden" else "24 hours"),
                        DropdownOption(ClipboardRetention.DAYS_7, if (de) "7 Tage" else "7 days"),
                        DropdownOption(ClipboardRetention.DAYS_30, if (de) "30 Tage" else "30 days"),
                        DropdownOption(ClipboardRetention.NEVER, if (de) "Nie löschen" else "Never delete")
                    )
                    MinimalDropdownRow(
                        title = if (de) "Automatisch löschen nach" else "Auto-delete after",
                        selectedValue = prefs.clipboardRetention,
                        options = retentionOptions,
                        onSelect = { ret -> onUpdatePrefs { it.copy(clipboardRetention = ret) } }
                    )
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Verlauf & Scan Text" else "Limits & OCR")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Max. Einträge" else "Max clips", fontSize = 15.sp)
                            Text("${prefs.clipboardMaxItems}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.clipboardMaxItems.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(clipboardMaxItems = v.roundToInt()) } },
                            valueRange = 20f..200f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Foto / Screenshot scannen (OCR)" else "Scan photo / screenshot (OCR)",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        photoOcrLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Kamera-OCR öffnen" else "Open Camera OCR",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        context.startActivity(Intent(context, ScanTextActivity::class.java))
                    }
                    if (ocrResultNotice != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = ocrResultNotice!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                AppleSectionFooter(
                    if (de) "Fotos werden nur einzeln über die System-Auswahl gelesen (kein dauerhafter Galeriezugriff)."
                    else "Photos are read one-by-one via Android Photo Picker without broad storage permission."
                )
            }
        }
    }
}
