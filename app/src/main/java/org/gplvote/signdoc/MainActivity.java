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
    public static final String APP_PREFERENCES = "org.gplvote.signdoc";
    public static final String HTTP_SEND_URL = "http://signdoc.gplvote.org/send";
    public static final String HTTP_GET_URL = "http://signdoc.gplvote.org/get";

    private static SharedPreferences sPref;
    private static Settings settings;
    private static String current_action;
    public static Sign sign = null;
    private static DialogFragment dlgPassword = null;

    private Button btnInit;
    private Button btnRegister;
    private Button btnCheckNew;
    private Button btnDocsList;

    private ReceiverTask receiver;
    private ProgressDialog receiver_pd;

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
        btnCheckNew = (Button) findViewById(R.id.btnCheckNew); btnCheckNew.setOnClickListener(this);
        btnDocsList = (Button) findViewById(R.id.btnDocsList); btnDocsList.setOnClickListener(this);

        if (sPref == null) { sPref = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE); };
        settings = Settings.getInstance();

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
      case R.id.btnCheckNew:
          if (!(null == sign) && sign.pvt_key_present()) {
              if (isInternetPresent(this)) {
                  if (receiver == null) {
                      receiver = new ReceiverTask();
                      receiver.execute();
                  }
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
        Log.d("MAIN", "onResume");
        updateButtonsState();
    }

    @Override
    protected void onDestroy() {
        do_destroy = true;
        super.onDestroy();
        if (isFinishing())
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

    class ReceiverResult {
        String error_str = null;
        ArrayList<DocSignRequest> documents = new ArrayList<DocSignRequest>();
        Long time = null;
    }

    class ReceiverTask extends AsyncTask<Void, Void, ReceiverResult> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Показываем Progress
            receiver_pd = new ProgressDialog(MainActivity.this);
            receiver_pd.setTitle(getString(R.string.title_get_new_docs));
            receiver_pd.setMessage(getString(R.string.msg_status_received));
            receiver_pd.show();
        }

        @Override
        protected ReceiverResult doInBackground(Void... params) {
            ReceiverResult result = new ReceiverResult();
            String document;
            DocRequestForUser request = new DocRequestForUser();

            // Формируем документ для запроса (публичный ключ, идентификатор публичного ключа, время с которого проверять документы)
            request.public_key = sign.getPublicKeyBase64();
            request.public_key_id = sign.getPublicKeyIdBase64();
            request.sign = sign.createBase64(sign.getPublicKeyId());
            request.from_time = (System.currentTimeMillis() / 1000L) - (24 * 3600); // Проверяем за сутки

            // Если есть последнее время с сервера - с него
            String last_recv_time = settings.get("last_recv_time");
            if ((last_recv_time != null) && (last_recv_time != "")) {
                try {
                    Log.d("MAIN", "LastRecvTime = ["+last_recv_time+"]");
                    request.from_time = Long.parseLong(last_recv_time);
                } catch (Exception e) {
                    request.from_time = 0L;
                }
            }

            document = request.toJson();

            if (request.sign == null) {
                result.error_str = getString(R.string.err_wrong_password);
            } else {

                HttpClient httpclient = new DefaultHttpClient();
                HttpPost httppost = new HttpPost(HTTP_GET_URL);

                try {
                    // Add your data
                    Log.d("AsyncHTTP", "User request: " + document);
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
                            result.time = json_resp.time;
                            // Загружаем список документов и в цикле вдыаем Activity для запроса подписи
                            if ((json_resp.documents != null) && (json_resp.documents.length > 0)) {
                                for (int i = 0; i < json_resp.documents.length; i++) {
                                    DocSignRequest doc = json_resp.documents[i];
                                    Log.d("AsyncHTTP", "Request sign document: " + doc.toJson());

                                    // В doc.data сожержится base64(encrypt(json(['val1', 'val2', ...])))

                                    if (doc.type.equals("SIGN_REQUEST")) {
                                        byte[] json_data = sign.decrypt(doc.data);
                                        if (json_data != null) {
                                            doc.dec_data = new String(json_data, "UTF-8");

                                            // NO SECURE: Log.d("AsyncReceiver", "Doc after decode: " + gson.toJson(doc));

                                            // Собираем все новые запросы на подписание в массив
                                            if (DocsStorage.is_new(MainActivity.this.getApplicationContext(), doc)) {
                                                result.documents.add(doc);
                                            } else {
                                                Log.d("AsyncReceiver", "Already present - NOT ADD");
                                            }
                                        } else {
                                            Log.e("AsyncDECRYPT", "Can not decrypt data from doc: " + doc.data);
                                        }
                                    } else if (doc.type.equals("SIGN_CONFIRM")) {
                                        result.documents.add(doc);
                                    }
                                }

                                String json_requests_list = gson.toJson(result.documents);
                                // NO SECURE: Log.d("AsyncDATA", "Json docs list: " + json_requests_list);
                            }
                        } else {
                            result.error_str = getString(R.string.err_http_send);
                            Log.e("AsyncHTTP", "Error HTTP response: " + body);
                        }
                    } else {
                        result.error_str = getString(R.string.msg_status_http_error);
                        Log.e("AsyncHTTP", "HTTP response: " + http_status + " body: " + body);
                    }
                } catch (Exception e) {
                    result.error_str = getString(R.string.msg_status_http_error);
                    Log.e("AsyncHTTP", "Error HTTP request: ", e);
                }
            }

            return(result);
        }

        @Override
        protected void onPostExecute(ReceiverResult result) {
            super.onPostExecute(result);

            if (receiver_pd != null) receiver_pd.hide();
            receiver_pd = null;

            if (result.error_str == null) {
                // Сначала обрабатываем подтверждения об обработке
                boolean confirms_present = false;
                if (result.documents.size() > 0) {
                    for (int i = 0; i < result.documents.size(); i++) {
                        DocSignRequest doc = result.documents.get(i);
                        if (doc.type.equals("SIGN_CONFIRM")) {
                            result.documents.remove(i);
                            i--;

                            Log.d("RECEIVER", "SIGN_CONFIRM document present " + doc.doc_id);
                            DocsStorage.set_confirm(MainActivity.this, doc.site, doc.doc_id);
                            confirms_present = true;
                        }
                    }
                }

                if (result.documents.size() > 0) {
                    Gson gson = new Gson();
                    Intent intent = new Intent(MainActivity.this, DoSign.class);
                    intent.putExtra("DocsList", gson.toJson(result.documents));
                    intent.putExtra("LastRecvTime", result.time.toString());
                    startActivity(intent);
                } else if (confirms_present) {
                    Intent intent;
                    intent = new Intent(MainActivity.this, DocsList.class);
                    startActivity(intent);
                } else {
                    settings.set("last_recv_time", result.time.toString());
                    MainActivity.alert(getString(R.string.msg_status_no_new), MainActivity.this);
                }
            } else {
                MainActivity.error(result.error_str, MainActivity.this);
            }

            receiver = null;
        }
    }
}
