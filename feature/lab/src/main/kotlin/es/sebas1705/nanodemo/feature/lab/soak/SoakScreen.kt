package es.sebas1705.nanodemo.feature.lab.soak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.thermalStatusLabel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.common.ChartSeries
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.LineChart
import es.sebas1705.nanodemo.feature.lab.common.SmallNote
import java.util.Locale

val soakExperiment = Experiment(
    id = "soak",
    category = ExperimentCategory.MEASUREMENT,
    title = R.string.lab_soak_title,
    subtitle = R.string.lab_soak_subtitle,
    icon = Icons.Filled.Thermostat,
    content = { SoakScreen() },
)

@Composable
fun SoakScreen(viewModel: SoakViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val minutes by viewModel.minutes.collectAsState()
    val points by viewModel.points.collectAsState()
    val running by viewModel.running.collectAsState()
    val summary = summarizeSoak(points)
    val selected = tier ?: InferenceTier.ML_KIT_NANO

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Un móvil que se calienta baja la frecuencia del procesador (throttling) y el modelo se vuelve más lento. " +
                "Esta prueba genera texto seguido en un motor y dibuja la velocidad (tokens/s) a lo largo del tiempo. " +
                "Mejor con el cargador desconectado y la pantalla encendida.",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
        )
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel("Motor")
            LabTierSelector(selected, availability, viewModel::selectTier)
            if (selected == InferenceTier.CLOUD_GEMINI || selected == InferenceTier.FIREBASE_AI) {
                SmallNote("Ojo: este motor es de nube; gasta cuota gratis y no calienta el móvil. Para medir el calor usa uno on-device.")
            }
            SectionLabel("Duración")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOAK_MINUTES.forEach { m ->
                    FilterChip(selected = m == minutes, onClick = { viewModel.selectMinutes(m) }, label = { Text("$m min") }, enabled = !running)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.start(selected) }, enabled = !running && availability[selected] == TierAvailability.AVAILABLE) {
                    Text(if (running) "Generando…" else "Empezar")
                }
                if (running) OutlinedButton(onClick = viewModel::stop) { Text("Parar") }
            }
        }

        if (points.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("Velocidad a lo largo del tiempo")
                LineChart(
                    series = listOf(
                        ChartSeries(
                            "[${selected.channelLetter()}] tok/s",
                            selected.signalColor,
                            points.mapNotNull { p -> p.tokensPerSecond?.let { p.elapsedSeconds to it.toFloat() } },
                        ),
                    ),
                    xLabel = "s",
                    yLabel = "tok/s",
                )
            }
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("Resultado")
                Text("${summary.iterations} generaciones", style = readoutStyle, color = DemoColors.Ink)
                if (summary.startTokensPerSecond != null && summary.endTokensPerSecond != null && summary.changePercent != null) {
                    Text(
                        "Velocidad ${"%.1f".format(Locale.US, summary.startTokensPerSecond)} → ${"%.1f".format(Locale.US, summary.endTokensPerSecond)} tok/s " +
                            "(${"%+.0f".format(Locale.US, summary.changePercent)}%)",
                        style = readoutStyle,
                        color = if (summary.changePercent < -10) DemoColors.Danger else DemoColors.TierA,
                    )
                } else {
                    SmallNote("Hacen falta al menos 3 generaciones con velocidad medible para comparar el principio y el final.")
                }
                Text("Estado térmico máximo: ${thermalStatusLabel(summary.maxThermalStatus)}", style = readoutStyle, color = DemoColors.Ink)
                summary.totalBatteryDrainUah?.let {
                    Text("Batería: ${if (it > 0) "−" else ""}${kotlin.math.abs(it) / 1000} mAh (lectura gruesa)", style = readoutStyle, color = DemoColors.Ink)
                }
            }
        }
    }
}
