package com.example.signdoc;

import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Sign {
    private static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    private static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    private static final String RSA_KEYS_TAG = "RSA";
    private static final String RSA_DECRYPT_TAG = "RSA/None/PKCS1Padding";
    private static final String AES_KEYS_TAG = "AES";
    private static final String SIGN_ALG_TAG = "SHA1withRSA";

    private Settings settings;

    private SecretKeySpec sks = null;
    private PrivateKey pvt_key = null;
    private PublicKey pub_key = null;

    public Sign(String password) {
        settings = Settings.getInstance();
        setPassword(password);
        restorePublicKey();
    }

    boolean setPassword(String password) {
        // Формируем из пароля ключ для расшифровки AES
        try {
            int keyLength = 128;
            byte[] keyBytes = new byte[keyLength / 8];
            Arrays.fill(keyBytes, (byte) 0x0);
            byte[] passwordBytes = password.getBytes("UTF-8");
            int length = passwordBytes.length < keyBytes.length ? passwordBytes.length : keyBytes.length;
            System.arraycopy(passwordBytes, 0, keyBytes, 0, length);
            sks = new SecretKeySpec(keyBytes, AES_KEYS_TAG);
        } catch (Exception e) {
            Log.e(AES_KEYS_TAG, "secret key spec error");
        }

        return(sks != null);
    }

    public byte[] create(byte[] data) {
        if (!restorePrivateKey()) { return(null); }

        byte[] signed = null;
        try {
            Signature signInstance = Signature.getInstance(SIGN_ALG_TAG);
            signInstance.initSign(pvt_key);
            signInstance.update(data);
            signed = signInstance.sign();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return(signed);
    }

    public String createBase64(byte[] data) {
        byte[] sign = create(data);
        if (sign != null) {
            return (Base64.encodeToString(sign, Base64.NO_WRAP));
        } else {
            return(null);
        }
    }

    public byte[] decrypt(byte[] enc_data) {
        if (!restorePrivateKey()) { return(null); }

        byte[] data = null;
        try {
            Cipher c = Cipher.getInstance(RSA_DECRYPT_TAG);
            c.init(Cipher.DECRYPT_MODE, pvt_key);
            data = c.doFinal(enc_data);
            Log.d("DATA", "Decrypted bytes: " + data.length);
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "RSA decryption error");
        }

        return(data);
    }

    public byte[] decrypt(String b64_enc_data) {
        try {
            return(decrypt(Base64.decode(b64_enc_data, Base64.NO_WRAP)));
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "Decrypt error: "+e.getMessage());
            return(null);
        }
    }

    public String getPublicKeyBase64() {
        return(settings.get(PREF_PUBLIC_KEY));
    }

    public byte[] getPublicKey()
    {
        String b64_pub_key = getPublicKeyBase64();
        return(Base64.decode(b64_pub_key, Base64.NO_WRAP));
    }

    public byte[] getPublicKeyId() {
        MessageDigest digest;
        byte[] hash;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            hash = digest.digest(getPublicKey());
            return(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e("SHA-256", "Hash create error: "+e.getMessage());
        }

        return(null);
    }

    public String getPublicKeyIdBase64() {
        byte[] hash = getPublicKeyId();
        if (hash != null) {
            return (Base64.encodeToString(hash, Base64.NO_WRAP));
        }
        return(null);
    }

    // PRIVATE

    private boolean restorePublicKey() {
        if (pub_key != null) { return(true); }

        String b64_pub_key = settings.get(PREF_PUBLIC_KEY);
        byte[] b_pub_key = Base64.decode(b64_pub_key, Base64.NO_WRAP);

        try {
            pub_key = KeyFactory.getInstance(RSA_KEYS_TAG).generatePublic(new X509EncodedKeySpec(b_pub_key));
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "Restore public key error: "+e.getMessage());
        }

        return(pub_key != null);
    }

    private boolean restorePrivateKey() {
        if (pvt_key != null) { return(true); }

        String b64_enc_pvt_key = settings.get(PREF_ENC_PRIVATE_KEY);
        byte[] enc_pvt_key = Base64.decode(b64_enc_pvt_key, Base64.NO_WRAP);

        byte[] dec_pvt_key;
        try {
            Cipher c = Cipher.getInstance(AES_KEYS_TAG);
            c.init(Cipher.DECRYPT_MODE, sks);
            dec_pvt_key = c.doFinal(enc_pvt_key);

            pvt_key = KeyFactory.getInstance(RSA_KEYS_TAG).generatePrivate(new PKCS8EncodedKeySpec(dec_pvt_key));
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "Restore private key error: "+e.getMessage());
        }

        return(pvt_key != null);
    }
}
