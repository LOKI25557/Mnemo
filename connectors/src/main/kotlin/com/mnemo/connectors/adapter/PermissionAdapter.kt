package com.mnemo.connectors.adapter

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Adapter utility for managing and verifying Android runtime permissions.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class PermissionAdapter(
    private val context: Context
) {
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermissionsForFeature(feature: String): List<String> {
        return when (feature.lowercase()) {
            "contacts" -> listOf(android.Manifest.permission.READ_CONTACTS)
            "calendar" -> listOf(android.Manifest.permission.READ_CALENDAR)
            "voice" -> listOf(android.Manifest.permission.RECORD_AUDIO)
            else -> emptyList()
        }
    }
}
