#!/usr/bin/env bash
set -euo pipefail
# Splits gyango_pad_model_source/gemma-4-E2B-it.litertlm into five ~500 MiB files
# gyango_pack_base_llm_{0..4}/src/main/assets/models/pad_chunk_{0..4}.bin
# Run from repo root or via Gradle Exec (cwd = repo root).

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

SOURCE="$ROOT_DIR/gyango_pad_model_source/gemma-4-E2B-it.litertlm"
CHUNK_BYTES=$((500 * 1024 * 1024))
MAX_TOTAL_BYTES=$((5 * CHUNK_BYTES))

if [[ ! -f "$SOURCE" ]]; then
  echo "[ERROR] Missing $SOURCE — copy the full LiteRT bundle there." >&2
  exit 1
fi

SIZE="$(stat -f "%z" "$SOURCE" 2>/dev/null || stat -c "%s" "$SOURCE")"
if (( SIZE > MAX_TOTAL_BYTES )); then
  echo "[ERROR] Model is ${SIZE} bytes (> 5×500 MiB)." >&2
  exit 1
fi

TMP="$ROOT_DIR/build/tmp-pad-split"
rm -rf "$TMP"
mkdir -p "$TMP"

# Prefix for split(1) output segments (names vary by OS; collect in sorted order).
split -b "$CHUNK_BYTES" "$SOURCE" "$TMP/padseg_"

SEGMENTS=()
while IFS= read -r line; do
  [[ -n "$line" ]] && SEGMENTS+=("$line")
done < <(find "$TMP" -maxdepth 1 -name 'padseg_*' | LC_ALL=C sort)

COUNT="${#SEGMENTS[@]}"
if (( COUNT < 1 || COUNT > 5 )); then
  echo "[ERROR] split produced $COUNT segments (expected 1..5)." >&2
  exit 1
fi

for i in $(seq 0 4); do
  OUT_DIR="$ROOT_DIR/gyango_pack_base_llm_$i/src/main/assets/models"
  mkdir -p "$OUT_DIR"
  OUT_FILE="$OUT_DIR/pad_chunk_$i.bin"
  if (( i < COUNT )); then
    mv "${SEGMENTS[$i]}" "$OUT_FILE"
  else
    # Some AAB / asset-pack paths reject strictly zero-byte pack assets; keep a minimal placeholder.
    printf '\0' >"$OUT_FILE"
  fi
done

rm -rf "$TMP"
echo "[INFO] PAD split complete ($COUNT non-empty chunks + empty tail pads if any)."
