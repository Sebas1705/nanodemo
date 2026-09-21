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
    val Danger = Color(0xFFF26D5C)
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

// Headline con tracking ancho, tipo placa de equipo de laboratorio. El resto
// (body/label) se deja en la tipografía Material3 por defecto: es contenido
// conversacional (prompt/respuesta) que necesita quedar legible, no decorado.
private val demoTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
        ),
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
