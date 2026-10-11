package com.example.gpsaltitudetile;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.content.ContextCompat;

/** Tile UI only; Android may unbind this service whenever QS is closed. */
public class AltitudeTileService extends TileService {
    private static final String TAG = "AltitudeTile";

    @Override public void onStartListening() {
        super.onStartListening();
        // A killed process can't retain the location service: never show stale ON.
        if (AltitudeState.isEnabled(this) && !AltitudeLocationService.isRunning()) {
            AltitudeState.setEnabled(this, false);
        }
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        if (AltitudeState.isEnabled(this) || AltitudeLocationService.isRunning()) {
            stopService(new Intent(this, AltitudeLocationService.class));
            AltitudeState.setEnabled(this, false);
            updateTile();
            return;
        }

        if (!hasPermissions()) {
            openPermissionInstructions();
            return;
        }

        try {
            AltitudeState.setEnabled(this, true);
            ContextCompat.startForegroundService(this,
                    new Intent(this, AltitudeLocationService.class));
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to start location foreground service", e);
            AltitudeState.setEnabled(this, false);
        }
        updateTile();
    }

    @Override public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override public void onTileRemoved() {
        stopService(new Intent(this, AltitudeLocationService.class));
        AltitudeState.setEnabled(this, false);
        super.onTileRemoved();
    }

    private boolean hasPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    private void openPermissionInstructions() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = AltitudeState.isEnabled(this);
        int altitude = AltitudeState.altitude(this);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);

        if (!enabled) {
            tile.setLabel("altimetro");
            tile.setContentDescription("Altimetro disattivato");
        } else if (altitude == AltitudeState.NO_ALTITUDE) {
            tile.setLabel("GPS...");
            tile.setContentDescription("Acquisizione altitudine in corso");
        } else {
            boolean msl = AltitudeState.isMsl(this);
            boolean approximate = AltitudeState.isApproximate(this);
            tile.setLabel((approximate || !msl ? "~" : "") + altitude + " m");
            tile.setContentDescription("Altitudine " + altitude + " metri"
                    + (msl ? " sul livello medio del mare"
                           : " rispetto all'ellissoide WGS84 (quota non MSL)")
                    + (approximate ? ", precisione limitata" : ""));
        }
        tile.updateTile();
    }

    static void requestRefresh(Context context) {
        try {
            requestListeningState(context,
                    new ComponentName(context, AltitudeTileService.class));
        } catch (RuntimeException e) {
            Log.d(TAG, "Tile refresh deferred", e);
        }
    }
}
