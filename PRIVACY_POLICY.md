# Privacy Policy — Tapwing Keyboard (`com.tapwing.keyboard`)

**Effective Date / Stand:** `2026-11-26` (Version `2.9.0`, `versionCode = 17`)
**Hosted Privacy Policy URL:** `https://tapwing-keyboard.mixxermannipropvp.workers.dev/privacy`
**Contact:** `mixxermannipropvp@gmail.com`

---

## English

### 1. Core Principle: On-Device First
Tapwing is designed so core keyboard functionality runs **100% locally on your device**:
- No advertisements, no behavioral analytics SDKs, no crash-reporting trackers, and no background keylogging uploads.
- Local typing, layout predictions, autocorrect, offline frequency dictionaries, camera OCR, and local rule-based writing assistance stay strictly on your device.

### 2. Storage Separation (Direct Boot DE vs. Credential-Encrypted CE Storage)
To protect sensitive user data when a device is locked after reboot (`Direct Boot`):
- **Device-Encrypted (DE) storage**: stores only non-sensitive keyboard configuration (active layout, height, theme preferences, feature toggles, consented provider IDs) so the keyboard can appear cleanly before first unlock.
- **Credential-Encrypted (CE) storage (accessible only after user unlock)**: stores all personal and behavioral user data:
  - `PersonalProfileStorage`: personal profile fields encrypted via `AndroidKeyStore` AES-256-GCM (`name`, `birthDate`, `email`, `phone`, address, company, website)
  - `SecureKeyStorage`: optional user API keys encrypted via hardware-backed `AndroidKeyStore` AES-256-GCM
  - `ClipboardStore`, `AdaptiveDictionaryStore`, and `TypingStatsStore`: clipboard history, learned vocabulary/bigrams/snippets, and local typing counters (automatically migrated from legacy DE paths with zero-fill deletion of legacy files).

### 3. Sensitive Editors, OTP Protection & Opt-In Learning
- **Sensitive field guard (`SensitiveEditorGuard`)**: Tapwing automatically pauses profile learning, clipboard persistence, and typing stats in password fields, credit card/CVV fields, OTP/TAN/PIN inputs, and any editor requesting `IME_FLAG_NO_PERSONALIZED_LEARNING`.
- **OTP clipboard filter**: Detected 4–8 digit verification/OTP codes copied to the system clipboard are surfaced only ephemerally or excluded from persistent clipboard history.
- **Incognito mode**: Pauses all adaptive learning, typing stats, and clipboard recording.
- **Smart profile learning is Opt-In**: `smartProfileAutoLearn` defaults to **Off (`false`)** and requires explicit user confirmation before learning profile attributes locally. Previously learned entries can be inspected individually or cleared completely in **Settings -> Autofill & Profile**.

### 4. Permissions & Zero Storage Access
Tapwing declares only minimal permissions in `AndroidManifest.xml`:
- **`RECORD_AUDIO`**: Only requested when you activate voice dictation or run mic diagnostics.
- **`CAMERA`**: Only requested when you open live camera OCR (`ScanTextActivity`); text recognition runs on-device via ML Kit.
- **`VIBRATE`**: Used locally for optional keypress haptics.
- **`INTERNET`**: Used strictly for user-initiated downloads (SHA-256 & exact byte-size verified on-device speech models / offline dictionaries) or explicitly consented optional cloud AI requests.
- **Zero Photo/Gallery Permissions**: Tapwing **does not** declare `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`. Single-image OCR uses Android's System Photo Picker (`PickVisualMedia`).

### 5. Optional Cloud AI & Cloud Dictation (Explicit Per-Provider Consent)
- All cloud features (`CLOUD` dictation, `GEMINI_LIVE`, and cloud AI writing providers) are **disabled unless explicitly configured and consented** by you per provider (`consentedCloudProviders`).
- Before enabling or testing a cloud provider, Tapwing displays an in-app consent dialog linking to that provider's privacy policy. You can revoke any or all cloud consents anytime in **Settings -> Privacy & Cloud Consent**.
- Custom self-hosted endpoints disallow cleartext HTTP except strictly local loopback addresses (`localhost`, `127.0.0.1`, `::1`); all remote endpoints require HTTPS.

### 6. Deletion & Contact
All local profile data, learned words, clipboard entries, stats, downloaded models, and API keys can be deleted individually inside Tapwing Settings or removed completely by uninstalling the app.
For privacy questions: `mixxermannipropvp@gmail.com`

---

## Deutsch

### 1. Grundprinzip: Verarbeitung lokal auf dem Gerät
Tapwing verarbeitet sämtliche Kernfunktionen **100 % lokal auf deinem Gerät**:
- Keine Werbung, keine Analyse-SDKs, kein Hintergrund-Upload von Eingaben.
- Tippen, Wortvorschläge, Autokorrektur, Offline-Wörterbücher, Kamera-OCR sowie regelbasierte Schreibhilfe und lokale KI-Modelle arbeiten vollständig offline.

### 2. Strikte Speichertrennung (Direct-Boot DE vs. Credential-Encrypted CE)
- **Device-Encrypted (DE) Speicher**: enthält ausschließlich unkritische Tastatur-Einstellungen (Sprachlayout, Höhe, Design, Schalter), damit die Tastatur nach einem Neustart sofort funktioniert.
- **Credential-Encrypted (CE) Speicher (erst nach Geräte-Entsperrung lesbar)**: enthält alle schützenswerten Daten:
  - `PersonalProfileStorage`: persönliche Profildaten (AES-256-GCM mit `AndroidKeyStore`)
  - `SecureKeyStorage`: optionale eigene API-Schlüssel (Hardware-gestützter `AndroidKeyStore` AES-256-GCM)
  - `ClipboardStore`, `AdaptiveDictionaryStore`, `TypingStatsStore`: Zwischenablage, gelernte Wörter/Kürzel und lokale Tippstatistiken. Eventuelle Altdaten aus DE-Pfaden werden beim Start einmalig sicher in CE-Storage verschoben und überschrieben gelöscht.

### 3. Sensible Felder, OTP-Schutz & Opt-in-Lernen
- **Sensible Eingabefelder (`SensitiveEditorGuard`)**: Passwort-, Kreditkarten-, OTP-/TAN-/PIN-Felder sowie Felder mit `IME_FLAG_NO_PERSONALIZED_LEARNING` deaktivieren automatisch Profilerkennung, Tippstatistik und Zwischenablage-Speicherung.
- **Inkognito-Modus**: Pausiert Lernen, Statistik und Verlauf jederzeit manuell.
- **Opt-in für Smart-Fill-Lernen**: Automatische Profilerkennung ist standardmäßig **ausgeschaltet** und erfordert eine ausdrückliche Bestätigung. Gelernte Werte lassen sich jederzeit einsehen und restlos löschen.

### 4. Berechtigungen (Kein Medien-/Speicherzugriff)
- **`RECORD_AUDIO`**: nur nach Aktivierung von Spracheingabe/Mikrofon.
- **`CAMERA`**: nur für die Live-Kamera-Texterkennung (`ScanTextActivity`, lokal über ML Kit).
- **`VIBRATE`**: optionales Tasten-Feedback.
- **`INTERNET`**: ausschließlich für vom Nutzer ausgelöste, per **SHA-256 und Bytegröße verifizierte** Modell-/Wörterbuch-Downloads sowie explizit freigegebene Cloud-Anfragen.
- **`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` wurden vollständig entfernt.** Foto-OCR liest ausschließlich einzelne ausgewählte Bilder über die Android-Systemauswahl (`PickVisualMedia`).

### 5. Optionale Cloud-KI & Cloud-Diktat (Explizite Einwilligung je Anbieter)
- Cloud-Funktionen übertragen Text oder Audio **erst nach ausdrücklicher Opt-in-Zustimmung je Anbieter** und aktivem Abruf (inkl. sichtbarem Cloud-Badge in der Leiste).
- Unter **Einstellungen -> Datenschutz & Cloud-Einwilligung** kann jede Freigabe einzeln oder gesammelt sofort widerrufen werden.
- Klartext-HTTP ist ausschließlich für lokale Loopback-Adressen (`localhost`, `127.0.0.1`, `::1`) erlaubt; externe Endpunkte erfordern HTTPS.

### 6. Löschung & Kontakt
Alle gespeicherten Daten können direkt in den Tapwing-Einstellungen oder durch Deinstallation der App vollständig gelöscht werden.
Datenschutz-Kontakt: `mixxermannipropvp@gmail.com` • Online unter: `https://tapwing-keyboard.mixxermannipropvp.workers.dev/privacy`
