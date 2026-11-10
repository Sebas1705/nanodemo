package es.sebas1705.nanodemo.feature.lab.agent.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** What a tool returns. Both cases become the next `Observation:` of the agent; `Error` also counts as a tool error. */
sealed interface ToolResult {
    data class Ok(val text: String) : ToolResult
    data class Error(val message: String) : ToolResult
}

/**
 * A capability the agent can call. Contract: [execute] may throw or run long, the registry applies the
 * [timeoutMs] and [maxOutputChars] limits and turns exceptions into errors, so a tool never breaks the loop.
 * Names, descriptions and hints are model-facing text: English, short, and stable across tiers.
 */
interface Tool {
    /** Canonical name the model writes after `Action:`. Lower case with underscores. */
    val name: String

    /** Other spellings the registry accepts for [name] (small models improvise). */
    val aliases: Set<String> get() = emptySet()

    /** One sentence: what it does and when to use it. */
    val description: String

    /** Example of the input, shown in the prompt, e.g. `{"expression": "(2+3)*4"}`. */
    val parameterHint: String

    val timeoutMs: Long get() = DEFAULT_TIMEOUT_MS

    /** Upper bound for the observation this tool produces. */
    val maxOutputChars: Int get() = DEFAULT_MAX_OUTPUT_CHARS

    suspend fun execute(args: ToolArgs): ToolResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAX_OUTPUT_CHARS = 600
    }
}

/** Result of running a tool through [ToolRegistry.invoke]: limits already applied. */
data class ToolInvocation(val tool: String, val result: ToolResult, val durationMs: Long) {
    val ok: Boolean get() = result is ToolResult.Ok
    val text: String
        get() = when (result) {
            is ToolResult.Ok -> result.text
            is ToolResult.Error -> result.message
        }
}

/** Marks a cut-off observation so the model knows it did not see everything. */
const val TRUNCATION_MARK = " …[truncated]"

fun String.truncateWithMark(max: Int): String =
    if (length <= max) this else take((max - TRUNCATION_MARK.length).coerceAtLeast(0)).trimEnd() + TRUNCATION_MARK

/** The tools an agent run may use. */
class ToolRegistry(val tools: List<Tool>, private val clock: () -> Long = System::currentTimeMillis) {
    private val byName: Map<String, Tool> = buildMap {
        tools.forEach { tool ->
            put(canonicalName(tool.name), tool)
            tool.aliases.forEach { put(canonicalName(it), tool) }
        }
    }

    val names: List<String> get() = tools.map { it.name }

    /** The tool the model meant by `name` (case, spaces and hyphens ignored), or `null`. */
    fun find(name: String): Tool? = byName[canonicalName(name)]

    /**
     * Runs a tool with its timeout and output cap (`outputCap` further lowers the tool's own cap, used to keep
     * observations small for tiny context windows). Never throws except for coroutine cancellation.
     */
    suspend fun invoke(name: String, rawInput: String, outputCap: Int = Int.MAX_VALUE, timeoutCapMs: Long = Long.MAX_VALUE): ToolInvocation {
        val tool = find(name) ?: return ToolInvocation(
            name,
            ToolResult.Error("unknown tool \"$name\". Available tools: ${names.joinToString()}. If no tool is needed, reply with \"Final Answer:\""),
            0L,
        )
        val started = clock()
        val timeout = minOf(tool.timeoutMs, timeoutCapMs).coerceAtLeast(1L)
        val result = try {
            withTimeoutOrNull(timeout) { tool.execute(ToolArgs(rawInput)) }
                ?: ToolResult.Error("${tool.name} timed out after ${timeout / 1000.0}s")
        } catch (e: TimeoutCancellationException) {
            ToolResult.Error("${tool.name} timed out") // a timeout inside the tool, not a cancelled run
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: e.javaClass.simpleName)
        }
        val cap = minOf(tool.maxOutputChars, outputCap)
        val capped = when (result) {
            is ToolResult.Ok -> ToolResult.Ok(result.text.trim().truncateWithMark(cap))
            is ToolResult.Error -> ToolResult.Error(result.message.trim().truncateWithMark(cap))
        }
        return ToolInvocation(tool.name, capped, clock() - started)
    }

    companion object {
        /** "Current Time", "current-time" and "current_time" are the same tool. */
        fun canonicalName(name: String): String =
            name.trim().lowercase().replace(Regex("[\\s\\-]+"), "_").filter { it.isLetterOrDigit() || it == '_' }
    }
}
