package com.mnemo.app.feature.chat

import com.mnemo.core.model.SourceReference
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<SourceReference> = emptyList(),
    val isError: Boolean = false,
    val isConfirmationRequired: Boolean = false
)
