package es.sebas1705.nanodemo.feature.lab.bench

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.thermalStatusLabel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.common.BarRow
import es.sebas1705.nanodemo.feature.lab.common.HeatCellBox
import es.sebas1705.nanodemo.feature.lab.common.SmallNote
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.common.passRateColor
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val benchExperiment = Experiment(
    id = "bench",
    category = ExperimentCategory.MEASUREMENT,
    title = "Benchmark",
    subtitle = "Tareas con corrección automática en todos los motores: ranking, mapa de calor, latencias y determinismo.",
    icon = Icons.Filled.Speed,
    content = { BenchScreen() },
)

@Composable
fun BenchScreen(viewModel: BenchViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val baselineId by viewModel.baselineId.collectAsState()

    LaunchedEffect(availability) { viewModel.applyDefaultsIfNeeded(availability) }

    val taskCount = BenchTasks.byCategories(setup.categories).size
    val totalRuns = setup.tiers.size * taskCount * setup.repetitions

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel("Motores")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InferenceTier.entries) { tier ->
                    val usable = availability.isUsable(tier)
                    TierChip(
                        tier = tier,
                        selected = tier in setup.tiers,
                        onClick = { if (usable) viewModel.toggleTier(tier) },
                        modifier = Modifier.alpha(if (usable) 1f else 0.4f),
                    )
                }
            }
            if (InferenceTier.entries.any { !availability.isUsable(it) }) {
                SmallNote("Los motores no disponibles ahora no se pueden elegir; se activan al descargar su modelo o configurar su clave.")
            }

            SectionLabel("Categorías")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BenchCategory.entries) { category ->
                    FilterChip(
                        selected = category in setup.categories,
                        onClick = { viewModel.toggleCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeticiones", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.setRepetitions(setup.repetitions - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Menos repeticiones", tint = DemoColors.Ink)
                }
                Text("${setup.repetitions}", style = readoutStyle, color = DemoColors.Ink)
                IconButton(onClick = { viewModel.setRepetitions(setup.repetitions + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Más repeticiones", tint = DemoColors.Ink)
                }
            }
            Text(
                "Temperatura ${"%.1f".format(Locale.US, setup.temperature)} (0 = lo más determinista)",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            Slider(value = setup.temperature, onValueChange = viewModel::setTemperature, valueRange = 0f..1.5f)
            OutlinedTextField(
                value = setup.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Nombre de la sesión (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SmallNote("$taskCount tareas × ${setup.tiers.size} motores × ${setup.repetitions} rep. = $totalRuns ejecuciones, una a una.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::start, enabled = viewModel.canStart(setup)) { Text(if (running) "Ejecutando…" else "Ejecutar") }
                if (running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
            }
        }

        progress?.let { p ->
            AppCard(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = p.tier?.signalColor ?: DemoColors.TierA,
                )
                Text(
                    "${p.done}/${p.total}" + (p.tier?.let { " · [${it.channelLetter()}]" } ?: "") + (p.taskTitle?.let { " · $it" } ?: ""),
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
            }
        }

        if (samples.isNotEmpty()) {
            val baseline = sessions.firstOrNull { it.id == baselineId }
            Results(samples, baseline)
            ExportRow(samples)
        }

        if (sessions.isNotEmpty()) {
            SessionsList(
                sessions = sessions,
                baselineId = baselineId,
                canShow = !running,
                onShow = viewModel::show,
                onBaseline = { viewModel.setBaseline(if (baselineId == it) null else it) },
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
private fun Results(samples: List<BenchSample>, baseline: BenchSession?) {
    val summaries = remember(samples) { summarize(samples) }
    val heat = remember(samples) { heatmap(samples) }
    val determinism = remember(samples) { determinism(samples) }
    val tiers = summaries.map { it.tier }
    val categories = BenchCategory.entries.filter { c -> tiers.any { heat.containsKey(it to c) } }

    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Ranking")
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TableRow(listOf("Motor", "Acierto", "Nota", "p50", "p95", "1.er tok", "tok/s", "Err"), header = true)
            summaries.forEach { s ->
                TableRow(
                    listOf(
                        "[${s.tier.channelLetter()}]",
                        "%.0f%%".format(Locale.US, s.passRate * 100),
                        "%.2f".format(Locale.US, s.meanScore),
                        s.latencyP50Ms?.let(::formatDuration) ?: "–",
                        s.latencyP95Ms?.let(::formatDuration) ?: "–",
                        s.ttftP50Ms?.let(::formatDuration) ?: "–",
                        s.tokensPerSecond?.let { "%.0f".format(Locale.US, it) } ?: "–",
                        if (s.errors > 0) "${s.errors}" else "0",
                    ),
                    color = s.tier.signalColor,
                    passRate = s.passRate,
                )
            }
        }
        SmallNote("Acierto = tareas con puntuación perfecta. Nota = puntuación media (0-1, con crédito parcial). Los tiempos no cuentan los errores ni la carga del modelo.")
    }

    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Mapa de calor: acierto por categoría")
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("", Modifier.width(40.dp))
                categories.forEach { c -> Text(c.label.take(5), style = readoutStyle, color = DemoColors.Muted, modifier = Modifier.width(54.dp)) }
            }
            tiers.forEach { tier ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("[${tier.channelLetter()}]", style = readoutStyle, color = tier.signalColor, modifier = Modifier.width(40.dp))
                    categories.forEach { c ->
                        val cell = heat[tier to c]
                        HeatCellBox(cell?.passRate, cell?.let { "%.0f%%".format(Locale.US, it.passRate * 100) } ?: "–", 54.dp)
                    }
                }
            }
        }
    }

    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Latencia mediana por respuesta")
        val maxP50 = summaries.mapNotNull { it.latencyP50Ms }.maxOrNull() ?: 1L
        summaries.forEach { s ->
            s.latencyP50Ms?.let {
                BarRow("[${s.tier.channelLetter()}]", it.toFloat() / maxP50, formatDuration(it), s.tier.signalColor)
            }
        }
        val extras = summaries.filter { it.maxLoadMs != null || it.maxThermalStatus != null || it.batteryDrainUah != null }
        if (extras.isNotEmpty()) {
            SectionLabel("Dispositivo durante la sesión")
            extras.forEach { s ->
                val parts = buildList {
                    s.maxLoadMs?.let { add("carga del modelo ${formatDuration(it)}") }
                    s.maxThermalStatus?.let { add("térmica máx. ${thermalStatusLabel(it)}") }
                    s.batteryDrainUah?.takeIf { it > 0 }?.let { add("batería −${it / 1000} mAh") }
                }
                Text("[${s.tier.channelLetter()}] ${parts.joinToString(" · ")}", style = readoutStyle, color = DemoColors.Muted)
            }
        }
    }

    if (determinism.values.any { it != null }) {
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel("Determinismo")
            determinism.forEach { (tier, share) ->
                share?.let { BarRow("[${tier.channelLetter()}]", it.toFloat(), "%.0f%%".format(Locale.US, it * 100), passRateColor(it)) }
            }
            SmallNote("Tareas con la misma respuesta en todas las repeticiones. A temperatura 0 debería ser 100%.")
        }
    }

    baseline?.let { base ->
        val deltas = compareSessions(samples, base.samples)
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel("Contra «${base.label}»")
            if (deltas.isEmpty()) {
                SmallNote("No hay motores en común con esa sesión.")
            } else {
                deltas.forEach { d ->
                    val pass = d.passRateDelta * 100
                    val latency = d.latencyP50DeltaMs
                    Text(
                        "[${d.tier.channelLetter()}] acierto ${"%+.0f".format(Locale.US, pass)} pts" +
                            (latency?.let { " · mediana ${if (it >= 0) "+" else "−"}${formatDuration(kotlin.math.abs(it))}" } ?: ""),
                        style = readoutStyle,
                        color = if (d.passRateDelta >= 0) DemoColors.TierA else DemoColors.Danger,
                    )
                }
            }
        }
    }

    val failures = samples.filter { !it.passed }
    if (failures.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        AppCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
            SectionLabel("Fallos (${failures.size}) ${if (expanded) "▾" else "▸"}")
            if (expanded) {
                val titles = BenchTasks.all.associate { it.id to it.title }
                failures.take(60).forEach { f ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("[${f.tier.channelLetter()}] ${titles[f.taskId] ?: f.taskId} · ${"%.2f".format(Locale.US, f.score)}", style = readoutStyle, color = f.tier.signalColor)
                        Text((f.error ?: f.answer.ifBlank { "(sin respuesta)" }).take(240), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, color: androidx.compose.ui.graphics.Color = DemoColors.Ink, passRate: Double? = null) {
    val widths = listOf(48, 64, 52, 62, 62, 66, 52, 40)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEachIndexed { index, text ->
            val tint = when {
                header -> DemoColors.Muted
                index == 0 -> color
                index == 1 && passRate != null -> passRateColor(passRate)
                else -> DemoColors.Ink
            }
            Text(text, style = readoutStyle, color = tint, modifier = Modifier.width(widths[index].dp))
        }
    }
}

@Composable
private fun ExportRow(samples: List<BenchSample>) {
    val context = LocalContext.current
    fun share(name: String, mime: String, content: String) {
        val file = File(context.cacheDir, name).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir resultados"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { share("nanodemo-bench.csv", "text/csv", samplesToCsv(samples)) }) { Text("Exportar CSV") }
        OutlinedButton(
            onClick = {
                val json = Json { prettyPrint = true }.encodeToString(ListSerializer(BenchSample.serializer()), samples)
                share("nanodemo-bench.json", "application/json", json)
            },
        ) { Text("Exportar JSON") }
    }
}

@Composable
private fun SessionsList(
    sessions: List<BenchSession>,
    baselineId: Long?,
    canShow: Boolean,
    onShow: (BenchSession) -> Unit,
    onBaseline: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Sesiones guardadas")
        val format = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
        sessions.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Text(
                    "${format.format(Date(s.startedAtMs))} · ${s.samples.size} ejecuciones · temp ${"%.1f".format(Locale.US, s.temperature)} · ×${s.repetitions}",
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onShow(s) }, enabled = canShow) { Text("Ver") }
                    TextButton(onClick = { onBaseline(s.id) }) { Text(if (baselineId == s.id) "Quitar comparación" else "Comparar con esta") }
                    TextButton(onClick = { onDelete(s.id) }) { Text("Borrar") }
                }
            }
        }
    }
}
