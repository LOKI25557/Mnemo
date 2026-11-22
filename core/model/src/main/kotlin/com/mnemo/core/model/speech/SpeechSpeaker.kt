package com.mnemo.core.model.speech

/**
 * On-device text-to-speech speaker interface.
 * Strictly local synthesis via Android TextToSpeech engine.
 */
interface SpeechSpeaker {
    /**
     * Checks if local TTS engine is ready and available.
     */
    fun isAvailable(): Boolean

    /**
     * Speaks the provided text to the user.
     */
    suspend fun speak(text: String)

    /**
     * Immediately stops active speech output.
     */
    fun stop()
}
