package es.sebas1705.nanodemo.data.repositories.repos

/**
 * Nombres de eventos, operaciones y parámetros **propios de nanodemo**. Los genéricos
 * (`feature_used`, `operation_result`, `duration_ms`, `error_type`...) están en `core:common`
 * (`TelemetryEvents` / `TelemetryParams`) y son los mismos en todas las apps del template.
 * Todos cumplen los límites de Firebase (snake_case, ≤ 40 caracteres); ver `docs/TELEMETRY.md`.
 */
internal object InferenceEvents {
    const val TIER_SELECTED = "tier_selected"
    const val TIER_AVAILABILITY = "tier_availability"
}

/** Valores del parámetro `operation` de `operation_result`. */
internal object InferenceOperations {
    const val CHECK_AVAILABILITY = "check_availability"
    const val DOWNLOAD = "download"
    const val GENERATE = "generate"
    const val EXPORT_CSV = "export_csv"
    const val DELETE_CONVERSATION = "delete_conversation"

    /** Guardar un turno en el historial local, después de que `generate` ya tuvo éxito. */
    const val PERSIST_TURN = "persist_turn"

    /** Persistencia local de métricas (`data:history` / tabla `metrics`), separada de `persist_turn`. */
    const val PERSIST_METRIC = "persist_metric"
    const val LOAD_METRICS = "load_metrics"

    /** `InferenceBackend.close()` / `resetConversation()`: no deberían fallar, pero si lo hacen no tiran la app. */
    const val CLOSE_BACKEND = "close_backend"
    const val RESET_BACKEND = "reset_backend"

    /** Descargar la página de "pegar un link y preguntar" (ver `core:common.fetchUrlText`). */
    const val FETCH_URL = "fetch_url"
}

/** Valores del parámetro `feature` de `feature_used`. */
internal object InferenceFeatures {
    const val CONVERSATION_RESET = "conversation_reset"
    const val CONVERSATION_OPEN = "conversation_open"
    const val CONVERSATION_DELETE = "conversation_delete"
    const val MEDIAPIPE_MODEL_SELECT = "mediapipe_model_select"
    const val COMPARE_RUN = "compare_run"
    const val URL_ATTACH = "url_attach"
}

internal object InferenceParams {
    const val TIER = "tier"
    const val MODEL = "model"
    const val REASON = "reason"
    const val AVAILABILITY = "availability"
    const val MODE = "mode"
    const val TURNS = "turns"
    const val HAS_IMAGE = "has_image"
    const val FIRST_CHUNK_MS = "first_chunk_ms"
    const val PROMPT_CHARS = "prompt_chars"
    const val RESPONSE_CHARS = "response_chars"
    const val RESPONSE_TOKENS = "response_tokens"
    const val TOKENS_EXACT = "tokens_exact"
    const val MEMORY_DELTA_KB = "memory_delta_kb"
}

/** Valores del parámetro `mode` de la generación. */
internal object GenerationMode {
    const val CHAT = "chat"
    const val COMPARE = "compare"
}
