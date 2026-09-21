package es.sebas1705.nanodemo.feature.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import es.sebas1705.nanodemo.core.common.pushAndFree
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.rememberIsCompactHeight
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.feature.chat.ChatScreen
import es.sebas1705.nanodemo.feature.compare.CompareScreen
import es.sebas1705.nanodemo.feature.config.ConfigScreen
import es.sebas1705.nanodemo.feature.device.DeviceScreen
import es.sebas1705.nanodemo.feature.history.HistoryScreen
import es.sebas1705.nanodemo.feature.stats.StatsScreen

/**
 * Shell de navegación adaptativo: `NavigationBar` abajo en teléfono,
 * `NavigationRail` al costado en tablet/plegable/landscape ancho — mismo
 * breakpoint (`ScreenSizeClass`) que ya usaba la versión de un solo módulo,
 * reescrito sobre Navigation 3 en vez de Navigation Compose 2.x.
 */
@Composable
fun AppNav() {
    val sizeClass = rememberScreenSizeClass()
    val isCompactHeight = rememberIsCompactHeight()
    val backStack = rememberNavBackStack(AppGraph.Chat())
    val currentDestination = (backStack.lastOrNull() as? AppGraph)?.toDestination()

    // Un teléfono en landscape entra en medium/expanded por ancho, pero no
    // tiene alto suficiente para un rail de 6 ítems — en ese caso se queda
    // con la barra inferior aunque el ancho diga "tablet".
    if (sizeClass == ScreenSizeClass.COMPACT || isCompactHeight) {
        Scaffold(
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
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        ) { padding ->
            AppNavHost(
                backStack = backStack,
                sizeClass = sizeClass,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = DemoColors.Panel) {
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
            AppNavHost(
                backStack = backStack,
                sizeClass = sizeClass,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
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
            entry<AppGraph.Chat> { route -> ChatScreen(conversationId = route.conversationId) }
            entry<AppGraph.Compare> { CompareScreen(sizeClass) }
            entry<AppGraph.Config> { ConfigScreen() }
            entry<AppGraph.Device> { DeviceScreen() }
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
