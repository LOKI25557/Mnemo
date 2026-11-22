package com.mnemo.core.model.security

/**
 * Biometric authentication contract abstracting Android BiometricPrompt.
 * Implemented in `:core:security` (Nikhi's ownership).
 */
interface BiometricAuthManager {
    /**
     * Checks if biometric hardware is present, enrolled, and ready for authentication.
     */
    fun canAuthenticate(): Boolean

    /**
     * Diagnostic string indicating why biometric auth is unavailable, if any.
     */
    fun getAvailabilityStatus(): String

    /**
     * Prompts the user for biometric authentication (fingerprint, face, or secure device credential).
     *
     * @param title Prompt dialog title.
     * @param subtitle Prompt dialog subtitle.
     * @return True if authentication succeeded, false otherwise.
     */
    suspend fun authenticate(title: String, subtitle: String): Boolean
}
