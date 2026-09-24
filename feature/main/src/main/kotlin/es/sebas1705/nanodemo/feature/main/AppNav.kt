package es.sebas1705.nanodemo.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.rememberIsCompactHeight
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.feature.chat.ChatScreen
import es.sebas1705.nanodemo.feature.compare.CompareScreen
import es.sebas1705.nanodemo.feature.history.HistoryScreen
import es.sebas1705.nanodemo.feature.stats.StatsScreen
import kotlinx.coroutines.launch

/**
 * Shell de navegación adaptativo con menú lateral (`DrawerContent`) en vez de la barra inferior: en
 * teléfono es un drawer modal que se abre con el botón de menú o deslizando desde el borde; en
 * tablet/plegable/landscape ancho es un drawer permanente a la izquierda — mismo breakpoint
 * (`ScreenSizeClass`) de siempre, sobre Navigation 3. Las dos ramas comparten `ChannelTopBar` arriba
 * (título de la pantalla + tier activo). Ajustes ya no es un FAB: es un ítem al pie del menú.
 */
@Composable
fun AppNav(initialSharedText: String? = null, mainViewModel: MainViewModel = hiltViewModel()) {
    val sizeClass = rememberScreenSizeClass()
    val isCompactHeight = rememberIsCompactHeight()
    val backStack = rememberNavBackStack(AppGraph.Chat(sharedText = initialSharedText))
    val currentDestination = (backStack.lastOrNull() as? AppGraph)?.toDestination()
    val activeTier by mainViewModel.activeTier.collectAsState()
    val availability by mainViewModel.availability.collectAsState()
    val recents by mainViewModel.recentConversations.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Una vista de pantalla por cambio de sección. Abrir una conversación desde el historial no cambia
    // de destino (sigue siendo Chat), así que no cuenta como pantalla nueva.
    LaunchedEffect(currentDestination) {
        currentDestination?.let { mainViewModel.onScreenShown(it.name.lowercase()) }
    }

    // `afterAction` cierra el drawer modal tras elegir algo (en el permanente no hace nada).
    val drawerContent: @Composable (afterAction: () -> Unit) -> Unit = { afterAction ->
        DrawerContent(
            currentDestination = currentDestination,
            activeTier = activeTier,
            recents = recents,
            onNavigate = { destination ->
                backStack.pushAndFree(destination.toRoute())
                afterAction()
            },
            onNewConversation = {
                mainViewModel.startNewConversation()
                backStack.pushAndFree(AppGraph.Chat())
                afterAction()
            },
            onOpenConversation = { conversationId ->
                backStack.pushAndFree(AppGraph.Chat(conversationId))
                afterAction()
            },
            onOpenSettings = {
                showSettings = true
                afterAction()
            },
        )
    }

    val topBarTier = availability[activeTier] ?: TierAvailability.UNKNOWN

    // Un teléfono en landscape entra en medium/expanded por ancho, pero no tiene alto para un drawer
    // permanente cómodo — en ese caso se queda con el modal aunque el ancho diga "tablet".
    if (sizeClass == ScreenSizeClass.COMPACT || isCompactHeight) {
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = DemoColors.Panel) {
                    drawerContent { scope.launch { drawerState.close() } }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    ChannelTopBar(
                        title = currentDestination?.label ?: "",
                        activeTier = activeTier,
                        availability = topBarTier,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                    )
                },
            ) { padding ->
                AppNavHost(
                    backStack = backStack,
                    sizeClass = sizeClass,
                    // consumeWindowInsets: el padding ya incluye la barra de navegación del sistema; sin esto,
                    // un `imePadding()` más adentro (composer del Chat) sumaría el teclado ENCIMA de ese hueco.
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
                )
            }
        }
    } else {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = DemoColors.Panel) {
                    drawerContent {}
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                ChannelTopBar(
                    title = currentDestination?.label ?: "",
                    activeTier = activeTier,
                    availability = topBarTier,
                    onOpenMenu = null,
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
 * Barra superior compartida entre las pantallas: botón de menú (solo con drawer modal), título y el
 * tier activo. Deliberadamente NO es el `TopAppBar` de Material3 (con su elevación/scroll-behavior):
 * un `Row` simple mantiene la estética plana del resto de la app — pero al ser custom, a diferencia
 * de un `TopAppBar` real, no reserva sola el espacio de la barra de estado: `statusBarsPadding()` hace
 * ese trabajo a mano (si no, con `targetSdk` 36 la barra de estado del sistema dibuja encima del título).
 */
@Composable
private fun ChannelTopBar(
    title: String,
    activeTier: InferenceTier,
    availability: TierAvailability,
    onOpenMenu: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onOpenMenu != null) 4.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onOpenMenu?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menú", tint = DemoColors.Ink)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = DemoColors.Ink,
                modifier = Modifier.padding(vertical = if (onOpenMenu == null) 12.dp else 0.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TierAvatar(activeTier, 24.dp)
            StatusIndicator(availability, activeTier.signalColor)
        }
    }
}

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
