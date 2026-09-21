# Módulos de IA reutilizables

Guía para llevar cada motor de IA de nanodemo a otra app (o al template
`modular-features` de Templetry) sin arrastrar el resto del demo. Cada motor ya es un
módulo Gradle `data:*` que implementa un mismo contrato, así que la unidad de
reutilización es **contrato + un módulo por motor**.

> Estado: esto documenta lo que hay que copiar y qué cambia. **Nada se ha portado todavía
> al template**; la guía sale del código real de este repo (imports y `build.gradle.kts` de
> cada módulo), no de una suposición.

## 1. El contrato (se copia siempre)

Todos los motores dependen solo de estos tipos de `domain:models` y de un helper de
`core:common`:

| Tipo | Archivo | Para qué |
|---|---|---|
| `InferenceBackend` | `domain/models/InferenceBackend.kt` | Contrato: `capabilities`, `checkAvailability()`, `download()`, `generateContentStream()`, `resetConversation()`, `lastResponseTokens()`, `close()` |
| `ChatMessage`, `Role` | `ChatModels.kt` | Turno de conversación (texto + imagen opcional) |
| `GenerationConfig` | `ChatModels.kt` | temperature / top-K / top-P / max tokens / system prompt |
| `TierCapabilities` | `ChatModels.kt` | Qué soporta cada motor (para que la UI habilite controles) |
| `TierAvailability` | `InferenceTier.kt` | `AVAILABLE` / `DOWNLOADABLE` / `UNAVAILABLE` (el progreso de descarga va aparte, en `DemoState`) |
| `hasInternetConnection`, `hasAtLeastRamBytes`, `hasEnoughRamForMediaPipe` | `core/common/DeviceCapabilities.kt` | Comprobaciones de dispositivo que llaman `checkAvailability()` |

`InferenceTier` (enum), `MediaPipeModelOption` y el resto de `domain:models` **no** son
parte del contrato: son de la app demo (los motores no los importan, salvo MediaPipe con su
catálogo).

**Propuesta para el template**: crear un módulo `domain:ai` (o `core:ai`) con solo el
contrato y las funciones de dispositivo, y que cada `data:ai-*` dependa de él. Es lo que
evita que una app que solo quiere Gemini en la nube tenga que traerse Room, Compose o
Navigation.

## 2. Un módulo por motor

Todos se aplican con el convention plugin `buildlogic.android.data`, dependen de
`domain:models` (+ `core:common` si usan helpers de dispositivo) y **no** dependen de Hilt
ni de ninguna feature: se instancian a mano en `InferenceRepository`.

### `data:mlkitnano` — Tier A · ML Kit GenAI Prompt (Gemini Nano)

- **Dependencia**: `com.google.mlkit:genai-prompt:1.0.0-beta4` (beta).
- **Secretos / config**: ninguno.
- **Requisitos de dispositivo**: flagship con Gemini Nano v3 (AICore). Sin él,
  `checkAvailability()` devuelve `UNAVAILABLE`.
- **Particularidades**: stateless (reconstruye el contexto de todo el historial en cada
  turno); acepta imágenes; los subtipos de `DownloadStatus` no exponen campos públicos, por
  eso solo se distingue empezó/progresa/terminó/falló.
- **Al portarlo**: nada que cambiar salvo el paquete.

### `data:mediapipe` — Tier B · MediaPipe LLM Inference (Gemma local)

- **Dependencia**: `com.google.mediapipe:tasks-genai:0.10.35` (Google la marca
  *maintenance-only*; el sucesor es LiteRT-LM, ver Tier D).
- **Secretos / BuildConfig**: `MEDIAPIPE_MODEL_URL_GEMMA_3_1B`,
  `MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1/PART2`, `MEDIAPIPE_MODEL_TOKEN` (Doppler →
  `local.properties`; vacío = ese modelo `UNAVAILABLE`, no crashea).
- **Requisitos**: ≥ 4 GB de RAM (`hasEnoughRamForMediaPipe`).
- **Particularidades**: catálogo de modelos (`MediaPipeModelOption`, `selectModel()`);
  descarga con `HttpURLConnection` + progreso, con soporte de **modelo partido en 2 partes**
  (límite de 2 GB por asset de GitHub Releases) que se concatena a `.assembling` y se
  renombra; sesión con estado propio (temperature/top-K/top-P se fijan al crear la sesión);
  no soporta imágenes con el modelo hosteado.
- **Al portarlo**: si la app nueva solo usa un modelo, se puede simplificar el catálogo; el
  origen de descarga (GitHub Release privado + token de solo lectura) es una decisión de
  este demo, no del motor.

### `data:cloudgemini` — Tier C · Gemini REST directo

- **Dependencias**: ninguna (`HttpURLConnection` + `org.json` del SDK de Android).
- **Secretos**: `GEMINI_API_KEY`.
- **Particularidades**: existe porque el SDK `google-genai-kotlin` **lanza una excepción en
  runtime** si se construye un `Client` con API key en Android. La key queda embebida en el
  APK → válido para un demo, **no para una app publicada** (usar el Tier E con App Check).
- **Al portarlo**: si la app va a publicarse, portar solo `data:firebaseai`.

### `data:litertlm` — Tier D · LiteRT-LM + Gemma 4 E2B

- **Dependencia**: `com.google.ai.edge.litertlm:litertlm-android:0.17.1`.
- **Secretos**: `LITERTLM_MODEL_URL` opcional; por defecto apunta al repo público de
  Hugging Face (sin token).
- **Requisitos**: ≥ 6 GB de RAM nominales (`hasAtLeastRamBytes`, umbral ya descontado a
  4,5 GiB), dispositivo arm64; el modelo pesa ~2,6 GB.
- **Particularidades**: `Engine` residente (hay que liberarlo con `close()` al cambiar de
  motor — `InferenceRepository.residentEngineTiers`); backend CPU; texto + 1 imagen con
  fallback a solo texto si el motor de visión no arranca; el historial se siembra con
  `initialMessages` al abrir una conversación.
- **Sin verificar aún**: que el streaming de `sendMessageAsync` emita *deltas* (así está
  asumido) — falta probarlo en un dispositivo real.

### `data:firebaseai` — Tier E · Firebase AI Logic

- **Dependencia**: `com.google.firebase:firebase-ai:17.17.0`.
- **Secretos / BuildConfig**: `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`.
- **Requisitos de consola**: habilitar Firebase AI Logic (Gemini Developer API) en el
  proyecto de Firebase.
- **Particularidades**: **sin** plugin `google-services` ni `google-services.json`:
  `FirebaseApp.initializeApp(context, FirebaseOptions)` manual y perezoso
  (`ensureInitialized()`); modelo `gemini-3.5-flash`; se crea un `generativeModel` por turno.
  Firebase ya inicializado por el plugin en la app destino → quitar `ensureInitialized()`.
- **Al portarlo**: en el template ya hay un `FirebaseConventionPlugin` y un `FirebaseModule`
  en `:app`; usarlos en lugar de la inicialización manual.

### `data:history` — historial local (opcional)

Room 2.8.5 (`conversations` / `messages`) detrás de `IHistoryRepository`. Independiente de
los motores: solo conoce `ChatMessage` y `InferenceTier` por su nombre (los nombres del enum
se persisten, por eso el enum es *append-only*).

## 3. Cómo se conectan (`data:repositories`)

`InferenceRepository` (`@Singleton`) recibe un `Map<InferenceTier, InferenceBackend>` y es la
única pieza que conoce a los cinco motores: fallback automático de tier, cierre de motores
residentes, métricas, persistencia. Para una app que usa un solo motor **no hace falta**:
basta inyectar el `InferenceBackend` concreto desde un módulo Hilt.

```kotlin
@Module @InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton
    fun backend(@ApplicationContext c: Context): InferenceBackend = FirebaseAiBackend(c)
}
```

## 4. Receta de portado (un motor)

1. Copiar el contrato (sección 1) a `domain:ai` — o a `domain:models` del destino.
2. Copiar `data/<motor>/` completo y añadir `include(":data:<motor>")` en `settings.gradle.kts`.
3. Copiar las entradas del motor de `gradle/libs.versions.toml` (versión + librería).
4. Reescribir el paquete `es.sebas1705.nanodemo` → el de la app destino (incluye los
   `namespace` de los `build.gradle.kts`).
5. Copiar el helper `secret(...)` del `build.gradle.kts` raíz si el motor lee `BuildConfig`
   (orden: variable de entorno → `local.properties` → valor por defecto) y cargar los
   secretos en Doppler / `local.properties` (tabla de la sección 2).
6. Comprobar la **compatibilidad de toolchain**: este repo usa AGP 9 con Kotlin integrado,
   KSP y Hilt 2.60.1; el template está en Hilt 2.58 con kapt. Hilt 2.58 no encuentra la
   extensión de AGP 9 — si el destino sube a AGP 9, subir Hilt a ≥ 2.60.1.
7. Registrar el motor en el mapa de `InferenceRepository` (o inyectarlo directo).

## 5. Riesgos conocidos a la hora de portar

- Los módulos se compilan y arrancan, pero **los tiers D y E no se han ejecutado en un
  dispositivo real** todavía (solo compilación en CI).
- Las APIs de LiteRT-LM, ML Kit GenAI y Firebase AI están en beta / evolución rápida: los
  nombres usados se verificaron contra el `.aar` real de cada versión fijada, no contra un
  rango de versiones.
- `GEMINI_API_KEY` y `MEDIAPIPE_MODEL_TOKEN` acaban dentro del APK (ver
  [DISTRIBUTION.md](DISTRIBUTION.md#secretos-dentro-del-apk)).
