package es.sebas1705.nanodemo.feature.lab.refine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration
import java.util.Locale

/**
 * Layout shared by the three experiments: on a phone one scrolling column (inputs, then results); on wider
 * windows two panes side by side, each scrolling on its own, so the controls stay in reach while the results
 * grow. Both leave room for the keyboard.
 */
@Composable
fun LabPane(
    controls: @Composable ColumnScope.() -> Unit,
    results: @Composable ColumnScope.() -> Unit,
) {
    if (rememberScreenSizeClass() == ScreenSizeClass.COMPACT) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            controls()
            results()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = controls,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = results,
            )
        }
    }
}

/** Run / cancel buttons. */
@Composable
fun RunBar(running: Boolean, canRun: Boolean, runLabel: String, onRun: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onRun, enabled = canRun && !running) { Text(if (running) "Ejecutando…" else runLabel) }
        if (running) OutlinedButton(onClick = onCancel) { Text("Cancelar") }
    }
}

/** Progress bar with what the run is doing right now; nothing when idle. */
@Composable
fun ProgressBlock(progress: LabProgress?) {
    if (progress == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = DemoColors.TierA,
            trackColor = DemoColors.Outline,
        )
        Text(
            "${progress.done} de ${progress.total} · ${progress.label}",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
        )
    }
}

/** A labelled engine picker for one role. */
@Composable
fun EngineRow(
    label: String,
    selected: InferenceTier,
    availability: Map<InferenceTier, TierAvailability>,
    onSelect: (InferenceTier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
        LabTierSelector(selected, availability, onSelect)
    }
}

/** Pick several engines at once; engines that are not usable right now are dimmed and cannot be toggled. */
@Composable
fun EngineMultiSelect(
    selected: Set<InferenceTier>,
    availability: Map<InferenceTier, TierAvailability>,
    onToggle: (InferenceTier) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InferenceTier.entries) { tier ->
            val usable = availability.isUsable(tier)
            TierChip(
                tier = tier,
                selected = tier in selected,
                onClick = { if (usable) onToggle(tier) },
                modifier = Modifier.alpha(if (usable) 1f else 0.4f),
            )
        }
    }
}

/** A single-choice row of chips. */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** A multi-choice row of chips. */
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(label(option)) })
        }
    }
}

/** The trace of a run; long runs show only the latest steps so the screen stays light. */
@Composable
fun TailTrace(trace: PipelineTrace, maxSteps: Int = 12) {
    if (trace.steps.size <= maxSteps) {
        PipelineTraceView(trace)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Últimos $maxSteps de ${trace.steps.size} pasos",
                style = MaterialTheme.typography.labelSmall,
                color = DemoColors.Muted,
            )
            PipelineTraceView(PipelineTrace(trace.steps.takeLast(maxSteps)))
        }
    }
}

/** A small card-less table: a muted header and rows, the first column wider. Cells are plain strings. */
@Composable
fun StatTable(header: List<String>, rows: List<List<String>>, highlight: Set<Int> = emptySet(), firstWeight: Float = 1.6f) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TableLine(header, muted = true, firstWeight = firstWeight)
        rows.forEachIndexed { index, row -> TableLine(row, highlighted = index in highlight, firstWeight = firstWeight) }
    }
}

@Composable
private fun TableLine(cells: List<String>, muted: Boolean = false, highlighted: Boolean = false, firstWeight: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                style = if (index == 0 && !muted) MaterialTheme.typography.bodyMedium else readoutStyle,
                color = when {
                    muted -> DemoColors.Muted
                    highlighted -> DemoColors.TierA
                    index == 0 -> DemoColors.Ink
                    else -> DemoColors.Ink
                },
                fontWeight = if (highlighted) FontWeight.SemiBold else null,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (index == 0) firstWeight else 1f),
            )
        }
    }
}

/** A horizontal bar filled to `fraction`, for votes and leaderboards. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(8.dp).clip(shape).background(DemoColors.Outline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(color))
    }
}

/** A titled card, the container of every result block. */
@Composable
fun ResultCard(title: String, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth(), accent = accent) {
        SectionLabel(title)
        content()
    }
}

fun percent(ratio: Double?): String = ratio?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "—"

fun CallStats.display(): String = "$calls · ${formatDuration(latencyMs)} · ~$tokens tok"
