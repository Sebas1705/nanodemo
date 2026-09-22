# Distribución del APK

Una sola vía, disparada solo por un tag `vX.Y.Z` (o el workflow a mano indicando la versión):
compila el APK release firmado (`.github/actions/build-release-apk`), crea una GitHub Release
con el APK adjunto, su `.sha256` y notas generadas, y sube ese mismo APK a Firebase App
Distribution con el `versionName` limpio (sin sufijo).

| | |
|---|---|
| Workflow | `.github/workflows/release-apk.yml` |
| Disparador | tag `vX.Y.Z` / manual (con versión) |
| Resultado | GitHub Release con el APK, su `.sha256` y notas generadas — y la misma release en Firebase App Distribution |
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

o Actions → **Release APK** → *Run workflow* con `1.2.0` (crea el tag sobre el commit del
run). Versiones con sufijo (`1.2.0-beta.1`) se publican como *pre-release*. El workflow
valida el formato, rechaza una versión que ya tiene release y adjunta
`AIMobiDemo-vX.Y.Z.apk` y `AIMobiDemo-vX.Y.Z.apk.sha256`. Además sube ese mismo APK a Firebase
App Distribution con `versionName` = la versión del tag, sin sufijo `-dev`.

Requiere `DOPPLER_TOKEN`, `FIREBASE_SERVICE_ACCOUNT_JSON` y permiso `contents: write` (ya
declarado en el workflow). El tag `models-v1` (modelos de MediaPipe) no dispara este workflow:
el patrón es `v*`.

## Secretos dentro del APK

`GEMINI_API_KEY` y `MEDIAPIPE_MODEL_TOKEN` se compilan en `BuildConfig`: cualquiera con el
APK puede extraerlos, y el token de MediaPipe da lectura al repo privado. Repartir solo a
gente de confianza, o sacar esos secretos del build (p. ej. modelos en un origen público,
como el Tier D). Doppler protege dónde viven los secretos, no lo que acaba dentro del APK. En
una release **pública** de GitHub esto es más grave que en Firebase: el APK queda descargable
para cualquiera con acceso al repo.

## Llevarlo al template

Los dos archivos son independientes del contenido de la app (solo asumen un módulo `:app`
y Doppler):

```
.github/actions/build-release-apk/action.yml   # build + firma
.github/workflows/release-apk.yml              # release + distribución
```

Al copiarlos hay que cambiar: el nombre `AIMobiDemo` en el nombre del asset
(`release-apk.yml`), la lista de secretos que verifica la acción (`action.yml`) y el
`VERSION_CODE` / `VERSION_NAME` que lee `app/build.gradle.kts` (ver su cabecera). El template
ya tiene un `release.yml` propio para releases de la plantilla (`vYYYY.MM.PATCH` + CHANGELOG):
no se pisa, el APK usa `release-apk.yml`. Por eso sus tags también coinciden con `v*`; si el
template publica tags de plantilla y de APK en el mismo repo, hay que separar los patrones
(p. ej. `app-v*` para el APK).
