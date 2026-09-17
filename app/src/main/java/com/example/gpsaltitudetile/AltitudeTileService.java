package com.example.gpsaltitudetile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.core.app.NotificationCompat;
import androidx.core.location.LocationCompat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class AltitudeTileService extends TileService {

    private static final String TAG = "AltitudeTileService";
    private static final String PREFS = "gps_altitude";
    private static final String KEY_ENABLED = "enabled";
    private static final String CHANNEL_ID = "alt_tile_channel";
    private static final int NOTIF_ID = 1001;
    private static final long SCREEN_OFF_TIMEOUT_MS = 30_000L;

    private LocationManager locationManager;
    private NotificationManager notificationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening = false;
    private boolean started = false;
    private boolean screenReceiverRegistered = false;
    private long lastAltitude = Long.MIN_VALUE;
    private long screenOffTime = 0L;

    private final Runnable stopTask = new Runnable() {
        @Override public void run() {
            forceStopAndOff();
        }
    };

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOffTime = SystemClock.elapsedRealtime();
                handler.removeCallbacks(stopTask);
                handler.postDelayed(stopTask, SCREEN_OFF_TIMEOUT_MS);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handler.removeCallbacks(stopTask);
                // Se lo schermo rimane spento più di 30 secondi e il timer in background è stato congelato dal sistema,
                // forziamo lo spegnimento immediato al momento della riaccensione
                if (screenOffTime > 0 && (SystemClock.elapsedRealtime() - screenOffTime) >= SCREEN_OFF_TIMEOUT_MS) {
                    forceStopAndOff();
                } else {
                    updateTile();
                }
                screenOffTime = 0L;
            }
        }
    };

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location == null) return;

            if (LocationCompat.hasMslAltitude(location)) {
                lastAltitude = Math.round(LocationCompat.getMslAltitudeMeters(location));
            } else if (location.hasAltitude()) {
                lastAltitude = Math.round(location.getAltitude());
            }

            updateTile();
            updateNotification();
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override public void onStartListening() {
        super.onStartListening();
        // Questo è il punto in cui il sistema ci dà accesso a un Tile non nullo.
        // È qui che una eventuale disattivazione avvenuta mentre non eravamo
        // "listening" (es. a schermo spento) viene finalmente riflessa in UI.
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        boolean enabled = getPrefs().getBoolean(KEY_ENABLED, false);
        setEnabled(!enabled);
    }

    @Override public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override public void onTileRemoved() {
        setEnabled(false);
        super.onTileRemoved();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(stopTask);
        stopLocation();
        super.onDestroy();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void setEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_ENABLED, enabled).apply();

        if (enabled) {
            startLocation();
        } else {
            stopLocation();
        }
        updateTile();
    }

    private void forceStopAndOff() {
        lastAltitude = Long.MIN_VALUE;
        getPrefs().edit().putBoolean(KEY_ENABLED, false).apply();
        stopLocation();

        // NB: getQsTile() restituisce null se il servizio non è "listening"
        // in questo momento (es. pannello Quick Settings chiuso, come è quasi
        // sempre il caso a 30s da uno screen-off). In quel caso questo blocco
        // viene saltato e l'aggiornamento visivo arriverà solo quando il
        // sistema richiamerà onStartListening() (v. sotto e requestListeningState).
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("altimetro");
            tile.setContentDescription("Altimetro disattivato");
            tile.updateTile();
        }

        requestTileRefresh();
    }

    /**
     * Chiede al sistema di ri-bindare il servizio così che onStartListening()
     * (e quindi updateTile()) venga richiamato anche se in questo momento
     * getQsTile() è null. È una richiesta "best effort": il sistema può
     * ignorarla, in particolare a schermo spento / dispositivo non
     * interattivo o se già chiamata di recente (rate-limit interno). Per
     * questo lo stato "vero" resta comunque quello salvato in
     * SharedPreferences, e la UI si allineerà comunque alla riapertura
     * manuale del pannello Quick Settings, anche se questa chiamata fallisce.
     */
    private void requestTileRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestListeningState(
                        getApplicationContext(),
                        new ComponentName(getApplicationContext(), AltitudeTileService.class)
                );
            } catch (IllegalStateException e) {
                // Il sistema non ha onorato la richiesta (tipicamente perché
                // il device non è interattivo). Non è un errore fatale: la
                // tile si aggiornerà comunque al prossimo onStartListening().
                Log.d(TAG, "requestListeningState non onorata: " + e.getMessage());
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocation() {
        if (!hasLocationPermission()) {
            updateTile();
            return;
        }

        // Fondamentale: senza questa chiamata il servizio resta "solo bound".
        // Alla chiusura del pannello Quick Settings il sistema fa l'unbind e,
        // non essendo mai stato "started", distrugge subito il servizio
        // (onDestroy -> stopLocation -> GPS interrotto), anche se abbiamo
        // appena chiamato startForeground(). startForegroundService() lo
        // marca come started: da qui in poi sopravvive all'unbind e resta
        // sotto il nostro controllo (lo fermiamo noi con stopSelf()).
        if (!started) {
            ContextCompat.startForegroundService(
                    this, new Intent(this, AltitudeTileService.class));
            started = true;
        }

        promoteToForeground();
        registerScreenReceiver();

        if (listening) {
            return;
        }

        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0.5f,
                        listener,
                        Looper.getMainLooper());
                listening = true;
            }
        } catch (SecurityException ignored) {
        }
        updateTile();
    }

    private void stopLocation() {
        if (locationManager != null && listening) {
            try {
                locationManager.removeUpdates(listener);
            } catch (SecurityException ignored) {
            }
        }
        listening = false;
        unregisterScreenReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        // Rilascia lo stato "started" impostato in startLocation(): da qui
        // in poi il servizio torna a essere gestito solo dal binding di
        // sistema (onStartListening/onClick), come una normale TileService.
        if (started) {
            started = false;
            stopSelf();
        }
    }

    private Notification buildNotification() {
        String contentText = (lastAltitude != Long.MIN_VALUE)
                ? "Altitudine attuale: " + lastAltitude + " m s.l.m."
                : "Acquisizione segnale GPS in corso...";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Altimetro GPS")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_tile)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void promoteToForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void updateNotification() {
        if (listening && notificationManager != null) {
            notificationManager.notify(NOTIF_ID, buildNotification());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Altimetro Tile Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void registerScreenReceiver() {
        if (screenReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
        screenReceiverRegistered = true;
    }

    private void unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return;
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        screenReceiverRegistered = false;
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean enabled = getPrefs().getBoolean(KEY_ENABLED, false);
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));

        if (enabled) {
            tile.setLabel(lastAltitude != Long.MIN_VALUE
                    ? lastAltitude + " m"
                    : "GPS Altitude");
            tile.setContentDescription(lastAltitude != Long.MIN_VALUE
                    ? "Altitudine GPS: " + lastAltitude + " metri"
                    : "Altitudine GPS: acquisizione");
        } else {
            tile.setLabel("altimetro");
            tile.setContentDescription("Altimetro disattivato");
        }
        tile.updateTile();
    }
}
