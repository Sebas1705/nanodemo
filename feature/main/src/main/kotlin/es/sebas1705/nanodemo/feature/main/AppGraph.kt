package es.sebas1705.nanodemo.feature.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Rutas de las 6 pantallas de la app (Navigation 3). */
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
    data object Config : AppGraph

    @Serializable
    data object Device : AppGraph

    @Serializable
    data object Stats : AppGraph

    @Serializable
    data object History : AppGraph
}
