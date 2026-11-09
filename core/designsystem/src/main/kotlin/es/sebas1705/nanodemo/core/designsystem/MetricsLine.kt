package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import java.util.Locale
import kotlin.math.abs

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

// Below this the PSS reading is noise (allocator jitter), not a model loading or unloading.
private const val MIN_MEMORY_DELTA_KB = 10_240L

/** "+1.2 GB" / "-340 MB": the sign matters, a negative delta means memory was released. */
internal fun signedMegabytes(kb: Long): String {
    val sign = if (kb < 0) "-" else "+"
    val mb = abs(kb) / 1024.0
    return if (mb >= 1024) String.format(Locale.US, "%s%.1f GB", sign, mb / 1024) else String.format(Locale.US, "%s%.0f MB", sign, mb)
}

/**
 * Las cifras de una corrida, listas para mostrar: primer texto, total, velocidad, tokens y (si hubo) carga del
 * modelo. El "primer texto" incluye la búsqueda/herramienta cuando las hay, porque lo primero que se emite es
 * el aviso de ese paso. Los tokens van con "~" cuando son aproximados (caracteres/4) y no reales.
 */
fun GenerationMetric.displayParts(loadLabel: String = "load"): List<String> = buildList {
    firstChunkLatencyMs?.let { add("⚡ ${seconds(it)}") }
    add("⏱ ${seconds(totalDurationMs)}")
    tokensPerSecond()?.let { add(String.format(Locale.US, "%.0f tok/s", it)) }
    if (responseTokens > 0) add("${if (responseTokensExact) "" else "~"}$responseTokens tok")
    loadTimeMs?.let { add("$loadLabel ${seconds(it)}") }
    pssDeltaKb?.takeIf { abs(it) >= MIN_MEMORY_DELTA_KB }?.let { add("RAM ${signedMegabytes(it)}") }
}

/** Línea chica con las métricas de una respuesta, bajo el texto — en Chat y en cada tarjeta de Comparar. */
@Composable
fun MetricsLine(metric: GenerationMetric, modifier: Modifier = Modifier) {
    Text(
        metric.displayParts(stringResource(R.string.metrics_load)).joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = DemoColors.Muted,
        modifier = modifier,
    )
}
