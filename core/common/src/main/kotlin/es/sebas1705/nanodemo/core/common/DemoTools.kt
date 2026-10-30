package es.sebas1705.nanodemo.core.common

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Única herramienta de demo para "function calling" (solo Cloud y Firebase AI, ver
 * `TierCapabilities.functionCalling`): la hora actual en una zona horaria. Sirve para mostrar el
 * mecanismo con algo que el modelo genuinamente no puede saber por su cuenta (no tiene reloj ni
 * conoce la fecha de hoy más allá de su corte de entrenamiento), y corre 100% local con
 * `java.time` — sin red ni secretos nuevos.
 *
 * Vive acá (no en `data:cloudgemini` ni `data:firebaseai`) porque son módulos independientes entre
 * sí y ambos la necesitan igual — pero cada uno arma la declaración/respuesta en SU propio formato
 * de red (JSON crudo para el REST directo, `Schema`/`FunctionDeclaration` tipados para el SDK de
 * Firebase AI Logic), así que acá solo vive la lógica en común: los textos y el cálculo.
 *
 * Los 3 tiers locales (A/B/D) no tienen protocolo nativo de tools en su API — no hay forma de
 * declarar una función y recibir un `functionCall` estructurado como en Cloud/Firebase. En cambio
 * emulan la misma herramienta "por prompt" (patrón ReAct): [reactInstruction] se agrega al system
 * prompt pidiéndole al modelo que, para usar la herramienta, responda ÚNICAMENTE con una etiqueta
 * de texto parseable (`[[GET_CURRENT_TIME:zona]]`) en vez de una función tipada; cada backend local
 * detecta esa etiqueta con [matchReactCall] sobre el turno completo (sin dejarla filtrar al chat) y
 * hace su propio segundo pase con el resultado, igual en espíritu a los 2 pases de Cloud/Firebase.
 * Es más frágil que un tool-calling nativo (depende de que el modelo respete el formato pedido) —
 * aceptable para una demo de un solo tool simple, no un mecanismo general.
 */
object DemoTools {
    const val GET_CURRENT_TIME = "get_current_time"
    const val PARAM_TIMEZONE = "timezone"

    const val RESULT_ISO8601 = "iso8601"
    const val RESULT_READABLE = "readable"

    /**
     * `timezone` inválida o ausente cae a UTC en vez de fallar — que la herramienta nunca rompa el turno.
     * Las claves son estables (el modelo las lee tal cual); solo la fecha legible depende del idioma.
     */
    fun executeCurrentTime(timezone: String?, texts: AppTexts): Map<String, String> {
        val zoneId = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        val now = ZonedDateTime.now(zoneId)
        return mapOf(
            RESULT_ISO8601 to now.toString(),
            RESULT_READABLE to texts.formatDateTime(now),
        )
    }

    // La etiqueta es la misma en todos los idiomas: es lo que busca REACT_CALL_REGEX en la respuesta.
    private const val REACT_TAG = "GET_CURRENT_TIME"
    private val REACT_CALL_REGEX = Regex("""\[\[$REACT_TAG(?::([^\]]*))?\]\]""")

    private fun reactTag(timezone: String) = "[[$REACT_TAG:$timezone]]"

    /**
     * Addendum de system prompt para los tiers locales (A/B/D): sin esto, el modelo solo inventaría
     * una hora. Pedirle que responda ÚNICAMENTE con la etiqueta (nada de texto alrededor) es lo que
     * permite tratar el turno completo como "sí" o "no" hubo pedido de herramienta — ver [matchReactCall].
     */
    fun reactInstruction(texts: AppTexts): String =
        texts.reactInstruction(reactTag(texts.timeZonePlaceholder), reactTag("Europe/Madrid"))

    /**
     * `null` si `text` no contiene la etiqueta emulada — no hubo pedido de herramienta, se puede
     * mostrar tal cual. Si la contiene, devuelve la zona horaria pedida (cadena vacía si no la
     * especificó — [executeCurrentTime] ya cae a UTC en ese caso). Busca la etiqueta en cualquier
     * parte del texto (no ancla al string completo): los modelos chicos a veces agregan texto
     * alrededor pese a la instrucción de responder solo la etiqueta.
     */
    fun matchReactCall(text: String): String? = REACT_CALL_REGEX.find(text)?.groupValues?.get(1)
}
