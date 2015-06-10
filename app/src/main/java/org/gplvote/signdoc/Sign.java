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
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// TODO: Добавить методы для шифрования сообщений публичным ключем так-же как для расшифровки
public class Sign {
    private static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    private static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    private static final String PREF_CANCEL_ENC_PRIVATE_KEY = MainActivity.PREF_CANCEL_ENC_PRIVATE_KEY;
    private static final String PREF_CANCEL_PUBLIC_KEY = MainActivity.PREF_CANCEL_PUBLIC_KEY;

    private static final String RSA_KEYS_TAG = "RSA";
    private static final String RSA_DECRYPT_TAG = "RSA/None/PKCS1Padding";
    private static final String RSA_ENCRYPT_TAG = "RSA/None/PKCS1Padding";
    private static final String AES_KEYS_TAG = "AES";
    public static final String SIGN_ALG_TAG = "SHA256withRSA";

    private Settings settings;

    private byte[] cache_pvt_key_enc = null;
    private PublicKey pub_key = null;
    private byte[] cache_aes_key = null;
    private byte[] cache_cancel_pvt_key = null;

    public Sign(Context context) {
        settings = Settings.getInstance(context);
        restorePublicKey();
    }

    boolean setPassword(String password) {
        if (pvt_key_present()) { return(true); }

        // Формируем из пароля ключ для расшифровки AES
        try {
            byte[] passwordBytes = password.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(passwordBytes);
            byte[] keyBytes = md.digest();
            SecretKeySpec aes_restore_key = new SecretKeySpec(keyBytes, AES_KEYS_TAG);
            restoreCancelPrivateKey(aes_restore_key);
            return(restorePrivateKey(aes_restore_key));
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

    public byte[] encrypt(byte[] data_bytes, byte[] pub_key_bytes) {
        if (pub_key_bytes == null || data_bytes == null) { return(null); }

        byte[] enc_data = null;
        try {
            PublicKey pub = KeyFactory.getInstance(RSA_KEYS_TAG).generatePublic(new X509EncodedKeySpec(pub_key_bytes));

            Cipher rsa = Cipher.getInstance(RSA_ENCRYPT_TAG);
            rsa.init(Cipher.ENCRYPT_MODE, pub);
            if (data_bytes.length <= 256) {
                enc_data = new byte[256];
                enc_data = rsa.doFinal(data_bytes);
            } else {
                // Use RSA+AES encoding

                // HEADER
                byte[] header = new byte[80];
                // Random AES key with random Initial Vector
                SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
                byte[] aes_key_bytes = sr.generateSeed(32);
                byte[] aes_iv_bytes = sr.generateSeed(16);

                // CRC of data
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(data_bytes);
                byte[] crc_data = md.digest();

                // Encode header over RSA
                System.arraycopy(aes_key_bytes, 0, header, 0, 32);
                System.arraycopy(aes_iv_bytes, 0, header, 32, 16);
                System.arraycopy(crc_data, 0, header, 48, 32);
                byte[] enc_header = rsa.doFinal(header);

                // Encode data over AES
                SecretKeySpec sks = new SecretKeySpec(aes_key_bytes, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(aes_iv_bytes);
                Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aes.init(Cipher.ENCRYPT_MODE, sks, ivSpec);
                byte[] enc_data_block = aes.doFinal(data_bytes);

                enc_data = new byte[enc_header.length + enc_data_block.length];

                System.arraycopy(enc_header, 0, enc_data, 0, enc_header.length);
                System.arraycopy(enc_data_block, 0, enc_data, enc_header.length, enc_data_block.length);
            }
            if (enc_data != null)
                Log.d("DATA", "Encrypted bytes: " + enc_data.length);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return(enc_data);
    }

    public String encrypt_base64(String data, String pub_key_base64) {
        return(Base64.encodeToString(encrypt(data.getBytes(), Base64.decode(pub_key_base64, Base64.NO_WRAP)), Base64.NO_WRAP));
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
                byte[] data_crc  = new byte[32];
                System.arraycopy(aes_key_plus_iv, 0, aes_key, 0, 32);
                System.arraycopy(aes_key_plus_iv, 32, aes_iv, 0, 16);
                System.arraycopy(aes_key_plus_iv, 48, data_crc, 0, 32);

                // Decode big data over AES/CBC
                SecretKeySpec sks = new SecretKeySpec(aes_key, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(aes_iv);
                Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aes.init(Cipher.DECRYPT_MODE, sks, ivSpec);
                data = aes.doFinal(enc_data, 256, (enc_data.length - 256));

                // Check crc for decrypted data
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(data);
                byte[] crc = md.digest();

                if (!Arrays.equals(data_crc, crc)) {
                    data = null;
                }
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


    public String getCancelPublicKeyBase64() {
        return(settings.get(PREF_CANCEL_PUBLIC_KEY));
    }

    public byte[] getCancelPublicKey()
    {
        String b64_pub_key = getCancelPublicKeyBase64();
        return(Base64.decode(b64_pub_key, Base64.NO_WRAP));
    }

    public byte[] getCancelPublicKeyId() {
        MessageDigest digest;
        byte[] hash;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            hash = digest.digest(getCancelPublicKey());
            return(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e("SHA-256", "Hash create error: "+e.getMessage());
        }

        return(null);
    }

    public byte[] getCancelPvtKey() { return(cache_cancel_pvt_key); }

    public String getCancelPvtKeyBase64() {
        if (cache_cancel_pvt_key != null) {
            return (Base64.encodeToString(cache_cancel_pvt_key, Base64.NO_WRAP));
        } else {
            return(null);
        }
    }

    public String getCancelPublicKeyIdBase64() {
        byte[] hash = getCancelPublicKeyId();
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
    public boolean cancel_pvt_key_present() { return(cache_cancel_pvt_key != null); }

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

    private boolean restoreCancelPrivateKey(SecretKeySpec sks) {
        if (cancel_pvt_key_present()) { return(true); }

        String b64_enc_pvt_key = settings.get(PREF_CANCEL_ENC_PRIVATE_KEY);
        byte[] enc_pvt_key = Base64.decode(b64_enc_pvt_key, Base64.NO_WRAP);

        byte[] dec_pvt_key;
        try {
            Cipher c = Cipher.getInstance(AES_KEYS_TAG);
            c.init(Cipher.DECRYPT_MODE, sks);
            dec_pvt_key = c.doFinal(enc_pvt_key);

            cache_cancel_pvt_key = KeyFactory.getInstance(RSA_KEYS_TAG).generatePrivate(new PKCS8EncodedKeySpec(dec_pvt_key)).getEncoded();
        } catch (Exception e) {
            Log.e(RSA_KEYS_TAG, "Restore cancel private key error: "+e.getMessage());
        }

        return(cancel_pvt_key_present());
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
            // sr.setSeed(Long.toString(System.currentTimeMillis()).getBytes()); // Not secured
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
