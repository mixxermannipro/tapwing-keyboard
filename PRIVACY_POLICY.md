# Privacy Policy — Tapwing Keyboard (`com.tapwing.keyboard`)

**Effective Date / Stand:** 2026-11-19

---

## English

Tapwing Keyboard ("Tapwing") is built around local, on-device processing. Tapwing does not run analytics SDKs, advertising trackers, or developer-operated telemetry servers.

### 1. What We Do Not Collect
- Tapwing **never** logs, uploads, sells, or shares what you type in any app.
- Passwords, sensitive fields, and typing performed while **Incognito Mode** is active are excluded from adaptive learning and clipboard history.
- Tapwing includes **zero** ads and **zero** third-party analytics libraries.

### 2. Local On-Device Storage
The following data is stored exclusively in app-private, device-protected local storage on your Android device:
- **Keyboard Preferences**: layout, scale, custom quick-action bar configuration, clipboard retention limits (`DataStore Preferences`).
- **Custom Dictionary & Adaptive Learning**: words, frequency weights, text shortcuts, and bigrams you create or import (for example from a `.zip`, `.txt`, `.tsv`, `.csv`, or `.json` file), plus rejected autocorrections (`tapwing_dictionary_state.json`). You can export or delete this data at any time in *Settings > Dictionary*.
- **Clipboard History**: copied text snippets up to your configured item limit and retention window (`1 hour` to `30 days` or `Never`). Can be disabled or cleared anytime.
- **Optional AI API Key**: if you configure a custom AI key, it is encrypted locally using hardware-backed `AndroidKeyStore` (`AES/GCM/NoPadding`).

### 3. Optional Device & Network Features (User-Initiated Only)
- **Voice Typing (`RECORD_AUDIO`)**: triggered only when you tap the microphone button. Audio handling is delegated directly to your device's Android `SpeechRecognizer` system service. Transcript cleanup runs locally on your device.
- **Camera OCR (`CAMERA`)**: triggered only when you open *Scan Text*. Text recognition runs 100% locally on your device using Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`) without sending images to external servers.
- **Image/Screenshot OCR (`PickVisualMedia`)**: uses the Android System Photo Picker so Tapwing reads only the single image you explicitly select—without requesting broad gallery or storage permissions.
- **Optional Bring-Your-Own-Key AI Writing Tools (`INTERNET`)**: Writing Tools work offline using built-in local rules (`LocalWritingAssistant`) by default. Network requests occur **only** if you explicitly select Gemini API or an OpenAI-compatible endpoint in Settings, enter your own API key, and tap a Writing Tools action on the selected text.

### 4. Data Deletion & User Control
You retain complete control over your local data:
- Clear learned vocabulary/bigrams or imported dictionaries in *Settings > Dictionary*.
- Clear clipboard items directly from the clipboard panel or reduce retention in *Settings > Clipboard & OCR*.
- Remove your stored API key in *Settings > AI Writing Tools*.
- Uninstalling Tapwing removes all locally stored app data.

### 5. Contact
For privacy questions regarding Tapwing Keyboard (`com.tapwing.keyboard`), please contact the repository/store publisher listed on the Google Play store page.

---

## Deutsch

**Tapwing Keyboard** („Tapwing“) ist konsequent für lokale Verarbeitung direkt auf deinem Gerät entwickelt. Tapwing enthält keine Werbung, keine Analyse-SDKs und keine Entwickler-Telemetrie.

### 1. Was Tapwing nicht sammelt
- Tastenanschläge und getippte Texte werden **niemals** an eigene Server übertragen, protokolliert, verkauft oder geteilt.
- Im **Inkognito-Modus** werden weder Wörter gelernt noch Einträge in der Zwischenablage gespeichert.

### 2. Lokale Speicherung auf dem Gerät
Alle folgenden Daten verbleiben ausschließlich lokal im privaten App-Speicher auf deinem Smartphone:
- **Einstellungen**: Tastaturgröße, Design, belegte Schnellleiste, Aufbewahrungsdauer.
- **Wörterbuch & adaptives Lernen**: selbst angelegte oder importierte Wörterbücher (`.zip`, `.txt`, `.tsv`/`.csv`, `.json`), Textkürzel und lokal gelernte Wortpaare. Diese Daten lassen sich in den Einstellungen jederzeit exportieren oder vollständig löschen.
- **Zwischenablage**: lokal gespeicherte Text-Clips gemäß deinem eingestellten Limit und Löschzeitraum.
- **Optionaler API-Schlüssel**: wird ausschließlich auf Wunsch mit dem `AndroidKeyStore` (`AES/GCM/NoPadding`) verschlüsselt auf dem Gerät gespeichert.

### 3. Optionale System- und Netzwerkfunktionen
- **Spracheingabe (`RECORD_AUDIO`)**: startet erst nach Tipp auf das Mikrofon und nutzt den systemeigenen Android-`SpeechRecognizer`. Die Textbereinigung erfolgt lokal.
- **Kamera-Texterkennung (`CAMERA`)**: öffnet sich nur bei „Text scannen“. Die Texterkennung läuft offline über ML Kit auf dem Gerät.
- **Foto-/Screenshot-OCR (`PickVisualMedia`)**: nutzt den Android-System-Fotoauswahldialog für genau ein ausgewähltes Bild – ohne pauschale Galerie- oder Speicherberechtigung.
- **Optionale Cloud-KI (`INTERNET`)**: Die Schreibhilfe funktioniert standardmäßig vollständig offline über lokale Regeln. Netzwerkanfragen finden nur statt, wenn du ausdrücklich einen Gemini- oder OpenAI-kompatiblen Endpunkt samt eigenem API-Key konfigurierst und eine KI-Aktion ausführst.

### 4. Löschung
Alle lokalen Lern-, Wörterbuch-, Zwischenablage- und Key-Daten können jederzeit direkt in den Tapwing-Einstellungen gelöscht oder durch Deinstallation der App restlos entfernt werden.
