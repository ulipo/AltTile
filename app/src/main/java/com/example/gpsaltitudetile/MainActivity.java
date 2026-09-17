package com.example.gpsaltitudetile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_FOREGROUND_LOCATION = 10;

    private TextView statusText;
    private Button openSettingsButton;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("GPS Altitude Tile");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, 30);
        root.addView(statusText);

        openSettingsButton = new Button(this);
        openSettingsButton.setText("Apri impostazioni posizione");
        openSettingsButton.setOnClickListener(v -> openAppSettings());
        root.addView(openSettingsButton);

        setContentView(root);

        requestForegroundLocationIfNeeded();
        updateStatusText();
    }

    @Override protected void onResume() {
        super.onResume();
        // L'utente può tornare qui dopo aver cambiato il permesso dalle
        // Impostazioni di sistema: aggiorniamo subito testo e pulsante.
        updateStatusText();
    }

    private void requestForegroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_FOREGROUND_LOCATION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatusText();
    }

    /**
     * Il permesso "in primo piano" (ACCESS_FINE_LOCATION) non basta per far
     * funzionare la tile quando l'app non è aperta. Da Android 10 in poi
     * serve ACCESS_BACKGROUND_LOCATION, che il sistema NON concede tramite
     * il dialog standard di requestPermissions() a partire da Android 11:
     * va abilitato manualmente in Impostazioni > Permessi > Posizione >
     * "Consenti sempre". Per questo qui ci limitiamo a verificarlo e a
     * guidare l'utente lì, invece di richiederlo via requestPermissions().
     */
    private boolean hasBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Prima di Android 10 il permesso di primo piano vale anche in background.
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatusText() {
        boolean foregroundGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!foregroundGranted) {
            statusText.setText("1. Concedi il permesso di posizione quando richiesto.\n"
                    + "2. Aggiungi \u201cGPS Altitude\u201d alle Quick Settings.\n"
                    + "3. Tocca la tile per avviare/arrestare il GPS.\n\n"
                    + "L'altitudine viene mostrata direttamente nella tile.\n"
                    + "Dopo 30 s a schermo spento la misurazione si arresta.");
            openSettingsButton.setVisibility(View.GONE);

        } else if (!hasBackgroundLocation()) {
            statusText.setText("Permesso di posizione concesso, ma solo \u201cdurante "
                    + "l'utilizzo dell'app\u201d.\n\n"
                    + "La tile deve leggere il GPS anche quando l'app non è aperta: "
                    + "senza il permesso \u201cConsenti sempre\u201d la tile si accende "
                    + "ma non mostra mai l'altitudine.\n\n"
                    + "Tocca il pulsante qui sotto, apri \u201cPermessi > Posizione\u201d "
                    + "e seleziona \u201cConsenti sempre\u201d.");
            openSettingsButton.setVisibility(View.VISIBLE);

        } else {
            statusText.setText("Permessi di posizione configurati correttamente.\n\n"
                    + "Aggiungi \u201cGPS Altitude\u201d alle Quick Settings e tocca la "
                    + "tile per avviare/arrestare il GPS.\n\n"
                    + "Dopo 30 s a schermo spento la misurazione si arresta.");
            openSettingsButton.setVisibility(View.GONE);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
}
