# Telemetría: analytics y errores

Cómo se observa la app (qué hacen los usuarios y qué falla) y cómo mantener **el mismo esquema en
todas las apps del template**, para reutilizar filtros, dashboards y consultas de BigQuery.

## Piezas

| Módulo | Qué aporta |
|---|---|
| `core:common` | Contratos `AnalyticsTracker` y `ErrorReporter` (sin Firebase), fachada `Telemetry`, nombres estándar (`TelemetryEvents`, `TelemetryParams`, `TelemetryResult`), contexto común (`TelemetryContextKeys`, `collectTelemetryContext`, `installId`) |
| `data:analytics` | `FirebaseAnalyticsTracker` (Firebase Analytics 23.2.0) |
| `data:crashlytics` | `CrashlyticsErrorReporter` (Firebase Crashlytics 20.1.1) |
| `data:firebasecore` | `FirebaseBootstrap`: arranque común de `FirebaseApp` |
| `:app` | Enlaza los proveedores, genera las resources de Firebase, inicializa en `Application.onCreate()` |

El código de la app solo conoce `Telemetry`. Cambiar de proveedor (o apagarlo) es cambiar el módulo
`data:*` que enlaza cada contrato; nada más se toca.

## Qué llega a cada sitio

`Telemetry.event()` hace dos cosas: manda el evento a Analytics **y** deja una miga de pan en el
registro de Crashlytics. Así, un crash o error no fatal muestra las últimas acciones del usuario
antes de ocurrir.

| Llamada | Analytics | Crashlytics |
|---|---|---|
| `event(name, params…)` | Evento `name` con parámetros | Miga `evento name k=v…` |
| `feature(nombre, params…)` | `feature_used` | Miga |
| `screen(nombre)` | `screen_view` | Clave `screen` + miga |
| `operation(nombre, resultado, ms, params…)` | `operation_result` | Miga |
| `failure(nombre, error, ms, params…)` | `operation_result` con `result=failure`, `error_type`, `error_message` | **Error no fatal** + atributos en el registro + clave `last_error` |
| `breadcrumb(texto)` | — | Miga |
| `setContext(clave, valor)` | — | Clave |
| `snapshotMemory()` | — | Claves `avail_mem_mb`, `low_memory`, `java_heap_mb` |

## Contexto común (igual en todas las apps)

Se adjunta al arrancar. En Crashlytics van todas como **claves** de cada reporte; a Analytics solo
van las marcadas como **propiedad de usuario**, porque Firebase ya recoge por su cuenta versión de
la app, modelo y sistema operativo.

| Clave | Ejemplo | Analytics |
|---|---|---|
| `app_id` | `es.sebas1705.nanodemo` | — |
| `app_version` / `version_code` | `1.0.42` / `42` | — (automático) |
| `build_type` | `debug` / `release` | propiedad |
| `installer` | `com.android.vending` / `sideload` | propiedad |
| `device` | `Google Pixel 10` | — (automático) |
| `android_sdk` | `36` | — (automático) |
| `abi` | `arm64-v8a` | propiedad |
| `ram_bucket` | `<=3gb`, `4gb`, `6gb`, `8gb`, `12gb`, `>=16gb` | propiedad |
| `locale` | `es-ES` | — (automático) |

Además, un **id de instalación** (UUID aleatorio, guardado en las preferencias de la app y borrado al
desinstalar) se manda como `userId` a Analytics y a Crashlytics. Permite seguir una misma instalación
entre eventos y crashes; no identifica a la persona ni al dispositivo.

Claves de estado que mantiene la app: `active_tier`, `mediapipe_model`, `screen`, `last_trim_level`,
`last_error`, y las de memoria de `snapshotMemory()`.

## Eventos genéricos (todas las apps)

| Evento | Parámetros | Cuándo |
|---|---|---|
| `screen_view` (estándar) | `screen_name` | Cambio de pantalla |
| `feature_used` | `feature` + los que aporte la app | El usuario usó una función |
| `operation_result` | `operation`, `result` (`success`/`failure`/`cancelled`), `duration_ms`, `error_type`, `error_message` (≤100 caracteres) | Terminó una operación con nombre |
| `memory_trim` | `level` (`running_low`, `running_critical`, `background`, `moderate`, `complete`…) | Android pide liberar memoria |

`memory_trim` es la mejor pista de por qué el sistema mata la app mientras hay un modelo cargado:
esas muertes **no generan reporte de crash**. `ui_hidden` (cada vez que la app pasa a segundo plano)
no se cuenta, solo queda como miga.

## Eventos propios de nanodemo

Definidos en `data/repositories/.../InferenceTelemetry.kt`, sobre los genéricos:

| Evento / valor | Parámetros |
|---|---|
| `tier_selected` | `tier`, `reason` (`auto` / `manual` / `none_available`) |
| `tier_availability` | `tier`, `model`, `availability` |
| `operation_result` con `operation=generate` | `tier`, `model`, `mode` (`chat`/`compare`), `turns`, `has_image`, `first_chunk_ms`, `prompt_chars`, `response_chars`, `response_tokens`, `tokens_exact`, `memory_delta_kb` |
| `operation_result` con `operation=download` | `tier`, `model`, `duration_ms` |
| `operation_result` con `operation=check_availability` / `export_csv` | `tier` / `turns` |
| `feature_used` con `feature=` `conversation_reset`, `conversation_open`, `mediapipe_model_select`, `compare_run` | `tier`, `model`, `turns` |

Las descargas dejan además migas de pan cada 25 %.

## Añadir eventos en una app nueva

1. Usa los nombres genéricos cuando encajen (`feature_used`, `operation_result`).
2. Los eventos propios van en un archivo de constantes del módulo de la app, no en `core:common`.
3. Nombres en `snake_case`, ≤ 40 caracteres, empezando por letra; sin prefijos `firebase_`, `google_`
   ni `ga_`. `FirebaseAnalyticsTracker` normaliza lo que se pase de estos límites en vez de perder el
   evento, pero conviene respetarlos desde el origen.
4. Máximo 25 parámetros por evento; valores de texto ≤ 100 caracteres (se recortan).
5. Prefiere pocos valores distintos (`tier`, `mode`), no texto libre: cada valor nuevo es una fila más
   en los informes.

## Privacidad

- **Nunca** prompts, respuestas, rutas ni URIs de imágenes en eventos, claves o migas.
- Sin ID de publicidad ni SSAID: el manifest desactiva su recolección y quita el permiso
  `AD_ID`.
- `error_message` se limita a 100 caracteres; el reporte completo a Crashlytics sí lleva la excepción.
- Apagar la recolección sin tocar código: `ANALYTICS_ENABLED=false` / `CRASHLYTICS_ENABLED=false` en
  Doppler o `local.properties` (útil en la config `dev`).

## Cómo funciona por dentro (verificado en el bytecode)

- **Analytics no lee `FirebaseOptions`**: toma el app id de la resource `google_app_id`
  (`Resources.getIdentifier("google_app_id", "string", …)` en `play-services-measurement`). Por eso
  `:app/build.gradle.kts` genera `google_app_id`, `google_api_key` y `project_id` con `resValue` desde
  los mismos secretos (lo que haría el plugin `google-services`). Sin esas resources, Analytics queda
  desactivado sin avisar.
- Con esas resources, `FirebaseInitProvider` crea la `FirebaseApp` **antes** de
  `Application.onCreate()`, y Crashlytics captura también los crashes del arranque.
  `FirebaseBootstrap` queda como respaldo idempotente.
- **Crashlytics sin plugin de Gradle**: necesita un *build id* que solo genera ese plugin; sin él lanza
  `IllegalStateException` y no arranca. Se evita con la resource
  `com.google.firebase.crashlytics.RequireBuildId=false`, válido mientras no haya R8/minify.

## Comprobar que llega

En la pestaña **Dispositivo → Diagnóstico** hay tres botones: evento de prueba, error no fatal de
prueba y crash de prueba.

- **Analytics en tiempo real**: activa DebugView para la app y mira
  [DebugView](https://console.firebase.google.com/project/nanodemo-79f3a/analytics/debugview):

  ```bash
  adb shell setprop debug.firebase.analytics.app es.sebas1705.nanodemo
  ```

  Sin DebugView los eventos tardan hasta unas horas en aparecer en los informes.
- **Crashlytics**: los no fatales llegan en unos minutos; el crash, al reabrir la app.
- El proyecto de Firebase necesita **Google Analytics habilitado** (Configuración del proyecto →
  Integraciones). Sin él, Analytics no recoge nada.

## Al llevarlo al template

Copiar `core:common` (los cinco archivos de telemetría), `data:firebasecore`, `data:analytics` y
`data:crashlytics`; el bloque de `resValue` y `buildFeatures.resValues` de `:app`; el manifest
(`AD_ID`, meta-data de Analytics); la resource `RequireBuildId` (solo si no se usa el plugin de
Crashlytics); y la llamada `telemetry.initialize()` en la `Application`. El template ya tiene su propio
`data:analytics` con `EventLog`/`UserProperty`: conviene migrarlo a este contrato para que todas las apps
compartan el esquema.
