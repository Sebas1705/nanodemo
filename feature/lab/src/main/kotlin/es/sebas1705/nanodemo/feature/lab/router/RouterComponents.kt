package es.sebas1705.nanodemo.feature.lab.router

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration
import java.util.Locale

// ---- formatting (pure) ---------------------------------------------------------------------------------------

internal fun formatPercent(fraction: Double): String = "${Math.round(fraction * 100)}%"

internal fun formatCost(usd: Double): String = when {
    usd <= 0.0 -> "$0"
    usd < 0.0001 -> "<$0.0001"
    else -> String.format(Locale.US, "$%.4f", usd)
}

internal fun formatLatency(ms: Double): String = if (ms <= 0.0) "—" else formatDuration(ms.toLong())

private fun TierAvailability.text(): String = when (this) {
    TierAvailability.AVAILABLE -> "listo"
    TierAvailability.DOWNLOADABLE -> "por descargar"
    TierAvailability.UNAVAILABLE -> "no disponible"
    TierAvailability.CHECKING -> "comprobando…"
    TierAvailability.UNKNOWN -> "sin comprobar"
}

// ---- engine availability -------------------------------------------------------------------------------------

/** Which engines the router can use right now, grouped as the policies see them: on the device or in the cloud. */
@Composable
internal fun EngineSummaryCard(availability: Map<InferenceTier, TierAvailability>) {
    val usable = InferenceTier.entries.filter { availability.isUsable(it) }
    val local = usable.count { !it.isCloud }
    val cloud = usable.count { it.isCloud }
    AppCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when {
                usable.isEmpty() -> "Ningún motor utilizable ahora"
                else -> "Utilizables: $local en el dispositivo · $cloud en la nube"
            },
            style = MaterialTheme.typography.titleSmall,
            color = DemoColors.Ink,
        )
        InferenceTier.entries.forEach { tier ->
            val state = availability[tier] ?: TierAvailability.UNKNOWN
            Row(
                modifier = Modifier.alpha(if (state == TierAvailability.AVAILABLE) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TierAvatar(tier, 24.dp)
                Text(tier.label(), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, modifier = Modifier.weight(1f))
                Text(if (tier.isCloud) "nube" else "local", style = readoutStyle, color = DemoColors.Muted)
                Text(state.text(), style = readoutStyle, color = if (state == TierAvailability.AVAILABLE) tier.signalColor else DemoColors.Muted)
            }
        }
    }
}

// ---- policy and options --------------------------------------------------------------------------------------

@Composable
internal fun PolicyPicker(
    form: RouterForm,
    availability: Map<InferenceTier, TierAvailability>,
    onChange: ((RouterForm) -> RouterForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Política de ruta")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RoutingPolicy.entries) { policy ->
                FilterChip(
                    selected = form.policy == policy,
                    onClick = { onChange { it.copy(policy = policy) } },
                    label = { Text(policy.label) },
                )
            }
        }
        Text(form.policy.description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        if (form.policy == RoutingPolicy.MANUAL) {
            LabTierSelector(
                selected = form.manualTier ?: InferenceTier.ML_KIT_NANO,
                availability = availability,
                onSelect = { tier -> onChange { it.copy(manualTier = tier) } },
            )
        }
    }
}

@Composable
internal fun OptionsCard(
    form: RouterForm,
    availability: Map<InferenceTier, TierAvailability>,
    onChange: ((RouterForm) -> RouterForm) -> Unit,
) {
    val usable = InferenceTier.entries.filter { availability.isUsable(it) }
    AppCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleRow(
            "Filtrar datos personales antes de la nube",
            "Correos, teléfonos, IBAN, tarjetas, DNI/NIE, IP, URL con claves y API keys se sustituyen por marcadores y se restauran aquí.",
            form.gatewayEnabled,
        ) { on -> onChange { it.copy(gatewayEnabled = on) } }
        ToggleRow(
            "Marcar el prompt como sensible",
            "Con «Privacidad primero» no saldrá del dispositivo aunque no se detecte ningún dato.",
            form.markedSensitive,
        ) { on -> onChange { it.copy(markedSensitive = on) } }
        ToggleRow(
            "Esperar JSON",
            "La nube usa modo JSON; los motores locales reciben la instrucción. Una respuesta que no sea JSON válido escala.",
            form.expectJson,
        ) { on -> onChange { it.copy(expectJson = on) } }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Control de calidad")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ConfidenceMode.entries) { mode ->
                    FilterChip(
                        selected = form.confidenceMode == mode,
                        onClick = { onChange { it.copy(confidenceMode = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
            if (form.confidenceMode == ConfidenceMode.JUDGE) {
                Text("Motor juez", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(form.judgeTier == null, { onChange { it.copy(judgeTier = null) } }, { Text("Automático") }) }
                    items(usable) { tier ->
                        FilterChip(form.judgeTier == tier, { onChange { it.copy(judgeTier = tier) } }, { Text(tier.label()) })
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Clasificador")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(form.classifierTier == null, { onChange { it.copy(classifierTier = null) } }, { Text("Heurístico") }) }
                items(usable.filter { !it.isCloud }) { tier ->
                    FilterChip(form.classifierTier == tier, { onChange { it.copy(classifierTier = tier) } }, { Text("LLM · ${tier.label()}") })
                }
            }
            Text(
                "El LLM solo corre en motores locales y su lectura nunca puede quitar la sensibilidad detectada.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Escalados máximos", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Text("Cuántas veces puede pasar al siguiente motor", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
            }
            OutlinedButton(onClick = { onChange { it.copy(maxEscalations = (it.maxEscalations - 1).coerceAtLeast(0)) } }) { Text("−") }
            Text("${form.maxEscalations}", style = readoutStyle, color = DemoColors.Ink, modifier = Modifier.width(20.dp))
            OutlinedButton(onClick = { onChange { it.copy(maxEscalations = (it.maxEscalations + 1).coerceAtMost(3)) } }) { Text("+") }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ---- classification and route -------------------------------------------------------------------------------

@Composable
internal fun RouteCard(outcome: RouterOutcome) {
    val c = outcome.classification ?: return
    AppCard(Modifier.fillMaxWidth()) {
        Text("Lectura del prompt", style = MaterialTheme.typography.titleSmall, color = DemoColors.Muted)
        Text(c.summary(), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
        LinearProgressIndicator(
            progress = { c.score / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = when (c.complexity) {
                Complexity.TRIVIAL -> DemoColors.TierA
                Complexity.MODERATE -> DemoColors.TierB
                Complexity.COMPLEX -> DemoColors.Danger
            },
            trackColor = DemoColors.Outline,
        )
        c.reasons.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted) }
        outcome.plan?.let { plan ->
            Spacer(Modifier.height(4.dp))
            Text("Ruta: ${if (plan.blocked) "bloqueada (${plan.blockedReason})" else plan.engines.joinToString(" → ") { "[${it.tier.channelLetter()}] ${it.tier.label()}" }}",
                style = readoutStyle, color = DemoColors.Ink)
            plan.notes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted) }
        }
    }
}

// ---- answer --------------------------------------------------------------------------------------------------

@Composable
internal fun AnswerCard(outcome: RouterOutcome) {
    outcome.error?.let { ErrorCard(it) }
    if (outcome.answer.isBlank()) return
    val tier = outcome.answeredBy
    AppCard(Modifier.fillMaxWidth(), accent = tier?.signalColor) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tier?.let { TierAvatar(it, 22.dp) }
            Text(
                buildString {
                    append(tier?.label() ?: "")
                    append(if (outcome.answeredLocally) " · en el dispositivo" else " · en la nube")
                    if (outcome.escalations > 0) append(" · ${outcome.escalations} escalado${if (outcome.escalations == 1) "" else "s"}")
                },
                style = readoutStyle,
                color = DemoColors.Muted,
            )
        }
        MarkdownText(outcome.answer)
        Text(
            "${formatDuration(outcome.latencyMs)} · nube estimada ${formatCost(outcome.cloudCostUsd)}",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
        )
        if (!outcome.accepted) {
            Text(
                "Ningún motor superó el control de calidad: se muestra la última respuesta útil.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Danger,
            )
        }
    }
}

// ---- what left the device ------------------------------------------------------------------------------------

private val placeholderPattern = Regex("""<[A-Z]+_\d+>""")

private fun highlightPii(text: String, spans: List<PiiMatch>, color: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    spans.sortedBy { it.start }.forEach { span ->
        if (span.start < cursor || span.end > text.length) return@forEach
        append(text.substring(cursor, span.start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text.substring(span.start, span.end)) }
        cursor = span.end
    }
    append(text.substring(cursor))
}

private fun highlightPlaceholders(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    placeholderPattern.findAll(text).forEach { m ->
        append(text.substring(cursor, m.range.first))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)) { append(m.value) }
        cursor = m.range.last + 1
    }
    append(text.substring(cursor))
}

/**
 * Collapsible panel with exactly what each cloud call received (after the gateway) next to the original text, the
 * table of what was replaced, and a warning when personal data went out in clear.
 */
@Composable
internal fun ExposurePanel(outcome: RouterOutcome) {
    var open by remember { mutableStateOf(false) }
    val exposures = outcome.exposures
    val replaced = exposures.sumOf { it.redaction?.distinctCount ?: 0 }
    val title = when {
        exposures.isEmpty() -> "Qué salió del dispositivo: nada"
        outcome.piiLeakedToCloud > 0 -> "Qué salió del dispositivo: ${outcome.piiLeakedToCloud} dato(s) SIN filtrar"
        else -> "Qué salió del dispositivo: ${exposures.size} envío(s), $replaced dato(s) enmascarado(s)"
    }
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (outcome.piiLeakedToCloud > 0) DemoColors.Danger else DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Contraer" else "Expandir", tint = DemoColors.Muted)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (exposures.isEmpty()) {
                    Text(
                        "Ninguna petición llegó a un motor en la nube: todo se resolvió en el dispositivo o la ruta no incluía la nube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DemoColors.Muted,
                    )
                }
                exposures.forEach { ExposureDetail(it) }
            }
        }
    }
}

@Composable
private fun ExposureDetail(e: Exposure) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("[${e.tier.channelLetter()}] ${e.tier.label()} · ${e.purpose}", style = readoutStyle, color = e.tier.signalColor)
            LabeledText("antes (solo en el dispositivo)", highlightPii(e.original, e.redaction?.spans ?: PiiDetector.detect(e.original), DemoColors.Danger))
            LabeledText(
                if (e.gatewayApplied) "enviado a la nube (tras el filtro)" else "enviado a la nube (SIN filtro)",
                highlightPlaceholders(e.sent, e.tier.signalColor),
            )
            val replacements = e.redaction?.replacements.orEmpty()
            if (replacements.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("sustituido", style = readoutStyle, color = DemoColors.Muted)
                    replacements.forEach {
                        Text("${it.placeholder} ← ${it.original}  (${it.kind.label}, ${it.occurrences}×)", style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (e.leaked.isNotEmpty()) {
                Text(
                    "Datos que salieron tal cual: " + e.leaked.joinToString { it.kind.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, text: AnnotatedString) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = readoutStyle, color = DemoColors.Muted)
        Text(text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
    }
}

// ---- policy comparison ---------------------------------------------------------------------------------------

@Composable
internal fun ComparePanel(
    state: CompareState,
    sizeClass: ScreenSizeClass,
    disabled: Boolean,
    onRun: (quick: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Comparar políticas", style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
        Text(
            "Lanza un conjunto de prompts (simples, código, cálculo, datos personales, datos recientes, JSON, texto largo) bajo cada política " +
                "con los motores utilizables ahora. Cada motor responde una sola vez a cada texto, así que las diferencias vienen de la ruta, no del azar. " +
                "El coste es una estimación con el precio de Gemini Flash que usa Estadísticas.",
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onRun(true) }, enabled = !disabled && !state.running) { Text("Rápida (${RouterBenchSet.quick.size})") }
            OutlinedButton(onClick = { onRun(false) }, enabled = !disabled && !state.running) { Text("Completa (${RouterBenchSet.prompts.size})") }
            if (state.running) TextButton(onClick = onCancel) { Text("Cancelar") }
        }
        if (state.running) {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
                color = DemoColors.TierA,
                trackColor = DemoColors.Outline,
            )
            Text("${state.done}/${state.total} · ${state.label}", style = readoutStyle, color = DemoColors.Muted)
        }
        state.error?.let { ErrorCard(it) }
        if (state.summaries.isNotEmpty()) {
            Text("${state.promptCount} prompts por política", style = readoutStyle, color = DemoColors.Muted)
            if (sizeClass == ScreenSizeClass.COMPACT) {
                state.summaries.forEach { SummaryCard(it) }
            } else {
                SummaryTable(state.summaries)
            }
            Text(
                "«PII evitada»: datos personales de los prompts que no llegaron en claro a la nube (se quedaron en el dispositivo o los enmascaró el filtro), " +
                    "frente a enviarlo todo sin filtrar. «Fugas»: los que sí salieron sin enmascarar.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
    }
}

@Composable
private fun SummaryCard(s: PolicySummary) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.policy.label, style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("en local", formatPercent(s.localShare), Modifier.weight(1f))
            Metric("latencia", formatLatency(s.avgLatencyMs), Modifier.weight(1f))
            Metric("escalados", String.format(Locale.US, "%.1f", s.avgEscalations), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("coste nube", formatCost(s.cloudCostUsd), Modifier.weight(1f))
            Metric("PII evitada", "${s.piiLeaksPrevented}", Modifier.weight(1f))
            Metric("fugas", "${s.piiLeaked}", Modifier.weight(1f), if (s.piiLeaked > 0) DemoColors.Danger else DemoColors.Ink)
        }
        val notes = buildList {
            if (s.blocked > 0) add("${s.blocked} bloqueado(s)")
            if (s.failed > 0) add("${s.failed} sin respuesta")
            if (s.answered > 0) add("${formatPercent(s.acceptedShare)} con control superado")
        }
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = DemoColors.Ink) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
        Text(value, style = readoutStyle, color = color)
    }
}

private val tableColumns = listOf("política", "local", "latencia", "escal.", "coste", "PII evitada", "fugas", "control")

@Composable
private fun SummaryTable(summaries: List<PolicySummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            tableColumns.forEachIndexed { i, name ->
                Text(name, style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted, modifier = Modifier.weight(if (i == 0) 1.6f else 1f))
            }
        }
        summaries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                val cells = listOf(
                    s.policy.label, formatPercent(s.localShare), formatLatency(s.avgLatencyMs), String.format(Locale.US, "%.1f", s.avgEscalations),
                    formatCost(s.cloudCostUsd), "${s.piiLeaksPrevented}", "${s.piiLeaked}", formatPercent(s.acceptedShare),
                )
                cells.forEachIndexed { i, text ->
                    Text(
                        text,
                        style = if (i == 0) MaterialTheme.typography.bodyMedium else readoutStyle,
                        color = if (i == 6 && s.piiLeaked > 0) DemoColors.Danger else DemoColors.Ink,
                        modifier = Modifier.weight(if (i == 0) 1.6f else 1f),
                    )
                }
            }
        }
    }
}
