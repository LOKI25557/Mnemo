package com.mnemo.core.model.security

/**
 * Contract for secure local data erasure.
 * Guarantees cryptographic wipe and file overwrite for zero data remanence.
 */
interface SecureEraser {
    /**
     * Wipes all application databases, caches, and Keystore keys.
     *
     * @return True if wipe completed successfully.
     */
    suspend fun secureWipeAll(): Boolean
}
