package es.sebas1705.nanodemo.domain.models

/**
 * Los motores de IA que prueba el demo. El orden importa: es el orden de la UI
 * y el del fallback automático (se queda en el primero disponible o
 * descargable), así que los tiers nuevos (D/E) van al final para no cambiar
 * qué se elige por defecto. Los nombres se guardan en la base de historial
 * (`InferenceTier.valueOf`), por eso solo se agregan entradas, nunca se renombran.
 */
enum class InferenceTier {
    ML_KIT_NANO,
    MEDIAPIPE_LOCAL,
    CLOUD_GEMINI,
    LITERT_LM_LOCAL,
    FIREBASE_AI,
}

/** Disponibilidad de un tier concreto en este dispositivo/momento. */
enum class TierAvailability {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    DOWNLOADABLE,
    UNAVAILABLE,
}
