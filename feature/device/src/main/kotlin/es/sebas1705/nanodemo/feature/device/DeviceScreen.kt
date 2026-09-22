package es.sebas1705.nanodemo.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.hasInternetConnection
import es.sebas1705.nanodemo.core.common.totalRamGb
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import java.util.Locale

/** Lo que sabemos de este dispositivo en concreto, para interpretar los resultados con contexto. */
private val CAPABILITY_ROWS: List<Pair<String, (TierCapabilities) -> Boolean>> = listOf(
    "Temperature" to { it.temperature },
    "Top-K" to { it.topK },
    "Top-P" to { it.topP },
    "Max output tokens" to { it.maxOutputTokens },
    "System prompt" to { it.systemPrompt },
    "Multi-turno" to { it.multiTurn },
    "Imágenes" to { it.images },
)

/**
 * Ya no es una pantalla propia con su propio scroll — vive dentro de la hoja "Ajustes"
 * (`feature:main/SettingsSheet.kt`), que aporta el scroll y el padding compartidos con
 * `ConfigScreen`.
 */
@Composable
fun DeviceScreen(viewModel: DeviceViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val availability by viewModel.availability.collectAsState()
    val ramGb = remember { totalRamGb(context) }
    val hasInternet = remember { hasInternetConnection(context) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DemoColors.Panel, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoRow("RAM total", String.format(Locale.US, "%.1f GB", ramGb))
            InfoRow("¿Alcanza para MediaPipe (~4GB+)?", if (ramGb >= 4.0) "Sí" else "No")
            InfoRow("Conectividad", if (hasInternet) "Con internet" else "Sin conexión")
        }

        Text(
            "DISPONIBILIDAD ACTUAL",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
            letterSpacing = 1.5.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DemoColors.Panel, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InferenceTier.entries.forEach { tier ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "[${tier.channelLetter()}] ${tier.label()}",
                        style = readoutStyle,
                        color = tier.signalColor,
                    )
                    Text(
                        (availability[tier] ?: TierAvailability.UNKNOWN).toString(),
                        style = readoutStyle,
                        color = DemoColors.Muted,
                    )
                }
            }
        }

        Text(
            "QUÉ SOPORTA CADA TIER",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
            letterSpacing = 1.5.sp,
        )
        CapabilitiesTable(viewModel)

        Text(
            "DIAGNÓSTICO (CRASHLYTICS Y ANALYTICS)",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
            letterSpacing = 1.5.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DemoColors.Panel, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Para comprobar que los eventos y errores llegan a la consola de Firebase. El crash " +
                    "real se envía la próxima vez que abras la app.",
                style = readoutStyle,
                color = DemoColors.Muted,
            )
            OutlinedButton(onClick = viewModel::sendTestEvent, modifier = Modifier.fillMaxWidth()) {
                Text("Enviar evento de analytics de prueba")
            }
            OutlinedButton(onClick = viewModel::sendTestNonFatal, modifier = Modifier.fillMaxWidth()) {
                Text("Enviar error no fatal de prueba")
            }
            OutlinedButton(onClick = { viewModel.forceTestCrash() }, modifier = Modifier.fillMaxWidth()) {
                Text("Forzar crash de prueba")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = readoutStyle, color = DemoColors.Muted)
        Text(value, style = readoutStyle, color = DemoColors.Ink)
    }
}

/**
 * Antes era una sola tabla de 6 columnas (label + 5 tiers) — apretadísima en un ancho de
 * teléfono, sobre todo dentro de la hoja "Ajustes" (ya no una pantalla completa). Ahora es
 * una tarjeta por tier (label a la izquierda, ✓/✗ a la derecha, sin competir por ancho con
 * las otras 4), en una grilla que usa 1 columna en teléfono y 2-3 en tablet/pantalla ancha
 * (mismo `AdaptiveGrid`/`tierColumns` que ya usan Comparar y Estadísticas).
 */
@Composable
private fun CapabilitiesTable(viewModel: DeviceViewModel) {
    val sizeClass = rememberScreenSizeClass()
    AdaptiveGrid(items = InferenceTier.entries, columns = sizeClass.tierColumns) { tier, cardModifier ->
        Column(
            modifier = cardModifier
                .background(DemoColors.Panel, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "[${tier.channelLetter()}] ${tier.label()}",
                style = readoutStyle,
                color = tier.signalColor,
                fontWeight = FontWeight.Bold,
            )
            val capabilities = viewModel.capabilitiesOf(tier)
            CAPABILITY_ROWS.forEach { (label, supports) ->
                val supported = supports(capabilities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = readoutStyle, color = DemoColors.Ink)
                    Text(
                        if (supported) "✓" else "✗",
                        style = readoutStyle,
                        color = if (supported) tier.signalColor else DemoColors.Muted,
                    )
                }
            }
        }
    }
}
