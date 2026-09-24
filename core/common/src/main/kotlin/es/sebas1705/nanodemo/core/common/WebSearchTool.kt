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
private const val CACHE_TTL_MS = 2 * 60_000L

data class WebResult(val title: String, val description: String, val url: String)

sealed interface WebSearchOutcome {
    data class Found(val results: List<WebResult>) : WebSearchOutcome

    /** No se pudo buscar (sin configurar, cuota agotada, HTTP, red o sin resultados): `reason` es legible. */
    data class Unavailable(val reason: String) : WebSearchOutcome
}

/**
 * Búsqueda en internet para los 3 tiers locales (ML Kit Nano, MediaPipe, LiteRT-LM), que no tienen red
 * propia: la APP busca y le pasa los resultados al modelo (ver `WebSearchAnswer.kt` para cómo se arma el
 * turno). Cloud/Firebase AI no pasan por acá — usan el grounding nativo de Google Search de su API.
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

    // Comparar manda el mismo prompt a los 3 tiers locales casi a la vez: sin esto serían 3 búsquedas
    // iguales (3 unidades de cuota de Brave). También evita gastar otra si se reintenta enseguida.
    private val cache = HashMap<String, Pair<Long, WebSearchOutcome.Found>>()

    /**
     * Busca `query` y devuelve los primeros resultados (título, descripción, link). Nunca lanza: cualquier
     * fallo vuelve como [WebSearchOutcome.Unavailable] — la búsqueda nunca tiene que romper el turno.
     */
    suspend fun search(query: String): WebSearchOutcome = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext WebSearchOutcome.Unavailable("falta BRAVE_SEARCH_API_KEY")
        synchronized(cache) { cache[query]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS } }
            ?.let { return@withContext it.second }
        // Sin cuota mensual (ver UsageQuota) no se llama a Brave, en vez de cobrar de más en silencio.
        if (!UsageQuota.canUse(QuotaService.BRAVE_SEARCH)) {
            return@withContext WebSearchOutcome.Unavailable("límite gratis de Brave alcanzado")
        }
        UsageQuota.record(QuotaService.BRAVE_SEARCH)
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
                return@withContext WebSearchOutcome.Unavailable("error HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONObject(body).optJSONObject("web")?.optJSONArray("results")
            val results = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val item = array!!.getJSONObject(i)
                val link = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebResult(
                    title = cleanHtml(item.optString("title")).ifBlank { link },
                    description = cleanHtml(item.optString("description")),
                    url = link,
                )
            }
            if (results.isEmpty()) return@withContext WebSearchOutcome.Unavailable("sin resultados")
            WebSearchOutcome.Found(results).also { found ->
                synchronized(cache) { cache[query] = System.currentTimeMillis() to found }
            }
        } catch (e: Exception) {
            Log.e(TAG, "search(\"$query\"): falló", e)
            WebSearchOutcome.Unavailable("error de red")
        } finally {
            connection.disconnect()
        }
    }

    /** Brave devuelve `<strong>` en los snippets y entidades HTML: se limpian para no ensuciar el prompt. */
    private fun cleanHtml(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
