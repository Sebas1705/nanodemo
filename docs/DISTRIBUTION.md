# Distribución del APK

Hay **dos vías**, independientes, que comparten el mismo build firmado
(`.github/actions/build-release-apk`). Se puede usar una, la otra o las dos.

| | Vía 1 · Firebase App Distribution | Vía 2 · GitHub Release |
|---|---|---|
| Workflow | `.github/workflows/distribute.yml` | `.github/workflows/release-apk.yml` |
| Disparador | push a `master` / manual | tag `vX.Y.Z` / manual (con versión) |
| Resultado | Release en Firebase, aviso a los testers del grupo | GitHub Release con el APK, su `.sha256` y notas generadas |
| `versionName` | `<último tag>-dev.<run_number>` | la versión del tag |
| `versionCode` | `<run_number>` | `<run_number>` |
| Secretos extra | `FIREBASE_SERVICE_ACCOUNT_JSON` | ninguno |
| Cuándo usarla | Builds continuos para testers | Versiones nombradas y descargables |

Las dos vías comparten la misma nomenclatura de versión (la del tag más reciente `vX.Y.Z`): un
build de Firebase entre releases se ve, por ejemplo, como `1.1.1-dev.7` — mismo `1.1.1` que la
última GitHub Release, más un sufijo que marca que es un build continuo sin tag propio. Antes
`distribute.yml` ponía `1.0.<run_number>`, un número que no salía de ningún lado del proyecto y no
coincidía con lo que mostraba GitHub Releases para el mismo commit.

## Base común: secretos en Doppler

En GitHub solo hace falta un secreto, `DOPPLER_TOKEN` (service token de solo lectura de
`nanodemo/prd`). El resto sale de Doppler:

| Secreto | Uso |
|---|---|
| `GEMINI_API_KEY`, `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `MEDIAPIPE_*` | Se compilan en `BuildConfig` (tiers B, C, E) |
| `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | Firma del APK (las dos vías) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Solo vía 1 |

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

## Vía 1 · Firebase App Distribution

1. **Cuenta de servicio** en Google Cloud (proyecto de Firebase) con el rol
   **Firebase App Distribution Admin**; clave JSON → `FIREBASE_SERVICE_ACCOUNT_JSON`. No usar
   la cuenta del Admin SDK (permisos más amplios de lo necesario).
2. **App Distribution** habilitado en la consola de Firebase y un **grupo de testers** con
   alias `testers` (o cualquier otro, con la variable de repo `FIREBASE_TESTER_GROUP`).
3. `DOPPLER_TOKEN` en GitHub (Settings → Secrets and variables → Actions).

Cada push a `master` publica una release en Firebase y deja el APK 14 días como artefacto.

## Vía 2 · GitHub Release

Publicar una versión:

```bash
git tag v1.2.0
git push origin v1.2.0
```

o Actions → **Release APK** → *Run workflow* con `1.2.0` (crea el tag sobre el commit del
run). Versiones con sufijo (`1.2.0-beta.1`) se publican como *pre-release*. El workflow
valida el formato, rechaza una versión que ya tiene release y adjunta
`AIMobiDemo-vX.Y.Z.apk` y `AIMobiDemo-vX.Y.Z.apk.sha256`.

Requiere solo `DOPPLER_TOKEN` y permiso `contents: write` (ya declarado en el workflow).
El tag `models-v1` (modelos de MediaPipe) no dispara este workflow: el patrón es `v*`.

## Secretos dentro del APK

`GEMINI_API_KEY` y `MEDIAPIPE_MODEL_TOKEN` se compilan en `BuildConfig`: cualquiera con el
APK puede extraerlos, y el token de MediaPipe da lectura al repo privado. Repartir solo a
gente de confianza, o sacar esos secretos del build (p. ej. modelos en un origen público,
como el Tier D). Doppler protege dónde viven los secretos, no lo que acaba dentro del APK. En
una release **pública** de GitHub esto es más grave que en Firebase: el APK queda descargable
para cualquiera con acceso al repo.

## Llevarlo al template

Los tres archivos son independientes del contenido de la app (solo asumen un módulo `:app`
y Doppler):

```
.github/actions/build-release-apk/action.yml   # build + firma, común
.github/workflows/distribute.yml               # vía 1
.github/workflows/release-apk.yml              # vía 2
```

Al copiarlos hay que cambiar: el nombre `AIMobiDemo` en el nombre del asset
(`release-apk.yml`), la lista de secretos que verifica la acción (`action.yml`) y el
`VERSION_CODE` / `VERSION_NAME` que lee `app/build.gradle.kts` (ver su cabecera). El template
ya tiene un `release.yml` propio para releases de la plantilla (`vYYYY.MM.PATCH` + CHANGELOG):
no se pisa, el APK usa `release-apk.yml`. Por eso sus tags también coinciden con `v*`; si el
template publica tags de plantilla y de APK en el mismo repo, hay que separar los patrones
(p. ej. `app-v*` para el APK).
