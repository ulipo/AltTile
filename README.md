# GPS Altitude Tile

Progetto Android/AndroidIDE per una Quick Settings Tile che:
- avvia/arresta la misurazione GPS con un tap;
- mostra l'altitudine direttamente nella tile (es. `842 m`);
- usa il provider GPS/GNSS;
- richiede il permesso di posizione precisa;
- è predisposto per lo spegnimento dopo 30 s con schermo spento.

## Compilazione con AndroidIDE
1. Estrai questo ZIP.
2. Apri la cartella come progetto Gradle in AndroidIDE.
3. Lascia che Gradle sincronizzi il progetto.
4. Build > Assemble Debug.
5. Installa `app/build/outputs/apk/debug/app-debug.apk`.

## Aggiunta della tile
Dopo l'installazione:
- apri il pannello Quick Settings;
- Modifica / aggiungi tile;
- aggiungi `GPS Altitude`.

## Compilazione automatica su GitHub (CI)
Il repository include un workflow (`.github/workflows/android-build.yml`) che compila
automaticamente l'APK di debug ad ogni push/PR su `main`/`master`, oppure a mano da:
Actions → "Build APK" → Run workflow.

1. Crea un repository su GitHub e carica il contenuto di questa cartella (Gradle Wrapper incluso).
2. Vai nella scheda **Actions** del repository: il workflow parte da solo al push.
3. A build completata, apri il job e scarica l'artifact `GpsAltitudeTile-debug-apk`
   (contiene `app-debug.apk`) dalla sezione "Artifacts" in fondo alla pagina del run.

## Nota
Android può limitare la frequenza/continuità delle TileService in background.
Per una versione definitiva conviene usare un piccolo servizio foreground mentre la misurazione è attiva, se richiesto dalla versione Android del telefono. La tile rimane comunque l'interfaccia principale.
