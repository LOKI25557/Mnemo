package com.mnemo.app.feature.assistant

import com.mnemo.core.model.AssistantResult

/**
 * State representing assistant interaction stream, answers, and source citations.
 * Assigned to Pravee (Android UI Lead).
 */
data class AssistantInteractionState(
    val query: String = "",
    val isLoading: Boolean = false,
    val currentResult: AssistantResult? = null,
    val history: List<AssistantResult> = emptyList()
)
