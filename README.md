# AltTile — altimetro nelle Quick Settings

AltTile visualizza la quota direttamente nella tile delle impostazioni rapide Android.
Tocca la tile per avviare o fermare il rilevamento. Dopo 30 secondi a schermo
spento il servizio GPS viene arrestato automaticamente.

## Funzionamento

- Posizione fusa Android, con fallback al GPS dove necessario.
- Richiesta posizione ogni 2,5 secondi; tile e notifica aggiornate solo quando serve.
- Su Android 14+ conversione WGS84 → MSL (livello medio del mare) quando il
  provider non offre già una quota MSL. La conversione avviene fuori dal thread UI.
- Quando MSL non è disponibile, il valore è la quota ellissoidica WGS84:
  il simbolo `~` avvisa che non va interpretato come quota sul livello del mare.
  `~` segnala anche una stima con precisione verticale limitata.
- Un servizio separato dal pannello Quick Settings gestisce la localizzazione.
  Il servizio non si riavvia automaticamente dopo l'arresto del processo.
- Un wakelock limitato a circa 35 secondi permette di rispettare il timer
  di spegnimento anche quando il telefono va in sospensione.
- Nessuna cronologia delle posizioni viene memorizzata. Spegnendo la tile
  viene cancellata anche l'ultima quota visualizzata.

## Installazione

Installa un APK generato dal workflow GitHub Actions **Build APK** e aggiungi
**GPS Altitude** al pannello Quick Settings.

Apri l'app e concedi il permesso di posizione **precisa**. Su Android 10+
occorre anche **Consenti sempre**, da *Impostazioni > App > GPS Altitude Tile >
Permessi > Posizione*, perché la tile può avviare il GPS senza un'Activity
visibile. Se manca un permesso, un tocco sulla tile apre le istruzioni.

## Compilazione

Progetto Java/Gradle (minSdk 26, targetSdk 35). Compila con
`./gradlew assembleDebug` oppure GitHub Actions. Il keystore presente nel
repository è **solo di debug**: per una distribuzione pubblica occorre una
chiave di firma di release privata.

## Limiti

La qualità dell'altitudine dipende dal dispositivo, dalla qualità del
segnale e dai dati disponibili. Android precedenti alla versione 14
non garantiscono la conversione MSL se il provider non fornisce già quel dato.

Progetto realizzato tramite **vibe-coding**.
