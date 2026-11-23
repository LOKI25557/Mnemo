package com.mnemo.app.ui.home

import com.mnemo.core.model.MemoryItem

data class HomeUiState(
    val totalMemoriesCount: Int = 0,
    val recentMemories: List<MemoryItem> = emptyList(),
    val isEncrypted: Boolean = true
)
