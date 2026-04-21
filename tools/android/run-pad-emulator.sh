#!/usr/bin/env bash
set -euo pipefail

# Builds PAD bundle, installs local-testing APKS on emulator, launches app.
# Run from repo root:
#   ./tools/android/run-pad-emulator.sh
#
# Optional env overrides:
#   Huge logcat (full prompts, PAD progress, LiteRT perf): add -Pgyango.verboseLogs=true to your ./gradlew
#   invocation (default is off for pad and localDev).
#   APP_ID=ai.gyango
#   GRADLE_TASK=:app:bundlePadDebug
#   GYANGO_BUILD_TARGET=device  — use gradle/device.properties (match Play / physical device)
#   AAB_PATH=app/build/outputs/bundle/padDebug/app-pad-debug.aab
#   APKS_PATH=app/build/outputs/bundle/padDebug/app-pad-debug-local.apks
#   MODEL_PATH=gyango_pad_model_source/gemma-4-E2B-it.litertlm   (full bundle; Gradle splits into 5 PAD chunks)
#   BUNDLETOOL_JAR=/absolute/path/to/bundletool-all.jar   (if `bundletool` is not in PATH)
#   BUILD_APKS_MODE=default|auto|universal   (default: default — split APKs; universal breaks install-apks with PAD)
#   SKIP_APP_BUILD_CLEAN=1  — skip deleting app/build before Gradle (default: remove app/build each run to drop stale intermediates)

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

APP_ID="${APP_ID:-ai.gyango}"
GRADLE_TASK="${GRADLE_TASK:-:app:bundlePadDebug}"
AAB_PATH="${AAB_PATH:-app/build/outputs/bundle/padDebug/app-pad-debug.aab}"
APKS_PATH="${APKS_PATH:-app/build/outputs/bundle/padDebug/app-pad-debug-local.apks}"
MODEL_PATH="${MODEL_PATH:-gyango_pad_model_source/gemma-4-E2B-it.litertlm}"
# Split APKs (default) are required for local-testing install-apks with on-demand PAD packs.
# Universal mode produces one fat APK; bundletool then fails with:
#   Cannot restrict modules when the device matches a non-split APK.
BUILD_APKS_MODE="${BUILD_APKS_MODE:-default}"

# Loads gradle/emulator.properties (LiteRT AVD mitigations, etc.). Override with GYANGO_BUILD_TARGET=device.
GYANGO_BUILD_TARGET="${GYANGO_BUILD_TARGET:-emulator}"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Missing required command: $1" >&2
    exit 1
  fi
}

need_cmd adb

if command -v bundletool >/dev/null 2>&1; then
  BUNDLETOOL_CMD=(bundletool)
else
  need_cmd java
  if [[ -z "${BUNDLETOOL_JAR:-}" || ! -f "${BUNDLETOOL_JAR:-}" ]]; then
    echo "[ERROR] bundletool is not in PATH and BUNDLETOOL_JAR is not set to a valid file." >&2
    echo "        Example: BUNDLETOOL_JAR=\$HOME/bin/bundletool-all.jar ./tools/android/run-pad-emulator.sh" >&2
    exit 1
  fi
  BUNDLETOOL_CMD=(java -jar "$BUNDLETOOL_JAR")
fi

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "[ERROR] PAD model source missing: $MODEL_PATH" >&2
  echo "        Copy the full gemma-4-E2B-it.litertlm into gyango_pad_model_source/ (see gyango_pad_model_source/README.txt)." >&2
  echo "        Gradle splits it into five ~500 MiB on-demand packs at build time." >&2
  exit 1
fi
MODEL_SIZE_BYTES="$(stat -f "%z" "$MODEL_PATH")"
echo "[INFO] PAD model source size: ${MODEL_SIZE_BYTES} bytes (split into 5 packs by Gradle)"
MAX_PAD_SOURCE_BYTES=$((5 * 500 * 1024 * 1024))
if (( MODEL_SIZE_BYTES > MAX_PAD_SOURCE_BYTES )); then
  echo "[ERROR] Model is larger than 5×500 MiB (${MAX_PAD_SOURCE_BYTES} bytes). Add more packs or raise chunk layout." >&2
  exit 1
fi

EMULATOR_ID="$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ {print $1; exit}')"
if [[ -z "$EMULATOR_ID" ]]; then
  echo "[ERROR] No running emulator detected. Start a Google Play emulator first." >&2
  adb devices
  exit 1
fi

echo "[INFO] Using emulator: $EMULATOR_ID"
echo "[INFO] Building bundle with task: $GRADLE_TASK (GYANGO_BUILD_TARGET=$GYANGO_BUILD_TARGET)"
if [[ "${SKIP_APP_BUILD_CLEAN:-0}" != "1" ]]; then
  echo "[INFO] Removing app/build (SKIP_APP_BUILD_CLEAN=1 to keep and speed up incremental builds)"
  rm -rf "$ROOT_DIR/app/build"
else
  echo "[INFO] Skipping app/build clean (SKIP_APP_BUILD_CLEAN=1)"
fi
if ! ./gradlew -Pgyango.buildTarget="$GYANGO_BUILD_TARGET" "$GRADLE_TASK"; then
  echo "[ERROR] Gradle failed on $GRADLE_TASK (often :app:signPadDebugBundle / bundletool)." >&2
  echo "        Try:  ./gradlew $GRADLE_TASK --stacktrace --no-configuration-cache" >&2
  echo "        JDK:  use JDK 17 for Gradle (java -version). Debug keystore: ~/.android/debug.keystore" >&2
  echo "        PAD chunks: ensure splitPadBaseModelChunks ran (see preBuild); tiny models still need pad_chunk_*.bin files." >&2
  exit 1
fi

if [[ ! -f "$AAB_PATH" ]]; then
  echo "[ERROR] AAB not found at expected path: $AAB_PATH" >&2
  echo "        Override with AAB_PATH=/path/to/file.aab" >&2
  exit 1
fi

mkdir -p "$(dirname "$APKS_PATH")"
rm -f "$APKS_PATH"

echo "[INFO] Building local-testing APKS: $APKS_PATH (requested mode=$BUILD_APKS_MODE)"
case "$BUILD_APKS_MODE" in
  auto|universal|default) ;;
  *)
    echo "[ERROR] BUILD_APKS_MODE must be 'default', 'auto', or 'universal', got: $BUILD_APKS_MODE" >&2
    exit 1
    ;;
esac
RESOLVED_BUILD_APKS_MODE="$BUILD_APKS_MODE"
if [[ "$RESOLVED_BUILD_APKS_MODE" == "universal" ]]; then
  echo "[ERROR] build-apks --mode=universal is not compatible with install-apks for this app (PAD / asset-pack splits)." >&2
  echo "        Use default (split) mode, e.g.  BUILD_APKS_MODE=default  ./tools/android/run-pad-emulator.sh" >&2
  exit 1
fi
if [[ "$RESOLVED_BUILD_APKS_MODE" == "auto" ]]; then
  RESOLVED_BUILD_APKS_MODE="default"
  echo "[INFO] BUILD_APKS_MODE=auto resolved to default (split APKs for PAD local install)."
fi
echo "[INFO] Using build-apks mode: $RESOLVED_BUILD_APKS_MODE"
"${BUNDLETOOL_CMD[@]}" build-apks \
  --bundle "$AAB_PATH" \
  --output "$APKS_PATH" \
  --local-testing \
  --mode="$RESOLVED_BUILD_APKS_MODE"

echo "[INFO] Uninstalling old app (if present): $APP_ID"
adb -s "$EMULATOR_ID" uninstall "$APP_ID" >/dev/null 2>&1 || true

echo "[INFO] Installing APKS on emulator"
"${BUNDLETOOL_CMD[@]}" install-apks --apks "$APKS_PATH" --device-id="$EMULATOR_ID"

echo "[INFO] Launching app: $APP_ID"
adb -s "$EMULATOR_ID" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null

echo ""
echo "[DONE] PAD local-testing install complete."
echo "       To inspect PAD/model logs:"
echo "       adb -s $EMULATOR_ID logcat | rg 'GyangoPadDelivery|PAD pack|LiteRt|model'"
