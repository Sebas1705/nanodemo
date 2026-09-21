package es.sebas1705.nanodemo.domain.models

/** Un prompt preconfigurado, para probar el mismo caso de uso en los 3 tiers de forma repetible. */
data class PromptPreset(val name: String, val text: String)

val PROMPT_BANK: List<PromptPreset> = listOf(
    PromptPreset("Resumen", "Resumí en 3 viñetas qué es la computación cuántica."),
    PromptPreset("Código", "Escribí una función en Kotlin que invierta una lista enlazada."),
    PromptPreset("Traducción", "Traducí al inglés: 'La inteligencia artificial en el dispositivo cambia las reglas del juego.'"),
    PromptPreset("Razonamiento", "Si un tren sale a las 14:00 a 80km/h y otro a las 14:30 a 100km/h en la misma dirección, ¿cuándo se alcanzan?"),
    PromptPreset("Extracción", "Extraé nombre, fecha y monto de: 'Juan Pérez pagó \$4500 el 12/03/2026.'"),
    PromptPreset("Creatividad", "Escribí un microcuento de 3 líneas sobre un robot que aprende a soñar."),
    PromptPreset("Clasificación", "Clasificá este mensaje como spam o no spam: 'Ganaste un premio, hacé click acá ya!!!'"),
    PromptPreset("Instrucciones", "Explicá paso a paso cómo cambiar una rueda de auto."),
)
