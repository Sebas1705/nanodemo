package es.sebas1705.nanodemo.feature.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Las 4 pestañas del nav bar/rail — Parámetros y Dispositivo se movieron a la hoja "Ajustes"
 * (`SettingsSheet.kt`, abierta con el FAB), así que ya no compiten por espacio con las 4 que se
 * usan a diario. `label` es también el título que muestra la barra superior compartida
 * (`ChannelTopBar` en `AppNav.kt`) para esa pantalla, en mayúscula — una sola fuente para los dos.
 */
enum class Destination(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.Filled.Chat),
    Compare("Comparar", Icons.Filled.Science),
    History("Historial", Icons.Filled.History),
    Stats("Estadísticas", Icons.Filled.BarChart),
}

fun Destination.toRoute(): AppGraph = when (this) {
    Destination.Chat -> AppGraph.Chat()
    Destination.Compare -> AppGraph.Compare
    Destination.History -> AppGraph.History
    Destination.Stats -> AppGraph.Stats
}

fun AppGraph.toDestination(): Destination = when (this) {
    is AppGraph.Chat -> Destination.Chat
    AppGraph.Compare -> Destination.Compare
    AppGraph.History -> Destination.History
    AppGraph.Stats -> Destination.Stats
}
