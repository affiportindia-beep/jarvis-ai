package com.jarvisx.core.tools

import com.jarvisx.core.lang.ReplyLanguage
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Android permission a tool needs before it may run. */
enum class Permission { NONE, NETWORK, CONTACTS, CALL_PHONE, SEND_SMS, NOTIFICATIONS, ALARMS, CALENDAR }

data class ToolRequest(val text: String, val language: ReplyLanguage)

sealed class ToolResult {
    abstract val toolId: String

    /** A finished answer. [verified] is true only when the tool's own check passed. */
    data class Answer(override val toolId: String, val text: String, val verified: Boolean) : ToolResult()

    /** The tool could not answer; the reason is shown to the user, never hidden. */
    data class Failure(override val toolId: String, val reason: String) : ToolResult()
}

/**
 * A deterministic capability the router can use instead of the language model.
 *
 * Every tool declares its purpose, the permission it needs and a time limit, validates its
 * input in [confidence], and checks its own output in [verify] before the answer is shown.
 */
interface Tool {
    val id: String
    val purpose: String
    val permission: Permission
    val timeoutMs: Long

    /** 0.0 = cannot handle this request, 1.0 = certain. Must be cheap and side-effect free. */
    fun confidence(request: ToolRequest): Double

    fun execute(request: ToolRequest): ToolResult

    /** Independent check of a result. Return false if the answer should not be trusted. */
    fun verify(request: ToolRequest, result: ToolResult.Answer): Boolean
}

/** Holds the available tools. New tools are added here without changing the router. */
class ToolRegistry(tools: List<Tool>) {
    private val tools = tools.toList()

    init {
        val duplicates = this.tools.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate tool ids: $duplicates" }
    }

    fun all(): List<Tool> = tools

    /** The most confident tool at or above [threshold], or null. */
    fun best(request: ToolRequest, threshold: Double = 0.8): Tool? =
        tools.map { it to safeConfidence(it, request) }
            .filter { it.second >= threshold }
            .maxByOrNull { it.second }
            ?.first

    private fun safeConfidence(tool: Tool, request: ToolRequest): Double =
        try {
            tool.confidence(request).coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            0.0
        }
}

/** Runs a tool with its time limit, error handling and result verification. */
object ToolExecutor {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "jarvis-tool").apply { isDaemon = true } }

    fun run(tool: Tool, request: ToolRequest): ToolResult {
        val future = pool.submit(Callable { tool.execute(request) })
        val result = try {
            future.get(tool.timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            return ToolResult.Failure(tool.id, "The ${tool.id} tool took longer than ${tool.timeoutMs} ms and was stopped.")
        } catch (e: ExecutionException) {
            return ToolResult.Failure(tool.id, e.cause?.message ?: "The ${tool.id} tool failed.")
        }
        return when (result) {
            is ToolResult.Answer -> {
                val ok = try { tool.verify(request, result) } catch (e: Exception) { false }
                if (ok) result.copy(verified = true)
                else ToolResult.Failure(tool.id, "The ${tool.id} result failed its own check, so it is not shown.")
            }
            is ToolResult.Failure -> result
        }
    }
}
