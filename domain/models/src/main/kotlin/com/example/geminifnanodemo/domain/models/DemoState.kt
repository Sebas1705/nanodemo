package com.example.geminifnanodemo.domain.models

/**
 * Estados posibles de la demo para el tier activo. Modela tanto el ciclo de
 * vida del modelo (comprobar disponibilidad -> descargar -> listo) como el de
 * una generación de texto concreta.
 */
sealed interface DemoState {
    data object CheckingAvailability : DemoState
    data object Unavailable : DemoState
    data object Downloadable : DemoState
    data class Downloading(val progressPercent: Int? = null) : DemoState
    data object Ready : DemoState
    data class Generating(val partialText: String) : DemoState
    data class Result(val text: String) : DemoState
    data class Error(val message: String) : DemoState
}
