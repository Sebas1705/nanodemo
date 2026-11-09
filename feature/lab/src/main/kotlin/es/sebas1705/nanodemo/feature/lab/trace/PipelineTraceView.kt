package es.sebas1705.nanodemo.feature.lab.trace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.domain.models.TraceStep

/** Short Spanish label of what a step does. */
fun StepKind.label(): String = when (this) {
    StepKind.ROUTE -> "ruta"
    StepKind.REDACT -> "filtro"
    StepKind.RETRIEVE -> "búsqueda"
    StepKind.GENERATE -> "modelo"
    StepKind.TOOL -> "herramienta"
    StepKind.JUDGE -> "juez"
    StepKind.PLAN -> "plan"
    StepKind.CRITIQUE -> "crítica"
    StepKind.SUMMARIZE -> "resumen"
    StepKind.VALIDATE -> "validación"
}

/** "420 ms" under a second, "1.9 s" above. */
fun formatDuration(ms: Long): String = if (ms < 1_000) "$ms ms" else "%.1f s".format(ms / 1000.0)

private fun TraceStep.nodeColor(): Color = when {
    status == StepStatus.FAILED -> DemoColors.Danger
    status == StepStatus.SKIPPED -> DemoColors.Muted
    tier != null -> tier!!.signalColor
    else -> DemoColors.Ink
}

/**
 * Live timeline of a pipeline run: one row per step with the engine that ran it, its duration and tokens;
 * tapping a row expands its input, output and detail. This is the shared visual of every Lab experiment.
 */
@Composable
fun PipelineTraceView(trace: PipelineTrace, modifier: Modifier = Modifier) {
    if (trace.steps.isEmpty()) return
    AppCard(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        TraceSummary(trace)
        Box(Modifier.height(10.dp))
        trace.steps.forEachIndexed { index, step ->
            TraceStepRow(step, isLast = index == trace.steps.lastIndex)
        }
    }
}

@Composable
private fun TraceSummary(trace: PipelineTrace) {
    val parts = buildList {
        add("${trace.steps.size} pasos")
        add(formatDuration(trace.totalDurationMs))
        if (trace.totalTokens > 0) add("~${trace.totalTokens} tok")
        if (trace.tiersUsed.isNotEmpty()) add(trace.tiersUsed.joinToString(" ") { "[${it.channelLetter()}]" })
        if (trace.running) add("en curso…")
    }
    Text(parts.joinToString(" · "), style = readoutStyle, color = DemoColors.Muted)
}

@Composable
private fun TraceStepRow(step: TraceStep, isLast: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val color = step.nodeColor()
    val hasBody = step.input != null || step.output != null || step.detail != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (hasBody) Modifier.clickable { expanded = !expanded } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Rail: node + connector down to the next step.
        Column(modifier = Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(if (step.status == StepStatus.RUNNING) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (step.status == StepStatus.SKIPPED) DemoColors.Muted.copy(alpha = 0.5f) else color),
            )
            if (!isLast) {
                Box(Modifier.width(2.dp).weight(1f).background(DemoColors.Outline))
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.kind.label(), style = readoutStyle, color = color)
                Text(
                    step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DemoColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        step.status == StepStatus.SKIPPED -> "omitido"
                        step.durationMs != null -> formatDuration(step.durationMs!!)
                        else -> "…"
                    },
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
            }
            val meta = buildList {
                step.tier?.let { add("[${it.channelLetter()}]") }
                step.tokens?.let { add("~$it tok") }
                if (step.status == StepStatus.FAILED) add("falló")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = readoutStyle, color = DemoColors.Muted)
            }
            if (expanded) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        step.input?.let { BodyLine("entrada", it) }
                        step.output?.let { BodyLine("salida", it) }
                        step.detail?.let { BodyLine("detalle", it) }
                    }
                }
            } else if (hasBody) {
                Text(
                    (step.output ?: step.detail ?: step.input).orEmpty().replace('\n', ' ').take(90),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BodyLine(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = readoutStyle, color = DemoColors.Muted)
        Text(text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
    }
}
