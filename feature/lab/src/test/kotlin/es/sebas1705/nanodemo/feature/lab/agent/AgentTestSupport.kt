package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor

fun caps(contextTokens: Int? = null) = TierCapabilities(
    temperature = true, topK = true, topP = true, maxOutputTokens = true,
    systemPrompt = true, multiTurn = true, images = false, contextTokens = contextTokens,
)

/** Replies from `script` in order (the last one repeats), recording every prompt and config it was called with. */
class ScriptedExecutor(private val script: List<String>) : StepExecutor {
    val prompts = mutableListOf<String>()
    val configs = mutableListOf<GenerationConfig>()
    private var index = 0

    override suspend fun run(
        tier: es.sebas1705.nanodemo.domain.models.InferenceTier,
        messages: List<es.sebas1705.nanodemo.domain.models.ChatMessage>,
        config: GenerationConfig,
        onPartial: (String) -> Unit,
    ): StepResult {
        prompts += messages.last().text
        configs += config
        val reply = script.getOrElse(index++) { script.last() }
        onPartial(reply)
        return StepResult(reply, metric = null)
    }
}

fun action(tool: String, input: String, thought: String = "thinking") =
    "Thought: $thought\nAction: $tool\nAction Input: $input"

fun calc(expression: String) = action("calculator", "{\"expression\": \"$expression\"}")

fun finalAnswer(text: String) = "Thought: I know the answer\nFinal Answer: $text"
