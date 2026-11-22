package com.mnemo.app.feature.voice

/**
 * UI state for local voice capture and synthesis.
 * Assigned to Pravee (Android UI Lead).
 */
data class VoiceInteractionUiState(
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null
)
