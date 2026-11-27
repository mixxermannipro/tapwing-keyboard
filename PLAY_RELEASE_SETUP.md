# Google Play Release-Pipeline – Einrichtung

Mit dem Workflow `.github/workflows/play-store-release.yml` lässt sich ein neues signiertes Android App Bundle (`.aab`) direkt über GitHub Actions bauen und vollautomatisch im Google Play Store veröffentlichen.

## 1. Benötigte GitHub Repository Secrets

Hinterlege in beiden GitHub-Repositories (**Settings → Secrets and variables → Actions → New repository secret**) genau diese **5 Secrets**:

| Secret-Name | Inhalt & Format |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64-codierte Upload-Keystore-Datei (`.jks`, `.keystore` oder `.p12`), einzeilig ohne Zeilenumbrüche. |
| `SIGNING_KEY_ALIAS` | Alias des Upload-Schlüssels im Keystore (z. B. `opennews-upload` bzw. dein Tapwing-Alias). |
| `SIGNING_STORE_PASSWORD` | Passwort des Keystores. |
| `SIGNING_KEY_PASSWORD` | Passwort des konkreten Schlüssels (oft identisch mit `SIGNING_STORE_PASSWORD`). |
| `PLAY_SERVICE_ACCOUNT_JSON` | Vollständiger Inhalt der Google-Play-Service-Account-JSON-Datei. |

### Keystore in Base64 umwandeln (Linux / macOS)

```bash
base64 -w 0 /pfad/zum/upload-keystore.p12
```

Den ausgegebenen Text komplett in `SIGNING_KEYSTORE_BASE64` einfügen. Auf macOS ohne `-w 0` stattdessen `base64 -i /pfad/zum/upload-keystore.p12 | tr -d '\n'`.

> **Wichtig:** Prüfe vorher, dass der `versionCode` in `version.properties` nicht kleiner ist als der höchste jemals in der Google Play Console hochgeladene `versionCode`.

## 2. Google Play Service Account anlegen

1. Öffne die **Google Play Console** → **Einrichtung (Setup)** → **API-Zugriff (API access)** (bzw. verknüpfe ein Google-Cloud-Projekt).
2. Erstelle in der Google Cloud Console einen **Service Account** (Dienstkonto) und lade einen **JSON-Schlüssel** herunter.
3. Gewähre diesem Dienstkonto in der Google Play Console unter **Nutzer und Berechtigungen (Users and permissions)** mindestens die Rechte für:
   - App-Informationen ansehen
   - Releases in Produktions- und Test-Tracks verwalten (`Releases to production, exclude devices, and use Play App Signing` / `Release apps to testing tracks`).
4. Kopiere den gesamten Inhalt der heruntergeladenen JSON-Datei in das GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON`.

> **Hinweis zum ersten Release einer neuen App:** Google Play verlangt bei einer brandneuen App oft, dass das allererste `.aab` einmalig manuell in der Play Console hochgeladen wird, um den Paketnamen (`applicationId`) zu registrieren. Danach laufen alle weiteren Releases vollautomatisch über diese Pipeline.

## 3. One-Click-Launcher (`release-launcher.html`) nutzen

1. Erstelle auf GitHub unter **Settings → Developer settings → Personal access tokens → Fine-grained tokens** ein Token.
2. Wähle nur die betroffenen Repositories (`mixxermannipro/OpenNews`, `mixxermannipro/tapwing-keyboard`) aus.
3. Vergib ausschließlich die Berechtigung **Actions: Read and write** (Repository permissions).
4. Öffne `release-launcher.html` im Browser (Desktop oder Smartphone), speichere das Token lokal im Browser und starte Releases per Klick auf **„Build & Publish starten“**.

## 4. Lokale Open-Source-Builds

Wenn die Umgebungsvariablen `SIGNING_KEYSTORE_PATH`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD` und `PLAY_SERVICE_ACCOUNT_JSON_PATH` lokal **nicht** gesetzt sind, baut `./gradlew assembleDebug` (und `./gradlew bundleRelease`) weiterhin fehlerfrei ohne Release-Signierung und ohne Play-Upload.
