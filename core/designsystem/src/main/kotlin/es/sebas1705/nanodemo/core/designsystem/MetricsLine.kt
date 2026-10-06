package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import java.util.Locale

// Una respuesta que llega de un saque (p.ej. tras una herramienta, que se bufferea entera) tiene ~0 ms entre
// el primer y el último trozo: dividir por eso daría cifras absurdas, así que por debajo de esto no se calcula.
private const val MIN_GENERATION_MS = 50L

/**
 * Tokens por segundo de generación pura: desde el primer trozo hasta el final (el tiempo hasta el primer trozo
 * es "latencia", no velocidad). `null` si no hay base para calcularlo.
 */
fun GenerationMetric.tokensPerSecond(): Double? {
    val generationMs = totalDurationMs - (firstChunkLatencyMs ?: 0L)
    if (!success || responseTokens <= 0 || generationMs < MIN_GENERATION_MS) return null
    return responseTokens * 1000.0 / generationMs
}

private fun seconds(ms: Long) = String.format(Locale.US, "%.1fs", ms / 1000.0)

/**
 * Las cifras de una corrida, listas para mostrar: primer texto, total, velocidad, tokens y (si hubo) carga del
 * modelo. El "primer texto" incluye la búsqueda/herramienta cuando las hay, porque lo primero que se emite es
 * el aviso de ese paso. Los tokens van con "~" cuando son aproximados (caracteres/4) y no reales.
 */
fun GenerationMetric.displayParts(): List<String> = buildList {
    firstChunkLatencyMs?.let { add("⚡ ${seconds(it)}") }
    add("⏱ ${seconds(totalDurationMs)}")
    tokensPerSecond()?.let { add(String.format(Locale.US, "%.0f tok/s", it)) }
    if (responseTokens > 0) add("${if (responseTokensExact) "" else "~"}$responseTokens tok")
    loadTimeMs?.let { add("carga ${seconds(it)}") }
}

/** Línea chica con las métricas de una respuesta, bajo el texto — en Chat y en cada tarjeta de Comparar. */
@Composable
fun MetricsLine(metric: GenerationMetric, modifier: Modifier = Modifier) {
    Text(
        metric.displayParts().joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = DemoColors.Muted,
        modifier = modifier,
    )
}
