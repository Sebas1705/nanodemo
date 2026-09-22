package es.sebas1705.nanodemo.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val URL_FETCH_MAX_CHARS = 6_000
private const val URL_FETCH_TIMEOUT_MS = 15_000

/**
 * Descarga una URL y devuelve su texto legible — para "pegar un link y preguntar", que el usuario
 * no tenga que copiar/pegar el contenido a mano. Funciona igual en los 5 tiers (es solo texto de
 * más que se manda de contexto), a diferencia de la búsqueda con grounding, que solo la soportan
 * los tiers en la nube.
 *
 * Extracción básica (saca `<script>`/`<style>`, saca el resto de las etiquetas, decodifica un
 * puñado de entidades comunes) — no es un parser de HTML real ni ejecuta JavaScript, así que sirve
 * para artículos/docs/páginas de texto normales, no para SPAs que renderizan todo por JS. Se
 * recorta a [URL_FETCH_MAX_CHARS] para no inflar el prompt (y el costo/latencia) con páginas
 * enormes.
 */
suspend fun fetchUrlText(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = URL_FETCH_TIMEOUT_MS
        readTimeout = URL_FETCH_TIMEOUT_MS
        // Algunos sitios devuelven 403 sin un User-Agent de navegador.
        setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AIMobiDemo)")
    }
    try {
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} descargando la página" }
        val html = connection.inputStream.bufferedReader().use { it.readText() }
        extractReadableText(html).take(URL_FETCH_MAX_CHARS)
    } finally {
        connection.disconnect()
    }
}

private val SCRIPT_OR_STYLE_TAG = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
private val ANY_TAG = Regex("(?s)<[^>]+>")
private val WHITESPACE_RUN = Regex("\\s+")

private fun extractReadableText(html: String): String {
    val withoutScripts = SCRIPT_OR_STYLE_TAG.replace(html, " ")
    val withoutTags = ANY_TAG.replace(withoutScripts, " ")
    val decoded = withoutTags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    return WHITESPACE_RUN.replace(decoded, " ").trim()
}
