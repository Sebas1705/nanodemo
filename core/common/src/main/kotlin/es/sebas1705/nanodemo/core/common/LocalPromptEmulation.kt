package es.sebas1705.nanodemo.core.common

/**
 * Emulaciones por prompt para lo que los 3 tiers locales (A/B/D) no tienen nativo en su API:
 * tool-calling (ver [DemoTools]), búsqueda (ver [augmentWithWebSearch]) y salida JSON (`responseMimeType` en Cloud/
 * Firebase). Ninguna tiene garantía sintáctica acá — a diferencia de la nube, es el modelo
 * "portándose bien" con lo que se le pidió, no una restricción que imponga la API.
 * `JsonValidityBadge` en Chat (que ya solo compara "¿el texto es JSON válido?", sin importar el
 * tier) es la única forma de saber si funcionó, igual para los 5 tiers.
 */

/**
 * Combina [appContextPreamble] (siempre primero) con el system prompt del usuario y los addendums
 * de emulación que correspondan según `config`. Nunca es blank -- `appContextPreamble()` sola ya
 * garantiza contenido, así que a diferencia de antes esto no necesita devolver `String?`.
 *
 * `webSearch` tiene prioridad sobre `functionCalling` — mismo criterio que ya usan Cloud/Firebase
 * ("si también prendiste Herramientas, [búsqueda] gana", ver `ConfigScreen`): con búsqueda activa el
 * turno ya viene enriquecido con los resultados (ver `augmentWithWebSearch`), así que acá no se agrega
 * nada de búsqueda ni la instrucción de la herramienta de hora. Modo JSON en cambio es aditivo
 * (no es mutuamente excluyente con ninguna de las dos en la nube), aunque combinarlo con búsqueda o
 * herramientas en un tier local es una combinación no probada.
 */
fun buildLocalSystemPrompt(
    userSystemPrompt: String?,
    functionCalling: Boolean,
    webSearch: Boolean,
    jsonMode: Boolean,
    texts: AppTexts,
): String = buildList {
    add(appContextPreamble(texts))
    userSystemPrompt?.let(::add)
    if (functionCalling && !webSearch) add(DemoTools.reactInstruction(texts))
    // "Modo JSON" sin `responseMimeType` nativo: es un addendum más del system prompt.
    if (jsonMode) add(texts.jsonModeInstruction)
}.joinToString("\n\n")

/** Lo que hay que mostrar (`marker`) y agregar como turno siguiente (`resultMessage`) tras ejecutar una herramienta emulada. */
data class LocalToolCall(val marker: String, val resultMessage: String)

/**
 * Único punto donde los 3 backends locales deciden si el primer pase (ya bufferado completo, ver
 * cada backend) fue un pedido de la herramienta de hora o una respuesta normal, y si lo fue, la ejecutan.
 * `null` = no hubo pedido, mostrar `firstPassText` tal cual. (La búsqueda ya no pasa por acá: es
 * determinista, ver `augmentWithWebSearch`.)
 */
fun resolveLocalToolCall(firstPassText: String, texts: AppTexts): LocalToolCall? {
    val timezone = DemoTools.matchReactCall(firstPassText) ?: return null
    val toolResult = DemoTools.executeCurrentTime(timezone.ifBlank { null }, texts)
    return LocalToolCall(
        marker = toolMarker(texts.usingToolNotice(DemoTools.GET_CURRENT_TIME)),
        resultMessage = texts.toolResult(
            DemoTools.GET_CURRENT_TIME,
            toolResult.getValue(DemoTools.RESULT_READABLE),
            toolResult.getValue(DemoTools.RESULT_ISO8601),
        ),
    )
}
