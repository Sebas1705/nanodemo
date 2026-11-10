package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.CODE
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.EXTRACTION
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.INSTRUCTIONS
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.KNOWLEDGE
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.REASONING
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.SUMMARY
import es.sebas1705.nanodemo.feature.lab.bench.BenchCategory.TRANSLATION

/**
 * The built-in task set. Every task has a deterministic checker (no model grades another here), is short
 * enough for the 1k-token engines, and asks for a compact answer so the score measures the content and not
 * the chattiness. Prompts are in Spanish, the app's language, with a few English ones for translation, and
 * are deliberately NOT localized: they are model input, and the checkers expect Spanish answers. Only the
 * task titles (shown in the UI) are string resources.
 */
object BenchTasks {

    val all: List<BenchTask> = listOf(
        // Reasoning
        BenchTask("train-minutes", REASONING, R.string.bench_task_train_minutes, "Un tren sale a las 9:15 y llega a las 11:45. ¿Cuántos minutos dura el viaje? Responde solo con el número.", Checkers.number(150.0)),
        BenchTask("apples", REASONING, R.string.bench_task_apples, "María tiene 3 manzanas, compra 2 bolsas de 6 manzanas y regala 4. ¿Cuántas manzanas tiene? Responde solo con el número.", Checkers.number(11.0)),
        BenchTask("percent", REASONING, R.string.bench_task_percent, "¿Cuánto es el 15% de 240? Responde solo con el número.", Checkers.number(36.0)),
        BenchTask("youngest", REASONING, R.string.bench_task_youngest, "Ana es mayor que Beto. Beto es mayor que Carla. ¿Quién es la más joven? Responde con una palabra.", Checkers.word("Carla")),
        BenchTask("sequence", REASONING, R.string.bench_task_sequence, "Sigue la secuencia: 2, 4, 8, 16, ... ¿Cuál es el siguiente número? Responde solo con el número.", Checkers.number(32.0)),

        // Extraction
        BenchTask(
            "extract-json", EXTRACTION, R.string.bench_task_extract_json,
            "Extrae en JSON con los campos nombre, edad y ciudad: 'Lucía Pérez, de 34 años, vive en Valencia.' Devuelve solo el JSON.",
            Checkers.json(mapOf("nombre" to "Lucía Pérez", "edad" to 34, "ciudad" to "Valencia")), jsonMode = true,
        ),
        BenchTask("extract-email", EXTRACTION, R.string.bench_task_extract_email, "Devuelve solo el correo electrónico del texto: 'Escríbeme a marta.lopez@example.com antes del viernes.'", Checkers.contains("marta.lopez@example.com")),
        BenchTask("extract-date", EXTRACTION, R.string.bench_task_extract_date, "Convierte 'el 3 de marzo de 2024' al formato AAAA-MM-DD. Responde solo con la fecha.", Checkers.contains("2024-03-03")),
        BenchTask(
            "json-flags", EXTRACTION, R.string.bench_task_json_flags,
            "Devuelve un JSON con el campo ok igual a true (booleano) y el campo n igual a 7 (número). Solo el JSON.",
            Checkers.json(mapOf("ok" to true, "n" to 7)), jsonMode = true,
        ),

        // Instruction following
        BenchTask("three-words", INSTRUCTIONS, R.string.bench_task_three_words, "Escribe exactamente tres palabras sobre el mar, separadas por comas y sin punto final.", Checkers.allOf(Checkers.wordsExactly(3), Checkers.doesNotEndWith('.'))),
        BenchTask("yes-no", INSTRUCTIONS, R.string.bench_task_yes_no, "Responde solo con 'sí' o 'no': ¿es plana la Tierra?", Checkers.word("no")),
        BenchTask("short-sentence", INSTRUCTIONS, R.string.bench_task_short_sentence, "Escribe una frase de máximo 10 palabras sobre el invierno.", Checkers.wordsAtMost(10)),
        BenchTask("three-lines", INSTRUCTIONS, R.string.bench_task_three_lines, "Lista tres frutas, una por línea, sin numerar y sin texto adicional.", Checkers.lines(3)),

        // Knowledge
        BenchTask("capital", KNOWLEDGE, R.string.bench_task_capital, "¿Cuál es la capital de Australia? Responde con una palabra.", Checkers.contains("Canberra")),
        BenchTask("quixote", KNOWLEDGE, R.string.bench_task_quixote, "¿Quién escribió 'Don Quijote de la Mancha'? Responde solo con el apellido.", Checkers.contains("Cervantes")),
        BenchTask("gold", KNOWLEDGE, R.string.bench_task_gold, "¿Cuál es el símbolo químico del oro? Responde solo con el símbolo.", Checkers.word("Au")),
        BenchTask("moon", KNOWLEDGE, R.string.bench_task_moon, "¿En qué año llegó el ser humano a la Luna por primera vez? Responde solo con el año.", Checkers.number(1969.0)),

        // Translation
        BenchTask("to-english", TRANSLATION, R.string.bench_task_to_english, "Traduce al inglés: 'Buenos días, ¿dónde está la estación?'", Checkers.allOf(Checkers.contains("good morning", "station"), Checkers.language("en"))),
        BenchTask("to-spanish", TRANSLATION, R.string.bench_task_to_spanish, "Translate to Spanish: 'The weather is nice today.'", Checkers.allOf(Checkers.containsAny("tiempo", "clima"), Checkers.contains("hoy"), Checkers.language("es"))),

        // Code
        BenchTask("python-sum", CODE, R.string.bench_task_python_sum, "Escribe una función Python llamada suma que reciba a y b y devuelva su suma. Solo el código.", Checkers.allOf(Checkers.regex("def\\s+suma\\s*\\("), Checkers.contains("return"))),
        BenchTask("python-len", CODE, R.string.bench_task_python_len, "¿Qué imprime este código Python? print(len('hola')) Responde solo con el resultado.", Checkers.number(4.0)),

        // Summary (measurable constraints only)
        BenchTask(
            "summary-short", SUMMARY, R.string.bench_task_summary_short,
            "Resume en una sola frase de menos de 15 palabras: 'El ayuntamiento ha aprobado un plan para plantar 5000 árboles en los barrios con menos zonas verdes durante los próximos dos años.'",
            Checkers.allOf(Checkers.wordsAtMost(14), Checkers.containsAny("arbol", "arboles", "plantar")),
        ),
        BenchTask(
            "summary-number", SUMMARY, R.string.bench_task_summary_number,
            "Lee y responde solo con el número: 'Este trimestre la empresa vendió 1200 unidades, un 20% más que el anterior.' ¿Cuántas unidades vendió?",
            Checkers.number(1200.0),
        ),
    )

    fun byCategories(categories: Set<BenchCategory>): List<BenchTask> = all.filter { it.category in categories }
}
