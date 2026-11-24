<div align="center">

<img src="docs/screenshots/tapwing-header.jpg" width="900" alt="Tapwing Keyboard mit der lavendelfarbenen Tastatur und Wortvorschlägen" />

# Tippen, diktieren und Text bearbeiten

Tapwing verbindet Wortvorschläge, Spracheingabe, Schreibhilfe und eine anpassbare Werkzeugleiste. Schreiben, Lernen und viele Werkzeuge laufen direkt auf deinem Gerät.

![Version 2.6.0](https://img.shields.io/badge/Version-2.6.0-6750A4?style=flat-square)
![Android 10+](https://img.shields.io/badge/Android-10%2B-307D5D?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7451BD?style=flat-square&logo=kotlin&logoColor=white)
![Apache 2.0](https://img.shields.io/badge/Lizenz-Apache%202.0-405773?style=flat-square)

[Google Play · Geschlossener Test](https://play.google.com/apps/testing/com.tapwing.keyboard) · [Screenshots](#screenshots) · [Funktionen](#funktionen) · [Datenschutz](#verarbeitung-und-datenschutz) · [Build](#lokal-bauen) · [English](docs/README.en.md)

</div>

> [!NOTE]
> Tapwing 2.6.0 ist in Vorbereitung; der geschlossene Google-Play-Test setzt weiterhin einen freigeschalteten Testerzugang voraus.

## Screenshots

Aufnahmen der Oberfläche (Stand 2.2.0 auf einem Android-16-Emulator mit Beispieldaten).

| Tippen | Diktat | Werkzeuge |
| :---: | :---: | :---: |
| <img src="docs/screenshots/tippen.jpg" width="230" alt="Deutsches QWERTZ-Layout mit Vorschlägen nach dem Wort Ich" /> | <img src="docs/screenshots/diktat.jpg" width="230" alt="Diktat-Einstellungen mit Android offline, Whisper und Gemini Live" /> | <img src="docs/screenshots/werkzeuge.jpg" width="230" alt="Geöffnete Werkzeugübersicht mit Schreibhilfe, Scannen und Text-Edit" /> |

| Design | Zwischenablage | Schreibhilfe |
| :---: | :---: | :---: |
| <img src="docs/screenshots/themen.jpg" width="230" alt="Design-Einstellungen mit Höhenregler und ausgewähltem Lavendel-Farbschema" /> | <img src="docs/screenshots/zwischenablage.jpg" width="230" alt="Dunkle Tastatur mit einem Einfügevorschlag für einen kopierten Beispielsatz" /> | <img src="docs/screenshots/schreibhilfe.jpg" width="230" alt="Einstellungen für lokale und optionale Cloud-Schreibhilfe" /> |

## Funktionen

| Bereich | Was du damit tun kannst |
| --- | --- |
| **Tippen** | Zwischen Sprache und Layout wechseln, per Wischgeste tippen, Folgewörter übernehmen und Autokorrekturen zurücknehmen. Tapwing lernt Wortpaare auf dem Gerät und kann Vorschläge pro App anpassen. |
| **Diktat** | Blitzschnell lokal auf dem Gerät mit **Sherpa-ONNX** (FastConformer Deutsch, Kroko Live-Streaming mit Wort-für-Wort-Vorschau, Parakeet TDT v3 Ultra für 25 EU-Sprachen, Canary 180M Flash) oder **Whisper** diktieren – inklusive automatischer Verstärkung leiser Aufnahmen, Vorwärmung beim Tastaturstart und intelligenter Bereinigung von Füllwörtern, Stottern und Selbstkorrekturen. Alternativ über den Android-Sprachdienst oder optionale Cloud-APIs. |
| **Schreibhilfe** | Text korrigieren, kürzen und umformulieren. Die Optionen umfassen eingebaute Regeln, herunterladbare lokale Sprachmodelle und Cloud-Dienste mit eigenem Schlüssel. |
| **Zwischenablage** | Kopierte Texte direkt einfügen, häufig genutzte Einträge anheften und die Aufbewahrung einstellen. Kopierte Bestätigungscodes können kurz zum Einfügen angeboten werden. |
| **Text scannen** | Text mit der Kamera oder aus einem einzeln ausgewählten Foto erkennen und ins Schreibfeld übernehmen. |
| **Wörterbuch und Textbausteine** | Offline-Wörterbücher herunterladen, eigene Wörter importieren und exportieren sowie Kürzel wie `/datum` oder eigene Textbausteine einsetzen. |
| **Oberfläche & Schnellleiste** | Minimalistische Material-3-Expressive-Einstellungen im vertrauten Gboard-Aufbau mit federnden, flüssigen Übergängen. Höhe, Design und Schnellaktionen anpassen; Einhand- und geteiltes Layout sowie das Steuerkreuz stehen zur Auswahl. |
| **Weitere Werkzeuge** | Rechnungen wie `24*7=` im Eingabefeld lösen, Tippstatistiken ansehen und Android-Autofill (automatisches Ausfüllen durch den gewählten Dienst) ab Android 11 nutzen. |

### Neu in 2.6.0

- **Sherpa-ONNX-Engine auf dem Gerät (v2.0.3):** Deutlich schnellere lokale Spracherkennung mit kuratierter Modellauswahl und Empfehlungen direkt in den Einstellungen (inklusive Echtzeit-Live-Streaming über Kroko Zipformer, FastConformer Deutsch mit eingebauter Zeichensetzung, Parakeet 0.6B Ultra und Canary 180M).
- **Bereinigung auf Rambler-Niveau:** Verbesserte Erkennung von Stottern (`g- gehe`), Satz-Neustarts (`vergiss das`), deutschen/englischen Präpositions-Selbstkorrekturen, diktierten nummerierten Listen (`erstens / zweitens`), Nebensatz-Kommas und Sprachbefehlen (`Senden`).
- **Gboard-nahe, butterweiche Oberfläche:** Schlanke, übersichtliche Einstellungen mit Suchleiste, federnden Druck- und Panel-Animationen sowie live scrollender Diktat-Leiste.

## Erste Schritte

1. Tapwing über den [geschlossenen Google-Play-Test](https://play.google.com/apps/testing/com.tapwing.keyboard) installieren oder [lokal bauen](#lokal-bauen).
2. Tapwing öffnen und über **Aktivieren** in den Android-Tastatureinstellungen einschalten.
3. Über **Auswählen** zu Tapwing wechseln und im Testfeld tippen.
4. Für schnelles lokales Diktat unter **Diktat** das empfohlene Sherpa-ONNX-Modell (z. B. FastConformer Deutsch oder Kroko Live) herunterladen – danach diktiert Tapwing komplett offline.

## Verarbeitung und Datenschutz

| Auswahl | Was mit den Daten passiert |
| --- | --- |
| **Tippen, Wörterbuch, Zwischenablage und Texterkennung** | Die Verarbeitung findet auf dem Gerät statt. Der Inkognito-Modus setzt Lernen und Zwischenablage-Verlauf aus. |
| **Sherpa-ONNX, Whisper und Android offline** | Audio wird vollständig auf dem Gerät verarbeitet. Herunterladbare Modelle und Android-Sprachpakete liegen lokal vor. |
| **Android-Sprachdienst** | Abhängig von Gerät und Sprachdienst kann die Erkennung eine Internetverbindung nutzen. |
| **Gemini Live** | Wenn du den Modus mit eigenem Schlüssel einrichtest, sendet Tapwing Audio während des Diktats an Google. |
| **Cloud-Diktat und Cloud-Schreibhilfe** | Beim separaten Cloud-Diktat geht die Aufnahme nach dem Stopp an den gewählten Anbieter. Bei optionaler Cloud-Textbearbeitung wird der zu bearbeitende Text übertragen. |

Tapwing enthält keine Werbung oder Analyse-SDKs. Die [Datenschutzerklärung](PRIVACY_POLICY.md) erklärt Berechtigungen, Speicherorte und Löschmöglichkeiten.

## Lokal bauen

Voraussetzungen sind **JDK 21**, **Android SDK 36** und **Build-Tools 36.0.0**. Der Gradle-Wrapper liegt im Projekt. Android Studio kann den SDK-Pfad in `local.properties` eintragen. Alternativ setzt du dort `sdk.dir=/pfad/zum/Android/sdk`.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Die installierbare Debug-Datei liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Für signierte APK- und AAB-Dateien beschreibt der [Play-Store-Leitfaden](PLAY_STORE_GUIDE.md) die Einrichtung. Das Skript `./scripts/build-release.sh` schreibt seine Ausgaben nach `dist/`.

## Projektstruktur und Dokumentation

| Pfad | Inhalt |
| --- | --- |
| [`app/src/main/`](app/src/main/) | Android-Tastatur, Compose-Oberfläche, Diktat, Wörterbuch, Schreibhilfe und Texterkennung |
| [`app/src/test/`](app/src/test/) | Tests für Sprachverarbeitung, Vorschläge, Wischgesten, Zwischenablage und weitere Kernfunktionen |
| [`docs/play-store/`](docs/play-store/) | Store-Texte und Hinweise für die Play Console |
| [`ARCHITECTURE_ANALYSIS.md`](ARCHITECTURE_ANALYSIS.md) | Technischer Hintergrund zu Tastatur, KI und Android-Schnittstellen |

Fehler und Vorschläge kannst du auf [GitHub Issues](https://github.com/mixxermannipro/tapwing-keyboard/issues) melden. Tapwing steht unter der [Apache-Lizenz 2.0](LICENSE).
