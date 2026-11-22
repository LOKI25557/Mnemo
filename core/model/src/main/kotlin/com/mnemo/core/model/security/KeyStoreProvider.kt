package com.mnemo.core.model.security

/**
 * Security abstraction contract for cryptographic operations using Android Keystore.
 * Implemented in `:core:security` (Nikhi's ownership).
 *
 * Keys are hardware-backed and never exported from secure hardware.
 */
interface KeyStoreProvider {
    /**
     * Checks if hardware-backed key storage is supported on this device.
     */
    fun isHardwareBacked(): Boolean

    /**
     * Generates or retrieves an existing AES-256 GCM key stored in Android Keystore.
     *
     * @param alias Keystore alias identifier.
     */
    fun getOrCreateMasterKey(alias: String = DEFAULT_KEY_ALIAS): ByteArray

    /**
     * Encrypts plaintext bytes using authenticated AES-GCM cipher.
     */
    fun encrypt(plainText: ByteArray): ByteArray

    /**
     * Decrypts ciphertext bytes using authenticated AES-GCM cipher.
     */
    fun decrypt(cipherText: ByteArray): ByteArray

    /**
     * Permanently revokes and deletes the master key entry from Android Keystore.
     * Used for cryptographic erasure during application reset or secure data wipe.
     *
     * @param alias Keystore alias identifier.
     * @return True if the key entry was found and deleted, false otherwise.
     */
    fun deleteKey(alias: String = DEFAULT_KEY_ALIAS): Boolean

    companion object {
        /**
         * Authoritative master key alias used for envelope encryption of the database passphrase.
         * All generation, encryption, decryption, invalidation, and erasure MUST reference this alias.
         */
        const val DEFAULT_KEY_ALIAS = "mnemo_database_master_key"

        /**
         * Legacy key aliases recognized for backward compatibility and sanitization during secure wipe.
         */
        val LEGACY_KEY_ALIASES = listOf(
            "mnemo_master_database_key",
            "mnemo_db_master_key"
        )
    }
}
