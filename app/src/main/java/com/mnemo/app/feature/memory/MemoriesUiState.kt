package com.mnemo.app.feature.memory

import com.mnemo.core.model.MemoryItem

data class MemoriesUiState(
    val memories: List<MemoryItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val totalCount: Int = 0,
    val userNotification: String? = null
)
