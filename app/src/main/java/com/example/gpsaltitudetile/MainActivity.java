package com.example.gpsaltitudetile;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("GPS Altitude Tile\n\n1. Concedi il permesso di posizione precisa.\n"
                + "2. Aggiungi “GPS Altitude” alle Quick Settings.\n"
                + "3. Tocca la tile per avviare/arrestare il GPS.\n\n"
                + "L'altitudine viene mostrata direttamente nella tile.\n"
                + "Dopo 30 s a schermo spento la misurazione si arresta.");
        t.setGravity(Gravity.CENTER);
        t.setPadding(40,40,40,40);
        setContentView(t);
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
    }
}
