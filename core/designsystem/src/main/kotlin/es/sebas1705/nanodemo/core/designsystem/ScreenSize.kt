package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Breakpoints de ancho (mismos umbrales que Material3 window size class:
 * compact <600dp, medium 600-840dp, expanded >840dp) para decidir entre
 * layout de una columna (teléfono) o de paneles/rail lateral (tablet,
 * plegable, landscape ancho).
 */
enum class ScreenSizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberScreenSizeClass(): ScreenSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> ScreenSizeClass.COMPACT
        widthDp < 840 -> ScreenSizeClass.MEDIUM
        else -> ScreenSizeClass.EXPANDED
    }
}

/**
 * Un teléfono en landscape suele leer como ancho medium/expanded (>600dp)
 * pero con muy poca altura (<480dp, el breakpoint "compact height" de
 * Material3) — no alcanza para un rail lateral con varios ítems con label.
 * `AppNav` usa esto para no meter el rail cuando no entra verticalmente.
 */
@Composable
fun rememberIsCompactHeight(): Boolean {
    val heightDp = LocalConfiguration.current.screenHeightDp
    return heightDp < 480
}
