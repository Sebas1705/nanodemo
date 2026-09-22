package es.sebas1705.nanodemo.core.common

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
 */
object DemoTools {
    const val GET_CURRENT_TIME = "get_current_time"
    const val PARAM_TIMEZONE = "timezone"

    const val CURRENT_TIME_DESCRIPTION =
        "Devuelve la fecha y hora actual en una zona horaria IANA (ej. 'Europe/Madrid', " +
            "'America/Argentina/Buenos_Aires'). Usala siempre que te pregunten la hora o la fecha " +
            "de hoy: no tenés reloj propio ni sabés qué día es."

    const val TIMEZONE_PARAM_DESCRIPTION = "Zona horaria IANA, ej. Europe/Madrid. Si no está clara, usá UTC."

    /** `timezone` inválida o ausente cae a UTC en vez de fallar — que la herramienta nunca rompa el turno. */
    fun executeCurrentTime(timezone: String?): Map<String, String> {
        val zoneId = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        val now = ZonedDateTime.now(zoneId)
        return mapOf(
            "iso8601" to now.toString(),
            "legible" to now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es"))),
        )
    }
}
