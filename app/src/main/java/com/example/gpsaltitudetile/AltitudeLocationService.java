package com.example.gpsaltitudetile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.altitude.AltitudeConverter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.location.LocationCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the location subscription independently of the Quick Settings binding. */
public class AltitudeLocationService extends Service {
    private static final String TAG = "AltitudeLocation";
    private static final String CHANNEL_ID = "alt_tile_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long FIX_INTERVAL_MS = 2500L;
    private static final long SCREEN_OFF_TIMEOUT_MS = 30_000L;
    private static final long NOTIFICATION_INTERVAL_MS = 10_000L;
    private static final long MAX_FIX_AGE_MS = 30_000L;

    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean conversionPending = new AtomicBoolean();
    private final ExecutorService conversionExecutor = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private NotificationManager notificationManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock screenOffWakeLock;
    private boolean receiverRegistered;
    private boolean subscribed;
    private boolean foreground;
    private long screenOffAtMs = -1L;
    private long lastNotificationAtMs;
    private long generation;
    private AltitudeConverter altitudeConverter;

    static boolean isRunning() {
        return running;
    }

    private final Runnable screenOffStop = () -> {
        if (screenOffAtMs >= 0
                && SystemClock.elapsedRealtime() - screenOffAtMs >= SCREEN_OFF_TIMEOUT_MS) {
            stopSelf();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                beginScreenOffCountdown();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                long elapsed = screenOffAtMs < 0 ? 0
                        : SystemClock.elapsedRealtime() - screenOffAtMs;
                handler.removeCallbacks(screenOffStop);
                releaseWakeLock();
                screenOffAtMs = -1L;
                if (elapsed >= SCREEN_OFF_TIMEOUT_MS) stopSelf();
            }
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            handleLocation(location);
        }

        @Override public void onProviderDisabled(String provider) {
            AltitudeState.clearAltitude(AltitudeLocationService.this);
            AltitudeTileService.requestRefresh(AltitudeLocationService.this);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 34) altitudeConverter = new AltitudeConverter();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        if (!hasPermissions()) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foreground = true;
            if (!subscribeToLocation()) {
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }

            running = true;
            generation++;
            AltitudeState.setEnabled(this, true);
            registerScreenReceiver();
            if (powerManager != null && !powerManager.isInteractive()) {
                beginScreenOffCountdown();
            }
            AltitudeTileService.requestRefresh(this);
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot start location tracking", e);
            stopSelfResult(startId);
        }
        return START_NOT_STICKY; // Never restart GPS automatically after process death.
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == PackageManager.PERMISSION_GRANTED);
    }

    @SuppressLint("MissingPermission")
    private boolean subscribeToLocation() {
        if (locationManager == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,
                        FIX_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper());
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Criteria requests use Android's fused provider since KitKat.
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                locationManager.requestLocationUpdates(FIX_INTERVAL_MS, 0f, criteria,
                        locationListener, Looper.getMainLooper());
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        FIX_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper());
            } else {
                Log.w(TAG, "No available location provider");
                return false;
            }
            subscribed = true;
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Cannot register location updates", e);
            return false;
        }
    }

    private void handleLocation(Location location) {
        if (!running || location == null || !location.hasAltitude()
                || !Double.isFinite(location.getAltitude())) return;

        long fixAgeMs = SystemClock.elapsedRealtime()
                - TimeUnit.NANOSECONDS.toMillis(location.getElapsedRealtimeNanos());
        if (fixAgeMs > MAX_FIX_AGE_MS) return;

        final boolean approximate = location.hasVerticalAccuracy()
                && (!Float.isFinite(location.getVerticalAccuracyMeters())
                    || location.getVerticalAccuracyMeters() > 50f);

        if (LocationCompat.hasMslAltitude(location)) {
            double altitude = LocationCompat.getMslAltitudeMeters(location);
            if (Double.isFinite(altitude)) publishAltitude(altitude, true, approximate);
        } else if (Build.VERSION.SDK_INT >= 34) {
            // Geoid model may require disk access: do not block the main thread.
            if (!conversionPending.compareAndSet(false, true)) return;
            final Location locationCopy = new Location(location);
            final long currentGeneration = generation;
            conversionExecutor.execute(() -> {
                try {
                    altitudeConverter.addMslAltitudeToLocation(
                            getApplicationContext(), locationCopy);
                    if (LocationCompat.hasMslAltitude(locationCopy)) {
                        final double msl = LocationCompat.getMslAltitudeMeters(locationCopy);
                        handler.post(() -> {
                            if (running && generation == currentGeneration) {
                                publishAltitude(msl, true, approximate);
                            }
                        });
                    }
                } catch (IOException | IllegalArgumentException e) {
                    Log.w(TAG, "MSL conversion not available", e);
                    handler.post(() -> {
                        if (running && generation == currentGeneration) {
                            publishAltitude(locationCopy.getAltitude(), false, true);
                        }
                    });
                } finally {
                    conversionPending.set(false);
                }
            });
        } else {
            // Never present uncorrected ellipsoidal altitude as sea-level altitude.
            publishAltitude(location.getAltitude(), false, true);
        }
    }

    private void publishAltitude(double value, boolean msl, boolean approximate) {
        if (!running || !Double.isFinite(value) || Math.abs(value) > Integer.MAX_VALUE) return;
        int rounded = (int) Math.round(value);
        boolean changed = AltitudeState.altitude(this) != rounded
                || AltitudeState.isMsl(this) != msl
                || AltitudeState.isApproximate(this) != approximate;
        if (changed) {
            AltitudeState.setAltitude(this, rounded, msl, approximate);
            AltitudeTileService.requestRefresh(this);
        }

        long now = SystemClock.elapsedRealtime();
        if (changed || now - lastNotificationAtMs >= NOTIFICATION_INTERVAL_MS) {
            lastNotificationAtMs = now;
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification());
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && notificationManager != null) {
            notificationManager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Altimetro GPS", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildNotification() {
        int altitude = AltitudeState.altitude(this);
        String text = altitude == AltitudeState.NO_ALTITUDE
                ? "Acquisizione posizione in corso..."
                : altitude + " m " + (AltitudeState.isMsl(this)
                    ? "s.l.m." : "(ellissoide WGS84, non MSL)");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Altimetro GPS")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_tile)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void registerScreenReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void beginScreenOffCountdown() {
        if (screenOffAtMs >= 0) return;
        screenOffAtMs = SystemClock.elapsedRealtime();
        handler.removeCallbacks(screenOffStop);
        // Bounded wake lock: the delayed stop must run even during CPU suspension.
        if (powerManager != null) {
            screenOffWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "AltTile:ScreenOffStop");
            screenOffWakeLock.acquire(SCREEN_OFF_TIMEOUT_MS + 5_000L);
        }
        handler.postDelayed(screenOffStop, SCREEN_OFF_TIMEOUT_MS);
    }

    private void releaseWakeLock() {
        if (screenOffWakeLock != null) {
            if (screenOffWakeLock.isHeld()) screenOffWakeLock.release();
            screenOffWakeLock = null;
        }
    }

    @Override public void onDestroy() {
        running = false;
        generation++;
        handler.removeCallbacks(screenOffStop);
        releaseWakeLock();
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        if (subscribed && locationManager != null) {
            locationManager.removeUpdates(locationListener);
            subscribed = false;
        }
        conversionExecutor.shutdownNow();
        AltitudeState.setEnabled(this, false);
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        AltitudeTileService.requestRefresh(this);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
