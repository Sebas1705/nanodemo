# Demo de IA en móvil — 3 niveles de fallback

Demo en Kotlin + Jetpack Compose que intenta cubrir **cualquier hardware**,
probando tres niveles de IA de más a menos exigente y cayendo automáticamente
al siguiente si el dispositivo (o la configuración) no soporta el anterior:

1. **Tier A — ML Kit GenAI Prompt API (Gemini Nano on-device vía AICore)**:
   la mejor calidad, pero solo en flagships 2026 con ≥12GB de RAM.
2. **Tier B — MediaPipe LLM Inference (Gemma local)**: on-device pero
   gestionado por la propia app, corre en gama media (~4GB+ RAM).
3. **Tier C — Gemini API en la nube**: fallback universal, funciona en
   cualquier dispositivo con internet, pero ya no es on-device.

Es el proyecto de ejemplo que salió de la investigación sobre implementación
de agentes de IA en móvil (ver el proyecto "IA en móvil" en Claude, donde
está la comparativa completa entre estas APIs de Google y el Foundation
Models framework de Apple).

## Qué hace

1. Al abrir la app, prueba los tres tiers en orden (A → B → C) y se queda en
   el primero disponible o descargable.
2. Un selector arriba (`Nano` / `MediaPipe` / `Cloud`) permite forzar
   cualquiera de los tres manualmente, para comparar respuestas en el mismo
   dispositivo.
3. Si el tier activo necesita un paso previo (descargar el modelo de Gemini
   Nano o el `.task` de MediaPipe), lo muestra en pantalla con un botón para
   descargarlo — con progreso cuando se conoce — en vez de fallar en silencio.
4. Panel de **parámetros de generación** (temperature, top-K, top-P, max
   output tokens, system prompt) — cada control se deshabilita si el tier
   activo no lo soporta (ver tabla de capacidades más abajo).
5. **Conversación multi-turno**: el prompt y la respuesta quedan en un
   transcript que se manda de contexto en el siguiente turno; "Nueva
   conversación" lo resetea (y cierra la sesión de MediaPipe, si aplica).
6. **Imágenes**: se puede adjuntar una foto (Photo Picker, sin permisos) al
   turno de usuario — soportado en Nano y Cloud, no en MediaPipe (el modelo
   que tenemos hosteado no tiene encoder de visión).
7. **Modo comparación**: un toggle que corre el mismo prompt en los 3 tiers
   a la vez, mostrando las 3 respuestas lado a lado.
8. **Banco de prompts / benchmark**: una fila de presets (resumen, código,
   traducción, etc.) y un botón para correrlos todos contra los 3 tiers de
   forma repetible.
9. El panel de **estadísticas** compara latencia (promedio, p50, p95),
   velocidad y tokens (reales cuando el backend los expone, aproximados si
   no) por tier, con un botón para **exportar todo a CSV** y compartirlo.
10. **Historial de conversaciones**: cada conversación se guarda sola en una
    base de datos local (Room) a medida que se genera; una pantalla nueva
    lista las conversaciones pasadas (tier, título, fecha, cantidad de
    mensajes) para reabrirlas en Chat o borrarlas.
11. **Catálogo de modelos en el Tier B**: MediaPipe no está atado a un solo
    `.task` — hay un selector (chips) para elegir entre Gemma 3 1B y
    Gemma 2 2B, cada uno con su propia descarga/progreso.

## Arquitectura (multi-módulo, clean architecture)

La app está dividida en módulos Gradle por capa (convention plugins en
`build-logic/`, ver `gradle/libs.versions.toml` para versiones):

- **`core:common`** — utilidades sin UI (`pushAndFree` para Navigation 3,
  heurísticas de RAM/conectividad).
- **`core:ui`** — tema/colores del panel de instrumentos (`DemoTheme`).
- **`core:designsystem`** — composables compartidos entre pantallas
  (`TierChannelRow`, `MessageBubble`, `ImageAttachRow`, breakpoints de tamaño
  de pantalla) y el mapeo tier → color/label/mensajes.
- **`domain:models`** — `ChatMessage`/`Role`, `GenerationConfig`,
  `TierCapabilities`, `GenerationMetric`, `PromptPreset`, `DemoState`,
  `InferenceTier`/`TierAvailability`, `InferenceBackend` (el contrato común a
  los 3 tiers), `Conversation`, `MediaPipeModelOption`.
- **`domain:usescases:inference`** / **`domain:usescases:history`** — un caso
  de uso por acción (`GenerateUseCase`, `SelectTierUseCase`,
  `LoadConversationUseCase`, etc.), la única frontera que `feature:*` puede
  cruzar hacia la capa de datos.
- **`data:mlkitnano`** / **`data:mediapipe`** / **`data:cloudgemini`** — un
  backend por tier, cada uno implementando `InferenceBackend`.
- **`data:repositories`** — `InferenceRepository` (`@Singleton`): única
  fuente de verdad compartida por todas las pantallas (reemplaza al viejo
  `InferenceOrchestratorViewModel`), orquesta fallback automático,
  `generate()`/`generateCompare()`/`runBenchmark()`, `exportMetricsCsv()`, y
  persiste cada turno vía `data:history`.
- **`data:history`** — base de datos Room (`conversations`/`messages`) detrás
  de `IHistoryRepository`.
- **`feature:chat`** / **`compare`** / **`config`** / **`device`** /
  **`stats`** / **`history`** — una pantalla + su propio `@HiltViewModel` cada
  una.
- **`feature:main`** — shell de navegación adaptativo sobre Navigation 3
  (`NavigationBar`/`NavigationRail` según tamaño de pantalla).
- **`:app`** — solo `Application` (`@HiltAndroidApp`) y `MainActivity`
  (`@AndroidEntryPoint`).

## Qué soporta cada tier

| | Tier A (Nano) | Tier B (MediaPipe) | Tier C (Cloud) |
|---|---|---|---|
| temperature / top-K | ✅ | ✅ (solo se fijan al iniciar la conversación) | ✅ |
| top-P | ❌ (no existe en esta API) | ✅ (solo al iniciar) | ✅ |
| max output tokens | ✅ | ❌ (es un presupuesto fijo del motor, no por turno) | ✅ |
| system prompt | ✅ (Nano V3+) | ✅ (como primer turno interno, no template formal) | ✅ |
| multi-turno | ✅ | ✅ (sesión con estado propio) | ✅ |
| imágenes | ✅ | ❌ (el modelo hosteado no tiene encoder de visión) | ✅ |
| tokens reales en stats | ❌ (aproximado) | ✅ (`sizeInTokens()`) | ✅ (`usageMetadata`) |

Ver qué soporta cada tier es en sí mismo parte del estudio — por eso la UI
deshabilita en vez de ocultar los controles no soportados.

## Ver qué está pasando (Logcat)

Todo el flujo de decisión está logueado: qué tier se prueba, por qué queda
disponible/no disponible, cuándo empieza/termina una generación y cualquier
excepción con su stacktrace completo. En Android Studio, pestaña **Logcat**,
filtrá por tag:

```
tag:NanoDemo
```

(o `tag:NanoDemo.MlKitNano`, `tag:NanoDemo.MediaPipe`, `tag:NanoDemo.CloudGemini`
para ver solo un backend). Los chunks de streaming se loguean en nivel
`VERBOSE`, así que subí el nivel mínimo del filtro si no los ves.

## Cómo probarlo

1. Abre la carpeta del proyecto con Android Studio (versión reciente).
   Android Studio generará el resto del Gradle Wrapper si falta (`gradlew`);
   si te lo pide, acepta.
2. Sincroniza el proyecto (Gradle descargará las dependencias — necesita red).
3. **Configura la API key del Tier C** (opcional, pero sin ella ese tier
   queda marcado como no disponible): consigue una key gratis en
   https://aistudio.google.com/apikey y pégala en `local.properties`:
   ```
   GEMINI_API_KEY=tu-key-aqui
   ```
   Este archivo ya está en `.gitignore` — la key nunca se sube al repo.
4. **Configura la descarga automática del modelo del Tier B** (opcional; sin
   esto, ese tier queda "no disponible" salvo que pongas el archivo a mano —
   ver más abajo).

   Ya está resuelto para este repo: los modelos del catálogo (ver más abajo)
   están subidos como assets de un
   [GitHub Release](https://github.com/Sebas1705/nanodemo/releases/tag/models-v1)
   de este mismo repo. Como el repo es **privado**, hace falta un token para
   descargarlos:

   1. Creá un **Personal Access Token de grano fino** (solo lectura, limitado
      a este repo) en https://github.com/settings/tokens?type=beta →
      "Generate new token" → Repository access: "Only select repositories" →
      `nanodemo` → Repository permissions → **Contents: Read-only**. No le
      des ningún otro permiso.
   2. Pegalo en `local.properties`, junto con la URL de cada modelo que
      quieras habilitar (un modelo sin su URL queda marcado como no
      disponible, no crashea):
      ```
      MEDIAPIPE_MODEL_URL=https://api.github.com/repos/Sebas1705/nanodemo/releases/assets/566325429
      MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1=https://api.github.com/repos/Sebas1705/nanodemo/releases/assets/566784090
      MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2=https://api.github.com/repos/Sebas1705/nanodemo/releases/assets/566784089
      MEDIAPIPE_MODEL_TOKEN=tu-token-aqui
      ```
      (las URLs ya están puestas por defecto — solo falta el token.)
      **Gemma 2 2B pesa 2.71GB**, más que el límite de 2GB por asset de
      GitHub Releases, así que está partido en 2 (`PART1`/`PART2`) —
      `MediaPipeBackend` descarga ambas partes y las concatena en un solo
      `.task` antes de cargarlo.
   3. En la app, elegí el modelo con los chips de la pantalla Chat (solo
      visibles con el tier MediaPipe activo) y tocá **"Descargar modelo"** —
      la app lo baja sola mostrando el progreso (igual que el Tier A con
      Gemini Nano) y lo guarda en el almacenamiento propio de la app, por
      separado de los demás modelos del catálogo.

   Si en algún momento el repo pasa a ser público, cada `MEDIAPIPE_MODEL_URL*`
   puede apuntar directo al link público del release en vez del endpoint de
   la API, y `MEDIAPIPE_MODEL_TOKEN` puede quedar vacío.

   **Alternativa para pruebas locales sin storage propio**: podés poner el
   archivo directamente con `adb push` (sirve igual, la app solo comprueba
   si el archivo ya existe):
   ```
   adb push gemma3-1b-it-int4.task /sdcard/Android/data/com.example.geminifnanodemo/files/gemma-model.task
   ```
   Si usás Git Bash en Windows, cuidado con la conversión automática de
   rutas de MSYS — si `adb push` falla con `remote secure_mkdirs() failed`,
   corré `export MSYS_NO_PATHCONV=1` antes y pasá la ruta de origen en
   formato Windows (`cygpath -w ...`).
5. Ejecuta en un dispositivo físico. Para el Tier A necesitas un flagship
   2026 con Gemini Nano v3 (Pixel 10, Galaxy S26, OnePlus 15, Oppo Find X9,
   Motorola Signature — un Pixel 9 o un Galaxy Z Fold 7 se quedan fuera,
   tienen Nano v2). Para el Tier B, cualquier gama media con ~4GB+ de RAM.
   Para el Tier C, cualquier dispositivo con internet.

## Notas honestas sobre esta demo

- **Tier A** (`com.google.mlkit:genai-prompt:1.0.0-beta4`): en beta, su
  superficie puede cambiar. Los nombres de clase (`Generation`,
  `FeatureStatus`, `DownloadStatus`) y firmas (`checkStatus()`, `download()`,
  `generateContentStream()`) están verificados contra la documentación
  oficial de Google. Los **campos internos** de los subtipos de
  `DownloadStatus` no están publicados, así que el código deliberadamente no
  los usa — solo distingue "empezó / progresa / terminó / falló".
- **Tier B** (`com.google.mediapipe:tasks-genai:0.10.35`): Google marca esta
  API como **maintenance-only**, recomendando migrar a LiteRT-LM
  (`com.google.ai.edge.litertlm`) a futuro — ver
  https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android.
  Se usa aquí porque es la más documentada y estable hoy, pero si este
  proyecto sigue vivo un tiempo, vale la pena revisar si conviene migrar. Las
  firmas usadas (`LlmInference.createFromOptions`, `LlmInferenceOptions`,
  `generateResponseAsync(prompt, ProgressListener)`) están verificadas
  desinstalando el `.class` real del AAR (no solo contra la documentación).
  La descarga del modelo (`MediaPipeBackend.download()`) es un `HttpURLConnection`
  simple con progreso por `Content-Length`, sin dependencias nuevas — la
  licencia de Gemma en Hugging Face no se puede aceptar por API, por eso ese
  paso sigue siendo manual (una vez, del lado del desarrollador). El storage
  elegido es un asset de GitHub Release de este mismo repo (no requiere una
  cuenta de storage externa); al ser el repo privado, la descarga usa el
  endpoint `.../releases/assets/{id}` con `Accept: application/octet-stream`
  y un `Authorization: Bearer` con un token de grano fino de solo lectura
  (`MEDIAPIPE_MODEL_TOKEN`) — nunca el token amplio de `gh auth token`.
- **Tier C**: se probó primero con el SDK oficial
  `com.google.genai:google-genai-kotlin:1.1.0`, pero en un dispositivo real
  ese SDK **bloquea en runtime** construir un `Client` con API key en
  Android — lanza `IllegalStateException("SECURITY FATAL: ... please use
  Firebase AI Logic")` (`SecurityContext.kt` del propio SDK). Por eso este
  tier llama directo al REST API de Gemini
  (`generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent`,
  endpoint público y documentado) con `HttpURLConnection` + `org.json`, sin
  dependencias nuevas. Es el mismo patrón que ese bloqueo intenta evitar —
  aceptable para este demo local con la key fuera de git, pero **no para una
  app publicada**: para eso, migrar a Firebase AI Logic con App Check.
- **Verificado en dispositivo real**: los tres tiers llegaron a compilar y
  correr en un dispositivo físico durante el desarrollo (no solo en el
  entorno donde se escribió el código). El hallazgo del bloqueo de
  seguridad del Tier C salió justamente de los logs de una corrida real
  (ver sección de Logcat arriba) — otra razón para tener buen logging desde
  el principio en vez de asumir que "compila" significa "funciona".
- **Métricas nuevas**: `memoryDeltaBytes` es `Runtime.getRuntime()` heap
  usado antes/después de generar — una señal relativa entre tiers, **no**
  un profiler real ni una medición absoluta de consumo. Se descartó medir
  batería por llamada: no hay API pública para atribuir consumo a una sola
  invocación sin herramientas de profiling más invasivas, así que se
  documenta la limitación en vez de inventar un número.
- **Límite de MediaPipe**: `LlmInferenceSessionOptions` (temperature/topK/
  topP) se fija al crear la sesión, es decir, al primer mensaje de cada
  conversación — cambiar los sliders a mitad de charla no tiene efecto en
  ese tier hasta tocar "Nueva conversación". En Nano y Cloud (stateless) sí
  se aplican en cada turno.
- **Imágenes**: se usa el Photo Picker de Android (`ActivityResultContracts.PickVisualMedia`),
  que no pide permisos de almacenamiento.
- **Exportar CSV** usa un `FileProvider` (declarado en el manifest,
  `res/xml/file_paths.xml`) para compartir el archivo sin exponer una ruta
  de archivo cruda a otras apps.

## Fuentes usadas para este código

- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/ml-kit/genai/prompt/android/select-model
- https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/prompt/GenerativeModel
- https://developers.google.com/android/reference/com/google/mlkit/genai/common/DownloadStatus
- https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/common/FeatureStatus
- https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html
- https://9to5google.com/2026/05/15/gemini-intelligence-android-spec-requirements/ (requisitos de dispositivo)
- https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android (Tier B, incluye el aviso de maintenance-only)
- https://huggingface.co/litert-community (modelos Gemma en formato `.task`)
- https://ai.google.dev/api/generate-content (Tier C, REST API de Gemini usado directamente)
- https://firebase.google.com/docs/ai-logic (alternativa recomendada por Google para apps móviles públicas)
- https://aistudio.google.com/apikey (cómo conseguir la API key del Tier C)
