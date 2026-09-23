package es.sebas1705.nanodemo.core.common

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "NanoDemo.WebSearchTool"
private const val ENDPOINT = "https://api.search.brave.com/res/v1/web/search"
private const val MAX_RESULTS = 5
private const val TIMEOUT_MS = 15_000

/**
 * Segunda herramienta emulada por prompt para los tiers locales (ver `DemoTools` para la primera,
 * `get_current_time`, y `LocalPromptEmulation.kt` para el mecanismo general de etiqueta + 2 pases).
 * A diferencia de esa, esta SÍ necesita red: ninguno de los 3 motores locales (ML Kit Nano,
 * MediaPipe, LiteRT-LM) tiene acceso a internet por su cuenta, así que "búsqueda en internet" para
 * ellos solo puede significar que la APP busca y le pasa los resultados al modelo — google_search
 * nativo de Cloud/Firebase (grounding real del lado de Google) sigue siendo distinto de esto.
 *
 * Usa la Brave Search API (`BRAVE_SEARCH_API_KEY`, header `X-Subscription-Token`) — se probó antes
 * con Google Custom Search JSON API, pero Google discontinuó "buscar en toda la web" para motores
 * nuevos desde el 20/01/2026 (solo hasta 50 sitios fijos, no útil para grounding general). El
 * secreto es opcional: si falta, [isConfigured] da `false` y la búsqueda queda deshabilitada con un
 * mensaje, nunca rompe el turno (ver build.gradle.kts de este módulo).
 */
object WebSearchTool {
    val isConfigured: Boolean
        get() = BuildConfig.BRAVE_SEARCH_API_KEY.isNotBlank()

    private val REACT_CALL_REGEX = Regex("""\[\[SEARCH_WEB:([^\]]+)\]\]""")

    /**
     * Addendum de system prompt para los tiers locales: sin esto, el modelo respondería con lo que
     * sepa de su corte de entrenamiento en vez de reconocer que no puede saber algo actual. Misma
     * idea que [DemoTools.reactInstruction] — responder ÚNICAMENTE con la etiqueta permite tratar el
     * turno completo como "sí"/"no" hubo pedido de búsqueda.
     */
    fun reactInstruction(): String =
        "Si te preguntan algo actual que no podés saber con certeza por tu corte de entrenamiento " +
            "(noticias, precios, resultados recientes, versiones nuevas, etc.), no lo inventes. En vez " +
            "de responder directamente, respondé ÚNICAMENTE con esta etiqueta exacta, sin texto antes " +
            "ni después: [[SEARCH_WEB:tu consulta de búsqueda]] (ej. " +
            "[[SEARCH_WEB:resultado final del mundial 2026]]). Vas a recibir resultados reales y ahí sí " +
            "completás tu respuesta con esa información."

    /** `null` si `text` no contiene la etiqueta emulada. Si la contiene, la consulta pedida (recortada, nunca vacía). */
    fun matchReactCall(text: String): String? =
        REACT_CALL_REGEX.find(text)?.groupValues?.get(1)?.trim()?.ifBlank { null }

    /**
     * Busca `query` y devuelve un resumen legible de los primeros resultados (título + descripción +
     * link), listo para agregar como turno de contexto. Nunca lanza: cualquier fallo (sin
     * configurar, HTTP, red) vuelve como un string explicativo en vez de una excepción — la
     * herramienta nunca tiene que romper el turno, mismo criterio que `DemoTools.executeCurrentTime`.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext "Búsqueda no configurada en este dispositivo (falta BRAVE_SEARCH_API_KEY)."
        val url = "$ENDPOINT?q=${URLEncoder.encode(query, "UTF-8")}&count=$MAX_RESULTS"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Subscription-Token", BuildConfig.BRAVE_SEARCH_API_KEY)
        }
        try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "search(\"$query\"): HTTP ${connection.responseCode}")
                return@withContext "Error buscando \"$query\" (HTTP ${connection.responseCode})."
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONObject("web")?.optJSONArray("results")
            if (results == null || results.length() == 0) return@withContext "Sin resultados para \"$query\"."
            (0 until results.length()).joinToString("\n\n") { i ->
                val item = results.getJSONObject(i)
                "- ${item.optString("title")}: ${item.optString("description")} (${item.optString("url")})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "search(\"$query\"): falló", e)
            "No se pudo buscar \"$query\" (error de red)."
        } finally {
            connection.disconnect()
        }
    }
}
