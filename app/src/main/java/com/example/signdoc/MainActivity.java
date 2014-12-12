package com.example.signdoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {
    public static final String PREF_ENC_PRIVATE_KEY = "enc_priv_key";
    public static final String PREF_PUBLIC_KEY = "pub_key";
    public static final String APP_PREFERENCES = "org.gplvote.signdoc";
    public static final String HTTP_SEND_URL = "http://signdoc.gplvote.org/send";
    public static final String HTTP_GET_URL = "http://signdoc.gplvote.org/get";

    private static SharedPreferences sPref;
    private static Settings settings;

    private Button btnInit;
    private Button btnRegister;
    private Button btnCheckNew;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        
        btnInit = (Button) findViewById(R.id.btnInit); btnInit.setOnClickListener(this);
        btnRegister = (Button) findViewById(R.id.btnRegister); btnRegister.setOnClickListener(this);
        btnCheckNew = (Button) findViewById(R.id.btnCheckNew); btnCheckNew.setOnClickListener(this);

        if (sPref == null) { sPref = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE); };
        settings = Settings.getInstance();
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
    	  intent = new Intent(this, RegisterSign.class);
          startActivity(intent);
          break;
      case R.id.btnCheckNew:
    	  intent = new Intent(this, Receiver.class);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.title_warning)
                .setIcon(R.drawable.notification_icon)
                .setMessage(text)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static void error(String text, Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.title_error)
                .setIcon(R.drawable.cancel_icon)
                .setMessage(text)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonsState();
    }

    private void updateButtonsState() {
        if (settings.get(PREF_ENC_PRIVATE_KEY) == "") {
            btnInit.setEnabled(true);
            btnRegister.setEnabled(false);
            btnCheckNew.setEnabled(false);
        } else {
            btnInit.setEnabled(false);
            btnRegister.setEnabled(true);
            btnCheckNew.setEnabled(true);
        };
    }
}
