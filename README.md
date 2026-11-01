# Visitenkarte

Android-App, die deine **geschäftliche** und **private** Visitenkarte anzeigt und teilt –
und private Dokumente (z. B. Gesundheitsinfos, Patientenverfügung) immer offline dabei hat.
Alle Inhalte kommen von deinem eigenen Webserver (HTTPS + Basic Auth) und werden lokal gespeichert.

## Funktionen

| Bereich | Was passiert |
|---|---|
| **Karten** | Visitenkarten im Kartenlook (Farbe, Foto, Logo), Wischen zwischen *Geschäftlich*/*Privat*. Telefon, Mail, Web und Adresse sind antippbar. |
| **QR-Code** | vCard als QR-Code im Vollbild, Helligkeit wird automatisch auf Maximum gesetzt. Fotos werden für den QR-Code weggelassen. |
| **NFC – Handy an Handy** | Die App emuliert per Host Card Emulation ein NFC-Forum-Type-4-Tag mit der vCard. Das andere Handy (Android oder iPhone) liest es wie einen NFC-Sticker. Aktiv nur, solange der NFC-Bildschirm offen und das Handy entsperrt ist. |
| **NFC – Tag beschreiben** | Schreibt die vCard auf einen NFC-Sticker/-Karte (z. B. NTAG215/216). |
| **Teilen** | Normaler Android-Teilen-Dialog mit `.vcf`-Datei (Messenger, Mail, …). |
| **Quick Share** | Öffnet Quick Share (Google bzw. Samsung) direkt; falls nicht vorhanden, den Teilen-Dialog. |
| **Dokumente** | PDF (eingebauter Viewer mit Zoom), HTML (offline, ohne JavaScript/Netz), Bilder, Text. Teilen und „Extern öffnen“ möglich. |
| **Server** | URL, Benutzer, Passwort. Passwort liegt verschlüsselt im Android Keystore. Abgleich beim Start (wenn > 1 h her), alle 12 h im Hintergrund und per Pull-to-Refresh. |

**Offline:** Ein Abgleich ersetzt den lokalen Stand nur, wenn *alle* Dateien erfolgreich geladen wurden.
Ist der Server nicht erreichbar, bleibt der letzte Stand vollständig erhalten. Unveränderte Dateien werden
per `If-Modified-Since` nicht erneut übertragen. Die App-Daten sind von Android-/Cloud-Backups ausgenommen.

## Inhalte auf dem Server

Lege einen Ordner auf deinem Webserver an, z. B. `https://example.de/visitenkarte/`, mit einer
`manifest.json` und den Dateien. Ein vollständiges Beispiel liegt in [`server-beispiel/`](server-beispiel/).

```json
{
  "cards": [
    { "id": "business", "title": "Geschäftlich", "file": "geschaeftlich.vcf",
      "color": "#1E3A5F", "photo": "foto.jpg", "logo": "logo.png" },
    { "id": "private", "title": "Privat", "file": "privat.vcf", "color": "#2E7D32" }
  ],
  "documents": [
    { "id": "health", "title": "Gesundheitsinfos", "description": "Allergien, Medikamente",
      "file": "dokumente/gesundheit.html", "icon": "health", "assets": ["dokumente/style.css"] },
    { "id": "pv", "title": "Patientenverfügung", "file": "dokumente/patientenverfuegung.pdf", "icon": "legal" }
  ]
}
```

* `file`, `photo`, `logo`, `assets`: Pfade relativ zur `manifest.json`.
* `color`: Kartenfarbe (`#RRGGBB`), `photo`/`logo`: optional (alternativ `PHOTO` direkt in der vCard).
* `type`: optionaler MIME-Typ, sonst anhand der Endung (`pdf`, `html`, `jpg`, `png`, `txt`, …).
* `icon`: `health`, `notfall`, `legal` – sonst Symbol nach Dateityp.
* `assets`: zusätzliche Dateien für HTML-Dokumente (CSS, Bilder), damit sie offline funktionieren.
* Die `.vcf`-Dateien sind ganz normale vCards (3.0 oder 4.0, UTF-8) – z. B. aus deinem Adressbuch exportiert.

In der App unter **Server** die Ordner-URL (oder direkt die URL zur JSON-Datei), Benutzer und Passwort
eintragen → *Verbindung testen* → *Speichern & laden*.

### nginx

```nginx
location /visitenkarte/ {
    auth_basic           "Visitenkarte";
    auth_basic_user_file /etc/nginx/.htpasswd-visitenkarte;
    alias                /srv/visitenkarte/;
    add_header           Cache-Control "no-cache";
}
```

```sh
htpasswd -c /etc/nginx/.htpasswd-visitenkarte soenke
```

### Apache (`.htaccess` im Ordner)

```apache
AuthType Basic
AuthName "Visitenkarte"
AuthUserFile /pfad/zu/.htpasswd
Require valid-user
```

**Sicherheit:** Basic Auth nur über **HTTPS** verwenden. Die App lehnt unverschlüsseltes HTTP ab, außer
für Adressen im Heimnetz (`192.168.x.x`, `10.x.x.x`, `*.local`, `*.fritz.box`, …). Weiterleitungen auf
andere Hosts oder von HTTPS auf HTTP werden blockiert, damit das Passwort nicht abfließt. Eigene
Zertifikate (selbst signierte CA) funktionieren, wenn die CA in Android als Nutzerzertifikat installiert ist.

## Bauen & installieren

* **Obtainium (empfohlen):** In Obtainium *App hinzufügen* → `https://github.com/doenke/visitenkarte`.
  Obtainium findet die APK in den GitHub-Releases und meldet neue Versionen automatisch.
* **Direkt:** APK unter [Releases](https://github.com/doenke/visitenkarte/releases) herunterladen.
* **Testversion je Push:** *Actions → Build → Artifacts* „visitenkarte-debug-apk“ (nicht updatefähig, andere Signatur).
* **Lokal:** Android Studio öffnen, oder `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
* Tests: `./gradlew testDebugUnitTest`

### Releases erstellen

Ein Release entsteht durch einen Versions-Tag:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

oder in GitHub unter *Actions → Release → Run workflow* mit Versionsnummer (`X.Y.Z`). Der Workflow baut
die signierte Release-APK und hängt sie als `visitenkarte-X.Y.Z.apk` an das GitHub-Release.

Einmalig nötig sind diese Repository-Secrets (*Settings → Secrets and variables → Actions*):
`SIGNING_KEYSTORE_BASE64` (Keystore, base64-kodiert), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. Eigener Schlüssel:

```sh
keytool -genkeypair -storetype PKCS12 -keystore release.jks -alias visitenkarte \
        -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.jks   # Ausgabe als SIGNING_KEYSTORE_BASE64
```

Immer denselben Schlüssel verwenden – sonst lassen sich Updates nicht installieren.

Voraussetzungen: Android 8.0+ (API 26). NFC-Emulation braucht ein Gerät mit HCE (praktisch alle NFC-Handys).

## Hinweise zu NFC

* Android Beam gibt es seit Android 10 nicht mehr. Die App gibt sich deshalb selbst als NFC-Tag aus.
* Beim Empfänger muss NFC an und das Display entsperrt sein. Android öffnet dann den Kontakt-Import;
  iPhones (ab XS) lesen NFC-Tags im Hintergrund.
* Ab Android 15 schaltet die App während des Teilens das eigene Tag-Lesen ab – das verhindert, dass sich
  zwei Handys gegenseitig „lesen wollen“. Auf älteren Geräten ggf. kurz versetzt aneinanderhalten.

## Projektstruktur

```
app/src/main/java/de/soenkejacobs/visitenkarte/
├── data/   vCard-Parser, Manifest, Server-Einstellungen (Keystore), Sync/Offline-Speicher, QR
├── nfc/    Type-4-Tag-Emulation (HCE) und NDEF-Erzeugung
├── share/  Teilen-Dialog, Quick Share, FileProvider-Export
├── sync/   Hintergrund-Abgleich (WorkManager)
└── ui/     Compose-Oberfläche (Karten, QR, NFC, Dokumente, Server)
server-beispiel/   Beispielinhalte für den Webserver
```
