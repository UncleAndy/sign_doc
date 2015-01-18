package org.gplvote.signdoc;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Sign {
    private static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    private static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    private static final String RSA_KEYS_TAG = "RSA";
    private static final String RSA_DECRYPT_TAG = "RSA/None/PKCS1Padding";
    private static final String AES_KEYS_TAG = "AES";
    private static final String SIGN_ALG_TAG = "SHA1withRSA";

    private Settings settings;

    private byte[] cache_pvt_key_enc = null;
    private PublicKey pub_key = null;
    private byte[] cache_aes_key = null;

    public Sign(Context context) {
        settings = Settings.getInstance(context);
        restorePublicKey();
    }

    boolean setPassword(String password) {
        if (pvt_key_present()) { return(true); }

        // Формируем из пароля ключ для расшифровки AES
        try {
            int keyLength = 128;
            byte[] keyBytes = new byte[keyLength / 8];
            Arrays.fill(keyBytes, (byte) 0x0);
            byte[] passwordBytes = password.getBytes("UTF-8");
            int length = passwordBytes.length < keyBytes.length ? passwordBytes.length : keyBytes.length;
            System.arraycopy(passwordBytes, 0, keyBytes, 0, length);
            return(restorePrivateKey(new SecretKeySpec(keyBytes, AES_KEYS_TAG)));
        } catch (Exception e) {
            Log.e("setPassword", "error restore pvt key");
        }

        return(false);
    }

    public byte[] create(byte[] data) {
        if (!pvt_key_present()) { return(null); }

        byte[] signed = null;
        try {
            Signature signInstance = Signature.getInstance(SIGN_ALG_TAG);
            signInstance.initSign(pvt_key_from_cache());
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
        if (!pvt_key_present()) { return(null); }

        byte[] data = null;
        try {
            Cipher rsa = Cipher.getInstance(RSA_DECRYPT_TAG);
            rsa.init(Cipher.DECRYPT_MODE, pvt_key_from_cache());
            if (enc_data.length <= 256) {
                data = rsa.doFinal(enc_data);
            } else {
                // Use RSA+AES decoding

                // Decode AES+IV over RSA
                byte[] aes_key_plus_iv = rsa.doFinal(enc_data, 0, 256);
                byte[] aes_key = new byte[32];
                byte[] aes_iv  = new byte[16];
                System.arraycopy(aes_key_plus_iv, 0, aes_key, 0, 32);
                System.arraycopy(aes_key_plus_iv, 32, aes_iv, 0, 16);

                // Decode big data over AES/CBC
                SecretKeySpec sks = new SecretKeySpec(aes_key, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(aes_iv);
                Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aes.init(Cipher.DECRYPT_MODE, sks, ivSpec);
                data = aes.doFinal(enc_data, 256, (enc_data.length - 256));
            }
            if (data != null)
                Log.d("DATA", "Decrypted bytes: " + data.length);
        } catch (Exception e) {
            e.printStackTrace();
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

    // Cache private key (encrypted)
    public void cache_reset() {
        if (cache_pvt_key_enc != null) Arrays.fill(cache_pvt_key_enc, (byte) 0 );
        if (cache_aes_key != null) Arrays.fill(cache_aes_key, (byte) 0 );
        cache_pvt_key_enc = null;
        cache_aes_key = null;
    }

    public boolean pvt_key_present() { return(cache_pvt_key_enc != null); }

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

    private boolean restorePrivateKey(SecretKeySpec sks) {
        if (pvt_key_present()) { return(true); }

        String b64_enc_pvt_key = settings.get(PREF_ENC_PRIVATE_KEY);
        byte[] enc_pvt_key = Base64.decode(b64_enc_pvt_key, Base64.NO_WRAP);

        byte[] dec_pvt_key;
        try {
            Cipher c = Cipher.getInstance(AES_KEYS_TAG);
            c.init(Cipher.DECRYPT_MODE, sks);
            dec_pvt_key = c.doFinal(enc_pvt_key);

            pvt_key_to_cache(KeyFactory.getInstance(RSA_KEYS_TAG).generatePrivate(new PKCS8EncodedKeySpec(dec_pvt_key)));
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "Restore private key error: "+e.getMessage());
        }

        return(pvt_key_present());
    }

    private PrivateKey pvt_key_from_cache() {
        try {
            SecretKeySpec aes_key_sks = new SecretKeySpec(cache_aes_key, "AES");

            byte[] decodedBytes = null;
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, aes_key_sks);
            return(KeyFactory.getInstance(RSA_KEYS_TAG).generatePrivate(new PKCS8EncodedKeySpec(c.doFinal(cache_pvt_key_enc))));
        } catch (Exception e) {
            Log.e("PVT_KEY_FROM_CACHE", "AES encryption error: "+e.getLocalizedMessage());
        }
        return(null);
    }

    private void pvt_key_to_cache(PrivateKey key) throws NoSuchAlgorithmException {
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(Long.toString(System.currentTimeMillis()).getBytes());
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256, sr);
            SecretKeySpec aes_key_sks = new SecretKeySpec((kg.generateKey()).getEncoded(), "AES");
            cache_pvt_key_enc = null;
            cache_aes_key = aes_key_sks.getEncoded();

            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, aes_key_sks);
            cache_pvt_key_enc = c.doFinal(key.getEncoded());
        } catch (Exception e) {
            Log.e("PVT_KEY_TO_CACHE", "AES encryption error: "+e.getLocalizedMessage());
        }
    }
}
