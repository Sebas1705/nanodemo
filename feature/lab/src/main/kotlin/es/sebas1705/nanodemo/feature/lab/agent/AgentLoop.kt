package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import es.sebas1705.nanodemo.feature.lab.agent.tools.LenientJson
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolRegistry
import es.sebas1705.nanodemo.feature.lab.agent.tools.truncateWithMark
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Knobs of one agent run. */
data class AgentSettings(
    /** Maximum number of model calls. */
    val maxSteps: Int = 6,
    /** Wall-clock budget for the whole run; checked before every model call and enforced during it. */
    val timeBudgetMs: Long = 120_000L,
    /** Replies in a row without a usable Action/Final Answer tolerated before giving up. */
    val maxFormatErrors: Int = 2,
    /**
     * Repeats of an already-run Action+Input tolerated. The first repeat is not executed and only warns the
     * model; reaching this count ends the run as a loop.
     */
    val maxRepeats: Int = 2,
    val temperature: Float = 0.2f,
)

/** Why a run ended. Only [ANSWERED] is a success. */
enum class AgentOutcome { ANSWERED, MAX_STEPS, TIMEOUT, LOOP, FORMAT_FAILURE, MODEL_ERROR }

data class ToolCallRecord(val tool: String, val input: String, val output: String, val ok: Boolean)

/** Everything the UI and the benchmark need to know about a finished run. */
data class AgentRunResult(
    val task: String,
    val tier: InferenceTier,
    val outcome: AgentOutcome,
    /** The final answer; for failed runs, the best text available (may be empty). */
    val answer: String,
    val error: String? = null,
    /** Model calls made. */
    val steps: Int,
    val maxSteps: Int,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    /** Replies that had no usable Action or Final Answer. */
    val parseFailures: Int = 0,
    /** Answers given without the `Final Answer:` label (accepted, but a deviation from the protocol). */
    val implicitAnswers: Int = 0,
    /** Times the model repeated an Action it had already run. */
    val loopsDetected: Int = 0,
    val durationMs: Long = 0L,
    val tokens: Int = 0,
    /** Longest prompt sent, in characters: how close the run got to the context window. */
    val peakPromptChars: Int = 0,
) {
    val succeeded: Boolean get() = outcome == AgentOutcome.ANSWERED
    val toolErrors: Int get() = toolCalls.count { !it.ok }
    fun toolsUsed(): Set<String> = toolCalls.map { it.tool }.toSet()
}

/**
 * The ReAct loop. Every tier gets the same prompt protocol (no native function calling), so differences in
 * the results are differences between models, not between APIs. Each iteration is recorded in the trace of the
 * [PipelineRunner]: a GENERATE step (thought + action), a TOOL step (execution) and, at the end, a VALIDATE step
 * with the answer (or the reason the run stopped).
 *
 * Termination is always graceful: a final answer, the step limit, the time budget, a repeated action, repeated
 * format errors or a model error each end the run with an [AgentOutcome] and a closing step in the trace.
 */
class AgentRunner(
    private val runner: PipelineRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class ToolFailure(message: String) : Exception(message)

    suspend fun run(
        task: String,
        tier: InferenceTier,
        registry: ToolRegistry,
        capabilities: TierCapabilities,
        settings: AgentSettings = AgentSettings(),
    ): AgentRunResult {
        val started = clock()
        val budget = AgentBudget.forTier(tier, capabilities.contextTokens)
        val entries = mutableListOf<ScratchEntry>()
        val toolCalls = mutableListOf<ToolCallRecord>()
        val seen = HashMap<String, Pair<String, Boolean>>() // normalised Action+Input -> (observation, succeeded)
        var steps = 0
        var parseFailures = 0
        var implicitAnswers = 0
        var loops = 0
        var tokens = 0
        var peakPrompt = 0
        var consecutiveFormatErrors = 0

        fun result(outcome: AgentOutcome, answer: String = "", error: String? = null) = AgentRunResult(
            task = task, tier = tier, outcome = outcome, answer = answer, error = error, steps = steps,
            maxSteps = settings.maxSteps, toolCalls = toolCalls.toList(), parseFailures = parseFailures,
            implicitAnswers = implicitAnswers, loopsDetected = loops, durationMs = clock() - started,
            tokens = tokens, peakPromptChars = peakPrompt,
        )

        /** Closes the trace with a failed step that says why the run stopped, then returns the result. */
        fun stopped(outcome: AgentOutcome, title: String, reason: String, answer: String = "", error: String? = null): AgentRunResult {
            val id = runner.recorder.begin(StepKind.VALIDATE, title)
            runner.recorder.fail(id, reason)
            return result(outcome, answer, error)
        }

        try {
            while (true) {
                if (steps >= settings.maxSteps) {
                    return stopped(AgentOutcome.MAX_STEPS, "Sin respuesta", "límite de ${settings.maxSteps} pasos alcanzado")
                }
                val remaining = started + settings.timeBudgetMs - clock()
                if (remaining <= 0) {
                    return stopped(AgentOutcome.TIMEOUT, "Sin respuesta", "presupuesto de tiempo agotado")
                }
                steps++
                val lastStep = steps == settings.maxSteps
                val prompt = AgentPrompt.build(task, registry.tools, entries, budget, lastStep)
                peakPrompt = maxOf(peakPrompt, prompt.length)

                val generation = withTimeoutOrNull(remaining) {
                    runner.generate(
                        kind = StepKind.GENERATE,
                        title = "Paso $steps · pensar",
                        tier = tier,
                        messages = listOf(ChatMessage(Role.USER, prompt)),
                        config = GenerationConfig(
                            temperature = settings.temperature,
                            maxOutputTokens = budget.maxOutputTokens,
                        ),
                    )
                }
                if (generation == null) {
                    failRunningSteps("presupuesto de tiempo agotado")
                    return stopped(AgentOutcome.TIMEOUT, "Sin respuesta", "presupuesto de tiempo agotado")
                }
                if (!generation.ok) {
                    val message = generation.error ?: "Error del modelo"
                    return stopped(AgentOutcome.MODEL_ERROR, "Sin respuesta", message, error = message)
                }
                tokens += generation.metric?.responseTokens ?: (generation.text.length / AgentBudget.CHARS_PER_TOKEN)

                when (val reply = AgentParser.parse(generation.text)) {
                    is AgentReply.Final -> {
                        if (reply.implicit) implicitAnswers++
                        val answer = reply.answer
                        runner.local(StepKind.VALIDATE, "Respuesta final", input = null) {
                            val detail = buildString {
                                append("$steps ${if (steps == 1) "paso" else "pasos"}")
                                append(" · ${toolCalls.size} ${if (toolCalls.size == 1) "herramienta" else "herramientas"}")
                                if (reply.implicit) append(" · sin etiqueta «Final Answer»")
                            }
                            LocalOutcome(Unit, output = answer, detail = detail)
                        }
                        return result(AgentOutcome.ANSWERED, answer)
                    }

                    is AgentReply.Malformed -> {
                        parseFailures++
                        consecutiveFormatErrors++
                        val id = runner.recorder.begin(StepKind.VALIDATE, "Formato no válido", input = generation.text)
                        runner.recorder.fail(id, reply.reason)
                        if (consecutiveFormatErrors > settings.maxFormatErrors) {
                            val best = (reply.thought ?: generation.text).trim()
                            return stopped(AgentOutcome.FORMAT_FAILURE, "Sin respuesta", "el modelo no sigue el formato", answer = best)
                        }
                        entries += ScratchEntry(
                            thought = null, action = null, input = null,
                            observation = "Format error (${reply.reason}). Reply with \"Action:\" and \"Action Input:\", or with \"Final Answer:\".",
                        )
                    }

                    is AgentReply.Act -> {
                        consecutiveFormatErrors = 0
                        val tool = registry.find(reply.tool)
                        val toolName = tool?.name ?: reply.tool
                        val signature = toolName + "|" + normaliseInput(reply.input)
                        val earlier = seen[signature]
                        if (earlier != null) {
                            loops++
                            runner.recorder.skip(StepKind.TOOL, "Repetida · $toolName", "misma acción y entrada que antes; no se ejecuta")
                            if (loops >= settings.maxRepeats) {
                                return stopped(
                                    AgentOutcome.LOOP, "Sin respuesta", "el modelo repite la misma acción",
                                    answer = if (earlier.second) earlier.first else "",
                                )
                            }
                            entries += ScratchEntry(
                                thought = reply.thought?.truncateWithMark(THOUGHT_CHARS),
                                action = toolName,
                                input = reply.input.truncateWithMark(INPUT_CHARS),
                                observation = "You already ran this exact action and got: ${earlier.first.truncateWithMark(budget.toolOutputChars)} " +
                                    "Do not repeat it: use that result or give the Final Answer.",
                            )
                            continue
                        }

                        val observation = try {
                            runner.local(StepKind.TOOL, "Paso $steps · $toolName", input = reply.input.ifBlank { null }) {
                                val timeLeft = started + settings.timeBudgetMs - clock()
                                val invocation = registry.invoke(reply.tool, reply.input, budget.toolOutputChars, timeLeft)
                                if (!invocation.ok) throw ToolFailure(invocation.text)
                                LocalOutcome(invocation.text, output = invocation.text, detail = "${invocation.durationMs} ms")
                            }.let { it to true }
                        } catch (e: ToolFailure) {
                            ((e.message ?: "tool failed") to false)
                        }
                        val (text, ok) = observation
                        toolCalls += ToolCallRecord(toolName, reply.input, text, ok)
                        val shown = if (ok) text else "Error: $text"
                        seen[signature] = text to ok
                        entries += ScratchEntry(
                            thought = reply.thought?.truncateWithMark(THOUGHT_CHARS),
                            action = toolName,
                            input = reply.input.truncateWithMark(INPUT_CHARS),
                            observation = shown.truncateWithMark(budget.toolOutputChars + ERROR_PREFIX_ROOM),
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            failRunningSteps("cancelado")
            throw e
        }
    }

    private fun failRunningSteps(reason: String) {
        runner.recorder.trace.value.steps.filter { it.status == StepStatus.RUNNING }.forEach { runner.recorder.fail(it.id, reason) }
    }

    /** Two inputs that differ only in spacing, quoting or key order are the same call. */
    private fun normaliseInput(input: String): String {
        val obj = LenientJson.parseObject(input)
        if (obj != null) return obj.entries.sortedBy { it.key.lowercase() }.joinToString(",") { "${it.key.lowercase()}=${it.value.toString().trim().lowercase()}" }
        return input.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private companion object {
        const val THOUGHT_CHARS = 200
        const val INPUT_CHARS = 160
        const val ERROR_PREFIX_ROOM = 8
    }
}
