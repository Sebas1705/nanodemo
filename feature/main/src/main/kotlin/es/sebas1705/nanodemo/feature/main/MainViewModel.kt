package es.sebas1705.nanodemo.feature.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.core.common.Telemetry
import javax.inject.Inject

/** Único cometido: registrar qué pantalla ve el usuario (evento `screen_view` + clave `screen` en Crashlytics). */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val telemetry: Telemetry,
) : ViewModel() {

    fun onScreenShown(screenName: String) = telemetry.screen(screenName)
}
