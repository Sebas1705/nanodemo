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
fun appContextPreamble(texts: AppTexts): String = texts.appContextPreamble
