package org.gplvote.signdoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    public static final String APP_PREFERENCES = "org.gplvote.signdoc";
    public static final String HTTP_SEND_URL = "http://signdoc.gplvote.org/send";
    public static final String HTTP_GET_URL = "http://signdoc.gplvote.org/get";

    private static SharedPreferences sPref;
    private static Settings settings;
    private static String current_action;
    public static Sign sign = null;

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

        if ((settings.get(PREF_ENC_PRIVATE_KEY) != "") && (sign == null || !sign.pvt_key_present())) {
            DialogFragment dlgPassword = new DlgPassword(this);
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
          if (isInternetPresent(this)) {
              intent = new Intent(this, RegisterSign.class);
              startActivity(intent);
          } else {
              MainActivity.error(getString(R.string.err_internet_connection_absent), this);
          }
          break;
      case R.id.btnCheckNew:
          // DBG!!!
          // DocsStorage.clear_docs(this.getApplicationContext());
          if (isInternetPresent(this)) {
              receive();
          } else {
              MainActivity.error(getString(R.string.err_internet_connection_absent), this);
          }
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
        updateButtonsState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.exit(0);
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

    private void receive() {
        String document;
        DocRequestForUser request = new DocRequestForUser();
        ArrayList<DocSignRequest> requests_list = new ArrayList<DocSignRequest>();

        // Формируем документ для запроса (публичный ключ, идентификатор публичного ключа, время с которого проверять документы)
        request.public_key = sign.getPublicKeyBase64();
        request.public_key_id = sign.getPublicKeyIdBase64();
        request.sign = sign.createBase64(sign.getPublicKeyId());
        request.from_time = (System.currentTimeMillis() / 1000L) - (24 * 3600); // Проверяем за сутки
        document = request.toJson();

        if (request.sign == null) {
            MainActivity.error(getString(R.string.err_wrong_password), this);
        } else {

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(HTTP_GET_URL);

            try {
                // Add your data
                Log.d("HTTP", "User request: " + document);
                StringEntity se = new StringEntity(document);
                httppost.setEntity(se);
                httppost.setHeader("Accept", "application/json");
                httppost.setHeader("Content-type", "application/json");

                // Execute HTTP Post Request
                HttpResponse response = httpclient.execute(httppost);

                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                int http_status = response.getStatusLine().getStatusCode();
                if (http_status == 200) {
                    Gson gson = new Gson();
                    JsonResponse json_resp = gson.fromJson(body, JsonResponse.class);
                    if (json_resp.status == 0) {
                        // Загружаем список документов и в цикле вдыаем Activity для запроса подписи
                        if ((json_resp.documents != null) && (json_resp.documents.length > 0)) {
                            for (int i = 0; i < json_resp.documents.length; i++) {
                                DocSignRequest doc = json_resp.documents[i];
                                Log.d("HTTP", "Request sign document: " + doc.toJson());

                                // В doc.data сожержится base64(encrypt(json(['val1', 'val2', ...])))

                                byte[] json_data = sign.decrypt(doc.data);
                                if (json_data != null) {
                                    doc.dec_data = new String(json_data, "UTF-8");

                                    Log.d("Receiver", "Doc after decode: " + gson.toJson(doc));

                                    // Собираем все новые запросы на подписание в массив
                                    if (DocsStorage.is_new(this.getApplicationContext(), doc)) {
                                        Log.d("Receiver", "New doc add to list");
                                        requests_list.add(doc);
                                        Log.d("Receiver", "List array after add: " + gson.toJson(requests_list));
                                    } else {
                                        Log.d("Receiver", "Already present - NOT ADD");
                                    }
                                } else {
                                    Log.e("DECRYPT", "Can not decrypt data from doc: "+doc.data);
                                }
                            }
                            if (requests_list.size() > 0) {
                                String json_requests_list = gson.toJson(requests_list);
                                Log.d("DATA", "Json docs list: " + json_requests_list);

                                Intent intent = new Intent(this, DoSign.class);
                                intent.putExtra("DocsList", json_requests_list);
                                startActivity(intent);
                            } else {
                                MainActivity.alert(getString(R.string.msg_status_no_new), this);
                            }
                        } else {
                            MainActivity.alert(getString(R.string.msg_status_no_new), this);
                        }
                    } else {
                        MainActivity.error(getString(R.string.err_http_send), this);
                        Log.e("HTTP", "Error HTTP response: " + body);
                    }
                } else {
                    MainActivity.error(getString(R.string.msg_status_http_error), this);
                    Log.e("HTTP", "HTTP response: " + http_status + " body: " + body);
                }
            } catch (Exception e) {
                MainActivity.error(getString(R.string.msg_status_http_error), this);
                Log.e("HTTP", "Error HTTP request: ", e);
            }
        }
    }

    @Override
    public boolean onPassword(String password) {
        if (sign == null) {
            sign = new Sign();
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

    public static void initSign(String password) {
        sign = new Sign();
        sign.setPassword(password);
    }

    public static boolean isInternetPresent(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null)
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED)
                    {
                        return true;
                    }
        }
        return false;
    }
}
