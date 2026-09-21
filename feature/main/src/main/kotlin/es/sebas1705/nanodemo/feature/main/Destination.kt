package es.sebas1705.nanodemo.feature.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** Las 6 pestañas del nav bar/rail. `icon` es lo único que necesita la barra — el resto lo resuelve `AppGraph`. */
enum class Destination(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.Filled.Chat),
    Compare("Tests", Icons.Filled.Science),
    Config("Params", Icons.Filled.Tune),
    Device("Devices", Icons.Filled.Devices),
    History("Historial", Icons.Filled.History),
    Stats("Stats", Icons.Filled.BarChart),
}

fun Destination.toRoute(): AppGraph = when (this) {
    Destination.Chat -> AppGraph.Chat()
    Destination.Compare -> AppGraph.Compare
    Destination.Config -> AppGraph.Config
    Destination.Device -> AppGraph.Device
    Destination.History -> AppGraph.History
    Destination.Stats -> AppGraph.Stats
}

fun AppGraph.toDestination(): Destination = when (this) {
    is AppGraph.Chat -> Destination.Chat
    AppGraph.Compare -> Destination.Compare
    AppGraph.Config -> Destination.Config
    AppGraph.Device -> Destination.Device
    AppGraph.History -> Destination.History
    AppGraph.Stats -> Destination.Stats
}
