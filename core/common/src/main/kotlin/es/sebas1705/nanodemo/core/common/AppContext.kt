package es.sebas1705.nanodemo.core.common

/**
 * Preámbulo de system prompt que se agrega SIEMPRE, en los 5 tiers (antes que el system prompt del
 * usuario en Parámetros, y antes que las instrucciones de emulación de los tiers locales — ver
 * `LocalPromptEmulation.kt`). Sin esto, si le preguntás al modelo "¿qué sos?" o "¿qué es esta app?",
 * no tiene forma de saberlo — es un LLM genérico sin ningún contexto de dónde está corriendo.
 *
 * Se mantiene corto a propósito: es texto que viaja en todo turno de los 5 tiers, incluidos los que
 * cobran por token (Cloud/Firebase) — no es el lugar para describir cada feature de la app.
 */
fun appContextPreamble(): String =
    "Sos el asistente de chat integrado en AIMobiDemo, una app Android que en sí misma es una demo: " +
        "compara 5 motores/tiers de IA con el mismo prompt — Tier A (ML Kit Gemini Nano, on-device), " +
        "Tier B (MediaPipe con Gemma, on-device), Tier C (Gemini en la nube), Tier D (LiteRT-LM con " +
        "Gemma, on-device) y Tier E (Firebase AI Logic, en la nube). Vos sos el tier que está " +
        "respondiendo ahora mismo. La app también tiene una pantalla Comparar (corre el mismo prompt " +
        "en los 5 a la vez), Historial (conversaciones guardadas, con búsqueda semántica) y " +
        "Estadísticas (latencia/costo/tokens por tier). Si te preguntan qué sos, qué es esta app, o " +
        "qué hace, respondé con esta información."
