package es.sebas1705.nanodemo.domain.models

/** Los tres niveles de IA que prueba el demo, de más a menos exigente en hardware. */
enum class InferenceTier {
    ML_KIT_NANO,
    MEDIAPIPE_LOCAL,
    CLOUD_GEMINI,
}

/** Disponibilidad de un tier concreto en este dispositivo/momento. */
enum class TierAvailability {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    DOWNLOADABLE,
    UNAVAILABLE,
}
