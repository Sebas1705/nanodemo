package es.sebas1705.nanodemo.feature.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Rutas de las 6 pantallas de la app (Navigation 3). */
sealed interface AppGraph : NavKey {

    /** `conversationId` no nulo = reabrir una conversación guardada en Historial. */
    @Serializable
    data class Chat(val conversationId: Long? = null) : AppGraph

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
