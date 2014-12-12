package com.example.signdoc;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public class RegisterSign extends FragmentActivity implements OnClickListener, GetPassInterface {
    static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    static final String RSA_KEYS_TAG = "RSA";
    static final String AES_KEYS_TAG = "AES";
    static final String AES_HASH_TAG = "SHA1PRNG";

    private static Settings settings;

    private EditText edSite;
    private EditText edCode;
    private Button btnReady;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.register_sign);

        settings = Settings.getInstance();

        edSite = (EditText) findViewById(R.id.edSite);
        edCode = (EditText) findViewById(R.id.edCode);
        btnReady = (Button) findViewById(R.id.btnReady); btnReady.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        // Check input fields
        String errors = "";
        String sep = "";
        if (edSite.getText().toString().trim().isEmpty()) {
            errors = getString(R.string.err_site_required);
            sep = "\n";
        };
        if (edCode.getText().toString().trim().isEmpty()) {
            errors += sep + getString(R.string.err_code_required);
        };

        if (!errors.isEmpty()) {
            MainActivity.alert(errors, this);
        } else {
            DialogFragment dlgPassword = new DlgPassword(this);
            dlgPassword.show(getSupportFragmentManager(), "missiles");
        };
    }

    public void onPassword(String password) {
        // Формируем документ для регистрации подписи
        DocSignRegistration doc = new DocSignRegistration();
        doc.site = edSite.getText().toString().trim();
        doc.code = edCode.getText().toString().trim();

        // Расшифровываем приватный ключ
        Sign sign = new Sign(password);
        doc.public_key = sign.getPublicKeyBase64();

        // Формируем ЭЦП документа
        String sign_data = doc.code;
        byte[] b_sign = sign.create(sign_data.getBytes());

        if (b_sign == null) {
            MainActivity.alert(getString(R.string.err_wrong_password), this);
        } else {
            doc.sign = Base64.encodeToString(b_sign, Base64.NO_WRAP);

            // Запускаем отправку если все в норме
            Intent intent = new Intent(this, Sender.class);
            intent.putExtra("Doc", doc.toJson());
            startActivity(intent);
            Log.d("SIGN", "Sign doc: "+doc.toJson());
        };
    }
}
