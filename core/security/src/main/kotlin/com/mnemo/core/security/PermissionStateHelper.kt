package com.mnemo.core.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Diagnostic and permission status helper.
 * Never assumes permission is granted; inspects runtime state safely.
 */
class PermissionStateHelper(
    private val context: Context
) {
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionSummary(): Map<String, Boolean> {
        return mapOf(
            "Contacts" to isPermissionGranted(android.Manifest.permission.READ_CONTACTS),
            "Calendar" to isPermissionGranted(android.Manifest.permission.READ_CALENDAR),
            "Microphone" to isPermissionGranted(android.Manifest.permission.RECORD_AUDIO)
        )
    }
}
