package com.example.geminifnanodemo.domain.models

/**
 * Resumen de una conversación guardada en el historial local (pantalla
 * Historial) — no incluye los mensajes, solo lo necesario para listarla.
 */
data class Conversation(
    val id: Long,
    val tier: InferenceTier,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val messageCount: Int,
)
