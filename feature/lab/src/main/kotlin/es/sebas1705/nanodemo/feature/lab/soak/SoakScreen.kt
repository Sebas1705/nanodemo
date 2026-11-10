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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import es.sebas1705.nanodemo.feature.lab.common.thermalStatusLabelRes
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
            stringResource(R.string.soak_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
        )
        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.soak_engine))
            LabTierSelector(selected, availability, viewModel::selectTier)
            if (selected == InferenceTier.CLOUD_GEMINI || selected == InferenceTier.FIREBASE_AI) {
                SmallNote(stringResource(R.string.soak_cloud_warning))
            }
            SectionLabel(stringResource(R.string.soak_duration))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOAK_MINUTES.forEach { m ->
                    FilterChip(selected = m == minutes, onClick = { viewModel.selectMinutes(m) }, label = { Text(stringResource(R.string.soak_minutes, m)) }, enabled = !running)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.start(selected) }, enabled = !running && availability[selected] == TierAvailability.AVAILABLE) {
                    Text(stringResource(if (running) R.string.soak_generating else R.string.soak_start))
                }
                if (running) OutlinedButton(onClick = viewModel::stop) { Text(stringResource(R.string.soak_stop)) }
            }
        }

        if (points.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.soak_chart_title))
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
                SectionLabel(stringResource(R.string.soak_result_title))
                Text(pluralStringResource(R.plurals.soak_generations_count, summary.iterations, summary.iterations), style = readoutStyle, color = DemoColors.Ink)
                if (summary.startTokensPerSecond != null && summary.endTokensPerSecond != null && summary.changePercent != null) {
                    Text(
                        stringResource(
                            R.string.soak_speed_change,
                            "%.1f".format(Locale.US, summary.startTokensPerSecond),
                            "%.1f".format(Locale.US, summary.endTokensPerSecond),
                            "%+.0f".format(Locale.US, summary.changePercent),
                        ),
                        style = readoutStyle,
                        color = if (summary.changePercent < -10) DemoColors.Danger else DemoColors.TierA,
                    )
                } else {
                    SmallNote(stringResource(R.string.soak_not_enough_data))
                }
                Text(stringResource(R.string.soak_max_thermal, stringResource(thermalStatusLabelRes(summary.maxThermalStatus))), style = readoutStyle, color = DemoColors.Ink)
                summary.totalBatteryDrainUah?.let {
                    Text(stringResource(R.string.soak_battery, "${if (it > 0) "−" else ""}${kotlin.math.abs(it) / 1000}"), style = readoutStyle, color = DemoColors.Ink)
                }
            }
        }
    }
}
