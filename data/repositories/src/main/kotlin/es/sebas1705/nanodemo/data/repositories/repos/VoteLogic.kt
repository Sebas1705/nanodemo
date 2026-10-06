package es.sebas1705.nanodemo.data.repositories.repos

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally

/**
 * Aplica un voto a los acumulados. Una ronda cuenta una sola vez en `appearances` de cada participante, aunque
 * el usuario cambie de opinión: re-votar mueve la victoria del anterior al nuevo sin volver a contar la ronda.
 *
 * - Primer voto de la ronda (`previous == null`): suma una aparición a cada participante (y al ganador, por
 *   las dudas de que no figure) y una victoria al ganador.
 * - Cambio de voto: quita la victoria al anterior (nunca por debajo de 0) y se la da al nuevo.
 * - Mismo voto repetido: no cambia nada.
 */
internal fun applyVote(
    current: Map<InferenceTier, VoteTally>,
    participants: Set<InferenceTier>,
    winner: InferenceTier,
    previous: InferenceTier?,
): Map<InferenceTier, VoteTally> {
    if (previous == winner) return current
    val result = current.toMutableMap()
    fun tally(tier: InferenceTier) = result[tier] ?: VoteTally()

    if (previous == null) {
        (participants + winner).forEach { tier -> result[tier] = tally(tier).copy(appearances = tally(tier).appearances + 1) }
    } else {
        result[previous] = tally(previous).copy(wins = (tally(previous).wins - 1).coerceAtLeast(0))
    }
    result[winner] = tally(winner).copy(wins = tally(winner).wins + 1)
    return result
}
