package es.sebas1705.nanodemo.feature.lab.refine

enum class TaskCategory(val label: String, val role: String) {
    ARITHMETIC("Aritmética", "a careful math teacher who double-checks every calculation"),
    LOGIC("Lógica", "a rigorous logician"),
    EXTRACTION("Extracción", "a precise data-extraction assistant"),
    CLASSIFICATION("Clasificación", "an experienced text classifier"),
    SPANISH("Español", "a knowledgeable teacher who answers in Spanish"),
}

/** A deterministic check of the short final answer of a task. */
sealed interface Check {
    /** How the final answer should be read for voting and display. */
    val kind: AnswerKind

    /** `answer` is the final segment of the model's reply (see [finalSegment]). */
    fun passes(answer: String): Boolean

    /** A number, optionally also written as a word ("seis"). */
    data class NumberIs(val value: String, val words: List<String> = emptyList()) : Check {
        override val kind get() = AnswerKind.NUMBER
        override fun passes(answer: String): Boolean {
            val read = extractAnswer(answer, AnswerKind.NUMBER)?.key
            if (read != null) return read == normalizeNumber(value)
            val loose = normalizeLoose(answer).split(' ')
            return words.any { normalizeLoose(it) in loose }
        }
    }

    data class YesNoIs(val yes: Boolean) : Check {
        override val kind get() = AnswerKind.YES_NO
        override fun passes(answer: String) = extractAnswer(answer, AnswerKind.YES_NO)?.key == if (yes) "yes" else "no"
    }

    /** The answer, compared without case, accents or punctuation, contains one of the accepted forms. */
    data class ContainsAny(val options: List<String>) : Check {
        override val kind get() = AnswerKind.PHRASE
        override fun passes(answer: String): Boolean {
            val loose = normalizeLoose(answer)
            return options.any { normalizeLoose(it) in loose }
        }
    }

    /** Classification: the answer names `label` and none of the other labels. */
    data class Label(val label: String, val others: List<String>) : Check {
        override val kind get() = AnswerKind.PHRASE
        override fun passes(answer: String): Boolean {
            val words = normalizeLoose(answer).split(' ')
            return normalizeLoose(label) in words && others.none { normalizeLoose(it) in words }
        }
    }
}

data class StrategyTask(val id: String, val category: TaskCategory, val question: String, val check: Check)

/** Result of scoring one model reply against a task. `answer` is the short answer that was read. */
data class TaskScore(val correct: Boolean, val answer: String)

fun scoreTask(task: StrategyTask, reply: String): TaskScore {
    val answer = finalSegment(reply)
    return TaskScore(answer.isNotEmpty() && task.check.passes(answer), answer)
}

/**
 * The built-in task set: small, unambiguous questions with one right short answer, in English and Spanish, so
 * that a prompt strategy can be scored without a human. Kept free of the few-shot examples used by the strategies.
 */
object StrategyTasks {
    val all: List<StrategyTask> = listOf(
        StrategyTask(
            "arith-change", TaskCategory.ARITHMETIC,
            "A shop sells pencils at 3 euros each. Ana buys 7 pencils and pays with a 50 euro note. How many euros of change does she get?",
            Check.NumberIs("29"),
        ),
        StrategyTask(
            "arith-trip", TaskCategory.ARITHMETIC,
            "A train travels at 60 km/h for 2.5 hours and then at 80 km/h for 1.5 hours. How many kilometres does it travel in total?",
            Check.NumberIs("270"),
        ),
        StrategyTask(
            "arith-candies-es", TaskCategory.ARITHMETIC,
            "Marta tiene 24 caramelos, reparte la mitad entre sus amigos y después compra 5 más. ¿Cuántos caramelos tiene ahora?",
            Check.NumberIs("17"),
        ),
        StrategyTask(
            "logic-syllogism", TaskCategory.LOGIC,
            "All bloops are razzies. All razzies are lazzies. Are all bloops lazzies? Answer yes or no.",
            Check.YesNoIs(true),
        ),
        StrategyTask(
            "logic-order", TaskCategory.LOGIC,
            "Tom is taller than Sam. Sam is taller than Lee. Who is the shortest of the three?",
            Check.ContainsAny(listOf("Lee")),
        ),
        StrategyTask(
            "logic-weekday-es", TaskCategory.LOGIC,
            "Si hoy es miércoles, ¿qué día de la semana será dentro de 10 días?",
            Check.ContainsAny(listOf("sábado", "saturday")),
        ),
        StrategyTask(
            "extract-date", TaskCategory.EXTRACTION,
            "Extract the date from this sentence and write it as DD/MM/YYYY: \"The meeting was moved to 14 March 2027 at the main office.\"",
            Check.ContainsAny(listOf("14/03/2027", "14-03-2027")),
        ),
        StrategyTask(
            "extract-email", TaskCategory.EXTRACTION,
            "Extract the email address from this sentence: \"Please send the invoice to maria.lopez@example.com before Friday.\"",
            Check.ContainsAny(listOf("maria.lopez@example.com")),
        ),
        StrategyTask(
            "classify-sentiment", TaskCategory.CLASSIFICATION,
            "Classify the sentiment of this review as positive, negative or neutral: \"The battery died after two days and support never answered.\"",
            Check.Label("negative", listOf("positive", "neutral")),
        ),
        StrategyTask(
            "classify-topic-es", TaskCategory.CLASSIFICATION,
            "Clasifica el tema de esta frase como deportes, política o cocina: \"El portero paró dos penaltis en la final.\"",
            Check.Label("deportes", listOf("politica", "cocina")),
        ),
        StrategyTask(
            "es-capital", TaskCategory.SPANISH,
            "¿Cuál es la capital de Australia?",
            Check.ContainsAny(listOf("Canberra")),
        ),
        StrategyTask(
            "es-hexagon", TaskCategory.SPANISH,
            "¿Cuántos lados tiene un hexágono?",
            Check.NumberIs("6", words = listOf("seis", "six")),
        ),
    )

    fun byId(id: String): StrategyTask? = all.firstOrNull { it.id == id }
}
