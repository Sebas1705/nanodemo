package es.sebas1705.nanodemo.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.feature.config.ConfigScreen
import es.sebas1705.nanodemo.feature.device.DeviceScreen

/**
 * "Ajustes": características de dispositivo + parámetros de generación por tier/modelo,
 * combinados en una sola hoja modal abierta desde el FAB de `AppNav` — antes eran 2 pestañas
 * fijas (Dispositivo, Parámetros) compitiendo por espacio con las 4 que se usan a diario
 * (Chat/Comparar/Historial/Estadísticas). `ModalBottomSheet` maneja sus propios insets de
 * sistema (ver `docs`/skill de edge-to-edge), así que no hace falta repetir ese trabajo acá.
 *
 * `DeviceScreen`/`ConfigScreen` dejaron de traer su propio `fillMaxSize()+verticalScroll()` (antes
 * eran pantallas completas) — acá se anidarían dos scrolls verticales, que Compose no maneja bien.
 * Ahora es esta hoja la que scrollea todo junto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DemoColors.Panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "AJUSTES",
                style = MaterialTheme.typography.headlineSmall,
                color = DemoColors.Ink,
            )
            SettingsSection("DISPOSITIVO") { DeviceScreen() }
            HorizontalDivider(color = DemoColors.Muted.copy(alpha = 0.2f))
            SettingsSection("PARÁMETROS") { ConfigScreen() }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
            letterSpacing = 1.5.sp,
        )
        content()
    }
}
