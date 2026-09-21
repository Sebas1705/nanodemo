package es.sebas1705.nanodemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de entrada de Hilt para toda la app (necesario para que `@AndroidEntryPoint`/`@HiltViewModel` funcionen). */
@HiltAndroidApp
class NanoDemoApplication : Application()
