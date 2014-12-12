package com.example.signdoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by andy on 10.12.14.
 */

// SINGLETON
public class Settings {
    private static SharedPreferences sPref;
    private static Settings instance;

    public static Settings getInstance() {
        synchronized (Settings.class) {
            if (instance == null) {
                try {
                    instance = new Settings();
                } catch (Exception e) {
                    Log.e("Settings/Singleton", e.getMessage(), e);
                    instance = new Settings();
                }
            }
            return instance;
        }

    }

    public Settings() {
        if (this.sPref == null) { this.sPref = MainActivity.getPref(); };
    }

    public String get(String key) {
        String val = sPref.getString(key, "");
        return(val);
    }

    public void set(String key, String val) {
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString(key, val);
        ed.commit();
    }
}
