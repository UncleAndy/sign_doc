package org.gplvote.signdoc;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class DoDecrypt extends GetPassActivity {
    private static Settings settings;
    private String enc_text;
    private DoDecryptTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        setContentView(R.layout.do_decrypt);
        settings = Settings.getInstance(this);

        enc_text = getIntent().getStringExtra("EncryptedText");

        if (enc_text != null && !enc_text.equals("")) {
            checkPasswordDlgShow(settings);
            if (MainActivity.sign != null && MainActivity.sign.pvt_key_present()) {
                run_decrypt_task();
            }
        }
    }

    @Override
    public boolean onPassword(String password) {
        boolean result;
        if (result = super.onPassword(password)) {
            run_decrypt_task();
        }
        return(result);
    }

    private void run_decrypt_task() {
        task = new DoDecryptTask();
        task.execute(enc_text);
    }

    private void return_result(String text) {
        Intent intent = new Intent();
        intent.putExtra("DECRYPTED_TEXT", text);
        setResult(RESULT_OK, intent);
        finish();
    }

    class DoDecryptTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            // Расшифровывем текст
            String dec_data = null;
            try {
                dec_data = new String(MainActivity.sign.decrypt(params[0]), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            return(dec_data);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            DoDecrypt.this.return_result(result);

            task = null;

            DoDecrypt.this.finish();
        }
    }
}
