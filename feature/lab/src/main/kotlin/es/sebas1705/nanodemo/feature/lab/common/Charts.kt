package es.sebas1705.nanodemo.feature.lab.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.feature.lab.R

/** Red at 0, amber at 0.5, green at 1: the colour of a pass rate. Out-of-range values are clamped. */
fun passRateColor(rate: Double): Color {
    val r = rate.coerceIn(0.0, 1.0).toFloat()
    return if (r < 0.5f) lerp(DemoColors.Danger, DemoColors.TierB, r / 0.5f) else lerp(DemoColors.TierB, DemoColors.TierA, (r - 0.5f) / 0.5f)
}

/** One coloured cell of a heatmap; `value` null = no data (drawn empty). */
@Composable
fun HeatCellBox(value: Double?, label: String, width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(width)
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(value?.let { passRateColor(it).copy(alpha = 0.28f) } ?: DemoColors.Outline),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = readoutStyle, color = if (value != null) DemoColors.Ink else DemoColors.Muted)
    }
}

/** Horizontal bar with a label on the left and a value on the right; `fraction` is 0..1 of the full width. */
@Composable
fun BarRow(label: String, fraction: Float, valueText: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = readoutStyle, color = DemoColors.Muted, modifier = Modifier.width(52.dp))
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(DemoColors.Outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }
        Text(valueText, style = readoutStyle, color = DemoColors.Ink, modifier = Modifier.width(64.dp))
    }
}

/** A named polyline for [LineChart]. */
class ChartSeries(val label: String, val color: Color, val points: List<Pair<Float, Float>>)

/**
 * Minimal line chart: all series share the axes, y starts at 0, x spans the data. Axis labels show the
 * extreme values only. Built on Canvas, no chart library.
 */
@Composable
fun LineChart(series: List<ChartSeries>, xLabel: String, yLabel: String, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val all = series.flatMap { it.points }
    if (all.size < 2) {
        Text(stringResource(R.string.lab_common_no_data), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted, modifier = modifier)
        return
    }
    val minX = all.minOf { it.first }
    val maxX = all.maxOf { it.first }.let { if (it == minX) minX + 1f else it }
    val maxY = all.maxOf { it.second }.let { if (it <= 0f) 1f else it }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(s.color))
                    Text(s.label, style = readoutStyle, color = DemoColors.Muted)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            drawLine(DemoColors.Outline, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
            drawLine(DemoColors.Outline, Offset(0f, 0f), Offset(0f, h), strokeWidth = 2f)
            series.forEach { s ->
                val mapped = s.points.sortedBy { it.first }.map { (x, y) ->
                    Offset((x - minX) / (maxX - minX) * w, h - (y / maxY) * h * 0.95f)
                }
                mapped.zipWithNext().forEach { (a, b) -> drawLine(s.color, a, b, strokeWidth = 5f, cap = StrokeCap.Round) }
                mapped.forEach { drawCircle(s.color, radius = 5f, center = it) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${formatAxis(minX)} $xLabel", style = readoutStyle, color = DemoColors.Muted)
            Text(stringResource(R.string.lab_common_chart_max, yLabel, formatAxis(maxY)), style = readoutStyle, color = DemoColors.Muted)
            Text("${formatAxis(maxX)} $xLabel", style = readoutStyle, color = DemoColors.Muted)
        }
    }
}

internal fun formatAxis(value: Float): String =
    if (value >= 100f || value == value.toInt().toFloat()) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
fun SmallNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted, modifier = modifier.padding(vertical = 2.dp))
}
