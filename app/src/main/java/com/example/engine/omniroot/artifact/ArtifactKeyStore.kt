package com.example.engine.omniroot.artifact

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware/TEE-backed KeyStore manager for Omnivian Artifact passkeys.
 * - Stores encrypted passkeys in private SharedPreferences using an AndroidKeyStore master AES key.
 * - Computes SHA-256 hash for storage in Room (used for artifact verification).
 * - Decrypts original passkey when needed for unlock() and requestPasskey().
 */
object ArtifactKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "OmnivianArtifactMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PREFS_NAME = "artifact_keystore_prefs"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypts and saves the original passkey for an artifact provider.
     */
    fun savePasskey(context: Context, providerId: String, rawPasskey: String) {
        if (rawPasskey.isEmpty()) {
            removePasskey(context, providerId)
            return
        }
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(rawPasskey.toByteArray(Charsets.UTF_8))

            // Pack IV + ciphertext into base64
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("passkey_$providerId", encoded).apply()
        } catch (e: Exception) {
            com.example.utils.LogKeeper.log("ArtifactKeyStore", "EncryptError", "Failed to encrypt passkey for $providerId: ${e.message}")
        }
    }

    /**
     * Retrieves and decrypts the original passkey for an artifact provider.
     */
    fun getPasskey(context: Context, providerId: String): String? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encoded = prefs.getString("passkey_$providerId", null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            com.example.utils.LogKeeper.log("ArtifactKeyStore", "DecryptError", "Failed to decrypt passkey for $providerId: ${e.message}")
            return null
        }
    }

    /**
     * Removes the encrypted passkey from storage.
     */
    fun removePasskey(context: Context, providerId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("passkey_$providerId").apply()
    }

    /**
     * Computes the SHA-256 hash of a passkey for Room storage and artifact verification.
     */
    fun hashPasskey(rawPasskey: String): String {
        if (rawPasskey.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawPasskey.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * General AES-256-GCM encryption for Part 2 Secrets Bundles.
     * Encrypts plaintext string and returns Base64 encoded (IV + ciphertext).
     */
    fun encryptString(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * General AES-256-GCM decryption for Part 2 Secrets Bundles.
     * Decrypts Base64 encoded (IV + ciphertext) and returns plaintext string.
     */
    fun decryptString(encryptedBase64: String): String? {
        if (encryptedBase64.isEmpty()) return null
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            com.example.utils.LogKeeper.log("ERROR", "ArtifactKeyStore", "Failed to decrypt data payload: ${e.message}")
            null
        }
    }
}
