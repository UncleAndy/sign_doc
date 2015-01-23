package org.gplvote.signdoc;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class RegisterSign extends GetPassActivity implements OnClickListener {
    static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    static final String RSA_KEYS_TAG = "RSA";
    static final String AES_KEYS_TAG = "AES";
    static final String AES_HASH_TAG = "SHA1PRNG";
    public static final String HTTP_SEND_URL = MainActivity.HTTP_SEND_URL;

    private static Settings settings;

    private EditText edSite;
    private EditText edCode;
    private Button btnReady;

    private static ProgressDialog send_pd;
    private static SendRegisterTask send_task;
    private static boolean running;

    private static Uri external_calc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        Log.d("REGISTER_SIGN", "onCreate");
	    setContentView(R.layout.register_sign);

        settings = Settings.getInstance(this);

        edSite = (EditText) findViewById(R.id.edSite);
        edCode = (EditText) findViewById(R.id.edCode);
        btnReady = (Button) findViewById(R.id.btnReady); btnReady.setOnClickListener(this);

        if (running) {
            enableInputElements(false);

            send_pd = new ProgressDialog(RegisterSign.this);
            send_pd.setTitle(getString(R.string.title_send));
            send_pd.setMessage(getString(R.string.msg_status_start_deliver));
            send_pd.show();
        } else {
            Intent i = getIntent();
            if (i != null) external_calc = i.getData();

            if (external_calc != null && external_calc.getScheme().equals("signreg")) {
                if (!MainActivity.isInternetPresent(this)) {
                    MainActivity.error(getString(R.string.err_internet_connection_absent), this, true);
                    return;
                }

                if (MainActivity.sign != null && MainActivity.sign.pvt_key_present()) {
                    if (send_task == null) {
                        send_task = new SendRegisterTask();
                        send_task.execute(external_calc.getQueryParameter("site"), external_calc.getQueryParameter("code"), "http://" + external_calc.getHost() + external_calc.getPath());
                    }
                } else {
                    checkPasswordDlgShow(settings);
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        running = true;
        enableInputElements(false);

        // Check input fields
        if (MainActivity.isInternetPresent(this)) {
            String errors = "";
            String sep = "";
            String site = edSite.getText().toString().trim();
            String code = edCode.getText().toString().trim();
            if (site.isEmpty()) {
                errors = getString(R.string.err_site_required);
                sep = "\n";
            }
            if (code.isEmpty()) {
                errors += sep + getString(R.string.err_code_required);
            }

            if (errors.isEmpty()) {
                if (send_task == null) {
                    send_task = new SendRegisterTask();
                    send_task.execute(site, code);
                }
            } else {
                MainActivity.alert(errors, this);
                running = false;
                enableInputElements(true);
            }
        } else {
            MainActivity.error(getString(R.string.err_internet_connection_absent), this);
            running = false;
            enableInputElements(true);
        }
    }


    @Override
    public boolean onPassword(String password) {
        boolean result;
        if (result = super.onPassword(password)) {
            if (send_task == null) {
                send_task = new SendRegisterTask();
                send_task.execute(external_calc.getQueryParameter("site"), external_calc.getQueryParameter("code"), "http://"+external_calc.getHost()+external_calc.getPath());
            }
        }
        return(result);
    }

    @Override
    public void onPause() {
        super.onPause();

        if ((send_pd != null) && send_pd.isShowing())
            send_pd.dismiss();
        send_pd = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }

    protected void enableInputElements(boolean value) {
        edCode.setEnabled(true);
        edSite.setEnabled(true);
        btnReady.setEnabled(true);
    }

    class SendRegisterTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Показываем Progress
            send_pd = new ProgressDialog(RegisterSign.this);
            send_pd.setTitle(getString(R.string.title_send));
            send_pd.setMessage(getString(R.string.msg_status_start_deliver));
            send_pd.show();
        }

        @Override
        protected String doInBackground(String... params) {
            String result = null;

            DocSignRegistration doc = new DocSignRegistration();
            doc.site = params[0].trim();
            doc.code = params[1].trim();
            String register_url = "";
            if ((params.length > 2) && (params[2] != null)) register_url = params[2].trim();

            // Извлекаем публичный ключ
            doc.public_key = MainActivity.sign.getPublicKeyBase64();

            // Формируем ЭЦП документа
            String sign_data = doc.code;
            byte[] b_sign = MainActivity.sign.create(sign_data.getBytes());

            if (b_sign == null) {
                result = getString(R.string.err_wrong_password);
            } else {
                doc.sign = Base64.encodeToString(b_sign, Base64.NO_WRAP);

                Log.d("REGSIGN", "Register URL =  "+register_url);

                result = HTTPActions.deliver(doc.toJson(), RegisterSign.this, register_url);
                Log.d("SIGN_REGISTER", "Sign doc: "+doc.toJson());
            }

            return(result);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            running = false;

            if ((send_pd != null) && send_pd.isShowing())
                send_pd.dismiss();

            if (result == null) {
                MainActivity.alert(getString(R.string.msg_reg_sign_delivered), RegisterSign.this, true);
            } else {
                MainActivity.error(result, RegisterSign.this);

                enableInputElements(true);
            }

            send_task = null;
        }
    }
}
