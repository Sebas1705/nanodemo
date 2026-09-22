package es.sebas1705.nanodemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
