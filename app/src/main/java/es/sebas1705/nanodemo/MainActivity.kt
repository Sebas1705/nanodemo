package es.sebas1705.nanodemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import es.sebas1705.nanodemo.core.ui.DemoTheme
import es.sebas1705.nanodemo.feature.main.AppNav
import dagger.hilt.android.AndroidEntryPoint

/**
 * Demo de los 3 niveles de IA en Android, de más a menos exigente en hardware:
 * ML Kit GenAI Prompt API (Gemini Nano on-device) -> MediaPipe LLM Inference
 * (Gemma local) -> Gemini API en la nube (fallback universal con red).
 *
 * Repartido en 6 pantallas (Chat, Comparar, Parámetros, Dispositivo,
 * Historial, Estadísticas) con navegación adaptativa — ver
 * `feature:main/AppNav.kt` para el shell (`NavigationBar`/`NavigationRail`
 * según `ScreenSizeClass`) y `core:designsystem` para las piezas de UI
 * compartidas entre pantallas.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explícito, no solo por quedar prolijo: con targetSdk 36 el sistema ya dibuja edge-to-edge
        // sin pedirlo (desde Android 15), así que sin esto el contenido igual queda por detrás de
        // las barras del sistema, pero sin el manejo automático de color de sus iconos que da esta
        // llamada. Los insets de cada barra propia (ChannelTopBar, NavigationBar) se manejan aparte
        // — ver AppNav.kt.
        enableEdgeToEdge()
        // Sin esto, el sistema le pone un scrim translúcido a la barra de navegación por debajo
        // de nuestra `NavigationBar` (opaca, con su propio `DemoColors.Panel`) — minSdk 31 ya
        // cubre de sobra el API 29+ que pide esta propiedad.
        window.isNavigationBarContrastEnforced = false
        setContent {
            DemoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(initialSharedText = sharedTextFrom(intent))
                }
            }
        }
    }

    /**
     * `MainActivity` no fija `launchMode` (queda "standard"): compartir texto abre una instancia
     * nueva de la Activity en vez de reusar una existente vía `onNewIntent` — para este demo es
     * más simple y suficiente (ver AndroidManifest.xml, intent-filter ACTION_SEND).
     */
    private fun sharedTextFrom(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.takeIf { it.isNotBlank() }
}
