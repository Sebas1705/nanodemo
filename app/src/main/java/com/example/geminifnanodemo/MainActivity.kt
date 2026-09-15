package com.example.geminifnanodemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Demo mínima del ML Kit GenAI Prompt API (Gemini Nano on-device).
 *
 * Flujo: comprobar disponibilidad -> (descargar si hace falta) -> escribir
 * un prompt -> ver la respuesta generada en streaming, todo sin salir del
 * dispositivo ni llamar a un servidor propio.
 *
 * Requiere un dispositivo real compatible con Gemini Nano v3 (p.ej. Pixel 10,
 * Galaxy S26) con Google Play Services actualizado. En un emulador o en un
 * dispositivo sin AICore, checkStatus() devolverá UNAVAILABLE y la demo lo
 * mostrará en pantalla en lugar de fallar en silencio.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PromptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun DemoScreen(viewModel: PromptViewModel) {
    val state by viewModel.state.collectAsState()
    var promptText by remember { mutableStateOf("Explícame en una frase qué es un agente de IA.") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Gemini Nano — Prompt API",
                style = MaterialTheme.typography.headlineSmall
            )

            when (state) {
                is DemoState.CheckingAvailability -> {
                    Text("Comprobando si este dispositivo soporta Gemini Nano…")
                    CircularProgressIndicator()
                }

                is DemoState.Unavailable -> {
                    Text(
                        "Gemini Nano no está disponible en este dispositivo. " +
                            "Necesitas un móvil flagship de 2026 con ≥12GB de RAM " +
                            "(p.ej. Pixel 10 o Galaxy S26) y Google Play Services " +
                            "actualizado."
                    )
                }

                is DemoState.Downloadable -> {
                    Text("El modelo está disponible pero aún no se ha descargado.")
                    Button(onClick = { viewModel.downloadModel() }) {
                        Text("Descargar modelo")
                    }
                }

                is DemoState.Downloading -> {
                    Text("Descargando Gemini Nano… (puede tardar; solo ocurre una vez)")
                    CircularProgressIndicator()
                }

                is DemoState.Ready, is DemoState.Generating, is DemoState.Result, is DemoState.Error -> {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Prompt") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isGenerating = state is DemoState.Generating
                    Button(
                        onClick = { viewModel.generate(promptText) },
                        enabled = !isGenerating
                    ) {
                        Text(if (isGenerating) "Generando…" else "Generar (on-device)")
                    }

                    when (val s = state) {
                        is DemoState.Generating -> {
                            Text("Respuesta:", style = MaterialTheme.typography.titleSmall)
                            Text(s.partialText)
                        }
                        is DemoState.Result -> {
                            Text("Respuesta:", style = MaterialTheme.typography.titleSmall)
                            Text(s.text)
                        }
                        is DemoState.Error -> {
                            Text("Error: ${s.message}")
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
