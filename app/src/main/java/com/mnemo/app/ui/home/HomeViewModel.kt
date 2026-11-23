package com.mnemo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemo.core.model.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMemoryCount()
        loadRecentMemories()
    }

    fun loadRecentMemories() {
        viewModelScope.launch {
            try {
                val recent = memoryRepository.search("", limit = 3)
                _uiState.update { it.copy(recentMemories = recent) }
            } catch (_: Exception) {
                // Ignore transient db errors
            }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            try {
                memoryRepository.deleteById(id)
                loadRecentMemories()
            } catch (_: Exception) {
                // Ignore transient db errors
            }
        }
    }

    private fun observeMemoryCount() {
        viewModelScope.launch {
            try {
                memoryRepository.observeCount().collect { count ->
                    _uiState.update { it.copy(totalMemoriesCount = count) }
                    loadRecentMemories()
                }
            } catch (_: Exception) {
                // Ignore during wipe
            }
        }
    }
}
