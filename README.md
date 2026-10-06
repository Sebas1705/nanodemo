# AIMobiDemo — demo de IA en móvil con 5 motores y fallback

Demo en Kotlin + Jetpack Compose que intenta cubrir **cualquier hardware**,
probando cinco motores de IA (tiers) en orden y cayendo automáticamente
al siguiente si el dispositivo (o la configuración) no soporta el anterior.
Cada motor es un módulo Gradle reutilizable — ver
[docs/AI_MODULES.md](docs/AI_MODULES.md) para llevarlos a otra app — y el APK
se distribuye con dos vías (Firebase App Distribution y GitHub Release) — ver
[docs/DISTRIBUTION.md](docs/DISTRIBUTION.md). Los tres primeros, de más a menos
exigente:

1. **Tier A — ML Kit GenAI Prompt API (Gemini Nano on-device vía AICore)**:
   la mejor calidad, pero solo en flagships 2026 con ≥12GB de RAM.
2. **Tier B — MediaPipe LLM Inference (Gemma local)**: on-device pero
   gestionado por la propia app, corre en gama media (~4GB+ RAM).
3. **Tier C — Gemini API en la nube**: fallback universal, funciona en
   cualquier dispositivo con internet, pero ya no es on-device.
4. **Tier D — LiteRT-LM + Gemma 4 E2B** (on-device): el runtime que Google
   recomienda como sucesor de MediaPipe LLM Inference. Soporta imágenes y
   guarda el historial al retomar una charla. Necesita ~6GB+ de RAM.
5. **Tier E — Firebase AI Logic** (nube): la forma recomendada por Google de
   llamar a Gemini desde una app móvil (contraste directo con el Tier C, que
   usa la REST API con la key embebida).

Los tiers D y E se agregaron para poder comparar los motores más nuevos de
Android; van al final del orden de fallback, así que no cambian qué tier se
elige por defecto. Para no acumular varios modelos en RAM, al cambiar de tier
se libera el motor de MediaPipe / LiteRT-LM del que dejás.

**Configuración de los tiers nuevos**

- **Tier D** no requiere nada: Gemma 4 E2B (~2.6 GB, Apache 2.0) se descarga
  directo de Hugging Face, sin login ni token, desde el botón "Descargar modelo".
  (`LITERTLM_MODEL_URL` en `local.properties` permite apuntar a otro modelo.)
- **Tier E** requiere un proyecto de Firebase: creá uno en
  https://console.firebase.google.com, habilitá **Firebase AI Logic** (con el
  proveedor "Gemini Developer API"), registrá una app Android con el paquete
  `es.sebas1705.nanodemo` y copiá estos tres valores (Configuración del
  proyecto → tu app) a `local.properties`:
  ```
  FIREBASE_API_KEY=...
  FIREBASE_APP_ID=1:1234567890:android:abcdef
  FIREBASE_PROJECT_ID=...
  ```
  No hace falta `google-services.json` ni el plugin de Gradle: la app inicializa
  Firebase a mano con esos valores. Sin ellos, el tier queda "no disponible".
  App Check (recomendado para producción) no está activado en este demo. El modo
  híbrido on-device de Firebase AI Logic no se incluyó (es experimental y termina
  usando Gemini Nano, que ya es el Tier A).

Es el proyecto de ejemplo que salió de la investigación sobre implementación
de agentes de IA en móvil (ver el proyecto "IA en móvil" en Claude, donde
está la comparativa completa entre estas APIs de Google y el Foundation
Models framework de Apple).

## Qué hace

1. Al abrir la app, prueba los tiers en orden (A → B → C → D → E) y se queda en
   el primero disponible o descargable.
2. Un selector compacto arriba del chat (píldora con el motor activo) abre una
   hoja para forzar cualquiera de los cinco manualmente, para comparar
   respuestas en el mismo dispositivo. El chat sigue el diseño de las apps de
   IA: mensajes del usuario en burbuja a la derecha, respuestas del modelo a
   todo el ancho con Markdown (negritas, listas, bloques de código con
   copiar), respuesta en streaming con cursor, sugerencias en la pantalla
   vacía y una barra de entrada con "+" para adjuntar imagen o link.
3. Si el tier activo necesita un paso previo (descargar el modelo de Gemini
   Nano, el `.task` de MediaPipe o el `.litertlm` de LiteRT-LM), lo muestra en
   pantalla con un botón para descargarlo — con progreso cuando se conoce — en
   vez de fallar en silencio.
4. Panel de **parámetros de generación** (temperature, top-K, top-P, max
   output tokens, system prompt) — cada control se deshabilita si el tier
   activo no lo soporta (ver tabla de capacidades más abajo). Además del
   system prompt propio de cada tier, un preámbulo fijo (`appContextPreamble()`,
   `core:common`) le cuenta al modelo qué es AIMobiDemo en los 5 tiers, para
   que pueda responder si le preguntan qué es o qué hace la app.
5. **Conversación multi-turno**: el prompt y la respuesta quedan en un
   transcript que se manda de contexto en el siguiente turno; "Nueva
   conversación" lo resetea (y cierra la sesión de MediaPipe, si aplica).
6. **Imágenes**: se puede adjuntar una foto (Photo Picker, sin permisos) al
   turno de usuario — soportado en Nano, Cloud, LiteRT-LM y Firebase AI, no
   en MediaPipe (el modelo que tenemos hosteado no tiene encoder de visión).
7. **Modo comparación**: un toggle que corre el mismo prompt en los 5 tiers
   a la vez, mostrando las respuestas lado a lado (en cuadrícula adaptativa).
8. **Prompts recomendados**: en Comparar, un desplegable con prompts de prueba
   (resumen, código, traducción, etc.) que se cargan en el campo para revisarlos
   y mandarlos a los 5 tiers a la vez.
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
12. **Modo JSON** (Parámetros): pide salida estructurada — Cloud y Firebase AI
    lo garantizan (`responseMimeType` de su API); Nano/MediaPipe/LiteRT-LM lo
    emulan por prompt (sin garantía sintáctica). En Chat se marca si la
    respuesta terminó siendo JSON válido o no, en los 5 tiers, para comparar
    qué tan bien lo cumple cada uno.
13. **Pegar un link**: adjuntás una URL en Chat y su contenido (texto, sin
    scripts/estilos) se manda de contexto al generar — funciona en los 5
    tiers, a diferencia de la búsqueda con grounding (pendiente, solo nube).
14. **Compartir desde otras apps**: el selector "Compartir" de Android abre
    la app en Chat con el prompt precargado.
15. **Costo estimado** (Estadísticas): en los tiers de nube, el costo
    aproximado en USD según el precio público de Gemini Flash — los
    on-device siempre cuestan \$0.
16. **Herramientas (function calling)**: toggle en Parámetros — el modelo
    puede pedir la hora actual en vez de inventarla, algo que genuinamente no
    sabe por su cuenta. Cloud y Firebase AI lo hacen nativo (`tools`/
    `functionDeclarations` de sus propias APIs); Nano/MediaPipe/LiteRT-LM lo
    emulan por prompt — una etiqueta de texto parseable en vez de una función
    tipada, menos confiable porque depende de que el modelo respete el
    formato pedido (ver `DemoTools.reactInstruction()`/`matchReactCall()`).
    En Chat se ve cuándo la usó, en los 5 tiers.
17. **Búsqueda en internet**: toggle en Parámetros — se busca antes de responder,
    para preguntas actuales fuera del corte de entrenamiento. Cloud/Firebase AI
    usan grounding nativo de su propia API (el modelo decide cuándo; en Chat se
    ven las consultas hechas y cuántas fuentes citó). Nano/MediaPipe/LiteRT-LM
    no tienen red propia, así que con el toggle activo la app **siempre** busca
    (Brave Search API, requiere `BRAVE_SEARCH_API_KEY`; sin eso el toggle queda
    deshabilitado en esos 3), lee un extracto de las 2 primeras páginas y arma el
    turno con los resultados + instrucciones de síntesis y formato
    (`augmentWithWebSearch`): una sola pasada con streaming, respuesta en
    Markdown que cita [1], [2] y termina con la lista de fuentes clickeable.
    Un chip "busqué en internet…" marca que hubo búsqueda. Distinto de "Pegar
    un link" (#13): acá la app elige qué buscar a partir de tu pregunta.
18. **Búsqueda semántica del historial** (Historial): buscá conversaciones
    pasadas por significado ("¿qué le pregunté sobre X?"), no por texto
    exacto — 100% on-device (MediaPipe TextEmbedder, un modelo aparte de
    ~6MB, nada sale del teléfono). Funciona sobre los 5 tiers por igual: es
    sobre el historial guardado, no sobre la generación.
19. **Uso y límites gratis** (Ajustes → Uso y límites): un contador local
    (`UsageQuota`, `core:common`) cuenta cada llamada a los servicios con
    cuota — Gemini en Cloud y en Firebase AI (por día) y Brave Search (por
    mes) — y, con el bloqueo activo, frena el servicio al llegar al límite en
    vez de dejar que se cobre de más. Los topes son editables: Google no
    publica el número exacto del plan gratis de Gemini (se ve en
    [AI Studio](https://aistudio.google.com/rate-limit); por defecto 20/día,
    lo que reportan fuentes externas para Gemini 3.5 Flash) y Brave da $5 de
    crédito mensual (~1000 búsquedas). Los tiers on-device no gastan cuota. Ojo:
    el grounding con Google Search de Cloud/Firebase AI **no** está en el plan
    gratis de Gemini. El contador es solo de esta app/dispositivo; no reemplaza
    los topes de gasto de las consolas.
20. **Ajustes que se guardan**: los parámetros de cada tier (temperature,
    top-K/P, system prompt, Modo JSON, Herramientas, Búsqueda) y el último
    motor que elegiste a mano sobreviven al cierre de la app
    (`SettingsStore`, SharedPreferences). Al abrir, se prueba primero ese
    motor y, si ya no anda (modelo borrado, sin red), se sigue con el orden
    A → E de siempre; el contador de uso ya se guardaba aparte.
21. **Métricas en cada respuesta** (Chat y Comparar): bajo la respuesta, una
    línea con ⚡ tiempo hasta el primer token, ⏱ total, tokens/s y tokens
    (`MetricsLine`, `core:designsystem`). El **tiempo de carga** del modelo
    (MediaPipe/LiteRT-LM cargan el modelo en la primera generación) se mide
    aparte (`loadTimeMs`, "carga X") y se **descuenta** de los tiempos, para que
    la primera respuesta no parezca más lenta que las siguientes
    (`excludeLoadTime`). Estadísticas muestra el promedio de carga y el CSV
    exportado trae la columna `loadTimeMs`.
22. **Misma configuración en todos los motores** (Parámetros → "Usar esta
    configuración en todos los motores"): copia los parámetros del tier activo a
    los demás, pero solo lo que cada uno soporta
    (`GenerationConfig.withSupportedSettingsFrom`). Cada tier sigue teniendo sus
    ajustes propios; esto es para comparar con parámetros iguales.
23. **Votar la mejor respuesta** (Comparar): tras una ronda, elegís cuál
    respondió mejor (se puede cambiar el voto). Estadísticas acumula victorias y
    porcentaje de victorias por motor (`VoteTally`), con botón para reiniciar.
24. **Medidor de contexto** (Chat, MediaPipe y LiteRT-LM): una barra "~620 / 1024
    tokens" sobre el campo de texto, en ámbar al 80% y rojo al llenarse. Es una
    estimación (~4 caracteres por token + system prompt + bloque de búsqueda por
    turno) porque el motor no expone su conteo. Hallazgo: el bloque de búsqueda
    normal (~850 tokens) no entra en los 1024 de MediaPipe, por eso en ese tier
    la búsqueda usa un modo **compacto** (3 resultados, descripciones de 160
    caracteres, ~280 tokens).

## Arquitectura (multi-módulo, clean architecture)

La app está dividida en módulos Gradle por capa (convention plugins en
`build-logic/`, ver `gradle/libs.versions.toml` para versiones):

- **`core:common`** — utilidades sin UI (`pushAndFree` para Navigation 3,
  heurísticas de RAM/conectividad) y la telemetría común: fachada `Telemetry`,
  contratos `AnalyticsTracker`/`ErrorReporter` y el esquema estándar de eventos.
- **`core:ui`** — tema/colores del panel de instrumentos (`DemoTheme`).
- **`core:designsystem`** — composables compartidos entre pantallas
  (`TierChannelRow`, `MessageBubble`, `ImageAttachRow`, breakpoints de tamaño
  de pantalla) y el mapeo tier → color/label/mensajes.
- **`domain:models`** — `ChatMessage`/`Role`, `GenerationConfig`,
  `TierCapabilities`, `GenerationMetric`, `PromptPreset`, `DemoState`,
  `InferenceTier`/`TierAvailability`, `InferenceBackend` (el contrato común a
  los 5 tiers), `Conversation`, `MediaPipeModelOption`.
- **`domain:usescases:inference`** / **`domain:usescases:history`** — un caso
  de uso por acción (`GenerateUseCase`, `SelectTierUseCase`,
  `LoadConversationUseCase`, etc.), la única frontera que `feature:*` puede
  cruzar hacia la capa de datos.
- **`data:mlkitnano`** / **`data:mediapipe`** / **`data:cloudgemini`** /
  **`data:litertlm`** / **`data:firebaseai`** — un backend por tier, cada uno
  implementando `InferenceBackend`.
- **`data:repositories`** — `InferenceRepository` (`@Singleton`): única
  fuente de verdad compartida por todas las pantallas (reemplaza al viejo
  `InferenceOrchestratorViewModel`), orquesta fallback automático,
  `generate()`/`generateCompare()`, `exportMetricsCsv()`, y
  persiste cada turno vía `data:history`.
- **`data:history`** — base de datos Room (`conversations`/`messages`/
  `conversation_embeddings`) detrás de `IHistoryRepository`, que también
  indexa cada conversación para la búsqueda semántica (ver `data:embeddings`).
- **`data:embeddings`** — `LocalEmbeddingService`: convierte texto en un
  vector on-device (MediaPipe TextEmbedder) para la búsqueda por significado
  del historial. Sin relación con los 5 tiers de chat.
- **`data:firebasecore`** / **`data:analytics`** / **`data:crashlytics`** —
  arranque común de Firebase (inicialización manual, sin `google-services`) y
  los proveedores de Analytics y Crashlytics, detrás de los contratos
  `AnalyticsTracker` y `ErrorReporter` de `core:common`.
- **`feature:chat`** / **`compare`** / **`config`** / **`device`** /
  **`stats`** / **`history`** — una pantalla + su propio `@HiltViewModel` cada
  una.
- **`feature:main`** — shell de navegación adaptativo sobre Navigation 3
  con menú lateral (drawer modal en teléfono, permanente en tablet) en vez de barra inferior:
  secciones, "Nueva conversación", conversaciones recientes y Ajustes.
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
| Modo JSON (salida estructurada) | ✅ (emulado por prompt) | ✅ (emulado por prompt) | ✅ (nativo, `responseMimeType`) |
| Herramientas (function calling) | ✅ (emulado por prompt) | ✅ (emulado por prompt) | ✅ (nativo, `DemoTools`, ver Parámetros) |
| Búsqueda en internet (grounding) | ✅ (emulado, requiere `BRAVE_SEARCH_API_KEY`) | ✅ (emulado, requiere `BRAVE_SEARCH_API_KEY`) | ✅ (nativo, `google_search`, ver Parámetros) |

Ver qué soporta cada tier es en sí mismo parte del estudio — por eso la UI
deshabilita en vez de ocultar los controles no soportados. La tabla cubre los
tiers A–C; las capacidades de D (LiteRT-LM) y E (Firebase AI Logic) las declara
cada backend en su `TierCapabilities` (`data/litertlm`, `data/firebaseai`) y
sus particularidades están en [docs/AI_MODULES.md](docs/AI_MODULES.md).

## Ver qué está pasando (Logcat)

Todo el flujo de decisión está logueado: qué tier se prueba, por qué queda
disponible/no disponible, cuándo empieza/termina una generación y cualquier
excepción con su stacktrace completo. En Android Studio, pestaña **Logcat**,
filtrá por tag:

```
tag:NanoDemo
```

(o `tag:NanoDemo.MlKitNano`, `tag:NanoDemo.MediaPipe`, `tag:NanoDemo.CloudGemini`,
`tag:NanoDemo.LiteRtLm`, `tag:NanoDemo.FirebaseAI` para ver solo un backend). Los chunks de streaming se loguean en nivel
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
   adb push gemma3-1b-it-int4.task /sdcard/Android/data/es.sebas1705.nanodemo/files/gemma-model.task
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
- **Tiers D y E**: compilan y el APK release se genera y distribuye desde CI, pero
  **todavía no se han ejecutado en un dispositivo real**. En el Tier D falta confirmar
  que el streaming de `sendMessageAsync` emite deltas (así está asumido); el Tier E
  necesita Firebase AI Logic habilitado en la consola (sin eso falla con un error de
  permisos, y a veces hace falta registrar el SHA-1 de la firma release).
- **Verificado en dispositivo real**: los tres primeros tiers llegaron a compilar y
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

## Secretos con Doppler

Todos los secretos y la config que se compilan en el APK (API keys, URLs de modelos,
ids de Firebase, y para CI el keystore y la cuenta de servicio) viven en un proyecto de
[Doppler](https://www.doppler.com) llamado `nanodemo`, con dos configs: **`dev`**
(local) y **`prd`** (CI / builds distribuidos).

| Secreto | Para qué |
|---|---|
| `GEMINI_API_KEY` | Tier C |
| `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID` | Tier E (y `FIREBASE_APP_ID` también sube a App Distribution) |
| `MEDIAPIPE_MODEL_TOKEN`, `MEDIAPIPE_MODEL_URL`, `MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1`, `MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2` | Tier B |
| `LITERTLM_MODEL_URL` (opcional) | Tier D, para apuntar a otro modelo |
| `BRAVE_SEARCH_API_KEY` (opcional) | Búsqueda en internet emulada de los tiers A/B/D (Brave Search API) — `TierCapabilities.webSearch` de esos 3 tiers refleja si está configurado: si falta, el toggle sale deshabilitado en vez de fallar en runtime |
| `CRASHLYTICS_ENABLED`, `ANALYTICS_ENABLED` (opcionales, por defecto `true`) | `false` apaga el envío a Crashlytics / Analytics (útil en `dev`) |
| `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | Solo CI (`prd`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Solo CI (`prd`) |

**Conseguir `BRAVE_SEARCH_API_KEY`** (opcional, solo para la búsqueda emulada de A/B/D): creá una
cuenta en el [Brave Search API dashboard](https://api-dashboard.search.brave.com/), suscribite a
un plan (pide tarjeta, con $5 de crédito mensual gratis — ~1000 consultas — y $5 cada 1000 de más;
conviene poner un tope de gasto en el dashboard para no llevarse una sorpresa) y generá una API
key. Se probó antes con Google Custom Search JSON API, pero Google discontinuó "buscar en toda la
web" para motores nuevos desde el 20/01/2026 (solo deja hasta 50 sitios fijos, no sirve para
grounding general). Sin `BRAVE_SEARCH_API_KEY`, el toggle de búsqueda queda deshabilitado en los
3 tiers locales, sin romper nada.

El build lee cada clave primero de una **variable de entorno**, después de
`local.properties`, y si no está queda vacía (el tier sale "no disponible"). Dos formas de
usarlo en local:

- **Línea de comandos**: `doppler setup` (una vez: proyecto `nanodemo`, config `dev`) y
  después `doppler run -- ./gradlew :app:assembleDebug`.
- **Android Studio** (no hereda el entorno de Doppler): `scripts/doppler-sync-local-properties.sh`
  escribe esas claves en `local.properties` (sin tocar `sdk.dir` ni lo demás). Volvé a
  correrlo cuando cambie un secreto.

`local.properties` a mano sigue funcionando igual que antes.

## Analytics y errores (Firebase Analytics + Crashlytics)

La app manda a Firebase **eventos de uso** (Analytics) y **crashes y errores manejados**
(Crashlytics), unidos por una misma fachada `Telemetry`: cada evento deja además una miga de
pan en el registro de Crashlytics, así que un crash muestra qué hacía el usuario justo antes.
Todo con un esquema común pensado para reutilizarse en todas las apps del template — nombres
de eventos, parámetros y contexto del dispositivo iguales en cada una. **Nunca** se envía el
texto de prompts ni respuestas, y no se recoge ID de publicidad. Esquema completo, eventos y
privacidad: **[docs/TELEMETRY.md](docs/TELEMETRY.md)**.

**Una sola vez, en la consola de Firebase**:
- **Crashlytics** → *Habilitar* (sin eso no aparecen los reportes).
- **Google Analytics** habilitado en el proyecto (Configuración → Integraciones); sin él,
  Analytics no recoge nada.

No hace falta plugin de Gradle ni `google-services.json`: el build genera las resources de
Firebase desde los secretos de Doppler.

**Comprobar que funciona**: pestaña *Dispositivo* → *Diagnóstico*, con tres botones:
- *Evento de analytics de prueba*: se ve al instante en
  [DebugView](https://console.firebase.google.com/project/nanodemo-79f3a/analytics/debugview) tras
  `adb shell setprop debug.firebase.analytics.app es.sebas1705.nanodemo`; en los informes tarda horas.
- *Error no fatal de prueba*: llega en unos minutos a Crashlytics → *No fatales*.
- *Forzar crash de prueba*: el reporte se envía **al reabrir** la app.

`ANALYTICS_ENABLED=false` / `CRASHLYTICS_ENABLED=false` (Doppler o `local.properties`) apagan
el envío. Límites conocidos: los crashes nativos (MediaPipe / LiteRT-LM) y las muertes por falta
de RAM no generan reporte; para estas últimas queda el evento `memory_trim` y las claves de
memoria como pista.

## Tests y CI

Los tests unitarios (JUnit, JVM, sin dispositivo) cubren la lógica pura: parseo de Markdown, armado
del system prompt y la herramienta de hora emulada, la consulta/limpieza de la búsqueda, el cálculo de
cuota, el agrupado por fecha del historial y la (de)serialización de los ajustes guardados.

```bash
./gradlew testDebugUnitTest            # todos los módulos
./gradlew :core:common:testDebugUnitTest
```

`.github/workflows/ci.yml` compila el APK debug y corre esos tests en cada push a `master` y en cada
pull request (sin Doppler ni secretos: las keys de los tiers son opcionales en el build). Es
independiente de `release-apk.yml`, que solo corre al empujar un tag y arma el APK firmado.

## Distribución

Una sola vía (build firmado por la acción compuesta `.github/actions/build-release-apk`,
secretos desde Doppler — en GitHub solo hace falta `DOPPLER_TOKEN`):

| Workflow | Cuándo corre | Resultado |
|---|---|---|
| `.github/workflows/release-apk.yml` | tag `vX.Y.Z` / manual con versión | GitHub Release con `AIMobiDemo-vX.Y.Z.apk`, su `.sha256` y notas generadas — y ese mismo APK también a Firebase App Distribution, con `versionName` sin sufijo |

```bash
git tag v1.2.0 && git push origin v1.2.0   # publica la GitHub Release y la sube a Firebase App Distribution
```

Solo lo que se marca con un tag llega a los testers — no hay build continuo sin versión en
cada push a `master` (ver `docs/DISTRIBUTION.md`).

`versionCode` sale del número de run de GitHub (`VERSION_CODE`), así cada build puede
instalarse encima del anterior; en local sigue en 1. La preparación de una sola vez
(keystore fijo, cuenta de servicio, grupo de testers, `DOPPLER_TOKEN`), los detalles de cada
vía y cómo llevarlas al template están en **[docs/DISTRIBUTION.md](docs/DISTRIBUTION.md)**.

**Ojo con los secretos dentro del APK**: `GEMINI_API_KEY` y `MEDIAPIPE_MODEL_TOKEN` se
compilan en `BuildConfig`, así que cualquiera con el APK puede extraerlos (el token de
MediaPipe da lectura al repo privado). Repartilo solo a gente de confianza.

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
