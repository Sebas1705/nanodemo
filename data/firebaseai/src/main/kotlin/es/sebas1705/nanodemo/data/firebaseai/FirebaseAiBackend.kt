package es.sebas1705.nanodemo.data.firebaseai

import android.content.Context
import android.util.Log
import es.sebas1705.nanodemo.core.common.hasInternetConnection
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import com.google.firebase.ai.type.GenerationConfig as FirebaseGenerationConfig

private const val TAG = "NanoDemo.FirebaseAI"

// El mismo modelo que usa el Tier C (REST directo), a propósito: así la diferencia que se
// mide entre C y E es el camino de acceso (REST + API key embebida vs SDK de Firebase),
// no el modelo.
private const val CLOUD_MODEL = "gemini-3.5-flash"

/**
 * Tier E: Firebase AI Logic — la vía que Google recomienda para llamar a Gemini desde una
 * app móvil, en contraste con el Tier C (REST directo con la API key embebida en el
 * cliente, que el propio código marca como solo apto para demo).
 *
 * API verificada contra el `.aar` real de `firebase-ai:17.17.0`: `Firebase.ai(backend =
 * GenerativeBackend.googleAI()).generativeModel(...)` y `generateContentStream(List<Content>)`.
 *
 * Decisiones:
 * - Sin el plugin google-services: `FirebaseApp` se inicializa a mano con `FirebaseOptions`
 *   desde `local.properties` (ver `build.gradle.kts` de este módulo). Sin esos tres
 *   valores el tier queda UNAVAILABLE.
 * - No se activa App Check (la doc lo recomienda para producción): el proyecto de Firebase
 *   queda sin enforcement, suficiente para un demo local.
 * - No se usa Hybrid Inference (`onDeviceConfig`): hoy es experimental (`firebase-ai-ondevice`
 *   en beta), solo hace una pasada de texto, y en la práctica vuelve a ser Gemini Nano, que
 *   ya cubre el Tier A.
 * - Se crea el `GenerativeModel` en cada turno: es liviano y así los parámetros y el system
 *   prompt de la UI se aplican siempre (la charla la lleva la app, mandando todo el historial).
 */
class FirebaseAiBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InferenceBackend {

    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = true,
        maxOutputTokens = true,
        systemPrompt = true,
        multiTurn = true,
        images = true,
    )

    private var lastResponseTokens: Int? = null

    private fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    /** Inicializa `FirebaseApp` (la app "default", la que usa `Firebase.ai`) si todavía no existe. */
    private fun ensureInitialized() {
        check(isConfigured()) { "Falta FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID en local.properties" }
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "ensureInitialized: inicializando FirebaseApp a mano")
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }
    }

    override suspend fun checkAvailability(): TierAvailability {
        val configured = isConfigured()
        val hasInternet = hasInternetConnection(context)
        Log.d(TAG, "checkAvailability: configured=$configured hasInternet=$hasInternet")
        return if (configured && hasInternet) TierAvailability.AVAILABLE else TierAvailability.UNAVAILABLE
    }

    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        lastResponseTokens = null
        ensureInitialized()

        val generationConfig = FirebaseGenerationConfig.builder()
            .setTemperature(config.temperature)
            .setTopK(config.topK)
            .setTopP(config.topP)
            .setMaxOutputTokens(config.maxOutputTokens)
            .build()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            generationConfig = generationConfig,
            systemInstruction = config.systemPrompt?.let { prompt -> content { text(prompt) } },
        )

        val contents = history.map { message ->
            val image = message.imageUri?.let { readImage(it) }
            content(role = if (message.role == Role.USER) "user" else "model") {
                text(message.text)
                if (image != null) inlineData(image.first, image.second)
            }
        }

        Log.d(TAG, "generateContentStream: $CLOUD_MODEL, ${contents.size} turnos")
        model.generateContentStream(contents).collect { response ->
            response.usageMetadata?.candidatesTokenCount?.let { lastResponseTokens = it }
            val text = response.text
            Log.v(TAG, "chunk: \"$text\"")
            if (!text.isNullOrEmpty()) emit(text)
        }
    }.flowOn(Dispatchers.IO)

    private fun readImage(uri: android.net.Uri): Pair<ByteArray, String>? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes ->
            bytes to (context.contentResolver.getType(uri) ?: "image/jpeg")
        }
    } catch (e: Exception) {
        Log.e(TAG, "readImage: no se pudo leer $uri", e)
        null
    }

    override fun lastResponseTokens(): Int? = lastResponseTokens

    override fun close() {
        // Sin estado persistente: cada turno crea su propio modelo y el SDK maneja sus conexiones.
    }
}
