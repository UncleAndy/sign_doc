package com.example.signdoc;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public class InitKey extends Activity implements OnClickListener {
    public static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    public static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;

    static final String RSA_KEYS_TAG = "RSA";
    static final String AES_KEYS_TAG = "AES";
    static final String AES_HASH_TAG = "SHA1PRNG";
    static final int DEFAULT_KEY_BITS = 2048;

    private Settings settings;

    private TextView txtStatus;
    private Button btnBegin;
    private EditText edtPassword;


    @Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.init_key);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        edtPassword = (EditText) findViewById(R.id.edtPassword);
        btnBegin = (Button) findViewById(R.id.btnBegin);
        btnBegin.setOnClickListener(this);

        settings = Settings.getInstance();
	  }

    @Override
    public void onClick(View v) {
        Editable pass = edtPassword.getText();
        if (is_password_valid(pass.toString())) {
            Log.d("BTN_STATE_INIT", "private_key = " + settings.get(PREF_ENC_PRIVATE_KEY));

            //if (settings.get(PREF_ENC_PRIVATE_KEY) != "") {
            //    txtStatus.setText(R.string.init_already);
            //} else {
                createRSAKeysPair();
            //}
        } else {
            txtStatus.setText(R.string.err_bad_password);
        };
    }

    private void createRSAKeysPair() {
        Editable pass = edtPassword.getText();
        edtPassword.setEnabled(false);

        Key publicKey = null;
        Key privateKey = null;

        txtStatus.setText(R.string.init_start);
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
                byte[] passwordBytes = pass.toString().getBytes("UTF-8");
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

                    txtStatus.setText(R.string.init_keys_pair_created);
                } catch (Exception e) {
                    txtStatus.setText("encryption error");
                    Log.e(AES_KEYS_TAG, "encryption error");
                }
            } catch (Exception e) {
                txtStatus.setText("secret key spec error");
                Log.e(AES_KEYS_TAG, "secret key spec error");
            }
        } catch (Exception e) {
            txtStatus.setText(R.string.err_keys_pair_create);
            Log.e(RSA_KEYS_TAG, "error create keys pair");
        }

    }

    private boolean is_password_valid(String password) {
        return(true);
    }
}
