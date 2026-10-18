# Distribución del APK

Una sola vía, disparada solo por un tag `vX.Y.Z` (o el workflow a mano indicando la versión):
compila el APK release firmado (`.github/actions/build-release-apk`) y lo sube a Firebase App
Distribution con el `versionName` limpio (sin sufijo). **No se publica ninguna GitHub Release ni
artefacto**: el repo es público y el APK lleva claves reales compiladas (ver "Secretos dentro del
APK"), así que solo se reparte por Firebase a los testers.

| | |
|---|---|
| Workflow | `.github/workflows/release-apk.yml` |
| Disparador | tag `vX.Y.Z` / manual (con versión) |
| Resultado | El APK en Firebase App Distribution (grupo `testers`, o la variable `FIREBASE_TESTER_GROUP`). Sin GitHub Release |
| `versionName` | la versión del tag, sin sufijo |
| `versionCode` | `<run_number>` |
| Secretos extra | `FIREBASE_SERVICE_ACCOUNT_JSON` |

Antes había una segunda vía (`distribute.yml`) que subía un build a Firebase en cada push a
`master`, con `versionName` `<último tag>-dev.<run_number>` — se sacó a propósito: solo lo que
se decide marcar con un tag llega a los testers, nunca un build de paso sin versión.

## Secretos en Doppler

En GitHub solo hace falta un secreto, `DOPPLER_TOKEN` (service token de solo lectura de
`nanodemo/prd`). El resto sale de Doppler:

| Secreto | Uso |
|---|---|
| `GEMINI_API_KEY`, `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `MEDIAPIPE_*` | Se compilan en `BuildConfig` (tiers B, C, E) |
| `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | Firma del APK |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Paso de Firebase App Distribution |

La acción compuesta comprueba antes de compilar que existan todos y falla con el nombre
exacto del que falta.

### Keystore fijo (una vez)

Sin keystore fijo cada runner firmaría con una key de debug distinta y nadie podría
actualizar la app sin desinstalar.

```powershell
& "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1\bin\keytool.exe" -genkeypair -v -keystore nanodemo-release.jks -alias nanodemo -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("nanodemo-release.jks")) | doppler secrets set SIGNING_KEYSTORE_BASE64 -p nanodemo -c prd
```

`*.jks` y `*.keystore` están en `.gitignore`. Guardá una copia del `.jks` **fuera del repo**:
si se pierde, no se puede regenerar la misma firma. Ojo al pasar contraseñas por pipe en
PowerShell 5.1: añade `\r\n` al final; conviene comprobar la longitud del secreto guardado.

## Firebase App Distribution

1. **Cuenta de servicio** en Google Cloud (proyecto de Firebase) con el rol
   **Firebase App Distribution Admin**; clave JSON → `FIREBASE_SERVICE_ACCOUNT_JSON`. No usar
   la cuenta del Admin SDK (permisos más amplios de lo necesario).
2. **App Distribution** habilitado en la consola de Firebase y un **grupo de testers** con
   alias `testers` (o cualquier otro, con la variable de repo `FIREBASE_TESTER_GROUP`).
3. `DOPPLER_TOKEN` en GitHub (Settings → Secrets and variables → Actions).

## Publicar una versión

```bash
git tag v1.2.0
git push origin v1.2.0
```

o Actions → **Release APK** → *Run workflow* con `1.2.0` (no crea el tag: solo compila y
distribuye; el tag lo pones tú si quieres marcar el commit). El workflow valida el formato
(`X.Y.Z` o `X.Y.Z-sufijo`), rechaza una versión cuyo tag ya existe y sube el APK a Firebase
App Distribution con `versionName` = la versión, sin sufijo `-dev`.

Requiere `DOPPLER_TOKEN` y `FIREBASE_SERVICE_ACCOUNT_JSON`; el workflow solo pide permiso
`contents: read`. El tag `models-v1` (modelos de MediaPipe) no dispara este workflow: el
patrón es `v*`. Las GitHub Releases `v0.1.0`–`v0.9.0` se borraron al hacer público el repo (sus
APK llevaban claves reales); los tags se conservan.

## Secretos dentro del APK

`GEMINI_API_KEY`, `BRAVE_SEARCH_API_KEY`, `MEDIAPIPE_MODEL_TOKEN` y las claves de Firebase se
compilan en `BuildConfig`: cualquiera con el APK puede extraerlos. Doppler protege dónde viven
los secretos, no lo que acaba dentro del APK. Por eso el repo (público) **no** publica APK en
GitHub Releases ni como artefacto de Actions: el APK solo va a Firebase App Distribution, a
gente de confianza. Aun así, un tester puede extraer las claves: restríngelas en sus consolas
(Gemini/Firebase por app y paquete, topes de gasto) y rótalas si un APK se filtra. Como el repo
ya es público, `MEDIAPIPE_MODEL_TOKEN` (lectura de un repo privado) ya no hace falta para
descargar `models-v1` y conviene revocarlo.

## Llevarlo al template

Los dos archivos son independientes del contenido de la app (solo asumen un módulo `:app`
y Doppler):

```
.github/actions/build-release-apk/action.yml   # build + firma
.github/workflows/release-apk.yml              # build + distribución (Firebase)
```

Al copiarlos hay que cambiar: el nombre de la app (si lo usas en notas),
la lista de secretos que verifica la acción (`action.yml`) y el
`VERSION_CODE` / `VERSION_NAME` que lee `app/build.gradle.kts` (ver su cabecera). El template
ya tiene un `release.yml` propio para releases de la plantilla (`vYYYY.MM.PATCH` + CHANGELOG):
no se pisa, el APK usa `release-apk.yml`. Por eso sus tags también coinciden con `v*`; si el
template publica tags de plantilla y de APK en el mismo repo, hay que separar los patrones
(p. ej. `app-v*` para el APK).
