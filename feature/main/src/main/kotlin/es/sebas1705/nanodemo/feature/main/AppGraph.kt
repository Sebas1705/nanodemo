package es.sebas1705.nanodemo.feature.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Rutas de las 4 pestañas de la app (Navigation 3). Parámetros y Dispositivo dejaron de ser
 * pestañas propias — viven en la hoja modal "Ajustes" (`SettingsSheet.kt`), que se abre desde el
 * menú lateral y no participa del backstack (es estado local de `AppNav`, no una ruta).
 */
sealed interface AppGraph : NavKey {

    /**
     * `conversationId` no nulo = reabrir una conversación guardada en Historial.
     * `sharedText` no nulo = se abrió desde el selector de "Compartir" de otra app (ver
     * `MainActivity`); se usa una sola vez para precargar el prompt, no se persiste en la ruta.
     */
    @Serializable
    data class Chat(val conversationId: Long? = null, val sharedText: String? = null) : AppGraph

    @Serializable
    data object Compare : AppGraph

    @Serializable
    data object Stats : AppGraph

    @Serializable
    data object History : AppGraph
}
