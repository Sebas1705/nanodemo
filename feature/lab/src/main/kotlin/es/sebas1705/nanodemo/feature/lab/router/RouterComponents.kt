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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import es.sebas1705.nanodemo.feature.lab.R
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

@Composable
private fun TierAvailability.text(): String = stringResource(when (this) {
    TierAvailability.AVAILABLE -> R.string.router_availability_available
    TierAvailability.DOWNLOADABLE -> R.string.router_availability_downloadable
    TierAvailability.UNAVAILABLE -> R.string.router_availability_unavailable
    TierAvailability.CHECKING -> R.string.router_availability_checking
    TierAvailability.UNKNOWN -> R.string.router_availability_unknown
})

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
                usable.isEmpty() -> stringResource(R.string.router_engines_none)
                else -> stringResource(R.string.router_engines_usable, local, cloud)
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
                Text(stringResource(if (tier.isCloud) R.string.router_engine_cloud else R.string.router_engine_local), style = readoutStyle, color = DemoColors.Muted)
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
        SectionLabel(stringResource(R.string.router_policy_section))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RoutingPolicy.entries) { policy ->
                FilterChip(
                    selected = form.policy == policy,
                    onClick = { onChange { it.copy(policy = policy) } },
                    label = { Text(stringResource(policy.labelRes)) },
                )
            }
        }
        Text(stringResource(form.policy.descriptionRes), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
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
            stringResource(R.string.router_option_gateway_title),
            stringResource(R.string.router_option_gateway_hint),
            form.gatewayEnabled,
        ) { on -> onChange { it.copy(gatewayEnabled = on) } }
        ToggleRow(
            stringResource(R.string.router_option_sensitive_title),
            stringResource(R.string.router_option_sensitive_hint),
            form.markedSensitive,
        ) { on -> onChange { it.copy(markedSensitive = on) } }
        ToggleRow(
            stringResource(R.string.router_option_json_title),
            stringResource(R.string.router_option_json_hint),
            form.expectJson,
        ) { on -> onChange { it.copy(expectJson = on) } }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.router_quality_section))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ConfidenceMode.entries) { mode ->
                    FilterChip(
                        selected = form.confidenceMode == mode,
                        onClick = { onChange { it.copy(confidenceMode = mode) } },
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }
            if (form.confidenceMode == ConfidenceMode.JUDGE) {
                Text(stringResource(R.string.router_judge_engine), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(form.judgeTier == null, { onChange { it.copy(judgeTier = null) } }, { Text(stringResource(R.string.router_judge_auto)) }) }
                    items(usable) { tier ->
                        FilterChip(form.judgeTier == tier, { onChange { it.copy(judgeTier = tier) } }, { Text(tier.label()) })
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.router_classifier_section))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(form.classifierTier == null, { onChange { it.copy(classifierTier = null) } }, { Text(stringResource(R.string.router_classifier_heuristic)) }) }
                items(usable.filter { !it.isCloud }) { tier ->
                    FilterChip(form.classifierTier == tier, { onChange { it.copy(classifierTier = tier) } }, { Text(stringResource(R.string.router_classifier_llm, tier.label())) })
                }
            }
            Text(
                stringResource(R.string.router_classifier_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.router_escalations_title), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Text(stringResource(R.string.router_escalations_hint), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
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
    val texts = rememberRouterTexts()
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.router_reading_title), style = MaterialTheme.typography.titleSmall, color = DemoColors.Muted)
        Text(c.summary(texts), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
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
            val route = if (plan.blocked) {
                stringResource(R.string.router_route_blocked, plan.blockedReason.orEmpty())
            } else {
                plan.engines.joinToString(" → ") { "[${it.tier.channelLetter()}] ${it.tier.label()}" }
            }
            Text(stringResource(R.string.router_route_line, route), style = readoutStyle, color = DemoColors.Ink)
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
                listOfNotNull(
                    tier?.label() ?: "",
                    stringResource(if (outcome.answeredLocally) R.string.router_answered_local else R.string.router_answered_cloud),
                    if (outcome.escalations > 0) pluralStringResource(R.plurals.router_escalations_count, outcome.escalations, outcome.escalations) else null,
                ).joinToString(" · "),
                style = readoutStyle,
                color = DemoColors.Muted,
            )
        }
        MarkdownText(outcome.answer)
        Text(
            stringResource(R.string.router_answer_stats, formatDuration(outcome.latencyMs), formatCost(outcome.cloudCostUsd)),
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
        )
        if (!outcome.accepted) {
            Text(
                stringResource(R.string.router_answer_not_accepted),
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
    val summary = when {
        exposures.isEmpty() -> stringResource(R.string.router_exposure_nothing)
        outcome.piiLeakedToCloud > 0 -> pluralStringResource(R.plurals.router_exposure_unfiltered, outcome.piiLeakedToCloud, outcome.piiLeakedToCloud)
        else -> pluralStringResource(R.plurals.router_exposure_sends, exposures.size, exposures.size) + ", " +
            pluralStringResource(R.plurals.router_exposure_masked, replaced, replaced)
    }
    val title = stringResource(R.string.router_exposure_title, summary)
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
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(if (open) R.string.router_collapse else R.string.router_expand), tint = DemoColors.Muted)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (exposures.isEmpty()) {
                    Text(
                        stringResource(R.string.router_exposure_empty),
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
            Text("[${e.tier.channelLetter()}] ${e.tier.label()} · ${stringResource(e.purpose.labelRes)}", style = readoutStyle, color = e.tier.signalColor)
            LabeledText(stringResource(R.string.router_exposure_before), highlightPii(e.original, e.redaction?.spans ?: PiiDetector.detect(e.original), DemoColors.Danger))
            LabeledText(
                stringResource(if (e.gatewayApplied) R.string.router_exposure_sent_filtered else R.string.router_exposure_sent_raw),
                highlightPlaceholders(e.sent, e.tier.signalColor),
            )
            val replacements = e.redaction?.replacements.orEmpty()
            if (replacements.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.router_exposure_replaced), style = readoutStyle, color = DemoColors.Muted)
                    replacements.forEach {
                        Text("${it.placeholder} ← ${it.original}  (${stringResource(it.kind.labelRes)}, ${it.occurrences}×)", style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (e.leaked.isNotEmpty()) {
                Text(
                    stringResource(R.string.router_exposure_leaked, e.leaked.map { stringResource(it.kind.labelRes) }.joinToString()),
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
        Text(stringResource(R.string.router_compare_title), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
        Text(
            stringResource(R.string.router_compare_intro),
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onRun(true) }, enabled = !disabled && !state.running) { Text(stringResource(R.string.router_compare_quick, RouterBenchSet.quick.size)) }
            OutlinedButton(onClick = { onRun(false) }, enabled = !disabled && !state.running) { Text(stringResource(R.string.router_compare_full, RouterBenchSet.prompts.size)) }
            if (state.running) TextButton(onClick = onCancel) { Text(stringResource(R.string.router_cancel)) }
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
            Text(pluralStringResource(R.plurals.router_compare_prompts, state.promptCount, state.promptCount), style = readoutStyle, color = DemoColors.Muted)
            if (sizeClass == ScreenSizeClass.COMPACT) {
                state.summaries.forEach { SummaryCard(it) }
            } else {
                SummaryTable(state.summaries)
            }
            Text(
                stringResource(R.string.router_compare_legend),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
    }
}

@Composable
private fun SummaryCard(s: PolicySummary) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(s.policy.labelRes), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(stringResource(R.string.router_metric_local), formatPercent(s.localShare), Modifier.weight(1f))
            Metric(stringResource(R.string.router_metric_latency), formatLatency(s.avgLatencyMs), Modifier.weight(1f))
            Metric(stringResource(R.string.router_metric_escalations), String.format(Locale.US, "%.1f", s.avgEscalations), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(stringResource(R.string.router_metric_cloud_cost), formatCost(s.cloudCostUsd), Modifier.weight(1f))
            Metric(stringResource(R.string.router_metric_pii_avoided), "${s.piiLeaksPrevented}", Modifier.weight(1f))
            Metric(stringResource(R.string.router_metric_leaks), "${s.piiLeaked}", Modifier.weight(1f), if (s.piiLeaked > 0) DemoColors.Danger else DemoColors.Ink)
        }
        val notes = buildList {
            if (s.blocked > 0) add(pluralStringResource(R.plurals.router_summary_blocked, s.blocked, s.blocked))
            if (s.failed > 0) add(stringResource(R.string.router_summary_failed, s.failed))
            if (s.answered > 0) add(stringResource(R.string.router_summary_accepted, formatPercent(s.acceptedShare)))
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

private val tableColumns = listOf(
    R.string.router_col_policy, R.string.router_col_local, R.string.router_col_latency, R.string.router_col_escalations,
    R.string.router_col_cost, R.string.router_col_pii_avoided, R.string.router_col_leaks, R.string.router_col_check,
)

@Composable
private fun SummaryTable(summaries: List<PolicySummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            tableColumns.forEachIndexed { i, nameRes ->
                Text(stringResource(nameRes), style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted, modifier = Modifier.weight(if (i == 0) 1.6f else 1f))
            }
        }
        summaries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                val cells = listOf(
                    stringResource(s.policy.labelRes), formatPercent(s.localShare), formatLatency(s.avgLatencyMs), String.format(Locale.US, "%.1f", s.avgEscalations),
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
