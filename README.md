# GPS Altitude Tile

Progetto Android/AndroidIDE per una Quick Settings Tile che:
- avvia/arresta la misurazione GPS con un tap;
- mostra l'altitudine direttamente nella tile (es. `842 m`), preferendo l'altitudine sul livello medio del mare (MSL) quando fornita da Android;
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

## Permesso di posizione in background (obbligatorio)
Da Android 10 in poi, il permesso di posizione precisa concesso normalmente
("Consenti solo durante l'utilizzo dell'app") **non basta**: la tile legge il
GPS anche mentre l'app non è in primo piano, quindi Android richiede il
livello di permesso più alto, "Consenti sempre".

Senza questo permesso la tile si attiva ma non riceve mai posizioni valide
(resta su "Acquisizione segnale GPS in corso..." o non aggiorna l'altitudine).

Per concederlo:
1. Apri l'app **GPS Altitude Tile** almeno una volta e concedi il permesso di
   posizione quando richiesto (primo dialog: "Consenti solo durante
   l'utilizzo dell'app" o "Consenti").
2. Vai in **Impostazioni di sistema > App > GPS Altitude Tile > Permessi >
   Posizione**.
3. Seleziona **"Consenti sempre"** (su alcuni produttori: "Consenti tutto il
   tempo" / "Always allow").

Questo passaggio non può essere completato con un solo tap dentro l'app:
a partire da Android 11, il sistema obbliga a concedere "Consenti sempre"
tramite le Impostazioni, non tramite il dialog di richiesta permessi
standard. L'app può solo guidarti fino a quella schermata (vedi sotto).
