# Batman TBB Player

App Android che legge il file JSON `batman-the-brave-and-the-bold.json` (serie TV con stagioni,
episodi e link di streaming) e ne riproduce gli episodi **bloccando la pubblicità**.

## Scarica la APK

- Release firmata: **[app-release.apk](https://github.com/SancioPanza88/BatmanTBBPlayer/releases/download/v1.1.0/app-release.apk)**
- Versione debug: **[app-debug.apk](https://github.com/SancioPanza88/BatmanTBBPlayer/releases/download/v1.1.0/app-debug.apk)**
- Repository: https://github.com/SancioPanza88/BatmanTBBPlayer

Ogni push su `main` rilancia la build; ogni tag `v*` pubblica una nuova Release con gli APK.

## Funzionalità

- **Lettura del JSON**
  - file incluso negli asset dell'app: `app/src/main/assets/batman-the-brave-and-the-bold.json`
  - possibilità di aprire qualsiasi altro JSON compatibile dal dispositivo (pulsante **Apri JSON**)
- **Catalogo**: intestazione con i metadati della serie, stagioni comprimibili, ricerca episodi
- **Due sorgenti per episodio** (uqload.vc / chuckle-tube.com) selezionabili con tap lungo
- **Riproduzione "ad-free"** (come le app Streamflix / Just-Ad-Free):
  1. **Estrazione server** (predefinita): la pagina del provider viene analizzata dall'app,
     si ricava l'URL reale del video (`.m3u8` / `.mp4`) e la riproduzione avviene con
     **ExoPlayer (Media3)**: niente banner, niente pubblicità, controlli nativi, supporto HLS
     e sottotitoli quando disponibili
  2. **WebView filtrata** (fallback o modalità manuale): la pagina viene caricata in una WebView
     con **filtro di rete** (nessuna richiesta pubblicitaria raggiunge la rete) e **pulizia del DOM**
     (banner, iframe pubblicitari, overlay, popunder, clickbait); se la WebView intercetta il
     flusso video, la riproduzione passa comunque al player nativo
- **Contatore** delle richieste pubblicitarie bloccate, mostrato sotto il player
- Modalità scuro, schermo intero, apertura nel browser, copia link

## Estrazione del flusso video

Il cuore dell'app è `extractor/VideoExtractor.kt`, che applica in ordine diverse strategie:

| Strategia | Dove | Cosa fa |
|---|---|---|
| `uqload` | `https://uqload.vc/e/<code>.html` | POST a `/dl` (`op=embed`, `file_code`, `auto=1`) e spacchettamento del JS |
| `VoeStrategy` | qualunque pagina VOE (es. chuckle-tube.com) | decifra il payload `<script type="application/json">` (rot13 + sostituzioni + base64 + shift + reverse) |
| `PackedStrategy` | qualunque player packed | spacchetta `eval(function(p,a,c,k,e,d){...})` con l'unpacker in `JsUnpacker.kt` e legge `file: "..."` |
| `RegexStrategy` | fallback | cerca direttamente un URL `.m3u8` / `.mp4` nella pagina |

Se l'estrazione fallisce l'app ricade automaticamente sulla WebView filtrata.

## Struttura

```
app android/
├── .github/workflows/build.yml        # build automatico della APK su GitHub
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/batman-the-brave-and-the-bold.json
│       ├── java/com/toonitalia/batmanplayer/
│       │   ├── MainActivity.kt        # catalogo, ricerca, apertura JSON
│       │   ├── PlayerActivity.kt      # estrazione + player nativo + WebView filtrata
│       │   ├── block/AdBlocker.kt     # blacklist reti pubblicitarie
│       │   ├── block/AdBlockScripts.kt # JS di pulizia DOM e sniffing del video
│       │   ├── extractor/VideoExtractor.kt  # strategie di estrazione del flusso
│       │   ├── extractor/JsUnpacker.kt      # unpacker del JS "packed"
│       │   ├── extractor/VoeStrategy.kt     # player VOE (chuckle-tube)
│       │   ├── extractor/PackedStrategy.kt  # player con JS packed (uqload)
│       │   ├── extractor/RegexStrategy.kt   # fallback regex
│       │   ├── extractor/Http.kt            # client HTTP minimale
│       │   ├── player/NativePlayerFactory.kt# ExoPlayer con TLS permissivo
│       │   ├── data/SeriesParser.kt   # parsing del JSON
│       │   ├── model/Models.kt
│       │   └── ui/EpisodeAdapter.kt
│       └── res/                       # layout, drawable, icone
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/
```

## Compilare la APK con GitHub Actions

Il workflow `.github/workflows/build.yml` esegue:

1. checkout del repository
2. JDK 17 (Temurin) + Gradle
3. generazione di un keystore di firma e creazione di `keystore.properties`
4. `assembleDebug` e `assembleRelease`
5. upload degli APK come **artifact** della build
6. se la build è su un tag `v*`, pubblicazione degli APK nella **Release** GitHub

Per avviare la build: push sul branch `main` (o `master`), oppure *Actions → Build APK → Run workflow*.
Gli APK si scaricano dalla sezione **Artifacts** in fondo alla pagina della run.

## Compilare in locale

Con Android Studio: aprire la cartella e fare *Sync → Run*.

Da terminale (con JDK 17 installato):

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

Per firmare la release in locale creare `keystore.properties` nella radice del progetto:

```properties
storeFile=release.keystore
storePassword=android
keyAlias=batman
keyPassword=android
```

Se il file non esiste la release viene firmata con la chiave di debug.

## Formato JSON supportato

```jsonc
{
  "titolo": "Batman: The Brave and the Bold",
  "stagioni": [
    {
      "stagione": 1,
      "episodi": [
        {
          "stagione": 1,
          "episodio": 1,
          "titolo": "L'ascesa di Blue Beetle",
          "player1": "https://uqload.vc/e/....html",
          "player2": "https://chuckle-tube.com/e/...."
        }
      ]
    }
  ]
}
```

## Note

I link di streaming sono di terze parti e possono cambiare o non essere più disponibili:
l'app non ospita né riproduce contenuti propri, si limita a caricare le pagine indicate nel JSON.
Responsabilità dell'utente per l'uso che se ne fa dei contenuti.
