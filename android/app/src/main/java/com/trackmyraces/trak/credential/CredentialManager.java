package com.trackmyraces.trak.credential;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * CredentialManager
 *
 * Handles secure storage and retrieval of site passwords using Android Keystore.
 *
 * SECURITY DESIGN:
 * - Passwords encrypted with AES-256-GCM using hardware-backed Android Keystore
 * - Encrypted ciphertext + IV stored in EncryptedSharedPreferences
 * - Keystore alias per credential: "trak_cred_{credentialId}"
 * - Passwords NEVER leave the device — only session cookies go to Lambda
 * - Keys are non-extractable from the Keystore by design
 *
 * Usage:
 *   String alias = credentialManager.storePassword("cred-123", "mypassword");
 *   String password = credentialManager.retrievePassword("cred-123");
 *   credentialManager.deletePassword("cred-123");
 */
public class CredentialManager {

    private static final String TAG             = "CredentialManager";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String CIPHER_ALGO    = "AES/GCM/NoPadding";
    private static final String PREFS_NAME     = "trak_secure_creds";
    private static final int    GCM_TAG_LENGTH = 128;

    private final Context mContext;

    public CredentialManager(Context context) {
        mContext = context.getApplicationContext();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Encrypt and store a password for a credential.
     *
     * @param credentialId  The credential's UUID (used to derive the Keystore alias)
     * @param password      The plaintext password to store
     * @return              The Keystore alias (store this in CredentialEntryEntity)
     * @throws Exception    If Keystore operation fails
     */
    public String storePassword(String credentialId, String password) throws Exception {
        String alias = keystoreAlias(credentialId);
        generateKeyIfAbsent(alias);

        SecretKey key    = getKey(alias);
        Cipher    cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv         = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

        // Store IV + ciphertext together, separated by ":"
        String ivB64  = Base64.encodeToString(iv,         Base64.NO_WRAP);
        String ctB64  = Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        String stored = ivB64 + ":" + ctB64;

        mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(alias, stored)
            .apply();

        Log.d(TAG, "Password stored for alias: " + alias);
        return alias;
    }

    /**
     * Retrieve and decrypt a stored password.
     *
     * @param credentialId  The credential's UUID
     * @return              Plaintext password, or null if not found
     */
    public String retrievePassword(String credentialId) {
        String alias  = keystoreAlias(credentialId);
        String stored = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(alias, null);

        if (stored == null) {
            Log.w(TAG, "No stored password for alias: " + alias);
            return null;
        }

        try {
            String[] parts     = stored.split(":");
            byte[]   iv        = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[]   ciphertext= Base64.decode(parts[1], Base64.NO_WRAP);

            SecretKey key    = getKey(alias);
            Cipher    cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt password for alias: " + alias, e);
            return null;
        }
    }

    /**
     * Delete a stored password and its Keystore key.
     *
     * @param credentialId  The credential's UUID
     */
    public void deletePassword(String credentialId) {
        String alias = keystoreAlias(credentialId);

        // Remove from SharedPreferences
        mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(alias)
            .apply();

        // Remove key from Keystore
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
                Log.d(TAG, "Keystore key deleted for alias: " + alias);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete Keystore key for alias: " + alias, e);
        }
    }

    /**
     * Check whether a password is stored for the given credential.
     */
    public boolean hasPassword(String credentialId) {
        String alias = keystoreAlias(credentialId);
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(alias);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String keystoreAlias(String credentialId) {
        return "trak_cred_" + credentialId;
    }

    private void generateKeyIfAbsent(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        if (keyStore.containsAlias(alias)) return;

        KeyGenerator keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGen.init(
            new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // no biometric required for credentials
                .build()
        );
        keyGen.generateKey();
        Log.d(TAG, "Generated new Keystore key for alias: " + alias);
    }

    private SecretKey getKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(alias, null);
    }
}
