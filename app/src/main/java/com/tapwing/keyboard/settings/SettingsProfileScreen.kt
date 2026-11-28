// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.tapwing.keyboard.R
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.SmartFieldType
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.ui.keyboard.title

@Composable
internal fun AutofillSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    hasAutofillProvider: Boolean,
    dictionaryStore: AdaptiveDictionaryStore,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val profile = prefs.personalProfile
    var firstName by remember(profile.firstName) { mutableStateOf(profile.firstName) }
    var lastName by remember(profile.lastName) { mutableStateOf(profile.lastName) }
    var birthDate by remember(profile.birthDate) { mutableStateOf(profile.birthDate) }
    var street by remember(profile.street) { mutableStateOf(profile.street) }
    var postalCode by remember(profile.postalCode) { mutableStateOf(profile.postalCode) }
    var city by remember(profile.city) { mutableStateOf(profile.city) }
    var country by remember(profile.country) { mutableStateOf(profile.country) }
    var company by remember(profile.company) { mutableStateOf(profile.company) }
    var website by remember(profile.website) { mutableStateOf(profile.website) }
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var addressExpanded by rememberSaveable {
        mutableStateOf(profile.street.isNotBlank() || profile.postalCode.isNotBlank() || profile.city.isNotBlank() || profile.company.isNotBlank())
    }
    var learnedRevision by remember { mutableIntStateOf(0) }
    val learnedValues = remember(learnedRevision) { dictionaryStore.listLearnedProfileValues() }
    var showAutoLearnOptInDialog by remember { mutableStateOf(false) }

    fun saveProfile(transform: (PersonalProfile) -> PersonalProfile) {
        onUpdatePrefs { current ->
            val updated = transform(current.personalProfile)
            current.copy(
                personalProfile = updated,
                snippetUserName = updated.fullName,
                snippetUserEmail = updated.primaryEmail
            )
        }
    }

    if (showAutoLearnOptInDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLearnOptInDialog = false },
            title = {
                Text(
                    text = if (de) "Automatische Profilerkennung aktivieren?" else "Enable automatic profile learning?",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = if (de) {
                        "Wenn aktiviert, lernt Tapwing erkannte Profilfelder (z. B. Name, E-Mail, Telefon, Adresse) lokal beim Tippen und schlägt sie in Formularen vor. Passwort-, Kreditkarten-, OTP- und Inkognito-Felder bleiben jederzeit ausgeschlossen. Du kannst gelernte Werte jederzeit hier einsehen oder löschen."
                    } else {
                        "When enabled, Tapwing detects personal profile fields (such as name, email, phone, and address) locally on-device while typing and offers them in matching forms. Password, card, OTP, and incognito fields are always excluded. You can inspect or clear learned entries here anytime."
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    showAutoLearnOptInDialog = false
                    onUpdatePrefs { it.copy(smartProfileAutoLearn = true, profileAutoLearnNoticeSeen = true) }
                }) {
                    Text(if (de) "Aktivieren" else "Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoLearnOptInDialog = false }) {
                    Text(if (de) "Abbrechen" else "Cancel")
                }
            }
        )
    }

    val liveAge = remember(birthDate) { PersonalProfile(birthDate = birthDate).computedAgeYears() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (!prefs.profileAutoLearnNoticeSeen && learnedValues.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Hinweis zu erkannten Profildaten" else "Learned Profile Data Notice")
                    AppleGroupedSurface {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (de) {
                                    "Es wurden bereits automatisch erkannte Profilwerte lokal gespeichert. In dieser Version ist die automatische Erkennung standardmäßig deaktiviert (Opt-in)."
                                } else {
                                    "Previously auto-detected profile values are stored locally on this device. Automatic profile learning now defaults to off (opt-in)."
                                },
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    dictionaryStore.clearAllLearnedProfileValues()
                                    learnedRevision++
                                    onUpdatePrefs { it.copy(profileAutoLearnNoticeSeen = true) }
                                }) {
                                    Text(if (de) "Alle löschen" else "Clear all")
                                }
                                TextButton(onClick = {
                                    onUpdatePrefs { it.copy(profileAutoLearnNoticeSeen = true) }
                                }) {
                                    Text(if (de) "Behalten & ausblenden" else "Keep & dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Smart-Fill & Dynamic Auto-Detection controls
        item {
            Column {
                AppleSectionHeader(if (de) "Smart-Fill & Erkennung" else "Smart Fill & Auto-Detect")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Persönliche Vorschläge in der Leiste" else "Personal suggestions in strip",
                        checked = prefs.smartFillEnabled
                    ) { enabled ->
                        onUpdatePrefs { it.copy(smartFillEnabled = enabled) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Dynamisch erkennen & aktualisieren (Opt-in)" else "Auto-detect & update (Opt-in)",
                        checked = prefs.smartProfileAutoLearn
                    ) { enabled ->
                        if (enabled) {
                            showAutoLearnOptInDialog = true
                        } else {
                            onUpdatePrefs { it.copy(smartProfileAutoLearn = false) }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Optional: Erkennt Name, E-Mail, Telefon, Adresse & Geburtsdatum lokal beim Tippen (z. B. „Meine Nummer ist …“) und merkt sich App-Zuordnungen auf dem Gerät. Passwort-, Zahlungs- und Inkognito-Felder sind immer ausgenommen."
                    else "Optional: Detects name, email, phone, address, and birthdate locally on-device while typing and remembers per-app preferences. Password, payment, and incognito fields are always excluded."
                )
            }
        }

        // 2. Core Personal Profile (Name, Birthday + live Age, Emails, Phones)
        item {
            Column {
                AppleSectionHeader(if (de) "Persönliches Profil" else "Personal Profile")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (de) "Vorname" else "First name",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppleInlineField(
                                    value = firstName,
                                    onValueChange = { v ->
                                        firstName = v
                                        saveProfile { it.copy(firstName = v) }
                                    },
                                    placeholder = if (de) "z. B. Max" else "e.g. Alex"
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (de) "Nachname" else "Last name",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppleInlineField(
                                    value = lastName,
                                    onValueChange = { v ->
                                        lastName = v
                                        saveProfile { it.copy(lastName = v) }
                                    },
                                    placeholder = if (de) "z. B. Mustermann" else "e.g. Miller"
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (de) "Geburtsdatum" else "Birthdate",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (liveAge != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (de) "Alter: $liveAge Jahre (auto)" else "Age: $liveAge yrs (auto)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            AppleInlineField(
                                value = birthDate,
                                onValueChange = { v ->
                                    birthDate = v
                                    saveProfile { it.copy(birthDate = v) }
                                },
                                placeholder = if (de) "TT.MM.JJJJ (z. B. 14.05.1995)" else "YYYY-MM-DD (e.g. 1995-05-14)"
                            )
                        }

                        AppleHairlineDivider(startIndent = 0.dp)

                        // Multiple Email Addresses
                        Text(
                            text = if (de) "E-Mail-Adressen" else "Email addresses",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        profile.emails.forEachIndexed { idx, email ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = email,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = {
                                        saveProfile { p -> p.copy(emails = p.emails.filterIndexed { i, _ -> i != idx }) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppleInlineField(
                                    value = newEmail,
                                    onValueChange = { newEmail = it },
                                    placeholder = if (de) "name@beispiel.de hinzufügen" else "Add name@example.com"
                                )
                            }
                            FilledTonalButton(
                                enabled = newEmail.trim().contains("@"),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = {
                                    val clean = newEmail.trim()
                                    if (clean.contains("@")) {
                                        saveProfile { p -> p.copy(emails = (p.emails + clean).distinctBy { it.lowercase() }) }
                                        newEmail = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        AppleHairlineDivider(startIndent = 0.dp)

                        // Multiple Phone Numbers
                        Text(
                            text = if (de) "Telefonnummern" else "Phone numbers",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        profile.phones.forEachIndexed { idx, phone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = phone,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = {
                                        saveProfile { p -> p.copy(phones = p.phones.filterIndexed { i, _ -> i != idx }) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppleInlineField(
                                    value = newPhone,
                                    onValueChange = { newPhone = it },
                                    placeholder = if (de) "+49 170 1234567 hinzufügen" else "Add +1 555 123 4567"
                                )
                            }
                            FilledTonalButton(
                                enabled = newPhone.count(Char::isDigit) >= 6,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = {
                                    val clean = newPhone.trim()
                                    if (clean.count(Char::isDigit) >= 6) {
                                        saveProfile { p -> p.copy(phones = (p.phones + clean).distinct()) }
                                        newPhone = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Collapsible Address, Company & Website section (keeps screen sleek & uncluttered)
        item {
            AppleGroupedSurface {
                SettingsDisclosure(
                    title = if (de) "Adresse, Firma & Website" else "Address, company & website",
                    expanded = addressExpanded
                ) {
                    addressExpanded = !addressExpanded
                }
                AnimatedVisibility(
                    visible = addressExpanded,
                    enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppleHairlineDivider(startIndent = 0.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (de) "Straße & Hausnummer" else "Street address", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AppleInlineField(
                                value = street,
                                onValueChange = { v -> street = v; saveProfile { it.copy(street = v) } },
                                placeholder = if (de) "Musterstraße 12" else "123 Main St"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (de) "PLZ" else "Postal code", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppleInlineField(
                                    value = postalCode,
                                    onValueChange = { v -> postalCode = v; saveProfile { it.copy(postalCode = v) } },
                                    placeholder = if (de) "10115" else "94105"
                                )
                            }
                            Column(modifier = Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (de) "Stadt" else "City", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppleInlineField(
                                    value = city,
                                    onValueChange = { v -> city = v; saveProfile { it.copy(city = v) } },
                                    placeholder = if (de) "Berlin" else "San Francisco"
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (de) "Land" else "Country", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppleInlineField(
                                    value = country,
                                    onValueChange = { v -> country = v; saveProfile { it.copy(country = v) } },
                                    placeholder = if (de) "Deutschland" else "Germany"
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (de) "Firma" else "Company", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppleInlineField(
                                    value = company,
                                    onValueChange = { v -> company = v; saveProfile { it.copy(company = v) } },
                                    placeholder = if (de) "Optional" else "Optional"
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (de) "Website" else "Website", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AppleInlineField(
                                value = website,
                                onValueChange = { v -> website = v; saveProfile { it.copy(website = v) } },
                                placeholder = "https://..."
                            )
                        }
                    }
                }
            }
        }

        // 4. Dynamically auto-detected values (1-tap promote into profile or delete)
        if (learnedValues.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Automatisch erkannt" else "Auto-detected")
                    AppleGroupedSurface {
                        val entries = learnedValues.entries.flatMap { (type, vals) -> vals.map { type to it } }.take(12)
                        entries.forEachIndexed { index, (fieldType, value) ->
                            if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                            val typeLabel = when (fieldType) {
                                SmartFieldType.FIRST_NAME -> if (de) "Vorname" else "First name"
                                SmartFieldType.LAST_NAME -> if (de) "Nachname" else "Last name"
                                SmartFieldType.FULL_NAME -> if (de) "Name" else "Name"
                                SmartFieldType.EMAIL -> "E-Mail"
                                SmartFieldType.PHONE -> if (de) "Telefon" else "Phone"
                                SmartFieldType.STREET_ADDRESS -> if (de) "Adresse" else "Address"
                                SmartFieldType.POSTAL_CODE -> if (de) "PLZ" else "ZIP"
                                SmartFieldType.CITY -> if (de) "Stadt" else "City"
                                SmartFieldType.BIRTHDATE -> if (de) "Geburtsdatum" else "Birthdate"
                                SmartFieldType.COMPANY -> if (de) "Firma" else "Company"
                                else -> fieldType.name
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(typeLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        saveProfile { p ->
                                            when (fieldType) {
                                                SmartFieldType.EMAIL -> p.copy(emails = (p.emails + value).distinctBy { it.lowercase() })
                                                SmartFieldType.PHONE -> p.copy(phones = (p.phones + value).distinct())
                                                SmartFieldType.FIRST_NAME -> p.copy(firstName = value)
                                                SmartFieldType.LAST_NAME -> p.copy(lastName = value)
                                                SmartFieldType.FULL_NAME -> {
                                                    val parts = value.split(' ').filter { it.isNotBlank() }
                                                    if (parts.size >= 2) p.copy(firstName = parts.first(), lastName = parts.drop(1).joinToString(" "))
                                                    else p.copy(firstName = value)
                                                }
                                                SmartFieldType.STREET_ADDRESS -> p.copy(street = value)
                                                SmartFieldType.POSTAL_CODE -> p.copy(postalCode = value)
                                                SmartFieldType.CITY -> p.copy(city = value)
                                                SmartFieldType.BIRTHDATE -> p.copy(birthDate = value)
                                                SmartFieldType.COMPANY -> p.copy(company = value)
                                                else -> p
                                            }
                                        }
                                        dictionaryStore.removeLearnedProfileValue(fieldType, value)
                                        learnedRevision++
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(if (de) "Übernehmen" else "Save", fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        dictionaryStore.removeLearnedProfileValue(fieldType, value)
                                        learnedRevision++
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            title = if (de) "Alle gelernten Profilwerte löschen" else "Clear all learned profile values",
                            textColor = MaterialTheme.colorScheme.error
                        ) {
                            dictionaryStore.clearAllLearnedProfileValues()
                            learnedRevision++
                        }
                    }
                    AppleSectionFooter(
                        if (de) "Beim Tippen erkannt. Übernehme Einträge dauerhaft ins Profil oder entferne sie mit einem Tipp."
                        else "Detected while typing. Save entries into your profile or dismiss with one tap."
                    )
                }
            }
        }

        // 5. Android Password Manager & Inline Autofill
        item {
            Column {
                AppleSectionHeader(stringResource(
                    if (hasAutofillProvider) R.string.autofill_active else R.string.autofill_inactive
                ))
                AppleGroupedSurface {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        MinimalToggleRow(stringResource(R.string.autofill_inline_toggle), prefs.inlineAutofillEnabled) { enabled ->
                            onUpdatePrefs { it.copy(inlineAutofillEnabled = enabled) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                    }
                    MinimalActionRow(stringResource(R.string.autofill_android_settings), MaterialTheme.colorScheme.primary) {
                        val settingsIntents = listOf(
                            Intent("android.settings.CREDENTIAL_PROVIDER"),
                            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}")),
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        )
                        settingsIntents.any { runCatching { context.startActivity(it) }.isSuccess }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(stringResource(R.string.autofill_bitwarden_help), MaterialTheme.colorScheme.primary) {
                        context.openSettingsLink("https://bitwarden.com/help/auto-fill-android/")
                    }
                }
                AppleSectionFooter(stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.autofill_help else R.string.autofill_requires_android_11
                ))
            }
        }
    }
}
