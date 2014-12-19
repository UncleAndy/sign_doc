package org.gplvote.signdoc;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class InitKey extends Activity implements OnClickListener {
    public static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    public static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;

    static final String RSA_KEYS_TAG = "RSA";
    static final String AES_KEYS_TAG = "AES";
    static final String AES_HASH_TAG = "SHA1PRNG";
    static final int DEFAULT_KEY_BITS = 2048;

    private Settings settings;

    private EditText edtPassword;
    private Button btnBegin;

    private static InitKeyTask init_key_task;
    private static ProgressDialog init_key_pd;
    private static boolean running;

    @Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.init_key);

        edtPassword = (EditText) findViewById(R.id.edtPassword);
        btnBegin = (Button) findViewById(R.id.btnBegin);
        btnBegin.setOnClickListener(this);

        settings = Settings.getInstance();

        if (running) {
            edtPassword.setEnabled(false);
            btnBegin.setEnabled(false);

            init_key_pd = new ProgressDialog(InitKey.this);
            init_key_pd.setTitle(getString(R.string.title_init));
            init_key_pd.setMessage(getString(R.string.init_start));
            init_key_pd.show();
        }

        if (init_key_pd != null)
            init_key_pd.show();
	  }

    @Override
    public void onClick(View v) {
        Editable pass = edtPassword.getText();
        edtPassword.setEnabled(false);
        btnBegin.setEnabled(false);
        running = true;

        if (is_password_valid(pass.toString())) {
            Log.d("BTN_STATE_INIT", "private_key = " + settings.get(PREF_ENC_PRIVATE_KEY));

            if (!settings.get(PREF_ENC_PRIVATE_KEY).equals("")) {
                MainActivity.error(getString(R.string.init_already), InitKey.this);
            } else {
                createRSAKeysPair();
            }
        } else {
            MainActivity.error(getString(R.string.err_bad_password), InitKey.this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if ((init_key_pd != null) && init_key_pd.isShowing())
            init_key_pd.dismiss();
        init_key_pd = null;
    }

    private void createRSAKeysPair() {
        Editable pass = edtPassword.getText();
        edtPassword.setEnabled(false);

        init_key_task = new InitKeyTask();
        init_key_task.execute(pass.toString());
    }

    private boolean is_password_valid(String password) {
        return(true);
    }

    class TaskResult {
        String error_str = null;
        String pass = null;
    }

    class InitKeyTask extends AsyncTask<String, Void, TaskResult> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Показываем Progress
            init_key_pd = new ProgressDialog(InitKey.this);
            init_key_pd.setTitle(getString(R.string.title_init));
            init_key_pd.setMessage(getString(R.string.init_start));
            init_key_pd.show();
        }

        @Override
        protected TaskResult doInBackground(String... params) {
            // Формируем подпись пару ключей пользователя
            TaskResult result = new TaskResult();
            result.pass = params[0];

            Key publicKey = null;
            Key privateKey = null;

            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_KEYS_TAG);
                kpg.initialize(DEFAULT_KEY_BITS);
                KeyPair kp = kpg.genKeyPair();
                publicKey = kp.getPublic();
                privateKey = kp.getPrivate();

                // Сохраняем публичный ключ
                byte[] publicKeyBytes = publicKey.getEncoded();
                settings.set(PREF_PUBLIC_KEY, Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP));
                Log.d(RSA_KEYS_TAG, "Generated public key: "+Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP));

                // Шифруем и сохраняем приватный ключ
                SecretKeySpec sks = null;
                try {
                    int keyLength = 128;
                    byte[] keyBytes = new byte[keyLength / 8];
                    Arrays.fill(keyBytes, (byte) 0x0);
                    byte[] passwordBytes = params[0].getBytes("UTF-8");
                    int length = passwordBytes.length < keyBytes.length ? passwordBytes.length : keyBytes.length;
                    System.arraycopy(passwordBytes, 0, keyBytes, 0, length);
                    sks = new SecretKeySpec(keyBytes, AES_KEYS_TAG);

                    byte[] encodedBytes = null;
                    try {
                        Cipher c = Cipher.getInstance(AES_KEYS_TAG);
                        c.init(Cipher.ENCRYPT_MODE, sks);
                        encodedBytes = c.doFinal(privateKey.getEncoded());

                        settings.set(PREF_ENC_PRIVATE_KEY, Base64.encodeToString(encodedBytes, Base64.NO_WRAP));
                        Log.d(RSA_KEYS_TAG, "Encrypted private key: "+Base64.encodeToString(encodedBytes, Base64.NO_WRAP));

                        finish();
                    } catch (Exception e) {
                        result.error_str = "encryption error";
                        Log.e(AES_KEYS_TAG, "encryption error");
                    }
                } catch (Exception e) {
                    result.error_str = "secret key spec error";
                    Log.e(AES_KEYS_TAG, "secret key spec error");
                }
            } catch (Exception e) {
                result.error_str = getString(R.string.err_keys_pair_create);
                Log.e(RSA_KEYS_TAG, "error create keys pair");
            }

            return(result);
        }

        @Override
        protected void onPostExecute(TaskResult result) {
            super.onPostExecute(result);

            running = false;

            if ((init_key_pd != null) && init_key_pd.isShowing())
                init_key_pd.dismiss();

            if (result.error_str == null) {
                MainActivity.initSign(result.pass);
            } else {
                MainActivity.error(result.error_str, InitKey.this);
            }

            init_key_task = null;
        }
    }
}
