package es.sebas1705.nanodemo.domain.models

/**
 * Dónde corre LiteRT-LM (Tier D) la inferencia. CPU es el que no depende del hardware/driver; GPU suele
 * ser bastante más rápido pero no anda en todos los dispositivos — si no inicializa, el motor vuelve solo a CPU.
 */
enum class LiteRtLmAccelerator(val displayName: String) {
    CPU("CPU"),
    GPU("GPU"),
}
