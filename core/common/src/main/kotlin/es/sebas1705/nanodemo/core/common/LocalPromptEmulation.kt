package es.sebas1705.nanodemo.core.common

/**
 * Emulaciones por prompt para lo que los 3 tiers locales (A/B/D) no tienen nativo en su API:
 * tool-calling (ver [DemoTools]) y salida JSON (`responseMimeType` en Cloud/Firebase). Ninguna
 * de las dos tiene garantía sintáctica acá — a diferencia de la nube, es el modelo "portándose
 * bien" con lo que se le pidió, no una restricción que imponga la API. `JsonValidityBadge` en
 * Chat (que ya solo compara "¿el texto es JSON válido?", sin importar el tier) es la única forma
 * de saber si funcionó, igual para los 5 tiers.
 */

/** Addendum de system prompt para pedir "Modo JSON" cuando no hay `responseMimeType` nativo. */
const val JSON_MODE_INSTRUCTION =
    "Respondé ÚNICAMENTE con JSON válido: nada de texto antes ni después, nada de explicaciones, " +
        "nada de bloques de código markdown (sin ``` alrededor). Todo tu mensaje tiene que poder " +
        "parsearse como JSON de punta a punta."

/**
 * Combina el system prompt del usuario con los addendums de emulación que correspondan según
 * `config`, o `null` si no hay nada que agregar. Modo JSON + Herramientas juntos en un tier local
 * es una combinación no probada (2 instrucciones de "respondé ÚNICAMENTE con..." compitiendo) —
 * se agregan las dos igual, en vez de que una descarte a la otra, mismo espíritu "mejor esfuerzo"
 * que el resto de esta emulación.
 */
fun buildLocalSystemPrompt(userSystemPrompt: String?, functionCalling: Boolean, jsonMode: Boolean): String? =
    buildList {
        userSystemPrompt?.let(::add)
        if (functionCalling) add(DemoTools.reactInstruction())
        if (jsonMode) add(JSON_MODE_INSTRUCTION)
    }.joinToString("\n\n").ifBlank { null }
