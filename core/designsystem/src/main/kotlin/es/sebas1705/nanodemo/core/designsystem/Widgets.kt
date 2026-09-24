package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability

/**
 * Piezas de UI compartidas entre las pantallas — selector de tier, grilla adaptativa.
 */

/**
 * Grilla simple de `columns` columnas: filas de celdas del mismo ancho (`weight(1f)`);
 * las celdas que sobran en la última fila se rellenan con espacio vacío para que
 * no se estiren. No es lazy — se usa dentro de pantallas que ya scrollean.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowItems.forEach { item -> itemContent(item, Modifier.weight(1f)) }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun TierChannelRow(
    tier: InferenceTier,
    isActive: Boolean,
    availability: TierAvailability,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) tier.signalColor.copy(alpha = 0.14f) else DemoColors.Panel)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(tier.signalColor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[${tier.channelLetter()}]",
                style = readoutStyle,
                color = tier.signalColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tier.label(),
                style = MaterialTheme.typography.bodyLarge,
                color = DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            StatusIndicator(availability, tier.signalColor)
        }
    }
}

@Composable
fun StatusIndicator(availability: TierAvailability, color: Color) {
    when (availability) {
        TierAvailability.CHECKING ->
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
        TierAvailability.AVAILABLE -> Text("●", style = readoutStyle, color = color)
        TierAvailability.DOWNLOADABLE -> Text("◐", style = readoutStyle, color = color)
        TierAvailability.UNAVAILABLE -> Text("○", style = readoutStyle, color = DemoColors.Muted)
        TierAvailability.UNKNOWN -> Text("·", style = readoutStyle, color = DemoColors.Muted)
    }
}

