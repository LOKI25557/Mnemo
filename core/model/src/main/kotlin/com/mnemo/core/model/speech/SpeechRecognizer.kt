package com.mnemo.core.model.speech

import kotlinx.coroutines.flow.Flow

/**
 * On-device speech recognition interface.
 * Strictly local — no audio is ever transmitted off the device.
 */
interface SpeechRecognizer {
    /**
     * Checks if local on-device speech recognition is available on this Android device.
     */
    fun isAvailable(): Boolean

    /**
     * Starts listening to user voice input and emits recognized text segments.
     */
    fun startListening(): Flow<String>

    /**
     * Stops active voice capture.
     */
    suspend fun stopListening()
}
