package org.gplvote.signdoc;

import android.content.Intent;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class GetPassActivity extends FragmentActivity implements GetPassInterface {

    private static DialogFragment dlgPassword = null;

    public boolean checkPasswordDlgShow(Settings settings) {
        if (!settings.get(MainActivity.PREF_ENC_PRIVATE_KEY).equals("")) {
            if (MainActivity.sign == null || !MainActivity.sign.pvt_key_present()) {
                if (dlgPassword == null)
                    dlgPassword = new DlgPassword(this);
                dlgPassword.show(getSupportFragmentManager(), "missiles");
                return(true);
            } else {
                return(false);
            }
        } else {
            // Initialization
            Intent intent;
            intent = new Intent(this, InitKey.class);
            startActivity(intent);
            return(true);
        }
    }

    @Override
    public boolean onPassword(String password) {
        if (MainActivity.sign == null) {
            MainActivity.sign = new Sign(this);
        } else {
            MainActivity.sign.cache_reset();
        }
        Log.d("DocsList", "setPassword");
        MainActivity.sign.setPassword(password);
        if (MainActivity.sign.pvt_key_present()) {
            Log.d("GetPassActivity", "pvt_key_present true");
            return(true);
        } else {
            Log.d("GetPassActivity", "Error about wrong password");
            return(false);
        }
    }
}
