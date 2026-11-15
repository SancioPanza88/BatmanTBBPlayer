# Batman TBB Player

App Android che legge il file JSON `batman-the-brave-and-the-bold.json` (serie TV con stagioni,
episodi e link di streaming) e ne riproduce gli episodi **bloccando la pubblicità**.

## Scarica la APK

- Release firmata: **[app-release.apk](https://github.com/SancioPanza88/BatmanTBBPlayer/releases/download/v1.0.0/app-release.apk)**
- Versione debug: **[app-debug.apk](https://github.com/SancioPanza88/BatmanTBBPlayer/releases/download/v1.0.0/app-debug.apk)**
- Repository: https://github.com/SancioPanza88/BatmanTBBPlayer

Ogni push su `main` rilancia la build; ogni tag `v*` pubblica una nuova Release con gli APK.

## Funzionalità

- **Lettura del JSON**
  - file incluso negli asset dell'app: `app/src/main/assets/batman-the-brave-and-the-bold.json`
  - possibilità di aprire qualsiasi altro JSON compatibile dal dispositivo (pulsante **Apri JSON**)
- **Catalogo**: intestazione con i metadati della serie, stagioni comprimibili, ricerca episodi
- **Due sorgenti per episodio** (uqload.vc / chuckle-tube.com) selezionabili con tap lungo
- **Blocco pubblicità a 3 livelli**
  1. **Filtro di rete** (`shouldInterceptRequest`): le richieste verso reti pubblicitarie,
     popunder, tracker e pixel non escono mai dal dispositivo
  2. **Pulizia del DOM**: rimozione di banner, iframe pubblicitari, overlay fissi, clickbait;
     blocco di `window.open` (popunder) e delle aperture esterne
  3. **Player nativo**: quando il flusso video reale (`.m3u8` / `.mp4`) viene intercettato,
     la riproduzione passa a ExoPlayer (Media3) → **video senza banner, senza pubblicità,
     con controlli nativi e supporto HLS**
- **Contatore** delle richieste pubblicitarie bloccate, mostrato sotto il player
- Modalità scuro, schermo intero, apertura nel browser, copia link

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
│       │   ├── PlayerActivity.kt      # WebView filtrata + player nativo
│       │   ├── block/AdBlocker.kt     # blacklist reti pubblicitarie
│       │   ├── block/AdBlockScripts.kt # JS di pulizia DOM e sniffing del video
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
