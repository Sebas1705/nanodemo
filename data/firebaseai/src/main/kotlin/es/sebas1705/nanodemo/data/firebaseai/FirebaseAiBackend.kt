package es.sebas1705.nanodemo.data.firebaseai

import android.content.Context
import android.util.Log
import es.sebas1705.nanodemo.core.common.DemoTools
import es.sebas1705.nanodemo.core.common.hasInternetConnection
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import es.sebas1705.nanodemo.data.firebasecore.FirebaseBootstrap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *   desde Doppler o `local.properties` (ver `data:firebasecore`). Sin esos tres
 *   valores el tier queda UNAVAILABLE.
 * - No se activa App Check (la doc lo recomienda para producción): el proyecto de Firebase
 *   queda sin enforcement, suficiente para un demo local.
 * - No se usa Hybrid Inference (`onDeviceConfig`): hoy es experimental (`firebase-ai-ondevice`
 *   en beta), solo hace una pasada de texto, y en la práctica vuelve a ser Gemini Nano, que
 *   ya cubre el Tier A.
 * - Se crea el `GenerativeModel` en cada turno: es liviano y así los parámetros y el system
 *   prompt de la UI se aplican siempre (la charla la lleva la app, mandando todo el historial).
 * - `functionCalling`: el constructor de `Schema` y `Schema.Companion.str()/obj()` resultaron
 *   `internal` en este `.aar` (no en la doc, en el bytecode real) — la única herramienta de demo
 *   (`DemoTools`) queda sin parámetros acá, a diferencia del Tier C que sí puede declarar el
 *   parámetro de zona horaria (JSON crudo, sin ese límite). Ver `currentTimeDeclaration()`.
 */
class FirebaseAiBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebase: FirebaseBootstrap,
) : InferenceBackend {

    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = true,
        maxOutputTokens = true,
        systemPrompt = true,
        multiTurn = true,
        images = true,
        structuredOutput = true,
        functionCalling = true,
    )

    private var lastResponseTokens: Int? = null

    /** Garantiza la `FirebaseApp` "default" (la que usa `Firebase.ai`); normalmente ya la creó `Application`. */
    private fun ensureInitialized() {
        check(firebase.ensureInitialized()) {
            "Falta FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID (Doppler o local.properties)"
        }
    }

    override suspend fun checkAvailability(): TierAvailability {
        val configured = firebase.isConfigured
        val hasInternet = hasInternetConnection(context)
        Log.d(TAG, "checkAvailability: configured=$configured hasInternet=$hasInternet")
        return if (configured && hasInternet) TierAvailability.AVAILABLE else TierAvailability.UNAVAILABLE
    }

    /**
     * `functionCalling`: hasta 2 idas y vueltas al modelo por turno, igual que en el Tier C (ver su
     * comentario). La primera declara la herramienta; si `GenerateContentResponse.functionCalls`
     * viene con algo (convenience getter verificado contra el `.aar` real), se ejecuta local
     * (`DemoTools`, sin red) y se manda una segunda vez con el turno del modelo (`FunctionCallPart`)
     * + la respuesta (`FunctionResponsePart`) agregados — ahí sí llega el texto final.
     */
    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        lastResponseTokens = null
        ensureInitialized()

        val model = buildModel(config)
        val contents = history.map { it.toFirebaseContent() }

        Log.d(TAG, "generateContentStream: $CLOUD_MODEL, ${contents.size} turnos")
        val functionCall = streamRequest(model, contents) { text -> emit(text) }
        if (functionCall != null) {
            Log.d(TAG, "generateContentStream: pidió la función ${functionCall.name}")
            emit("🔧 usando \"${functionCall.name}\"…\n\n")
            // Sin parámetros en esta declaración (ver currentTimeDeclaration): siempre la zona del
            // dispositivo, no la que elija el modelo.
            val result = DemoTools.executeCurrentTime(java.time.ZoneId.systemDefault().id)
            val extraTurns = listOf(
                Content(role = "model", parts = listOf(functionCall)),
                // "user" (no "function"): así lo documenta ai.google.dev/api/generate-content para
                // el turno con functionResponse en el API de contents/parts clásico que usa este
                // SDK por debajo — a confirmar en un dispositivo real si el runtime lo rechaza.
                Content(
                    role = "user",
                    parts = listOf(
                        FunctionResponsePart(
                            name = functionCall.name,
                            response = JsonObject(result.mapValues { (_, v) -> JsonPrimitive(v) }),
                        )
                    ),
                ),
            )
            streamRequest(model, contents + extraTurns) { text -> emit(text) }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildModel(config: GenerationConfig): com.google.firebase.ai.GenerativeModel {
        val generationConfig = FirebaseGenerationConfig.builder()
            .setTemperature(config.temperature)
            .setTopK(config.topK)
            .setTopP(config.topP)
            .setMaxOutputTokens(config.maxOutputTokens)
            // setResponseMimeType/responseMimeType verificado contra el .aar real de firebase-ai
            // 17.17.0 (GenerationConfig.Builder). `.apply` porque solo se llama condicionalmente.
            .apply { if (config.jsonMode) setResponseMimeType("application/json") }
            .build()
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            generationConfig = generationConfig,
            systemInstruction = config.systemPrompt?.let { prompt -> content { text(prompt) } },
            tools = if (config.functionCalling) listOf(Tool.functionDeclarations(listOf(currentTimeDeclaration()))) else null,
        )
    }

    /**
     * `FunctionDeclaration` verificado contra el `.aar` real de firebase-ai 17.17.0 — pero tanto el
     * constructor de `Schema` como `Schema.Companion.str(...)`/`.obj(...)` resultaron `internal` en
     * esta versión (compila con "Cannot access ... it is internal" / "Unresolved reference"), así
     * que esta declaración no toma parámetros: usa siempre la zona horaria del dispositivo. El Tier
     * C (REST directo, JSON crudo sin esta limitación) sí deja que el modelo elija la zona.
     */
    private fun currentTimeDeclaration(): FunctionDeclaration = FunctionDeclaration(
        name = DemoTools.GET_CURRENT_TIME,
        description = "${DemoTools.CURRENT_TIME_DESCRIPTION} Siempre en la zona horaria del dispositivo.",
        parameters = emptyMap(),
    )

    private fun ChatMessage.toFirebaseContent(): Content {
        val image = imageUri?.let { readImage(it) }
        return content(role = if (role == Role.USER) "user" else "model") {
            text(text)
            if (image != null) inlineData(image.first, image.second)
        }
    }

    /**
     * Manda [contents] al modelo y consume el stream, emitiendo cada chunk de texto a [onText]. Si
     * el modelo pide la función en vez de responder texto, la devuelve sin llamar a [onText] para
     * ese chunk — el llamador decide qué hacer. Solo soporta UN pedido de función por turno.
     */
    private suspend fun streamRequest(
        model: com.google.firebase.ai.GenerativeModel,
        contents: List<Content>,
        onText: suspend (String) -> Unit,
    ): FunctionCallPart? {
        var functionCall: FunctionCallPart? = null
        model.generateContentStream(contents).collect { response ->
            response.usageMetadata?.candidatesTokenCount?.let { lastResponseTokens = it }
            response.functionCalls.firstOrNull()?.let { functionCall = it }
            val text = response.text
            Log.v(TAG, "chunk: \"$text\"")
            if (!text.isNullOrEmpty()) onText(text)
        }
        return functionCall
    }

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
