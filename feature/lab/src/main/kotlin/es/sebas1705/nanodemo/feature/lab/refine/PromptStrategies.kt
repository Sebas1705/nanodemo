package es.sebas1705.nanodemo.feature.lab.refine

enum class PromptStrategy(val label: String, val hint: String) {
    ZERO_SHOT("Zero-shot", "La pregunta tal cual, solo la respuesta final."),
    FEW_SHOT("Few-shot", "Tres ejemplos resueltos antes de la pregunta."),
    CHAIN_OF_THOUGHT("Cadena de pensamiento", "\"Piensa paso a paso\" y una última línea con la respuesta."),
    STEP_BACK("Paso atrás", "Primero el principio general, después la respuesta."),
    ROLE("Rol", "Se le asigna un experto del área antes de preguntar."),
}

/** Number of model calls a strategy needs per task (step-back asks for the principle first). */
val PromptStrategy.callsPerTask: Int get() = if (this == PromptStrategy.STEP_BACK) 2 else 1

/** One model call of a strategy: the prompt and how many answer tokens to ask for. */
data class StrategyStep(val prompt: String, val wantedTokens: Int)

private const val FINAL_LINE = "a line like: Final answer: <answer only>"

/** Examples used by few-shot. They are deliberately unrelated to the built-in tasks. */
private const val FEW_SHOT_EXAMPLES =
    "Question: What is 12 + 30?\nFinal answer: 42\n\n" +
        "Question: Is a whale a mammal? Answer yes or no.\nFinal answer: yes\n\n" +
        "Question: Which colour do you get by mixing blue and yellow?\nFinal answer: green\n\n"

/**
 * Prompt wording of each strategy. Every strategy asks for the same output format (a final-answer line) so the
 * scorer reads them all alike; what differs is only HOW the model is asked to get there.
 */
object StrategyPrompts {
    fun firstStep(strategy: PromptStrategy, task: StrategyTask, profile: EngineProfile): StrategyStep {
        val q = task.question
        return when (strategy) {
            PromptStrategy.ZERO_SHOT -> fitted(profile, 64, "Question: ", "\n\nReply with only the final answer, as $FINAL_LINE", q)
            PromptStrategy.FEW_SHOT ->
                fitted(profile, 64, "Answer each question with only the final answer.\n\n${FEW_SHOT_EXAMPLES}Question: ", "\nFinal answer:", q)
            PromptStrategy.CHAIN_OF_THOUGHT ->
                fitted(profile, 256, "Question: ", "\n\nThink step by step, then finish with $FINAL_LINE", q)
            PromptStrategy.STEP_BACK ->
                fitted(
                    profile, 96, "Question: ",
                    "\n\nBefore answering, state in one or two sentences the general principle, concept or formula needed to solve it. Do not answer yet.",
                    q,
                )
            PromptStrategy.ROLE -> {
                val head = "You are ${task.category.role}.\n\nQuestion: "
                fitted(profile, 64, head, "\n\nReply with only the final answer, as $FINAL_LINE", q)
            }
        }
    }

    /** The second call of [PromptStrategy.STEP_BACK], given the principle the model wrote. */
    fun stepBackAnswer(task: StrategyTask, principle: String, profile: EngineProfile): StrategyStep {
        val head = "Question: "
        val middle = "\n\nBackground: "
        val tail = "\n\nUsing the background, answer the question briefly. Finish with $FINAL_LINE"
        val out = profile.outputTokens(192)
        val (q, p) = profile.fitParts(out, head + middle + tail, task.question, principle)
        return StrategyStep("$head$q$middle$p$tail", 192)
    }

    private fun fitted(profile: EngineProfile, wanted: Int, head: String, tail: String, question: String): StrategyStep {
        val out = profile.outputTokens(wanted)
        val (q) = profile.fitParts(out, head + tail, question)
        return StrategyStep(head + q + tail, wanted)
    }
}
