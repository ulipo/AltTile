package com.example.gpsaltitudetile;

import android.content.Context;
import android.content.SharedPreferences;

/** State shared between the tile and the location service. No history is retained. */
final class AltitudeState {
    private static final String PREFS = "gps_altitude";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ALTITUDE = "altitude";
    private static final String KEY_MSL = "msl";
    private static final String KEY_APPROXIMATE = "approximate";
    static final int NO_ALTITUDE = Integer.MIN_VALUE;

    private AltitudeState() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static int altitude(Context context) {
        return prefs(context).getInt(KEY_ALTITUDE, NO_ALTITUDE);
    }

    static boolean isMsl(Context context) {
        return prefs(context).getBoolean(KEY_MSL, false);
    }

    static boolean isApproximate(Context context) {
        return prefs(context).getBoolean(KEY_APPROXIMATE, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_ALTITUDE, NO_ALTITUDE)
                .putBoolean(KEY_MSL, false)
                .putBoolean(KEY_APPROXIMATE, false)
                .apply();
    }

    static void setAltitude(Context context, int altitude, boolean msl, boolean approximate) {
        prefs(context).edit()
                .putInt(KEY_ALTITUDE, altitude)
                .putBoolean(KEY_MSL, msl)
                .putBoolean(KEY_APPROXIMATE, approximate)
                .apply();
    }

    static void clearAltitude(Context context) {
        prefs(context).edit()
                .putInt(KEY_ALTITUDE, NO_ALTITUDE)
                .putBoolean(KEY_MSL, false)
                .putBoolean(KEY_APPROXIMATE, false)
                .apply();
    }
}
