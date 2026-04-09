#!/usr/bin/env bash
# Legacy (RWKV → GGUF for llama.cpp). The Gemma/LiteRT branch bundles .litertlm under assets/models/.
# Convert BlinkDL RWKV-7 World .pth (e.g. RWKV-x070-World-1.5B-v3-*.pth) to GGUF, then Q4_K.
# Prereqs: git, python3, torch, a built llama.cpp tree with ./llama-quantize
#
# 1) pth → F16 GGUF via MollySophia/rwkv-mobile (bundles RWKV7 layout expected by ggml-org/llama.cpp).
# 2) llama-quantize → Q4_K
#
# Usage:
#   export LLAMA_CPP_BUILD=~/src/llama.cpp/build/bin
#   ./tools/convert_rwkv_blinkdl_pth_to_q4k.sh /path/to/RWKV-x070-World-1.5B-v3-20250127-ctx4096.pth
set -euo pipefail

PTH="${1:-}"
if [[ -z "$PTH" || ! -f "$PTH" ]]; then
  echo "Usage: $0 /path/to/model.pth" >&2
  exit 1
fi

LLAMA_CPP_BUILD="${LLAMA_CPP_BUILD:-}"
if [[ -z "$LLAMA_CPP_BUILD" ]]; then
  echo "Set LLAMA_CPP_BUILD to the directory containing llama-quantize (e.g. llama.cpp/build/bin)" >&2
  exit 1
fi
QUANTIZE="$LLAMA_CPP_BUILD/llama-quantize"
if [[ ! -x "$QUANTIZE" ]]; then
  echo "Not found or not executable: $QUANTIZE" >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

RWKV_MOBILE="$WORKDIR/rwkv-mobile"
git clone --depth 1 https://github.com/MollySophia/rwkv-mobile.git "$RWKV_MOBILE"
CONVERTER="$RWKV_MOBILE/converter/convert_rwkv_pth_to_gguf.py"
VOCAB="$WORKDIR/rwkv_vocab_v20230424.txt"
curl -sL -o "$VOCAB" "https://huggingface.co/RWKV/v5-Eagle-7B-HF/resolve/main/rwkv_vocab_v20230424.txt"

STEM=$(basename "$PTH" .pth)
F16_OUT="$WORKDIR/${STEM}-f16.gguf"
Q4_OUT="$(dirname "$PTH")/${STEM}-Q4_K.gguf"

echo "==> F16 GGUF (large intermediate)"
python3 "$CONVERTER" "$PTH" "$VOCAB" --outtype f16 --outfile "$F16_OUT"

echo "==> Q4_K quantization -> $Q4_OUT"
"$QUANTIZE" "$F16_OUT" "$Q4_OUT" Q4_K

echo "Done. Copy for Android assets:"
echo "  app/src/main/assets/models/rwkv7-g1e-1.5b-Q4_K_M.gguf  <- $Q4_OUT (or rename to match ModelPathProvider)"
