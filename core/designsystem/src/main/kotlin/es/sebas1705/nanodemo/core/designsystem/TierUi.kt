package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
        InferenceTier.LITERT_LM_LOCAL -> DemoColors.TierD
        InferenceTier.FIREBASE_AI -> DemoColors.TierE
    }

fun InferenceTier.label(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> "Nano"
    InferenceTier.MEDIAPIPE_LOCAL -> "MediaPipe"
    InferenceTier.CLOUD_GEMINI -> "Cloud"
    InferenceTier.LITERT_LM_LOCAL -> "LiteRT-LM"
    InferenceTier.FIREBASE_AI -> "Firebase AI"
}

fun InferenceTier.channelLetter(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> "A"
    InferenceTier.MEDIAPIPE_LOCAL -> "B"
    InferenceTier.CLOUD_GEMINI -> "C"
    InferenceTier.LITERT_LM_LOCAL -> "D"
    InferenceTier.FIREBASE_AI -> "E"
}

@Composable
fun InferenceTier.unavailableMessage(): String = stringResource(
    when (this) {
        InferenceTier.ML_KIT_NANO -> R.string.tier_unavailable_nano
        InferenceTier.MEDIAPIPE_LOCAL -> R.string.tier_unavailable_mediapipe
        InferenceTier.CLOUD_GEMINI -> R.string.tier_unavailable_cloud
        InferenceTier.LITERT_LM_LOCAL -> R.string.tier_unavailable_litertlm
        InferenceTier.FIREBASE_AI -> R.string.tier_unavailable_firebase
    },
)

@Composable
fun InferenceTier.downloadableMessage(): String = when (this) {
    InferenceTier.ML_KIT_NANO -> stringResource(R.string.tier_downloadable_nano)
    InferenceTier.MEDIAPIPE_LOCAL -> stringResource(R.string.tier_downloadable_mediapipe)
    InferenceTier.CLOUD_GEMINI -> "" // This tier never ends up in the Downloadable state.
    InferenceTier.LITERT_LM_LOCAL -> stringResource(R.string.tier_downloadable_litertlm)
    InferenceTier.FIREBASE_AI -> "" // Cloud: never ends up in the Downloadable state.
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
