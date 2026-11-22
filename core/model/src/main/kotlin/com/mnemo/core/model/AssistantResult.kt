package com.mnemo.core.model

/**
 * Standardized result returned by assistant operations and tool executions.
 *
 * @property isSuccess True if the requested operation or query succeeded.
 * @property message User-readable natural language message or answer summary.
 * @property sources Traceable list of local on-device sources used to form the answer.
 * @property error Structured error detail when [isSuccess] is false.
 * @property requiresConfirmation Indicates if follow-up confirmation is required from the user.
 * @property data Optional typed payload of retrieved items or tool artifacts.
 */
data class AssistantResult(
    val isSuccess: Boolean,
    val message: String,
    val sources: List<SourceReference> = emptyList(),
    val error: AssistantError? = null,
    val requiresConfirmation: Boolean = false,
    val data: List<MemoryItem> = emptyList()
) {
    companion object {
        fun success(
            message: String,
            sources: List<SourceReference> = emptyList(),
            data: List<MemoryItem> = emptyList()
        ): AssistantResult {
            return AssistantResult(
                isSuccess = true,
                message = message,
                sources = sources,
                data = data
            )
        }

        fun failure(
            message: String,
            error: AssistantError,
            sources: List<SourceReference> = emptyList()
        ): AssistantResult {
            return AssistantResult(
                isSuccess = false,
                message = message,
                error = error,
                sources = sources
            )
        }

        fun confirmationRequired(
            message: String,
            sources: List<SourceReference> = emptyList(),
            data: List<MemoryItem> = emptyList()
        ): AssistantResult {
            return AssistantResult(
                isSuccess = true,
                message = message,
                sources = sources,
                requiresConfirmation = true,
                data = data
            )
        }
    }
}
