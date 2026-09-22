package es.sebas1705.nanodemo.core.common

/**
 * Emulaciones por prompt para lo que los 3 tiers locales (A/B/D) no tienen nativo en su API:
 * tool-calling (ver [DemoTools]/[WebSearchTool]) y salida JSON (`responseMimeType` en Cloud/
 * Firebase). Ninguna tiene garantía sintáctica acá — a diferencia de la nube, es el modelo
 * "portándose bien" con lo que se le pidió, no una restricción que imponga la API.
 * `JsonValidityBadge` en Chat (que ya solo compara "¿el texto es JSON válido?", sin importar el
 * tier) es la única forma de saber si funcionó, igual para los 5 tiers.
 */

/** Addendum de system prompt para pedir "Modo JSON" cuando no hay `responseMimeType` nativo. */
const val JSON_MODE_INSTRUCTION =
    "Respondé ÚNICAMENTE con JSON válido: nada de texto antes ni después, nada de explicaciones, " +
        "nada de bloques de código markdown (sin ``` alrededor). Todo tu mensaje tiene que poder " +
        "parsearse como JSON de punta a punta."

/**
 * Combina el system prompt del usuario con los addendums de emulación que correspondan según
 * `config`, o `null` si no hay nada que agregar.
 *
 * `webSearch` tiene prioridad sobre `functionCalling` — mismo criterio que ya usan Cloud/Firebase
 * ("si también prendiste Herramientas, [búsqueda] gana", ver `ConfigScreen`): son 2 instrucciones
 * de "respondé ÚNICAMENTE con..." apuntando a etiquetas distintas, así que solo se agrega una de
 * las dos. Modo JSON en cambio es aditivo (no es mutuamente excluyente con ninguna de las dos en
 * la nube), aunque combinarlo con alguna de ellas en un tier local es una combinación no probada.
 */
fun buildLocalSystemPrompt(
    userSystemPrompt: String?,
    functionCalling: Boolean,
    webSearch: Boolean,
    jsonMode: Boolean,
): String? = buildList {
    userSystemPrompt?.let(::add)
    when {
        webSearch -> add(WebSearchTool.reactInstruction())
        functionCalling -> add(DemoTools.reactInstruction())
    }
    if (jsonMode) add(JSON_MODE_INSTRUCTION)
}.joinToString("\n\n").ifBlank { null }

/** Lo que hay que mostrar (`marker`) y agregar como turno siguiente (`resultMessage`) tras ejecutar una herramienta emulada. */
data class LocalToolCall(val marker: String, val resultMessage: String)

/**
 * Único punto donde los 3 backends locales deciden si el primer pase (ya bufferado completo, ver
 * cada backend) fue un pedido de herramienta o una respuesta normal, y si lo fue, la ejecutan.
 * `null` = no hubo pedido, mostrar `firstPassText` tal cual. Mismo orden de prioridad que
 * [buildLocalSystemPrompt] (`webSearch` antes que `functionCalling`) para que la etiqueta que se
 * busca sea siempre la única que el system prompt le pidió al modelo.
 */
suspend fun resolveLocalToolCall(firstPassText: String, functionCalling: Boolean, webSearch: Boolean): LocalToolCall? {
    if (webSearch) {
        val query = WebSearchTool.matchReactCall(firstPassText) ?: return null
        return LocalToolCall(
            marker = "🔧 buscando \"$query\"…\n\n",
            resultMessage = "Resultados de la búsqueda \"$query\":\n${WebSearchTool.search(query)}",
        )
    }
    if (functionCalling) {
        val timezone = DemoTools.matchReactCall(firstPassText) ?: return null
        val toolResult = DemoTools.executeCurrentTime(timezone.ifBlank { null })
        return LocalToolCall(
            marker = "🔧 usando \"${DemoTools.GET_CURRENT_TIME}\"…\n\n",
            resultMessage = "Resultado de get_current_time: ${toolResult["legible"]} (${toolResult["iso8601"]})",
        )
    }
    return null
}
