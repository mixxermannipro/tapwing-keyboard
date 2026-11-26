// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import com.tapwing.keyboard.dictionary.PackInstallState
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun DictionaryLearningScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    dictionaryStore: AdaptiveDictionaryStore,
    packManager: OfflineDictionaryPackManager,
    onDictionaryChanged: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packStates by packManager.states.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newWordInput by remember { mutableStateOf("") }
    var newShortcutTrigger by remember { mutableStateOf("") }
    var newShortcutExpansion by remember { mutableStateOf("") }
    var newSnippetTrigger by remember { mutableStateOf("") }
    var newSnippetTitle by remember { mutableStateOf("") }
    var newSnippetBody by remember { mutableStateOf("") }
    var profileName by remember(prefs.snippetUserName) { mutableStateOf(prefs.snippetUserName) }
    var profileEmail by remember(prefs.snippetUserEmail) { mutableStateOf(prefs.snippetUserEmail) }

    val stats = remember(revision) { dictionaryStore.stats() }
    val customWords = remember(revision) { dictionaryStore.listCustomWords().take(25) }
    val learnedWords = remember(revision) { dictionaryStore.listTopLearnedWords(12) }
    val rejectedWords = remember(revision) { dictionaryStore.listRejectedCorrections().take(12) }
    val shortcuts = remember(revision) { dictionaryStore.listShortcuts().take(20) }
    val snippets = remember(revision) { dictionaryStore.listSnippets() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            dictionaryStore.importStream(stream)
                        } ?: throw IllegalStateException("Datei konnte nicht gelesen werden")
                    }
                }.onSuccess { result ->
                    revision++
                    onDictionaryChanged()
                    val wordTextDe = if (result.addedWords == 1) "1 neues Wort" else "${result.addedWords} neue Wörter"
                    val wordTextEn = if (result.addedWords == 1) "1 word" else "${result.addedWords} words"
                    val shortcutTextEn = if (result.addedShortcuts == 1) "1 shortcut" else "${result.addedShortcuts} shortcuts"
                    statusMessage = when (result.sourceFormat) {
                        "gboard_zip" -> if (de) {
                            "Wörterbuch-.zip importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "Dictionary .zip imported: $wordTextEn and $shortcutTextEn."
                        }
                        "gboard_tsv" -> if (de) {
                            "Wörterbuch importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "Dictionary imported: $wordTextEn and $shortcutTextEn."
                        }
                        else -> if (de) {
                            "Importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "Imported $wordTextEn and $shortcutTextEn."
                        }
                    }
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Import error"
                }
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val localeTag = if (de) "de-DE" else "en-US"
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            dictionaryStore.exportGboardZip(out, defaultLocaleTag = localeTag)
                        } ?: 0
                    }
                }.onSuccess { count ->
                    statusMessage = if (de) {
                        "Wörterbuch-.zip exportiert ($count Einträge)."
                    } else {
                        "Dictionary .zip exported ($count entries)."
                    }
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Export error"
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(dictionaryStore.exportJson())
                        }
                    }
                }.onSuccess {
                    statusMessage = if (de) "JSON-Backup exportiert." else "JSON backup exported."
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Export error"
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
            Column {
                AppleSectionHeader(if (de) "Lernen & Statistik" else "Learning")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Adaptives Lernen" else "Adaptive learning",
                        checked = prefs.learningEnabled
                    ) { v -> onUpdatePrefs { it.copy(learningEnabled = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBadge(if (de) "Wörter" else "Words", stats.customWordCount.toString())
                        StatBadge(if (de) "Kürzel" else "Shortcuts", stats.shortcutCount.toString())
                        StatBadge(if (de) "Gelernt" else "Learned", stats.learnedWordCount.toString())
                        StatBadge(if (de) "Paare" else "Bigrams", stats.learnedBigramCount.toString())
                        StatBadge(if (de) "3-Gramm" else "Trigrams", stats.learnedTrigramCount.toString())
                    }
                }
                AppleSectionFooter(
                    if (de) "Lernt deine Wörter, Wortpaare und respektiert rückgängig gemachte Korrekturen."
                    else "Learns your vocabulary and respects undone corrections."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Import & Export" else "Import & Export")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "text/*",
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (de) "Importieren" else "Import", fontSize = 13.sp)
                        }
                        FilledTonalButton(
                            onClick = { exportZipLauncher.launch("Tapwing-PersonalDictionary.zip") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (de) "Export (.zip)" else "Export (.zip)", fontSize = 13.sp)
                        }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "JSON-Backup exportieren" else "Export JSON backup",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        exportJsonLauncher.launch("tapwing-dictionary.json")
                    }
                    if (statusMessage != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = statusMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                AppleSectionFooter(
                    if (de) "Kompatibel mit Wörterbuch-.zip (Gboard-Export / dictionary.txt), .txt, .csv/.tsv und .json."
                    else "Compatible with dictionary .zip (Gboard exports), .txt, .csv/.tsv, and .json."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Eigenes Wort" else "Custom words")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppleInlineField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            placeholder = if (de) "Wort hinzufügen…" else "Add word…",
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addCustomWord(newWordInput)
                                newWordInput = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newWordInput.trim().length >= 2,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = if (de) "Wort hinzufügen" else "Add word",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    customWords.forEach { (word, weight) ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$word ($weight)", modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeCustomWord(word)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = if (de) "$word löschen" else "Delete $word",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (stats.customWordCount > customWords.size) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = if (de) {
                                "Zeige Top ${customWords.size} von ${stats.customWordCount} Wörtern"
                            } else {
                                "Showing top ${customWords.size} of ${stats.customWordCount} words"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Textkürzel" else "Text shortcuts")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = newShortcutTrigger,
                                onValueChange = { newShortcutTrigger = it },
                                placeholder = if (de) "Kürzel" else "Trigger",
                                modifier = Modifier.weight(0.38f)
                            )
                            AppleInlineField(
                                value = newShortcutExpansion,
                                onValueChange = { newShortcutExpansion = it },
                                placeholder = if (de) "Text" else "Phrase",
                                modifier = Modifier.weight(0.62f)
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addShortcut(newShortcutTrigger, newShortcutExpansion)
                                newShortcutTrigger = ""
                                newShortcutExpansion = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newShortcutTrigger.isNotBlank() && newShortcutExpansion.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (de) "Kürzel speichern" else "Save shortcut", fontSize = 13.sp)
                        }
                    }
                    shortcuts.forEach { (trigger, expansion) ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$trigger → $expansion",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeShortcut(trigger)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = if (de) "Kürzel $trigger löschen" else "Delete shortcut $trigger",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                AppleSectionFooter(if (de) "Dynamisch eingebaut: /datum, /zeit, /woche, /monat, /jahr" else "Built-in triggers: /date, /time, /week, /month, /year")
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Offline-Wörterbuch-Pakete (50.000 Wörter)" else "Offline Dictionary Packs (50k words)")
                AppleGroupedSurface {
                    OfflineDictionaryPackManager.availablePacks.forEachIndexed { idx, spec ->
                        if (idx > 0) AppleHairlineDivider(startIndent = 16.dp)
                        val state = packStates[spec.langCode] ?: PackInstallState.NotInstalled
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spec.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    val sub = when (state) {
                                        PackInstallState.NotInstalled -> "~${spec.approxSizeKb} KB · Offline"
                                        is PackInstallState.Downloading -> "${(state.progress * 100).roundToInt()}%"
                                        is PackInstallState.Installed -> if (de) {
                                            "Installiert · ${state.wordCount} Wörter"
                                        } else {
                                            "Installed · ${state.wordCount} words"
                                        }
                                        is PackInstallState.Error -> state.message
                                    }
                                    Text(
                                        text = sub,
                                        fontSize = 12.sp,
                                        color = if (state is PackInstallState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                when (state) {
                                    is PackInstallState.Downloading -> {
                                        TextButton(onClick = { packManager.cancelDownload(spec.langCode) }) {
                                            Text(if (de) "Stopp" else "Cancel", fontSize = 12.sp)
                                        }
                                    }
                                    is PackInstallState.Installed -> {
                                        IconButton(
                                            onClick = { packManager.deletePack(spec.langCode) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = if (de) "${spec.displayName} löschen" else "Delete ${spec.displayName}",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        FilledTonalButton(
                                            onClick = { packManager.startDownload(spec) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (de) "Laden" else "Download", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            if (state is PackInstallState.Downloading) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Lädt bis zu 50.000 häufige Wörter pro Sprache lokal aufs Gerät — verbessert Wisch-Tippen & Korrektur ohne Internet."
                    else "Downloads 50,000 high-frequency words locally per language — improves swipe typing & autocorrection offline."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Textbausteine & Platzhalter" else "Multi-line Snippets & Placeholders")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = profileName,
                                onValueChange = {
                                    profileName = it
                                    onUpdatePrefs { p -> p.copy(snippetUserName = it) }
                                },
                                placeholder = "{NAME}",
                                modifier = Modifier.weight(1f)
                            )
                            AppleInlineField(
                                value = profileEmail,
                                onValueChange = {
                                    profileEmail = it
                                    onUpdatePrefs { p -> p.copy(snippetUserEmail = it) }
                                },
                                placeholder = "{EMAIL}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = newSnippetTrigger,
                                onValueChange = { newSnippetTrigger = it },
                                placeholder = if (de) "Kürzel (z.B. /sig)" else "Trigger (e.g. /sig)",
                                modifier = Modifier.weight(0.4f)
                            )
                            AppleInlineField(
                                value = newSnippetTitle,
                                onValueChange = { newSnippetTitle = it },
                                placeholder = if (de) "Titel" else "Title",
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                        OutlinedTextField(
                            value = newSnippetBody,
                            onValueChange = { newSnippetBody = it },
                            placeholder = {
                                Text(
                                    if (de) "Mehrzeiliger Text mit {DATUM}, {ZEIT}, {NAME}, {EMAIL}, {ZWISCHENABLAGE}…"
                                    else "Multi-line text with {DATE}, {TIME}, {NAME}, {EMAIL}, {CLIPBOARD}…",
                                    fontSize = 13.sp
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addOrUpdateSnippet(
                                    trigger = newSnippetTrigger,
                                    title = newSnippetTitle,
                                    body = newSnippetBody
                                )
                                newSnippetTrigger = ""
                                newSnippetTitle = ""
                                newSnippetBody = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newSnippetTrigger.isNotBlank() && newSnippetBody.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (de) "Textbaustein speichern" else "Save snippet", fontSize = 13.sp)
                        }
                    }
                    snippets.forEach { snippet ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${snippet.trigger} · ${snippet.title}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = snippet.body,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    dictionaryStore.deleteSnippet(snippet.id)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = if (de) "Textbaustein ${snippet.title} löschen" else "Delete snippet ${snippet.title}",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Platzhalter: {DATUM}, {ZEIT}, {WOCHE}, {MONAT}, {JAHR}, {WOCHENTAG}, {NAME}, {EMAIL}, {ZWISCHENABLAGE}"
                    else "Placeholders: {DATE}, {TIME}, {WEEK}, {MONTH}, {YEAR}, {WEEKDAY}, {NAME}, {EMAIL}, {CLIPBOARD}"
                )
            }
        }

        if (learnedWords.isNotEmpty() || rejectedWords.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Häufig gelernte Wörter" else "Top learned words")
                    AppleGroupedSurface {
                        learnedWords.forEachIndexed { idx, (word, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$word ($count×)", modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(
                                    onClick = {
                                        dictionaryStore.removeLearnedWord(word)
                                        revision++
                                        onDictionaryChanged()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = if (de) "$word entfernen" else "Remove $word",
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            if (idx < learnedWords.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                        }
                    }
                    if (rejectedWords.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        AppleSectionHeader(if (de) "Ignorierte Autokorrekturen" else "Ignored autocorrections")
                        AppleGroupedSurface {
                            rejectedWords.forEachIndexed { idx, word ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(word, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    TextButton(onClick = {
                                        dictionaryStore.unrejectCorrection(word)
                                        revision++
                                        onDictionaryChanged()
                                    }) {
                                        Text(if (de) "Freigeben" else "Allow", fontSize = 12.sp)
                                    }
                                }
                                if (idx < rejectedWords.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                            }
                        }
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                MinimalActionRow(
                    title = if (de) "Lerndaten leeren" else "Reset learned data",
                    textColor = MaterialTheme.colorScheme.error
                ) {
                    dictionaryStore.clearLearnedData()
                    revision++
                    onDictionaryChanged()
                    statusMessage = if (de) "Gelernte Daten gelöscht." else "Learned data cleared."
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalActionRow(
                    title = if (de) "Eigene Wörter leeren" else "Clear custom words",
                    textColor = MaterialTheme.colorScheme.error
                ) {
                    dictionaryStore.clearCustomWords()
                    revision++
                    onDictionaryChanged()
                    statusMessage = if (de) "Importierte Wörter gelöscht." else "Custom words cleared."
                }
            }
        }
    }
}
