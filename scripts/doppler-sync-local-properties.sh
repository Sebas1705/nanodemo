#!/usr/bin/env bash
# Genera/actualiza local.properties (lo que lee Android Studio) con los secretos de Doppler.
#
# Uso:  scripts/doppler-sync-local-properties.sh [proyecto] [config]      (por defecto: nanodemo dev)
#
# - Solo toca las claves que usa el build (lista KEYS); no borra sdk.dir ni nada tuyo.
# - Si una clave no existe en Doppler, deja como está la que ya tuvieras en local.properties.
# - Las claves con valor en Doppler se reescriben en un bloque marcado al final del archivo
#   (y se borra la línea suelta anterior de esa clave, para no dejar duplicados).
# Desde la línea de comandos no hace falta esto: `doppler run -- ./gradlew ...` inyecta lo mismo
# como variables de entorno, que el build lee primero (ver `secret` en build.gradle.kts).
set -euo pipefail

PROJECT="${1:-nanodemo}"
CONFIG="${2:-dev}"
KEYS=(
  GEMINI_API_KEY
  FIREBASE_API_KEY FIREBASE_APP_ID FIREBASE_PROJECT_ID
  MEDIAPIPE_MODEL_TOKEN MEDIAPIPE_MODEL_URL
  MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1 MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2
  LITERTLM_MODEL_URL CRASHLYTICS_ENABLED ANALYTICS_ENABLED
)

cd "$(dirname "$0")/.."
command -v doppler >/dev/null || { echo "Falta la CLI de Doppler: winget install doppler.doppler" >&2; exit 1; }

touch local.properties
current="$(mktemp)"
block="$(mktemp)"
trap 'rm -f "$current" "$block"' EXIT

# Sacar el bloque generado la vez anterior.
sed '/^# >>> doppler/,/^# <<< doppler/d' local.properties > "$current"

synced=0
for key in "${KEYS[@]}"; do
  if value="$(doppler secrets get "$key" --plain -p "$PROJECT" -c "$CONFIG" 2>/dev/null)"; then
    value="${value%$'\r'}" # la CLI en Windows puede devolver CRLF
    if [ -n "$value" ]; then
      printf '%s=%s\n' "$key" "$value" >> "$block"
      sed -i "/^${key}=/d" "$current"
      synced=$((synced + 1))
      continue
    fi
  fi
  echo "  sin valor en Doppler ($PROJECT/$CONFIG): $key" >&2
done

{
  cat "$current"
  echo "# >>> doppler ($PROJECT/$CONFIG) — generado por scripts/doppler-sync-local-properties.sh, no editar a mano"
  cat "$block"
  echo "# <<< doppler"
} > local.properties

echo "local.properties actualizado: $synced clave(s) desde Doppler ($PROJECT/$CONFIG)."
