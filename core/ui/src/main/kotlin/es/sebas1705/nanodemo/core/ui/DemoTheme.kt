package es.sebas1705.nanodemo.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Paleta tipo panel de instrumentos de banco de pruebas: cada tier tiene su
 * propio color de señal (ver `signalColor` en `core:designsystem`, que sí
 * conoce `InferenceTier` — este módulo se mantiene agnóstico de dominio).
 */
object DemoColors {
    val Background = Color(0xFF0E1210)
    val Panel = Color(0xFF171D1A)
    val Ink = Color(0xFFE8F1EC)
    val Muted = Color(0xFF7C8C86)
    val TierA = Color(0xFF5CF29B) // Nano — canal más cercano al silicio.
    val TierB = Color(0xFFF2B35C) // MediaPipe — on-device, canal "tibio".
    val TierC = Color(0xFF5CC7F2) // Cloud — señal que viaja lejos.
    val TierD = Color(0xFFB08CF2) // LiteRT-LM — on-device, runtime nuevo de Google.
    val TierE = Color(0xFFF25CB6) // Firebase AI Logic — nube vía Firebase.
    val Danger = Color(0xFFF26D5C)

    /** Borde sutil de tarjetas/campos (blanco al 10%): reemplaza a los bordes de color de antes. */
    val Outline = Color(0x1AFFFFFF)
}

private val demoColorScheme = darkColorScheme(
    background = DemoColors.Background,
    surface = DemoColors.Panel,
    surfaceVariant = DemoColors.Panel,
    onBackground = DemoColors.Ink,
    onSurface = DemoColors.Ink,
    onSurfaceVariant = DemoColors.Muted,
    primary = DemoColors.TierA,
    onPrimary = DemoColors.Background,
    error = DemoColors.Danger,
    onError = DemoColors.Background,
)

// Estilo de chat de IA en toda la app: títulos en la fuente por defecto, sin mayúsculas ni tracking ancho
// (antes: placa de instrumento). Lo monoespaciado queda para datos/etiquetas chicas (`labelSmall`,
// `readoutStyle`) — un guiño al banco de pruebas, no el tono de toda la interfaz.
private val demoTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace),
    )
}

/** Estilo monospace para labels/datos/status — lectura tipo readout de instrumento. */
val readoutStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    // El panel de instrumentos es intencionalmente siempre oscuro (como el
    // chasis de un equipo real), independiente del tema claro/oscuro del
    // sistema — isSystemInDarkTheme() no se usa para elegir el esquema.
    MaterialTheme(
        colorScheme = demoColorScheme,
        typography = demoTypography,
        content = content,
    )
}
