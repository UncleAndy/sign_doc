package org.gplvote.signdoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity implements OnClickListener, GetPassInterface {
    public static final String PREF_ENC_PRIVATE_KEY = "enc_priv_key";
    public static final String PREF_PUBLIC_KEY = "pub_key";
    public static final String PREF_CANCEL_ENC_PRIVATE_KEY = "cancel_enc_priv_key";
    public static final String PREF_CANCEL_PUBLIC_KEY = "cancel_pub_key";
    public static final String APP_PREFERENCES = "org.gplvote.signdoc";
    public static final String HTTP_SEND_URL = "http://signdoc.gplvote.org/send";
    public static final String HTTP_GET_URL = "http://signdoc.gplvote.org/get";

    private static SharedPreferences sPref;
    public static Settings settings;
    private static String current_action;
    public static Sign sign = null;
    private static DialogFragment dlgPassword = null;

    private Button btnInit;
    private Button btnRegister;
    private Button btnDocsList;

    public static boolean do_destroy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        do_destroy = false;

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        btnInit = (Button) findViewById(R.id.btnInit); btnInit.setOnClickListener(this);
        btnRegister = (Button) findViewById(R.id.btnRegister); btnRegister.setOnClickListener(this);
        btnDocsList = (Button) findViewById(R.id.btnDocsList); btnDocsList.setOnClickListener(this);

        if (sPref == null) { sPref = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE); };
        settings = Settings.getInstance(this);

        checkPasswordDlgShow();
    }

    private void checkPasswordDlgShow() {
        if ((!settings.get(PREF_ENC_PRIVATE_KEY).equals("")) && (sign == null || !sign.pvt_key_present())) {
            if (dlgPassword == null)
                dlgPassword = new DlgPassword(this);
            dlgPassword.show(getSupportFragmentManager(), "missiles");
        }
    }

    @Override
    public void onClick(View v) {
      Intent intent;
    	
      switch (v.getId()) {
      case R.id.btnInit:
    	  intent = new Intent(this, InitKey.class);
          startActivity(intent);
    	  break;
      case R.id.btnRegister:
          if (!(null == sign) && sign.pvt_key_present()) {
              if (isInternetPresent(this)) {
                  intent = new Intent(this, RegisterSign.class);
                  startActivity(intent);
              } else {
                  MainActivity.error(getString(R.string.err_internet_connection_absent), this);
              }
          }
          break;
      case R.id.btnDocsList:
          intent = new Intent(this, DocsList.class);
          startActivity(intent);
          break;
      default:
        break;
      }
    }

    public static SharedPreferences getPref() {
        return(sPref);
    }

    /*
        Иконки из набора Oxygen Icons (http://www.iconarchive.com/show/oxygen-icons-by-oxygen-icons.org)
        Лицензия - GNU Lesser General Public License
     */

    public static void alert(String text, Activity activity) {
        alert(text, activity, false);
    }

    public static void alert(String text, final Activity activity, final boolean parent_exit) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.title_warning)
                .setIcon(R.drawable.notification_icon)
                .setMessage(text)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                       dialog.cancel();
                       if (parent_exit) {
                           activity.finish();
                       }
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static void error(String text, Activity activity) {
        error(text, activity, false);
    }
    public static void error(String text, final Activity activity, final boolean parent_exit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.title_error)
                .setIcon(R.drawable.cancel_icon)
                .setMessage(text)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        if (parent_exit) {
                            activity.finish();
                        }
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "onResume");
        updateButtonsState();
    }

    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");
        do_destroy = true;
        super.onDestroy();
        if (isFinishing())
            System.exit(0);
    }


    private void updateButtonsState() {
        if (settings.get(PREF_ENC_PRIVATE_KEY).equals("")) {
            btnInit.setEnabled(true);
            btnRegister.setEnabled(false);
        } else {
            btnInit.setEnabled(false);
            btnRegister.setEnabled(true);
        }
    }

    @Override
    public boolean onPassword(String password) {
        if (sign == null) {
            sign = new Sign(this);
        } else {
            sign.cache_reset();
        }
        Log.d("MainActivity", "setPassword");
        sign.setPassword(password);
        if (sign.pvt_key_present()) {
            Log.d("MainActivity", "pvt_key_present true");
            return(true);
        } else {
            Log.d("MainActivity", "Error about wrong password");

            return(false);
        }
    }

    public static void initSign(String password, Context context) {
        if (sign == null) {
            sign = new Sign(context);
        } else {
            sign.cache_reset();
        }
        sign.setPassword(password);
    }

    public static boolean isInternetPresent(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null)
                for (NetworkInfo anInfo : info)
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
        }
        return false;
    }
}
