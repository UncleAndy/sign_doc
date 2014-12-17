package org.gplvote.signdoc;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class RegisterSign extends FragmentActivity implements OnClickListener {
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

	@Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        Log.d("REGISTER_SIGN", "onCreate");
	    setContentView(R.layout.register_sign);

        settings = Settings.getInstance();

        edSite = (EditText) findViewById(R.id.edSite);
        edCode = (EditText) findViewById(R.id.edCode);
        btnReady = (Button) findViewById(R.id.btnReady); btnReady.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        // Check input fields
        if (MainActivity.isInternetPresent(this)) {
            String errors = "";
            String sep = "";
            if (edSite.getText().toString().trim().isEmpty()) {
                errors = getString(R.string.err_site_required);
                sep = "\n";
            };
            if (edCode.getText().toString().trim().isEmpty()) {
                errors += sep + getString(R.string.err_code_required);
            };

            if (errors.isEmpty()) {
                send_register_sign();
            } else {
                MainActivity.alert(errors, this);
            };
        } else {
            MainActivity.error(getString(R.string.err_internet_connection_absent), this);
        }
    }

    public void send_register_sign() {
        // Формируем документ для регистрации подписи
        DocSignRegistration doc = new DocSignRegistration();
        doc.site = edSite.getText().toString().trim();
        doc.code = edCode.getText().toString().trim();

        // Извлекаем публичный ключ
        doc.public_key = MainActivity.sign.getPublicKeyBase64();

        // Формируем ЭЦП документа
        String sign_data = doc.code;
        byte[] b_sign = MainActivity.sign.create(sign_data.getBytes());

        if (b_sign == null) {
            MainActivity.alert(getString(R.string.err_wrong_password), this);
        } else {
            doc.sign = Base64.encodeToString(b_sign, Base64.NO_WRAP);

            HTTPActions.deliver(doc.toJson(), this, true);

            // Запускаем отправку если все в норме
            //Intent intent = new Intent(this, Sender.class);
            //intent.putExtra("Doc", doc.toJson());
            //startActivity(intent);
            Log.d("SIGN", "Sign doc: "+doc.toJson());
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("REGISTER_SIGN", "onResume");
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d("REGISTER_SIGN", "onStart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("REGISTER_SIGN", "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("REGISTER_SIGN", "onStop");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("REGISTER_SIGN", "onRestart");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("REGISTER_SIGN", "onDestroy");
    }

}
