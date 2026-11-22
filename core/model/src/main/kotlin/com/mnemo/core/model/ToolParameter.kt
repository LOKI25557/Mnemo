package com.mnemo.core.model

/**
 * Declares a typed input parameter expected by an executable [com.mnemo.core.model.tool.MnemoTool].
 *
 * @property name Parameter key name.
 * @property type Expected parameter data type ("string", "number", "boolean").
 * @property description Human-readable explanation of parameter purpose.
 * @property isRequired Whether this parameter is mandatory to execute the tool.
 * @property defaultValue Optional fallback value when parameter is omitted.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val isRequired: Boolean = true,
    val defaultValue: String? = null
) {
    init {
        require(name.isNotBlank()) { "ToolParameter name must not be blank" }
        require(type.isNotBlank()) { "ToolParameter type must not be blank" }
    }
}
