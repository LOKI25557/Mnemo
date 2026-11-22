package com.mnemo.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.mnemo.core.model.security.KeyStoreProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class KeystoreSecurityLevel {
    STRONGBOX,
    TRUSTED_ENVIRONMENT,
    SOFTWARE,
    UNKNOWN_OR_UNAVAILABLE
}

/**
 * Android Keystore implementation for hardware-backed master key management.
 *
 * Uses AES-256 in Galois/Counter Mode (GCM) with no padding.
 * Keys are generated inside the Android Keystore (TEE or StrongBox) and never leave secure hardware.
 *
 * Master Key Alias: Strictly operates on [KeyStoreProvider.DEFAULT_KEY_ALIAS] ("mnemo_database_master_key").
 * Provides backward-compatibility fallback to [KeyStoreProvider.LEGACY_KEY_ALIASES] if an earlier installation
 * created keys under legacy names.
 */
class AndroidKeyStoreProvider : KeyStoreProvider {

    private val lock = Any()

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            ensureMasterKeyExists(KeyStoreProvider.DEFAULT_KEY_ALIAS)
            val key = resolveSecretKey(KeyStoreProvider.DEFAULT_KEY_ALIAS) ?: return false
            val factory = javax.crypto.SecretKeyFactory.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyInfo = factory.getKeySpec(
                key,
                android.security.keystore.KeyInfo::class.java
            ) as? android.security.keystore.KeyInfo ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX ||
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspects the underlying security module backing the specified Keystore key.
     * Distinguishes StrongBox, TEE, software emulation, or unavailable.
     */
    fun getSecurityLevel(alias: String = KeyStoreProvider.DEFAULT_KEY_ALIAS): KeystoreSecurityLevel {
        return try {
            ensureMasterKeyExists(alias)
            val key = resolveSecretKey(alias) ?: return KeystoreSecurityLevel.UNKNOWN_OR_UNAVAILABLE
            val factory = javax.crypto.SecretKeyFactory.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyInfo = factory.getKeySpec(
                key,
                android.security.keystore.KeyInfo::class.java
            ) as? android.security.keystore.KeyInfo ?: return KeystoreSecurityLevel.UNKNOWN_OR_UNAVAILABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeystoreSecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeystoreSecurityLevel.TRUSTED_ENVIRONMENT
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeystoreSecurityLevel.SOFTWARE
                    else -> KeystoreSecurityLevel.UNKNOWN_OR_UNAVAILABLE
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) {
                    KeystoreSecurityLevel.TRUSTED_ENVIRONMENT
                } else {
                    KeystoreSecurityLevel.SOFTWARE
                }
            }
        } catch (_: Exception) {
            KeystoreSecurityLevel.UNKNOWN_OR_UNAVAILABLE
        }
    }

    /**
     * Ensures that the authoritative AES-256-GCM master key exists in Android Keystore,
     * generating it if neither the authoritative nor legacy key exists.
     */
    fun ensureMasterKeyExists(alias: String = KeyStoreProvider.DEFAULT_KEY_ALIAS) {
        synchronized(lock) {
            if (resolveSecretKey(alias) == null) {
                generateMasterKey(alias)
            }
        }
    }

    override fun getOrCreateMasterKey(alias: String): ByteArray {
        ensureMasterKeyExists(alias)
        throw UnsupportedOperationException(
            "Hardware-backed Android Keystore AES keys are non-exportable (key.encoded == null). " +
            "Direct raw key extraction is forbidden. Use encrypt() and decrypt() for envelope encryption."
        )
    }

    override fun encrypt(plainText: ByteArray): ByteArray {
        require(plainText.isNotEmpty()) { "Plaintext to encrypt must not be empty" }
        synchronized(lock) {
            ensureMasterKeyExists(KeyStoreProvider.DEFAULT_KEY_ALIAS)
            val key = resolveSecretKey(KeyStoreProvider.DEFAULT_KEY_ALIAS)
                ?: throw IllegalStateException("Master key not found in Android Keystore")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv ?: throw IllegalStateException("Cipher failed to generate IV")
            val encrypted = cipher.doFinal(plainText)

            // Prepend 12-byte IV to ciphertext
            val result = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
            return result
        }
    }

    override fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > GCM_IV_LENGTH) { "Ciphertext too short to contain IV and auth tag" }
        synchronized(lock) {
            val key = resolveSecretKey(KeyStoreProvider.DEFAULT_KEY_ALIAS)
                ?: throw IllegalStateException("Master key not found in Android Keystore")

            val iv = ByteArray(GCM_IV_LENGTH)
            val actualCipherText = ByteArray(cipherText.size - GCM_IV_LENGTH)
            System.arraycopy(cipherText, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(cipherText, GCM_IV_LENGTH, actualCipherText, 0, actualCipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            return cipher.doFinal(actualCipherText)
        }
    }

    override fun deleteKey(alias: String): Boolean {
        synchronized(lock) {
            return try {
                var deletedAny = false
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                    deletedAny = true
                }
                // Also clean any legacy aliases if deleting default alias
                if (alias == KeyStoreProvider.DEFAULT_KEY_ALIAS) {
                    for (legacy in KeyStoreProvider.LEGACY_KEY_ALIASES) {
                        if (keyStore.containsAlias(legacy)) {
                            keyStore.deleteEntry(legacy)
                            deletedAny = true
                        }
                    }
                }
                deletedAny
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun generateMasterKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        // Attempt StrongBox if supported on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
                return
            } catch (_: Exception) {
                // Fallback to standard hardware-backed Keystore
            }
        }

        val fallbackBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(fallbackBuilder.build())
        keyGenerator.generateKey()
    }

    private fun resolveSecretKey(alias: String): SecretKey? {
        // 1. Check primary requested alias
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        // 2. If primary is default alias, check legacy aliases for backward compatibility
        if (alias == KeyStoreProvider.DEFAULT_KEY_ALIAS) {
            for (legacyAlias in KeyStoreProvider.LEGACY_KEY_ALIASES) {
                if (keyStore.containsAlias(legacyAlias)) {
                    val entry = keyStore.getEntry(legacyAlias, null) as? KeyStore.SecretKeyEntry
                    if (entry != null) return entry.secretKey
                }
            }
        }
        return null
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
