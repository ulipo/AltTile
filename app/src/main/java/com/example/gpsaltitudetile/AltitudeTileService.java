package com.example.gpsaltitudetile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class AltitudeTileService extends TileService {

    private static final String PREFS = "gps_altitude";
    private static final String KEY_ENABLED = "enabled";
    private static final long SCREEN_OFF_TIMEOUT_MS = 30_000L;

    private LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening = false;
    private boolean screenReceiverRegistered = false;
    private long lastAltitude = Long.MIN_VALUE;

    private final Runnable screenOffStop = new Runnable() {
        @Override public void run() {
            setEnabled(false);
        }
    };

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.postDelayed(screenOffStop, SCREEN_OFF_TIMEOUT_MS);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handler.removeCallbacks(screenOffStop);
            }
        }
    };

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location != null && location.hasAltitude()) {
                lastAltitude = Math.round(location.getAltitude());
                updateTile();
            }
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override public void onStartListening() {
        super.onStartListening();
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
        handler.removeCallbacksAndMessages(null);
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

    @SuppressLint("MissingPermission")
    private void startLocation() {
        if (!hasLocationPermission()) {
            updateTile();
            return;
        }

        registerScreenReceiver();

        if (listening) {
            return;
        }

        try {
            // GPS/GNSS only: avoids using network/Wi-Fi location.
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
        handler.removeCallbacks(screenOffStop);
        unregisterScreenReceiver();
    }

    private void registerScreenReceiver() {
        if (screenReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter);
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
            tile.setLabel("GPS OFF");
            tile.setContentDescription("GPS Altitude disattivato");
        }
        tile.updateTile();
    }
}
