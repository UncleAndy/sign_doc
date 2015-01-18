package org.gplvote.signdoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by andy on 10.12.14.
 */

// SINGLETON
public class Settings {
    public static final String APP_PREFERENCES = "org.gplvote.signdoc";

    private static SharedPreferences sPref;
    private static Settings instance;

    public static Settings getInstance(Context context) {
        synchronized (Settings.class) {
            if (instance == null) {
                try {
                    instance = new Settings(context);
                } catch (Exception e) {
                    Log.e("Settings/Singleton", e.getMessage(), e);
                    instance = new Settings(context);
                }
            }
            return instance;
        }
    }

    public Settings(Context context) {
        if (sPref == null) sPref = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
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
