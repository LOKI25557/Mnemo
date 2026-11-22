package com.mnemo.core.model

/**
 * Encapsulates structured error information returned by assistant or connector operations.
 *
 * @property code Machine-readable error code.
 * @property message User-readable diagnostic explanation.
 * @property requiredPermission The specific Android permission needed if caused by permission denial.
 * @property cause The underlying exception, if any.
 */
data class AssistantError(
    val code: String,
    val message: String,
    val requiredPermission: String? = null,
    val cause: Throwable? = null
) {
    companion object {
        fun permissionDenied(permission: String, message: String): AssistantError {
            return AssistantError(
                code = "PERMISSION_DENIED",
                message = message,
                requiredPermission = permission
            )
        }

        fun notFound(entity: String, query: String): AssistantError {
            return AssistantError(
                code = "NOT_FOUND",
                message = "Could not find any $entity matching '$query'."
            )
        }

        fun executionFailed(message: String, cause: Throwable? = null): AssistantError {
            return AssistantError(
                code = "EXECUTION_FAILED",
                message = message,
                cause = cause
            )
        }
    }
}
