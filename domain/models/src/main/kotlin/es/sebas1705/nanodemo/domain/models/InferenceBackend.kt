package es.sebas1705.nanodemo.domain.models

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Contrato común que implementa cada uno de los 5 tiers de IA del demo
 * (adaptadores en `data:mlkitnano`/`data:mediapipe`/`data:cloudgemini`/
 * `data:litertlm`/`data:firebaseai`).
 *
 * Vive en `domain:models` (no en `data:repositories`, como decía el plan
 * original): así los módulos `data:*` que lo implementan dependen de
 * `domain:models`, y `data:repositories` depende de ellos para armar el
 * mapa de backends — sin esto, `data:repositories` y los `data:*` quedarían
 * en dependencia circular. Guía de portado: docs/AI_MODULES.md.
 */
interface InferenceBackend {

    /** Qué soporta realmente este tier (para que la UI muestre/oculte controles). */
    val capabilities: TierCapabilities

    /** Comprueba si este tier puede usarse ahora mismo en este dispositivo. */
    suspend fun checkAvailability(): TierAvailability

    /**
     * Dispara la descarga del modelo/recurso que le falte a este tier (si aplica).
     * Emite el progreso en 0..100 cuando se conoce (p.ej. por Content-Length);
     * si no hay forma de conocerlo, completa sin emitir nada. Por defecto no hay
     * nada que descargar (p.ej. el tier cloud).
     */
    fun download(): Flow<Int> = emptyFlow()

    /**
     * Envía una conversación y emite la respuesta en streaming, trozo a trozo.
     * `history` incluye el turno nuevo del usuario como último elemento (con
     * imagen opcional). Los backends stateless (ML Kit, Cloud) reconstruyen
     * contexto de todo `history`; los que mantienen sesión propia (MediaPipe)
     * solo necesitan el último turno nuevo.
     */
    fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String>

    /** Reinicia cualquier estado de conversación propio (p.ej. cierra una sesión). No-op por defecto. */
    fun resetConversation() {}

    /**
     * Cantidad real de tokens de la última respuesta generada, si el backend
     * la expone (MediaPipe vía `sizeInTokens()`, Cloud vía `usageMetadata`).
     * Null = no disponible; el llamador aproxima por caracteres.
     */
    fun lastResponseTokens(): Int? = null

    /**
     * Cuánto tardó cargar el motor/sesión del modelo en la ÚLTIMA generación, si esa llamada tuvo que
     * cargarlo (MediaPipe, LiteRT-LM: la primera vez, o tras cambiar de tier/modelo). `null` = no hubo
     * carga (motor ya listo) o este backend no carga nada (Nano, Cloud, Firebase).
     */
    fun lastLoadTimeMs(): Long? = null

    fun close()
}
