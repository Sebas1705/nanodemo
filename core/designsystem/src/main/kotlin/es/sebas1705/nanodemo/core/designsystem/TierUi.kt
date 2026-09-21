package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.ui.graphics.Color
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier

/**
 * Mapeo tier -> presentación (color/label/mensajes). Vive en `designsystem`
 * (no en `core:ui`) porque necesita conocer `InferenceTier`, un modelo de
 * `domain:models` — `core:ui` se mantiene agnóstico de dominio.
 */
val InferenceTier.signalColor: Color
    get() = when (this) {
        InferenceTier.ML_KIT_NANO -> DemoColors.TierA
        InferenceTier.MEDIAPIPE_LOCAL -> DemoColors.TierB
        InferenceTier.CLOUD_GEMINI -> DemoColors.TierC
    }

fun InferenceTier.label(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> "Nano"
    InferenceTier.MEDIAPIPE_LOCAL -> "MediaPipe"
    InferenceTier.CLOUD_GEMINI -> "Cloud"
}

fun InferenceTier.channelLetter(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> "A"
    InferenceTier.MEDIAPIPE_LOCAL -> "B"
    InferenceTier.CLOUD_GEMINI -> "C"
}

fun InferenceTier.unavailableMessage(): String = when (this) {
    InferenceTier.ML_KIT_NANO ->
        "Gemini Nano no está disponible en este dispositivo. Necesitas un móvil " +
            "flagship de 2026 con ≥12GB de RAM (p.ej. Pixel 10 o Galaxy S26) y " +
            "Google Play Services actualizado."
    InferenceTier.MEDIAPIPE_LOCAL ->
        "Este dispositivo no tiene RAM suficiente (~4GB+) para correr un modelo " +
            "Gemma local con MediaPipe."
    InferenceTier.CLOUD_GEMINI ->
        "Falta configurar GEMINI_API_KEY en local.properties, o no hay conexión " +
            "a internet."
}

fun InferenceTier.downloadableMessage(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> "El modelo está disponible pero aún no se ha descargado."
    InferenceTier.MEDIAPIPE_LOCAL ->
        "Falta el modelo Gemma (.task) — se puede descargar (unos cientos de MB)."
    InferenceTier.CLOUD_GEMINI -> "" // Este tier nunca queda en estado Downloadable.
}

/** Fases amplias de `DemoState`, para animar sin retriggerear en cada chunk de streaming. */
enum class ScreenPhase { CHECKING, UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, READY_GROUP }

val DemoState.phase: ScreenPhase
    get() = when (this) {
        is DemoState.CheckingAvailability -> ScreenPhase.CHECKING
        is DemoState.Unavailable -> ScreenPhase.UNAVAILABLE
        is DemoState.Downloadable -> ScreenPhase.DOWNLOADABLE
        is DemoState.Downloading -> ScreenPhase.DOWNLOADING
        else -> ScreenPhase.READY_GROUP
    }
