package es.sebas1705.nanodemo.data.embeddings

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.core.common.AppTextsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NanoDemo.Embeddings"

// Universal Sentence Encoder — el modelo de texto más chico del catálogo oficial de MediaPipe
// Tasks (~6MB, contra ~26MB del BERT embedder): de sobra para comparar títulos/mensajes cortos, y
// hospedado público en Google Cloud Storage (sin token, a diferencia de los modelos Gemma del
// Tier B) — verificado con una descarga real sin credenciales.
// https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/...
private const val DEFAULT_MODEL_URL =
    "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/" +
        "float32/latest/universal_sentence_encoder.tflite"

/**
 * Búsqueda semántica local del historial: convierte texto en un vector (embedding) 100% on-device,
 * sin mandar nada a la nube. `data:history` lo usa para indexar cada conversación al guardarla y
 * comparar contra la consulta de búsqueda con `TextEmbedder.cosineSimilarity` — ver
 * `HistoryRepository.searchConversations`.
 *
 * API verificada contra el `.aar` real de `tasks-text:1.0.0` (Java, estilo AutoValue, igual que
 * `LlmInferenceOptions` del Tier B): `BaseOptions.builder().setModelAssetPath(...)`,
 * `TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(...).build()`,
 * `TextEmbedder.createFromOptions(context, options)`, `embed(text).embeddingResult().embeddings()`.
 *
 * `setModelAssetPath` en otras Tasks de MediaPipe acepta tanto una ruta dentro de `assets/` como
 * una ruta absoluta del filesystem para un modelo descargado — no se pudo confirmar en un
 * dispositivo real cuál de las dos espera esta versión exacta; si falla, la alternativa verificada
 * es `setModelAssetBuffer(ByteBuffer)` leyendo el archivo a mano.
 */
@Singleton
class LocalEmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textsProvider: AppTextsProvider,
) {
    private var embedder: TextEmbedder? = null

    private fun modelFile(): File = File(context.getExternalFilesDir(null), "text-embedder.tflite")
    private fun tempModelFile(): File = File(context.getExternalFilesDir(null), "text-embedder.tflite.part")

    fun isAvailable(): Boolean = modelFile().exists()

    fun download(): Flow<Int> = flow {
        val texts = textsProvider.current()
        val temp = tempModelFile()
        val connection = (URL(DEFAULT_MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        Log.d(TAG, "download: GET $DEFAULT_MODEL_URL")
        try {
            if (connection.responseCode !in 200..299) {
                error(texts.errorDownloadingEmbeddingModel(connection.responseCode))
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastEmittedPercent = -1
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(percent)
                            }
                        }
                    }
                }
            }
            check(temp.renameTo(modelFile())) { texts.errorMovingModel }
            Log.d(TAG, "download: completado, $downloadedBytes bytes")
        } finally {
            connection.disconnect()
            temp.delete() // no-op si ya se renombró a modelFile()
        }
    }.flowOn(Dispatchers.IO)

    private fun getOrCreateEmbedder(): TextEmbedder = embedder ?: run {
        Log.d(TAG, "getOrCreateEmbedder: cargando modelo desde ${modelFile().absolutePath}")
        val baseOptions = BaseOptions.builder().setModelAssetPath(modelFile().absolutePath).build()
        val options = TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(baseOptions).build()
        TextEmbedder.createFromOptions(context, options).also { embedder = it }
    }

    /** `null` si el modelo no está descargado o falla — el llamador decide qué hacer (p.ej. saltar esa conversación). */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        try {
            getOrCreateEmbedder().embed(text).embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
        } catch (e: Exception) {
            Log.e(TAG, "embed: falló", e)
            null
        }
    }

    /** Envoltorio fino sobre `TextEmbedder.cosineSimilarity` (viene con el SDK, no hace falta escribir la fórmula). */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Double = TextEmbedder.cosineSimilarity(
        Embedding.create(a, ByteArray(0), 0, Optional.empty()),
        Embedding.create(b, ByteArray(0), 0, Optional.empty()),
    )

    fun close() {
        embedder?.close()
        embedder = null
    }
}
