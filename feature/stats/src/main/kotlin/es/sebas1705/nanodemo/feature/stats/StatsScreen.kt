package es.sebas1705.nanodemo.feature.stats

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import java.util.Locale

/**
 * Estadísticas: una tarjeta por tier con filas etiqueta/valor separadas (en
 * vez de un único string empaquetado con "·", que era difícil de leer) + un
 * medidor de nivel de latencia relativa, más el botón de exportar a CSV.
 * Adaptativo: apiladas en teléfono, en fila (una junto a otra) en
 * tablet/plegable/landscape ancho — tiene sentido porque son exactamente 3.
 */
@Composable
fun StatsScreen(sizeClass: ScreenSizeClass, viewModel: StatsViewModel = hiltViewModel()) {
    val metrics by viewModel.metrics.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ESTADÍSTICAS",
                style = MaterialTheme.typography.headlineSmall,
                color = DemoColors.Ink,
            )
            if (metrics.isNotEmpty()) {
                TextButton(onClick = { exportAndShare(viewModel, context) }) { Text("Exportar CSV") }
            }
        }

        HelpPanel()

        if (metrics.isEmpty()) {
            Text(
                "Todavía no generaste nada. Probá un prompt en Chat o en Comparar " +
                    "para empezar a ver métricas acá.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            return@Column
        }

        val byTier = metrics.groupBy { it.tier }
        val stats = InferenceTier.entries.mapNotNull { tier -> byTier[tier]?.let { tier to computeTierStat(it) } }
        val maxAvgLatencyMs = stats.maxOf { it.second.avgLatencyMs }.coerceAtLeast(1.0)

        AdaptiveGrid(items = stats, columns = sizeClass.tierColumns) { (tier, stat), cardModifier ->
            StatCard(tier, stat, maxAvgLatencyMs, cardModifier)
        }
    }
}

private val METRIC_HELP: List<Pair<String, String>> = listOf(
    "Latencia prom." to "Tiempo total promedio desde que se envía el prompt hasta que llega " +
        "la última palabra de la respuesta.",
    "p50 / p95" to "Percentiles: p50 = la mitad de las corridas fueron más rápidas que este " +
        "valor. p95 = el 95% lo fueron. Sirven para ver consistencia, no solo el promedio " +
        "(un tier con p95 mucho más alto que su p50 es \"impredecible\").",
    "Velocidad" to "Caracteres de la respuesta generados por segundo, en promedio.",
    "Tokens" to "Cantidad de tokens (unidades de texto que procesa el modelo, no exactamente " +
        "palabras) de la respuesta. Real cuando el backend lo expone (MediaPipe, Cloud); " +
        "aproximado (caracteres/4) en ML Kit Nano.",
    "Éxito" to "Cuántas de las corridas contra este tier terminaron sin error, sobre el total.",
    "Costo (estimado)" to "Solo Cloud y Firebase AI (los tiers en la nube) lo muestran — los " +
        "on-device no llaman a ninguna API paga, cuestan \$0. Se calcula con el precio público de " +
        "Gemini Flash (entrada/salida) y una aproximación de tokens del prompt (caracteres/4, no " +
        "hay conteo exacto de entrada); tratalo como orden de magnitud, no como factura real.",
)

@Composable
private fun HelpPanel() {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "¿QUÉ SIGNIFICA CADA MÉTRICA?",
                style = MaterialTheme.typography.labelSmall,
                color = DemoColors.Muted,
                letterSpacing = 1.5.sp,
            )
            Text(if (expanded) "▲" else "▼", style = readoutStyle, color = DemoColors.Muted)
        }
        if (expanded) {
            METRIC_HELP.forEach { (label, help) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = readoutStyle, color = DemoColors.Ink, fontWeight = FontWeight.Bold)
                    Text(help, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                }
            }
        }
    }
}

private fun exportAndShare(viewModel: StatsViewModel, context: Context) {
    viewModel.exportMetricsCsv()?.let { uri ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir estadísticas"))
    }
}

private data class TierStat(
    val avgLatencyMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val charsPerSec: Double,
    val avgTokens: Double,
    val tokensExact: Boolean,
    val successCount: Int,
    val totalCount: Int,
    /** `null` en los tiers on-device (A/B/D): no hay llamada facturada, el costo real es 0. */
    val totalCostUsd: Double?,
)

private fun computeTierStat(runs: List<GenerationMetric>): TierStat {
    val durations = runs.map { it.totalDurationMs }.sorted()
    val charsPerSec = runs
        .filter { it.success && it.totalDurationMs > 0 }
        .map { it.responseChars * 1000.0 / it.totalDurationMs }
        .let { if (it.isEmpty()) 0.0 else it.average() }
    val avgTokens = runs.filter { it.success }.map { it.responseTokens }
        .let { if (it.isEmpty()) 0.0 else it.average() }
    return TierStat(
        avgLatencyMs = durations.average(),
        p50Ms = percentile(durations, 0.5),
        p95Ms = percentile(durations, 0.95),
        charsPerSec = charsPerSec,
        avgTokens = avgTokens,
        tokensExact = runs.any { it.responseTokensExact },
        successCount = runs.count { it.success },
        totalCount = runs.size,
        totalCostUsd = estimatedCostUsd(runs),
    )
}

// Precios de Gemini Flash (nivel pagado, USD/millón de tokens), verificados en
// https://ai.google.dev/gemini-api/docs/pricing el 22/09/2026 — Gemini 3.7/3.8 Flash: $0.75
// entrada / $3.75 salida (vigente hasta el 31/12/2026). El modelo real que usan los Tiers C/E
// (`gemini-3.5-flash`) no está listado ahí con precio propio; se usa el de la familia Flash más
// cercana como aproximación — de ahí el "(estimado)" en la UI. Sube a $1.50/$7.50 desde 2027.
private const val INPUT_USD_PER_MILLION_TOKENS = 0.75
private const val OUTPUT_USD_PER_MILLION_TOKENS = 3.75

/** Solo los tiers en la nube facturan por token; los on-device (A/B/D) no llaman a ninguna API paga. */
private val METERED_TIERS = setOf(InferenceTier.CLOUD_GEMINI, InferenceTier.FIREBASE_AI)

private fun estimatedCostUsd(runs: List<GenerationMetric>): Double? {
    val tier = runs.firstOrNull()?.tier ?: return null
    if (tier !in METERED_TIERS) return null
    return runs.sumOf { run ->
        // No se captura el conteo real de tokens del prompt (solo de la respuesta): se aproxima
        // igual que el resto del código (caracteres/4, ver GenerationMetric.responseTokensExact).
        val inputTokens = run.promptChars / CHARS_PER_TOKEN_APPROX
        val outputTokens = run.responseTokens.toDouble()
        (inputTokens * INPUT_USD_PER_MILLION_TOKENS + outputTokens * OUTPUT_USD_PER_MILLION_TOKENS) /
            TOKENS_PER_MILLION
    }
}

private const val CHARS_PER_TOKEN_APPROX = 4.0
private const val TOKENS_PER_MILLION = 1_000_000.0

/** Percentil simple sobre una lista YA ordenada ascendentemente. */
private fun percentile(sortedValues: List<Long>, fraction: Double): Double {
    if (sortedValues.isEmpty()) return 0.0
    val index = (fraction * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.size - 1)
    return sortedValues[index].toDouble()
}

@Composable
private fun StatCard(tier: InferenceTier, stat: TierStat, maxAvgLatencyMs: Double, modifier: Modifier) {
    val fraction = (stat.avgLatencyMs / maxAvgLatencyMs).toFloat().coerceIn(0.05f, 1f)
    Column(
        modifier = modifier
            .border(1.dp, tier.signalColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("[${tier.channelLetter()}]", style = readoutStyle, color = tier.signalColor, fontWeight = FontWeight.Bold)
            Text(tier.label(), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(DemoColors.Background, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(tier.signalColor, RoundedCornerShape(4.dp))
            )
        }

        StatRow("Latencia prom.", String.format(Locale.US, "%.1fs", stat.avgLatencyMs / 1000))
        StatRow("p50 / p95", String.format(Locale.US, "%.1fs / %.1fs", stat.p50Ms / 1000, stat.p95Ms / 1000))
        StatRow("Velocidad", String.format(Locale.US, "%.0f c/s", stat.charsPerSec))
        StatRow(
            "Tokens",
            String.format(Locale.US, "%.0f%s", stat.avgTokens, if (stat.tokensExact) "" else " (aprox.)"),
        )
        StatRow("Éxito", "${stat.successCount}/${stat.totalCount}")
        if (stat.totalCostUsd != null) {
            StatRow("Costo (estimado)", String.format(Locale.US, "\$%.4f", stat.totalCostUsd))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = readoutStyle, color = DemoColors.Muted)
        Text(value, style = readoutStyle, color = DemoColors.Ink)
    }
}
