# AltTile — Altimetro nelle Quick Settings

AltTile permette di accendere e spegnere il GPS dalle impostazioni rapide
di Android e visualizza la quota direttamente nella tile.

## Funzioni

- Acquisizione GNSS esplicita dal provider GPS Android, senza dipendere da
  un provider fuso che potrebbe non accendere il ricevitore satellitare.
- Richiesta di posizione ogni 2 secondi, anche da fermi.
- La tile e la notifica vengono aggiornate solo se cambia la quota arrotondata
  o il suo riferimento altimetrico.
- Preferisce la quota MSL (livello medio del mare) quando il provider la offre.
  In caso contrario mostra la quota ellissoidica WGS84 con prefisso `~`,
  per non indicarla erroneamente come quota sul livello del mare.
- La rilevazione si arresta automaticamente dopo 30 secondi a schermo spento
  (il sistema può differire l'esecuzione del timer se sospende il processo:
  alla riaccensione viene ricontrollato il tempo trascorso).
- La localizzazione viene interrotta quando si spegne la tile.

## Installazione e permessi

Installa l'APK creato da GitHub Actions, apri l'app e concedi la
**posizione precisa**. Su Android 10+ è richiesto anche il permesso
**Consenti sempre** nelle impostazioni di posizione dell'app, perché il
riquadro Quick Settings viene usato senza un'Activity visibile.

Aggiungi quindi il riquadro **GPS Altitude** alle Quick Settings.

## Sviluppo

Progetto Android Java/Gradle, minSdk 26, targetSdk 35.
Compila con `./gradlew assembleDebug` o il workflow GitHub Actions.
Le release sono firmate tramite GitHub Secrets. La chiave privata non è più nel ramo corrente del repository; consulta [SIGNING.md](SIGNING.md) per configurazione e migrazione dagli APK precedenti.

L'app è stata realizzata tramite **vibe-coding**.
