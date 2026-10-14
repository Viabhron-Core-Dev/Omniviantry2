package com.example.engine.db

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.utils.LogKeeper
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed KeyStore manager for database encryption keys (Phase 13).
 *
 * Implements Android KeyStore AES-256 master key derivation inside hardware TEE / StrongBox.
 * Generates and encrypts a 256-bit cryptographically secure database passphrase.
 * Strictly adheres to Credential Immunity: zero plaintext keys on disk, secure in-memory wipe patterns,
 * and zero credentials logged.
 */
object DatabaseKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "OmnivianDatabaseMasterKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PREFS_NAME = "database_keystore_prefs"
    private const val PREF_KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val PREF_KEY_IV = "enc_db_iv"
    private const val PREF_KEY_HARDWARE_LEVEL = "db_hw_security_level"

    enum class SecurityLevel {
        STRONGBOX,
        TEE_HARDWARE,
        SOFTWARE_FALLBACK
    }

    /**
     * Retrieves or generates the master secret key inside AndroidKeyStore.
     */
    @Synchronized
    private fun getOrCreateMasterKey(context: Context): Pair<SecretKey, SecurityLevel> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val levelName = prefs.getString(PREF_KEY_HARDWARE_LEVEL, SecurityLevel.TEE_HARDWARE.name)
            val level = try { SecurityLevel.valueOf(levelName ?: SecurityLevel.TEE_HARDWARE.name) } catch (e: Exception) { SecurityLevel.TEE_HARDWARE }
            return Pair(secretKey, level)
        }

        // Key doesn't exist; generate new key
        var keyGenerator: KeyGenerator? = null
        var securityLevel = SecurityLevel.TEE_HARDWARE

        // Attempt StrongBox on Android P+ (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val strongBoxSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(true)
                    .build()
                keyGenerator.init(strongBoxSpec)
                val key = keyGenerator.generateKey()
                securityLevel = SecurityLevel.STRONGBOX
                LogKeeper.log("DatabaseKeyStore", "KeyGen", "Master AES-256 key generated in hardware StrongBox")
                saveSecurityLevel(context, securityLevel)
                return Pair(key, securityLevel)
            } catch (e: Exception) {
                LogKeeper.log("DatabaseKeyStore", "StrongBoxFallback", "StrongBox unavailable, falling back to standard TEE")
            }
        }

        // Standard TEE KeyStore generation
        try {
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val teeSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(teeSpec)
            val key = keyGenerator.generateKey()
            securityLevel = SecurityLevel.TEE_HARDWARE
            LogKeeper.log("DatabaseKeyStore", "KeyGen", "Master AES-256 key generated in Hardware TEE")
            saveSecurityLevel(context, securityLevel)
            return Pair(key, securityLevel)
        } catch (e: Exception) {
            LogKeeper.log("DatabaseKeyStore", "KeyGenFailure", "KeyStore generation error: ${e.javaClass.simpleName}")
            throw SecurityException("Failed to generate hardware-backed master key", e)
        }
    }

    private fun saveSecurityLevel(context: Context, level: SecurityLevel) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_HARDWARE_LEVEL, level.name).apply()
    }

    /**
     * Gets the current hardware security level for the database key.
     */
    fun getHardwareSecurityLevel(context: Context): SecurityLevel {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val levelName = prefs.getString(PREF_KEY_HARDWARE_LEVEL, null)
        return if (levelName != null) {
            try { SecurityLevel.valueOf(levelName) } catch (e: Exception) { SecurityLevel.TEE_HARDWARE }
        } else {
            getOrCreateMasterKey(context).second
        }
    }

    /**
     * Retrieves the 256-bit database encryption passphrase from KeyStore.
     * Generates a new 256-bit random passphrase if not previously created.
     */
    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encPassphraseBase64 = prefs.getString(PREF_KEY_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(PREF_KEY_IV, null)

        val (masterKey, _) = getOrCreateMasterKey(context)

        if (encPassphraseBase64 != null && ivBase64 != null) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
                val encryptedBytes = Base64.decode(encPassphraseBase64, Base64.NO_WRAP)
                val decrypted = cipher.doFinal(encryptedBytes)
                LogKeeper.log("DatabaseKeyStore", "KeyRecall", "Database passphrase decrypted from hardware KeyStore")
                return decrypted
            } catch (e: Exception) {
                LogKeeper.log("DatabaseKeyStore", "DecryptError", "Failed to decrypt stored passphrase: ${e.javaClass.simpleName}")
                throw SecurityException("Database master passphrase decryption failed", e)
            }
        }

        // Generate brand new 256-bit (32 bytes) cryptographically random passphrase
        val secureRandom = SecureRandom()
        val rawPassphrase = ByteArray(32)
        secureRandom.nextBytes(rawPassphrase)

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(rawPassphrase)

            prefs.edit()
                .putString(PREF_KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(PREF_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()

            LogKeeper.log("DatabaseKeyStore", "NewPassphrase", "New 256-bit database passphrase encrypted and stored via KeyStore")
            return rawPassphrase
        } catch (e: Exception) {
            wipeBytes(rawPassphrase)
            LogKeeper.log("DatabaseKeyStore", "EncryptError", "Failed to encrypt new passphrase: ${e.javaClass.simpleName}")
            throw SecurityException("Database master passphrase encryption failed", e)
        }
    }

    /**
     * Securely clears a byte array in memory by overwriting with zeros.
     */
    fun wipeBytes(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }

    /**
     * Checks if a database passphrase has already been provisioned.
     */
    fun isPassphraseProvisioned(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_KEY_ENCRYPTED_PASSPHRASE) && prefs.contains(PREF_KEY_IV)
    }
}
