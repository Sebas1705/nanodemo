package es.sebas1705.nanodemo.domain.models

/**
 * Votos de "esta fue la mejor respuesta" en Comparar, por motor: en cuántas rondas votadas ganó
 * ([wins]) de las que participó ([appearances], o sea rondas donde llegó a dar una respuesta).
 * Es preferencia humana, no una métrica objetiva — el complemento de la latencia y los tokens.
 */
data class VoteTally(val wins: Int = 0, val appearances: Int = 0) {
    /** 0..1; `null` si el motor todavía no participó de ninguna ronda votada. */
    val winRate: Float? get() = if (appearances <= 0) null else wins.toFloat() / appearances
}
