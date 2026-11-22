package com.mnemo.intelligence.router

import com.mnemo.core.model.tool.MnemoTool
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry storing all active tools and connectors available to Mnemo.
 */
class ToolRegistry {

    private val tools = ConcurrentHashMap<String, MnemoTool>()

    fun register(tool: MnemoTool) {
        tools[tool.id] = tool
    }

    fun unregister(toolId: String) {
        tools.remove(toolId)
    }

    fun get(toolId: String): MnemoTool? = tools[toolId]

    fun getAll(): List<MnemoTool> = tools.values.toList()
}
