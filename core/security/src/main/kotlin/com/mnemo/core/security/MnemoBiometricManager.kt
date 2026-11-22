package com.mnemo.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.mnemo.core.model.security.BiometricAuthManager

/**
 * Biometric authentication adapter wrapping AndroidX BiometricManager and BiometricPrompt.
 */
class MnemoBiometricManager(
    private val context: Context
) : BiometricAuthManager {

    private val biometricManager = BiometricManager.from(context)

    override fun canAuthenticate(): Boolean {
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    override fun getAvailabilityStatus(): String {
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Ready and available"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware available"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware currently unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled by user"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required"
            else -> "Unsupported or locked"
        }
    }

    override suspend fun authenticate(title: String, subtitle: String): Boolean {
        // In Phase 0, checks hardware readiness. Full UI prompt lifecycle is invoked
        // from FragmentActivity/MainActivity in presentation layer.
        return canAuthenticate()
    }
}
