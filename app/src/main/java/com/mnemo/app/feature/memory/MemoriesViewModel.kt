package com.mnemo.app.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MemoriesViewModel(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
        observeCount()
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        loadMemories(newQuery)
    }

    fun loadMemories(query: String = _uiState.value.searchQuery) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = if (query.isBlank()) {
                    // Load all or recent memories
                    memoryRepository.search("", limit = 50)
                } else {
                    memoryRepository.search(query.trim(), limit = 50)
                }
                _uiState.update { it.copy(memories = results, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userNotification = "Failed to load memories: ${e.message}"
                    )
                }
            }
        }
    }

    fun addMemory(title: String, content: String, sourceType: DataSourceType) {
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
                _uiState.update {
                    it.copy(userNotification = "Saved \"${item.title}\" to encrypted memory")
                }
                loadMemories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userNotification = "Error adding memory: ${e.message}")
                }
            }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            try {
                memoryRepository.deleteById(id)
                _uiState.update {
                    it.copy(userNotification = "Memory item deleted")
                }
                loadMemories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userNotification = "Failed to delete: ${e.message}")
                }
            }
        }
    }

    /**
     * Seeds initial demo items clearly identified as sample data.
     * Complies with: "Use sample data only when clearly identified as demo data."
     */
    fun seedDemoData() {
        viewModelScope.launch {
            try {
                val demoItems = listOf(
                    MemoryItem(
                        id = "demo_01",
                        sourceType = DataSourceType.CONTACT,
                        title = "Rahul Sharma (Sample Data)",
                        content = "Phone: +91 9876543210. Senior Android Platform Architect.",
                        personName = "Rahul Sharma",
                        sourceUri = "content://contacts/demo/1"
                    ),
                    MemoryItem(
                        id = "demo_02",
                        sourceType = DataSourceType.CALENDAR,
                        title = "Mnemo Architecture Sync (Sample Data)",
                        content = "Review offline retrieval pipeline, local SQLite encryption, and Keystore envelopes with team.",
                        sourceUri = "content://calendar/events/demo/2"
                    ),
                    MemoryItem(
                        id = "demo_03",
                        sourceType = DataSourceType.MESSAGE,
                        title = "Office WiFi Password (Sample Data)",
                        content = "Guest WiFi SSID: MnemoHQ, Password: MnemoLocalEncrypted2026",
                        sourceUri = "content://sms/demo/3"
                    )
                )
                memoryRepository.insertAll(demoItems)
                _uiState.update {
                    it.copy(userNotification = "Seeded 3 sample data memories (clearly tagged as Demo Data)")
                }
                loadMemories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userNotification = "Error seeding demo data: ${e.message}")
                }
            }
        }
    }

    fun clearNotification() {
        _uiState.update { it.copy(userNotification = null) }
    }

    private fun observeCount() {
        viewModelScope.launch {
            try {
                memoryRepository.observeCount().collect { count ->
                    _uiState.update { it.copy(totalCount = count) }
                }
            } catch (_: Exception) {
                // Ignore collection errors during wipe
            }
        }
    }
}
