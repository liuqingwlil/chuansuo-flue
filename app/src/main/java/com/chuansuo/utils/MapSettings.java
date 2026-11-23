package com.chuansuo.utils;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.chuansuo.app.BuildConfig;

public final class MapSettings {
    private MapSettings() { }

    public static String apiKey(Context context) {
        if (BuildConfig.DEBUG) {
            String value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("setting_map_key", "");
            value = value == null ? "" : value.trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return BuildConfig.MAPS_API_KEY;
    }
}
