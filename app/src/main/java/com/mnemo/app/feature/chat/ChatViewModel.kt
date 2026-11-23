package com.mnemo.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.repository.MemoryRepository
import com.mnemo.intelligence.AssistantEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChatViewModel(
    private val assistantEngine: AssistantEngine,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Initial assistant greeting
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "Hello! I am Mnemo, your on-device private assistant. All your queries and memories stay strictly on this device.\n\n" +
                               "Ask me about your contacts, calendar, messages, files, or saved memories. " +
                               "You can also save information by typing \"Remember: <text>\" or using the Save Memory action."
                    )
                )
            )
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun sendMessage() {
        val rawQuery = _uiState.value.inputText.trim()
        if (rawQuery.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = rawQuery
        )

        // Clear input and append user message
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                if (isGreeting(rawQuery)) {
                    val greetingMessage = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "Hello! I am Mnemo, your private on-device assistant. I operate completely offline with no network permissions. How can I help you today?"
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + greetingMessage,
                            isLoading = false
                        )
                    }
                } else if (isMemorySaveIntent(rawQuery)) {
                    val memoryContent = extractMemoryContent(rawQuery)
                    val memoryItem = MemoryItem(
                        id = "mem_${UUID.randomUUID().toString().take(8)}",
                        sourceType = DataSourceType.MESSAGE,
                        title = memoryContent.take(40).ifBlank { "User Memory" },
                        content = memoryContent,
                        timestamp = System.currentTimeMillis()
                    )
                    // Persist to encrypted local database
                    memoryRepository.insert(memoryItem)

                    val assistantMessage = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "Memory saved to encrypted local storage:\n\n\"${memoryItem.content}\"",
                        sources = listOf(memoryItem.toSourceReference())
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + assistantMessage,
                            isLoading = false
                        )
                    }
                } else {
                    // Send query to AssistantEngine
                    val result = assistantEngine.processQuery(rawQuery)

                    // If engine found no sources, check if query contains natural language memory search phrases
                    val finalMessage = if (result.sources.isEmpty()) {
                        val searchPhrase = extractSearchPhrase(rawQuery)
                        val fallbackItems = if (searchPhrase.isNotBlank() && searchPhrase != rawQuery.lowercase(Locale.ROOT)) {
                            memoryRepository.search(searchPhrase)
                        } else emptyList()

                        if (fallbackItems.isNotEmpty()) {
                            val summary = buildString {
                                append("Found ${fallbackItems.size} item(s) related to \"$rawQuery\":\n\n")
                                fallbackItems.take(3).forEachIndexed { index, item ->
                                    append("${index + 1}. ")
                                    if (item.personName != null) {
                                        append("${item.personName} — ")
                                    }
                                    append("${item.title}: ${item.content.take(120)}\n")
                                }
                            }.trim()

                            ChatMessage(
                                sender = MessageSender.ASSISTANT,
                                text = summary,
                                sources = fallbackItems.map { it.toSourceReference() }
                            )
                        } else {
                            ChatMessage(
                                sender = MessageSender.ASSISTANT,
                                text = result.message,
                                sources = result.sources,
                                isError = !result.isSuccess,
                                isConfirmationRequired = result.requiresConfirmation
                            )
                        }
                    } else {
                        ChatMessage(
                            sender = MessageSender.ASSISTANT,
                            text = result.message,
                            sources = result.sources,
                            isError = !result.isSuccess,
                            isConfirmationRequired = result.requiresConfirmation
                        )
                    }

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + finalMessage,
                            isLoading = false,
                            errorMessage = if (!result.isSuccess && finalMessage.sources.isEmpty()) result.error?.message else null
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to process query on device"
                val assistantMessage = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Unable to process command: $errorMsg",
                    isError = true
                )
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + assistantMessage,
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    fun saveDirectMemory(title: String, content: String, sourceType: DataSourceType = DataSourceType.MESSAGE) {
        if (title.isBlank() && content.isBlank()) return

        viewModelScope.launch {
            try {
                val item = MemoryItem(
                    id = "mem_${UUID.randomUUID().toString().take(8)}",
                    sourceType = sourceType,
                    title = title.ifBlank { "Memory Note" },
                    content = content.ifBlank { title },
                    timestamp = System.currentTimeMillis()
                )
                memoryRepository.insert(item)

                val assistantMessage = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Saved memory: \"${item.title}\"\n${item.content}",
                    sources = listOf(item.toSourceReference())
                )
                _uiState.update { state ->
                    state.copy(messages = state.messages + assistantMessage)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(errorMessage = "Could not save memory: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearChat() {
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "Chat history cleared. You can ask any new question."
                    )
                )
            )
        }
    }

    private fun isGreeting(query: String): Boolean {
        val clean = query.trim().lowercase(Locale.ROOT).trimEnd('.', '!', '?')
        return clean == "hello" || clean == "hi" || clean == "hey" ||
               clean == "hello mnemo" || clean == "hi mnemo" || clean == "hey mnemo" ||
               clean == "good morning" || clean == "good afternoon" || clean == "good evening"
    }

    private fun isMemorySaveIntent(query: String): Boolean {
        val lower = query.lowercase(Locale.ROOT).trim()
        return lower.startsWith("remember:") ||
                lower.startsWith("remember that ") ||
                lower.startsWith("save memory:") ||
                lower.startsWith("save:") ||
                lower.startsWith("note:")
    }

    private fun extractMemoryContent(query: String): String {
        val prefixes = listOf("remember:", "remember that ", "save memory:", "save:", "note:")
        for (prefix in prefixes) {
            if (query.startsWith(prefix, ignoreCase = true)) {
                return query.substring(prefix.length).trim()
            }
        }
        return query.trim()
    }

    private fun extractSearchPhrase(query: String): String {
        var text = query.trim().trimEnd('.', '?', '!')
        val prefixes = listOf(
            "find my ", "find ", "search for my ", "search for ", "search ",
            "where is my ", "where is ", "what is my ", "what is ",
            "show me my ", "show me ", "show ",
            "do you remember my ", "do you remember ",
            "tell me about my ", "tell me about "
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.substring(prefix.length).trim()
                break
            }
        }
        return text.trim()
    }
}
