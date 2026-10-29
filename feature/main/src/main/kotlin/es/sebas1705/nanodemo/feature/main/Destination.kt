package es.sebas1705.nanodemo.feature.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Las 4 secciones del menú lateral — Parámetros y Dispositivo se movieron a la hoja "Ajustes"
 * (`SettingsSheet.kt`, ítem al pie del menú), así que ya no compiten por espacio con las 4 que se
 * usan a diario. `label` es también el título que muestra la barra superior compartida
 * (`ChannelTopBar` en `AppNav.kt`) para esa pantalla — una sola fuente para los dos.
 */
enum class Destination(@StringRes val labelRes: Int, val icon: ImageVector) {
    Chat(R.string.destination_chat, Icons.Filled.Chat),
    Compare(R.string.destination_compare, Icons.Filled.Science),
    History(R.string.destination_history, Icons.Filled.History),
    Stats(R.string.destination_stats, Icons.Filled.BarChart),
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
