# Gemini Nano Demo — ML Kit GenAI Prompt API

Demo mínima en Kotlin + Jetpack Compose que usa el **Prompt API de ML Kit GenAI**
para hablar con **Gemini Nano on-device** (sin servidor propio, sin llamadas a
la nube una vez descargado el modelo).

Es el proyecto de ejemplo que salió de la investigación sobre implementación
de agentes de IA en móvil (ver el proyecto "IA en móvil" en Claude, donde
está la comparativa completa entre esta API de Google y el Foundation Models
framework de Apple).

## Qué hace

1. Al abrir la app, comprueba si el dispositivo soporta Gemini Nano
   (`generativeModel.checkStatus()`).
2. Si el modelo aún no está descargado, ofrece un botón para descargarlo
   (`generativeModel.download()`).
3. Una vez listo, deja escribir un prompt libre y muestra la respuesta
   generada **en streaming**, token a token, on-device.

Todo el código relevante está en dos archivos:

- `app/src/main/java/com/example/geminifnanodemo/PromptViewModel.kt` — la
  lógica: disponibilidad, descarga, generación.
- `app/src/main/java/com/example/geminifnanodemo/MainActivity.kt` — la UI en
  Compose.

## Cómo probarlo

1. Abre la carpeta `GeminiNanoDemo/` con Android Studio (versión reciente,
   Koala o superior). Android Studio generará el resto del Gradle Wrapper
   automáticamente si falta (`gradlew`); si te lo pide, acepta.
2. Sincroniza el proyecto (Gradle descargará `com.google.mlkit:genai-prompt`
   y Compose la primera vez — necesita red).
3. Ejecuta en un **dispositivo físico compatible**, no en un emulador
   genérico: Gemini Nano necesita AICore, que a su vez necesita silicio real.
   Según la documentación de Google (a fecha de esta investigación), eso
   significa un móvil flagship de 2026 con Gemini Nano v3 y ≥12GB de RAM:
   Pixel 10, Galaxy S26, OnePlus 15, Oppo Find X9, Motorola Signature. Un
   Pixel 9 o un Galaxy Z Fold 7, aunque son recientes, se quedan fuera
   (tienen Nano v2).
4. Si el dispositivo no es compatible, la app no crashea: te lo dice en
   pantalla (estado `Unavailable`) en vez de fallar en silencio.

## Notas honestas sobre esta demo

- La dependencia (`com.google.mlkit:genai-prompt:1.0.0-beta4`), los nombres
  de clase (`Generation`, `GenerativeModel`, `FeatureStatus`, `DownloadStatus`)
  y las firmas de los métodos principales (`checkStatus()`, `download()`,
  `generateContent()`, `generateContentStream()`) están verificados contra la
  documentación oficial de Google (developers.google.com/ml-kit/genai y la
  referencia Kotlin de la API) en el momento de escribir esto.
- Los **campos internos** de los subtipos de `DownloadStatus` (por ejemplo,
  si `DownloadProgress` trae bytes descargados o un porcentaje) **no están
  publicados** en la documentación pública que pude consultar, así que el
  ViewModel deliberadamente no los usa — solo distingue "empezó / progresa /
  terminó / falló". Si quieres una barra de progreso real, ábrelo en Android
  Studio y mira qué campos ofrece el autocompletado sobre `DownloadStatus.DownloadProgress`.
- La API está en **beta** (así lo marca Google en el propio artefacto Maven),
  así que puede cambiar de superficie entre versiones.
- No ha sido posible compilar ni ejecutar este proyecto en el entorno donde
  se generó (no hay Android SDK ni un dispositivo/emulador con Gemini Nano
  disponibles ahí). Está escrito con cuidado y basado en ejemplos oficiales,
  pero la primera sincronización en Android Studio es el verdadero test.

## Fuentes usadas para este código

- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/ml-kit/genai/prompt/android/select-model
- https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/prompt/GenerativeModel
- https://developers.google.com/android/reference/com/google/mlkit/genai/common/DownloadStatus
- https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/common/FeatureStatus
- https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html
- https://9to5google.com/2026/05/15/gemini-intelligence-android-spec-requirements/ (requisitos de dispositivo)
