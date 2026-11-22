package com.mnemo.app.feature.settings

/**
 * State for user settings and local indexing controls.
 */
data class SettingsUiState(
    val isBubbleOverlayEnabled: Boolean = false,
    val isBiometricLockEnabled: Boolean = false,
    val contactsIndexingEnabled: Boolean = false,
    val calendarIndexingEnabled: Boolean = false,
    val notificationsIndexingEnabled: Boolean = false,
    val dataRetentionDays: Int = 30
)
