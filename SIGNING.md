# Firma APK di AltTile

AltTile usa la stessa procedura di BatteryRing, ma con una **chiave privata dedicata**.

## Configurazione

1. Genera in locale una chiave permanente con `bash tools/create-signing-key.sh`.
2. Su GitHub apri **Settings > Secrets and variables > Actions** del repository AltTile.
3. Aggiungi quattro repository secrets:
   - `ALTTILE_KEYSTORE_BASE64`: contenuto del file `alttile-release.jks.base64`
   - `ALTTILE_STORE_PASSWORD`: password del keystore
   - `ALTTILE_KEY_ALIAS`: `alttile` (se hai usato lo script)
   - `ALTTILE_KEY_PASSWORD`: password della chiave
4. Conserva un backup offline del keystore e delle password.
5. Esegui **Build APK** in GitHub Actions. Produce una release firmata. Se manca uno dei secret, il workflow fallisce senza pubblicare APK non firmati.

Il keystore viene ricostruito solo nel filesystem temporaneo del runner GitHub, con permessi 600, e viene eliminato a fine workflow. Il job ha solo `contents: read` e non firma pull request.

## Firma locale

Copia `keystore.properties.example` in `keystore.properties`, configura la chiave privata locale e compila con `./gradlew assembleRelease`.
Le build `assembleDebug` locali usano la normale chiave di debug del dispositivo di sviluppo.

## Compatibilità e chiave compromessa

La vecchia `debug.keystore` era pubblicata nel repository. **La sua rimozione da main non la cancella dalla cronologia Git**, né da fork e copie. Va considerata esposta e inadatta alle future release.

Passare a una nuova chiave implica che Android non consentirà l'aggiornamento diretto delle versioni precedenti firmate con la vecchia chiave: prima occorrerà disinstallare la vecchia app. Non esiste una migrazione diretta della firma di un APK già installato senza possedere e usare una catena di firma supportata.

Non riutilizzare la chiave privata di BatteryRing: condivisione del metodo e condivisione della chiave sono due cose diverse.

La pulizia della cronologia Git richiederebbe un intervento separato e potenzialmente distruttivo (riscrittura della storia), e comunque non renderebbe segreta una chiave già pubblicata.
