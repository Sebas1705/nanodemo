package es.sebas1705.nanodemo.domain.models

import android.net.Uri

/** Quién dijo un turno de la conversación. */
enum class Role { USER, MODEL }

/** Un turno de conversación, con imagen opcional (solo se usa en el último turno de usuario). */
data class ChatMessage(
    val role: Role,
    val text: String,
    val imageUri: Uri? = null,
)

/**
 * Parámetros de generación que el usuario puede tocar desde la UI. Cada
 * backend aplica solo los campos que soporta (ver [TierCapabilities]) e
 * ignora el resto — nunca falla por un campo no soportado. Los valores por
 * defecto son los típicos de un LLM (para que los sliders arranquen en una
 * posición razonable); `systemPrompt` empieza vacío y solo se manda si el
 * usuario escribe algo.
 */
data class GenerationConfig(
    val temperature: Float? = 1.0f,
    val topK: Int? = 40,
    val topP: Float? = 0.95f,
    val maxOutputTokens: Int? = 512,
    val systemPrompt: String? = null,
    /**
     * Pide al modelo que devuelva JSON en vez de texto libre (`responseMimeType` en el REST API y
     * en Firebase AI Logic). Solo lo garantizan los tiers con `TierCapabilities.structuredOutput`
     * — en el resto, la UI directamente no ofrece el control en vez de mandarlo y que se ignore.
     */
    val jsonMode: Boolean = false,
)

/**
 * Qué soporta realmente cada tier. La UI usa esto para mostrar u ocultar
 * controles en vez de aceptar un valor que después se ignora en silencio —
 * ver qué soporta cada tier es en sí mismo parte del estudio.
 */
data class TierCapabilities(
    val temperature: Boolean,
    val topK: Boolean,
    val topP: Boolean,
    val maxOutputTokens: Boolean,
    val systemPrompt: Boolean,
    val multiTurn: Boolean,
    val images: Boolean,
    /** `responseMimeType: application/json` — solo los tiers en la nube lo garantizan hoy. */
    val structuredOutput: Boolean = false,
)
