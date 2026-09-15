package com.example.geminifnanodemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estados posibles de la demo. Modelan tanto el ciclo de vida del modelo
 * on-device (comprobar disponibilidad -> descargar -> listo) como el de
 * una generación de texto concreta.
 */
sealed interface DemoState {
    data object CheckingAvailability : DemoState
    data object Unavailable : DemoState
    data object Downloadable : DemoState
    data object Downloading : DemoState
    data object Ready : DemoState
    data class Generating(val partialText: String) : DemoState
    data class Result(val text: String) : DemoState
    data class Error(val message: String) : DemoState
}

/**
 * ViewModel que envuelve el ML Kit GenAI Prompt API (Gemini Nano on-device).
 *
 * Referencia oficial:
 * https://developers.google.com/ml-kit/genai/prompt/android/get-started
 *
 * NOTA: el Prompt API está en beta (1.0.0-beta4 en el momento de escribir esto,
 * sept. 2026) y su superficie puede cambiar. Los nombres de clase/paquete de
 * este archivo (Generation, GenerativeModel, FeatureStatus, DownloadStatus)
 * están verificados contra la documentación oficial de Google, pero conviene
 * recompilar y revisar el autocompletado de Android Studio antes de confiar
 * en ellos en producción.
 */
class PromptViewModel : ViewModel() {

    // Generation.getClient() sin argumentos usa la configuración por defecto
    // (modelo "stable"). Se puede pasar un generationConfig { modelConfig { ... } }
    // para pedir explícitamente un modelo PREVIEW o preferencia FAST/FULL.
    private val generativeModel = Generation.getClient()

    private val _state = MutableStateFlow<DemoState>(DemoState.CheckingAvailability)
    val state: StateFlow<DemoState> = _state.asStateFlow()

    init {
        checkAvailability()
    }

    /** Comprueba si Gemini Nano está disponible, descargable o no soportado en este dispositivo. */
    fun checkAvailability() {
        viewModelScope.launch {
            _state.update { DemoState.CheckingAvailability }
            try {
                when (generativeModel.checkStatus()) {
                    FeatureStatus.AVAILABLE -> _state.update { DemoState.Ready }
                    FeatureStatus.DOWNLOADABLE -> _state.update { DemoState.Downloadable }
                    FeatureStatus.DOWNLOADING -> downloadModel()
                    FeatureStatus.UNAVAILABLE -> _state.update { DemoState.Unavailable }
                    else -> _state.update { DemoState.Unavailable }
                }
            } catch (e: Exception) {
                _state.update {
                    DemoState.Error(e.message ?: "Error comprobando disponibilidad del modelo")
                }
            }
        }
    }

    /** Dispara la descarga del modelo (requiere red la primera vez). */
    fun downloadModel() {
        viewModelScope.launch {
            _state.update { DemoState.Downloading }
            try {
                // download() emite un Flow<DownloadStatus> con el progreso.
                // Los subtipos exactos (DownloadStarted/DownloadProgress/
                // DownloadCompleted/DownloadFailed) están documentados, pero
                // Google no publica los nombres de sus campos internos (p.ej.
                // bytes descargados) — por eso aquí solo distinguimos el tipo
                // de evento y no leemos campos específicos. Revisa el
                // autocompletado de Android Studio si quieres mostrar un
                // porcentaje real de progreso.
                generativeModel.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadCompleted ->
                            _state.update { DemoState.Ready }
                        is DownloadStatus.DownloadFailed ->
                            _state.update { DemoState.Error("La descarga del modelo falló") }
                        else -> _state.update { DemoState.Downloading }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    DemoState.Error(e.message ?: "Error descargando el modelo")
                }
            }
        }
    }

    /** Envía un prompt a Gemini Nano y va actualizando el estado con la respuesta en streaming. */
    fun generate(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _state.update { DemoState.Generating("") }
            try {
                var fullResponse = ""
                generativeModel.generateContentStream(prompt).collect { chunk ->
                    val newText = chunk.candidates.firstOrNull()?.text.orEmpty()
                    fullResponse += newText
                    _state.update { DemoState.Generating(fullResponse) }
                }
                _state.update { DemoState.Result(fullResponse) }
            } catch (e: Exception) {
                _state.update {
                    DemoState.Error(e.message ?: "Error generando contenido")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generativeModel.close()
    }
}
