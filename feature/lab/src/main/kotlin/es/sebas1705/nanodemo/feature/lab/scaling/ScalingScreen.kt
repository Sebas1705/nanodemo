package es.sebas1705.nanodemo.feature.lab.scaling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.common.ChartSeries
import es.sebas1705.nanodemo.feature.lab.common.LineChart
import es.sebas1705.nanodemo.feature.lab.common.SmallNote
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import java.util.Locale

val scalingExperiment = Experiment(
    id = "scaling",
    category = ExperimentCategory.MEASUREMENT,
    title = R.string.lab_scaling_title,
    subtitle = R.string.lab_scaling_subtitle,
    icon = Icons.Filled.ShowChart,
    content = { ScalingScreen() },
)

@Composable
fun ScalingScreen(viewModel: ScalingViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val selected by viewModel.tiers.collectAsState()
    val points by viewModel.points.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LaunchedEffect(availability) { viewModel.applyDefaultsIfNeeded(availability) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Manda prompts cada vez más largos (de ${SCALING_STEPS.first()} a ${SCALING_STEPS.last()} caracteres) y mide el tiempo hasta el primer token. " +
                "La pendiente es lo que cuesta cada mil caracteres más de contexto. Se saltan los tamaños que no caben en el motor.",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
        )
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel("Motores")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InferenceTier.entries) { tier ->
                    val usable = availability.isUsable(tier)
                    TierChip(
                        tier = tier,
                        selected = tier in selected,
                        onClick = { if (usable) viewModel.toggleTier(tier) },
                        modifier = Modifier.alpha(if (usable) 1f else 0.4f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::start, enabled = selected.isNotEmpty() && !running) { Text(if (running) "Midiendo…" else "Medir") }
                if (running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
            }
            progress?.let { (done, total) ->
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = DemoColors.TierA)
                SmallNote("$done/$total peticiones")
            }
        }

        val series = meanTtftBySize(points)
        if (series.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("Primer token según el tamaño del prompt (ms)")
                LineChart(
                    series = series.map { (tier, list) ->
                        ChartSeries("[${tier.channelLetter()}]", tier.signalColor, list.map { it.first.toFloat() to it.second.toFloat() })
                    },
                    xLabel = "car.",
                    yLabel = "ms",
                )
            }
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("Coste de un prompt más largo")
                series.keys.forEach { tier ->
                    val slope = slopeMsPer1kChars(points.filter { it.tier == tier })
                    Text(
                        "[${tier.channelLetter()}] " + (slope?.let { "≈ ${"%.0f".format(Locale.US, it)} ms por cada 1000 caracteres" } ?: "sin datos suficientes"),
                        style = readoutStyle,
                        color = tier.signalColor,
                    )
                }
                SmallNote("Una pendiente cercana a cero indica que el motor procesa el prompt casi gratis (nube); una alta, que el prefill se nota (on-device).")
            }
        }
        val failed = points.count { !it.ok }
        if (failed > 0) SmallNote("$failed peticiones fallaron o tardaron demasiado y no cuentan.")
    }
}
