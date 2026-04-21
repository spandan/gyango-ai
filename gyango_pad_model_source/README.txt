PAD multi-pack build input
==========================

Place the full LiteRT bundle here as:

  gemma-4-E2B-it.litertlm

Before building any variant that includes Play Asset packs, Gradle runs
`splitPadBaseModelChunks`, which splits this file into five ~500 MiB chunks at:

  gyango_pack_base_llm_{0..4}/src/main/assets/models/pad_chunk_{0..4}.bin

The app fetches those five on-demand packs in order at runtime, then merges them
into internal storage for LiteRT.

Conversion / export checklist (performance + RAM)
================================================

- **Quantization:** ship **INT4** (or other fixed modest bit-width) weights for on-device Gemma.
  FP16 / FP32 bundles are much slower and can exhaust RAM on phones.

- **KV cache length:** when your converter exposes `kv_cache_max_len` (or equivalent static cache /
  context size), set it to **at least** the app’s sequence budget. Gyango uses
  `LlmDefaults.CONTEXT_LENGTH_TOKENS` (4096 in normal mode, 3072 in low-power) for
  `EngineConfig.maxNumTokens`. If `kv_cache_max_len` is smaller than the prompt + decode you need,
  the runtime may re-process context and feel very slow.

- **Decode length:** max new tokens are capped at `LlmDefaults.MAX_NEW_TOKENS_CAP` (768); keep
  cache / context comfortably above **longest prompt + max decode**.
