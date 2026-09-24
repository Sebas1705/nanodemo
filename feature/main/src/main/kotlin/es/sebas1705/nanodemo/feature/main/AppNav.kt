package es.sebas1705.nanodemo.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import es.sebas1705.nanodemo.core.common.pushAndFree
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.StatusIndicator
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.rememberIsCompactHeight
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.feature.chat.ChatScreen
import es.sebas1705.nanodemo.feature.compare.CompareScreen
import es.sebas1705.nanodemo.feature.history.HistoryScreen
import es.sebas1705.nanodemo.feature.stats.StatsScreen

/**
 * Shell de navegación adaptativo: `NavigationBar` abajo en teléfono,
 * `NavigationRail` al costado en tablet/plegable/landscape ancho — mismo
 * breakpoint (`ScreenSizeClass`) que ya usaba la versión de un solo módulo,
 * reescrito sobre Navigation 3 en vez de Navigation Compose 2.x. Las dos ramas
 * comparten `ChannelTopBar` arriba (título de la pantalla + tier activo).
 */
@Composable
fun AppNav(initialSharedText: String? = null, mainViewModel: MainViewModel = hiltViewModel()) {
    val sizeClass = rememberScreenSizeClass()
    val isCompactHeight = rememberIsCompactHeight()
    val backStack = rememberNavBackStack(AppGraph.Chat(sharedText = initialSharedText))
    val currentDestination = (backStack.lastOrNull() as? AppGraph)?.toDestination()
    val activeTier by mainViewModel.activeTier.collectAsState()
    val availability by mainViewModel.availability.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    // Una vista de pantalla por cambio de pestaña. Abrir una conversación desde el historial no cambia
    // de destino (sigue siendo Chat), así que no cuenta como pantalla nueva.
    LaunchedEffect(currentDestination) {
        currentDestination?.let { mainViewModel.onScreenShown(it.name.lowercase()) }
    }

    // Un teléfono en landscape entra en medium/expanded por ancho, pero no
    // tiene alto suficiente para un rail de 6 ítems — en ese caso se queda
    // con la barra inferior aunque el ancho diga "tablet".
    if (sizeClass == ScreenSizeClass.COMPACT || isCompactHeight) {
        // En Chat el FAB de Ajustes taparía el botón de enviar del composer (queda abajo a la derecha): ahí
        // el acceso pasa a un ícono en la barra superior.
        val settingsInTopBar = currentDestination == Destination.Chat
        Scaffold(
            topBar = {
                ChannelTopBar(
                    title = currentDestination?.label ?: "",
                    activeTier = activeTier,
                    availability = availability[activeTier] ?: TierAvailability.UNKNOWN,
                    onOpenSettings = if (settingsInTopBar) ({ showSettings = true }) else null,
                )
            },
            bottomBar = {
                NavigationBar(containerColor = DemoColors.Panel) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = { backStack.pushAndFree(destination.toRoute()) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = tabColor(currentDestination, destination),
                                )
                            },
                            label = { Text(destination.label) },
                            // Con 6 pestañas, un label fijo por ítem las aprieta — igual que ya
                            // hace el NavigationRail de abajo, solo el ítem activo lo muestra.
                            alwaysShowLabel = false,
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!settingsInTopBar) {
                    FloatingActionButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            }
        ) { padding ->
            AppNavHost(
                backStack = backStack,
                sizeClass = sizeClass,
                // consumeWindowInsets: el padding ya incluye la barra de navegación; sin esto, un
                // `imePadding()` más adentro (composer del Chat) sumaría el teclado ENCIMA de ese hueco.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
            )
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = DemoColors.Panel,
                header = {
                    FloatingActionButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            ) {
                Destination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination == destination,
                        onClick = { backStack.pushAndFree(destination.toRoute()) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = tabColor(currentDestination, destination),
                            )
                        },
                        label = { Text(destination.label) },
                        alwaysShowLabel = false, // solo el seleccionado muestra label: los 6 ítems entran más ajustados
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                ChannelTopBar(
                    title = currentDestination?.label ?: "",
                    activeTier = activeTier,
                    availability = availability[activeTier] ?: TierAvailability.UNKNOWN,
                )
                AppNavHost(
                    backStack = backStack,
                    sizeClass = sizeClass,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

/**
 * Barra superior compartida entre las 6 pantallas — antes cada una dibujaba su propio
 * título (`Text(headlineSmall)`) suelto adentro de su `Column` scrolleable, y no había
 * ningún lugar que mostrara qué tier está activo fuera de Chat. Deliberadamente NO es el
 * `TopAppBar` de Material3 (con su elevación/scroll-behavior): un `Row` simple mantiene la
 * estética plana de panel de instrumentos que ya tiene el resto de la app — pero al ser
 * custom, a diferencia de un `TopAppBar` real, no reserva sola el espacio de la barra de
 * estado: `statusBarsPadding()` hace ese trabajo a mano (si no, con `targetSdk` 36 la barra
 * de estado del sistema dibuja encima del título).
 */
@Composable
private fun ChannelTopBar(
    title: String,
    activeTier: InferenceTier,
    availability: TierAvailability,
    onOpenSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "[${activeTier.channelLetter()}]",
                style = readoutStyle,
                color = activeTier.signalColor,
            )
            StatusIndicator(availability, activeTier.signalColor)
            onOpenSettings?.let {
                // 32dp (no los 48dp por defecto) para que la barra no crezca solo en Chat.
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = DemoColors.Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun tabColor(current: Destination?, destination: Destination): Color =
    if (current == destination) DemoColors.TierA else DemoColors.Muted

@Composable
private fun AppNavHost(
    backStack: androidx.navigation3.runtime.NavBackStack<androidx.navigation3.runtime.NavKey>,
    sizeClass: ScreenSizeClass,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AppGraph.Chat> { route -> ChatScreen(conversationId = route.conversationId, sharedText = route.sharedText) }
            entry<AppGraph.Compare> { CompareScreen(sizeClass) }
            entry<AppGraph.Stats> { StatsScreen(sizeClass) }
            entry<AppGraph.History> {
                HistoryScreen(
                    onOpenConversation = { conversationId ->
                        backStack.pushAndFree(AppGraph.Chat(conversationId))
                    },
                )
            }
        },
    )
}
