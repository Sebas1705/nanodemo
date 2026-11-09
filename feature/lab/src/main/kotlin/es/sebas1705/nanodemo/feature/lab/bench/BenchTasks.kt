package es.sebas1705.nanodemo.feature.lab.bench

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
 * the chattiness. Prompts are in Spanish, the app's language, with a few English ones for translation.
 */
object BenchTasks {

    val all: List<BenchTask> = listOf(
        // Reasoning
        BenchTask("train-minutes", REASONING, "Minutos de viaje", "Un tren sale a las 9:15 y llega a las 11:45. ¿Cuántos minutos dura el viaje? Responde solo con el número.", Checkers.number(150.0)),
        BenchTask("apples", REASONING, "Manzanas", "María tiene 3 manzanas, compra 2 bolsas de 6 manzanas y regala 4. ¿Cuántas manzanas tiene? Responde solo con el número.", Checkers.number(11.0)),
        BenchTask("percent", REASONING, "Porcentaje", "¿Cuánto es el 15% de 240? Responde solo con el número.", Checkers.number(36.0)),
        BenchTask("youngest", REASONING, "Orden de edades", "Ana es mayor que Beto. Beto es mayor que Carla. ¿Quién es la más joven? Responde con una palabra.", Checkers.word("Carla")),
        BenchTask("sequence", REASONING, "Secuencia", "Sigue la secuencia: 2, 4, 8, 16, ... ¿Cuál es el siguiente número? Responde solo con el número.", Checkers.number(32.0)),

        // Extraction
        BenchTask(
            "extract-json", EXTRACTION, "Persona a JSON",
            "Extrae en JSON con los campos nombre, edad y ciudad: 'Lucía Pérez, de 34 años, vive en Valencia.' Devuelve solo el JSON.",
            Checkers.json(mapOf("nombre" to "Lucía Pérez", "edad" to 34, "ciudad" to "Valencia")), jsonMode = true,
        ),
        BenchTask("extract-email", EXTRACTION, "Correo", "Devuelve solo el correo electrónico del texto: 'Escríbeme a marta.lopez@example.com antes del viernes.'", Checkers.contains("marta.lopez@example.com")),
        BenchTask("extract-date", EXTRACTION, "Fecha ISO", "Convierte 'el 3 de marzo de 2024' al formato AAAA-MM-DD. Responde solo con la fecha.", Checkers.contains("2024-03-03")),
        BenchTask(
            "json-flags", EXTRACTION, "JSON con tipos",
            "Devuelve un JSON con el campo ok igual a true (booleano) y el campo n igual a 7 (número). Solo el JSON.",
            Checkers.json(mapOf("ok" to true, "n" to 7)), jsonMode = true,
        ),

        // Instruction following
        BenchTask("three-words", INSTRUCTIONS, "Tres palabras", "Escribe exactamente tres palabras sobre el mar, separadas por comas y sin punto final.", Checkers.allOf(Checkers.wordsExactly(3), Checkers.doesNotEndWith('.'))),
        BenchTask("yes-no", INSTRUCTIONS, "Sí o no", "Responde solo con 'sí' o 'no': ¿es plana la Tierra?", Checkers.word("no")),
        BenchTask("short-sentence", INSTRUCTIONS, "Frase corta", "Escribe una frase de máximo 10 palabras sobre el invierno.", Checkers.wordsAtMost(10)),
        BenchTask("three-lines", INSTRUCTIONS, "Tres líneas", "Lista tres frutas, una por línea, sin numerar y sin texto adicional.", Checkers.lines(3)),

        // Knowledge
        BenchTask("capital", KNOWLEDGE, "Capital", "¿Cuál es la capital de Australia? Responde con una palabra.", Checkers.contains("Canberra")),
        BenchTask("quixote", KNOWLEDGE, "Autor", "¿Quién escribió 'Don Quijote de la Mancha'? Responde solo con el apellido.", Checkers.contains("Cervantes")),
        BenchTask("gold", KNOWLEDGE, "Símbolo químico", "¿Cuál es el símbolo químico del oro? Responde solo con el símbolo.", Checkers.word("Au")),
        BenchTask("moon", KNOWLEDGE, "Año", "¿En qué año llegó el ser humano a la Luna por primera vez? Responde solo con el año.", Checkers.number(1969.0)),

        // Translation
        BenchTask("to-english", TRANSLATION, "ES → EN", "Traduce al inglés: 'Buenos días, ¿dónde está la estación?'", Checkers.allOf(Checkers.contains("good morning", "station"), Checkers.language("en"))),
        BenchTask("to-spanish", TRANSLATION, "EN → ES", "Translate to Spanish: 'The weather is nice today.'", Checkers.allOf(Checkers.containsAny("tiempo", "clima"), Checkers.contains("hoy"), Checkers.language("es"))),

        // Code
        BenchTask("python-sum", CODE, "Función Python", "Escribe una función Python llamada suma que reciba a y b y devuelva su suma. Solo el código.", Checkers.allOf(Checkers.regex("def\\s+suma\\s*\\("), Checkers.contains("return"))),
        BenchTask("python-len", CODE, "Salida de código", "¿Qué imprime este código Python? print(len('hola')) Responde solo con el resultado.", Checkers.number(4.0)),

        // Summary (measurable constraints only)
        BenchTask(
            "summary-short", SUMMARY, "Resumen corto",
            "Resume en una sola frase de menos de 15 palabras: 'El ayuntamiento ha aprobado un plan para plantar 5000 árboles en los barrios con menos zonas verdes durante los próximos dos años.'",
            Checkers.allOf(Checkers.wordsAtMost(14), Checkers.containsAny("arbol", "arboles", "plantar")),
        ),
        BenchTask(
            "summary-number", SUMMARY, "Dato clave",
            "Lee y responde solo con el número: 'Este trimestre la empresa vendió 1200 unidades, un 20% más que el anterior.' ¿Cuántas unidades vendió?",
            Checkers.number(1200.0),
        ),
    )

    fun byCategories(categories: Set<BenchCategory>): List<BenchTask> = all.filter { it.category in categories }
}
