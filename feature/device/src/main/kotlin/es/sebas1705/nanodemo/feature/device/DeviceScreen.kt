package es.sebas1705.nanodemo.feature.device

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.hasInternetConnection
import es.sebas1705.nanodemo.core.common.totalRamGb
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
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
private val CAPABILITY_ROWS: List<Pair<Int, (TierCapabilities) -> Boolean>> = listOf(
    R.string.device_cap_temperature to { it.temperature },
    R.string.device_cap_top_k to { it.topK },
    R.string.device_cap_top_p to { it.topP },
    R.string.device_cap_max_tokens to { it.maxOutputTokens },
    R.string.device_cap_system_prompt to { it.systemPrompt },
    R.string.device_cap_multi_turn to { it.multiTurn },
    R.string.device_cap_images to { it.images },
)

@StringRes
private fun TierAvailability.displayTextRes(): Int = when (this) {
    TierAvailability.AVAILABLE -> R.string.device_availability_available
    TierAvailability.DOWNLOADABLE -> R.string.device_availability_downloadable
    TierAvailability.UNAVAILABLE -> R.string.device_availability_unavailable
    TierAvailability.CHECKING -> R.string.device_availability_checking
    TierAvailability.UNKNOWN -> R.string.device_availability_unknown
}

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(stringResource(R.string.device_total_ram), String.format(Locale.US, "%.1f GB", ramGb))
            InfoRow(stringResource(R.string.device_enough_for_mediapipe), stringResource(if (ramGb >= 4.0) R.string.device_yes else R.string.device_no))
            InfoRow(stringResource(R.string.device_connectivity), stringResource(if (hasInternet) R.string.device_online else R.string.device_offline))
        }

        SectionLabel(stringResource(R.string.device_current_availability))
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InferenceTier.entries.forEach { tier ->
                val tierAvailability = availability[tier] ?: TierAvailability.UNKNOWN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TierAvatar(tier, 28.dp)
                    Text(
                        tier.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = DemoColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(tierAvailability.displayTextRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tierAvailability == TierAvailability.AVAILABLE) tier.signalColor else DemoColors.Muted,
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.device_what_each_supports))
        CapabilitiesTable(viewModel)

        SectionLabel(stringResource(R.string.device_diagnostics))
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.device_diagnostics_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
            OutlinedButton(onClick = viewModel::sendTestEvent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.device_send_test_event))
            }
            OutlinedButton(onClick = viewModel::sendTestNonFatal, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.device_send_test_non_fatal))
            }
            OutlinedButton(onClick = { viewModel.forceTestCrash() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.device_force_test_crash))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Una tarjeta por tier (label a la izquierda, ✓/✗ a la derecha, sin competir por ancho con las otras 4),
 * en una grilla que usa 1 columna en teléfono y 2-3 en tablet/pantalla ancha (mismo
 * `AdaptiveGrid`/`tierColumns` que ya usan Comparar y Estadísticas).
 */
@Composable
private fun CapabilitiesTable(viewModel: DeviceViewModel) {
    val sizeClass = rememberScreenSizeClass()
    AdaptiveGrid(items = InferenceTier.entries, columns = sizeClass.tierColumns) { tier, cardModifier ->
        AppCard(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TierAvatar(tier, 28.dp)
                Text(tier.label(), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
            }
            val capabilities = viewModel.capabilitiesOf(tier)
            CAPABILITY_ROWS.forEach { (label, supports) ->
                val supported = supports(capabilities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
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
