package com.mnemo.core.database.security

/**
 * Key provider interface for encrypted SQLite database operation with SQLCipher.
 *
 * Implements SQLCipher compatibility: supplies 256-bit passphrase protected by
 * hardware-backed Android Keystore directly to SQLCipher's [SupportOpenHelperFactory].
 */
interface DatabaseEncryptionKeyProvider {
    /**
     * Retrieves the existing 256-bit passphrase, or generates and securely persists a new one
     * if this is the first application launch.
     *
     * @return 32-byte array containing the raw 256-bit SQLCipher passphrase.
     * @throws DatabaseSecurityException if key retrieval, verification, or decryption fails.
     */
    fun getPassphrase(): ByteArray

    /**
     * Reports whether encrypted database operation is currently active.
     */
    fun isEncryptionActive(): Boolean

    /**
     * Checks if encrypted key material already exists on disk.
     */
    fun hasKeyMaterial(): Boolean

    /**
     * Securely destroys key material by overwriting the file with zeros before deletion.
     */
    fun destroyKeyMaterial()
}

/** Base security exception for database encryption errors. */
open class DatabaseSecurityException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

/** Thrown when key material is missing from storage and cannot be regenerated without data loss. */
class MissingKeyMaterialException(message: String) : DatabaseSecurityException(message)

/** Thrown when key file header, format, or metadata is corrupted or tampered. */
class CorruptedKeyMaterialException(message: String, cause: Throwable? = null) : DatabaseSecurityException(message, cause)

/** Thrown when Keystore decryption fails due to key invalidation, tag mismatch, or tampering. */
class KeyAuthenticationException(message: String, cause: Throwable? = null) : DatabaseSecurityException(message, cause)
