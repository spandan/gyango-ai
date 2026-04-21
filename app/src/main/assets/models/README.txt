Gyango model naming and file details

Canonical model filenames:
- Base model (LiteRT bundle): `gemma-4-E2B-it.litertlm`

Play Asset Delivery (five on-demand packs, ~500 MiB each):
- Put the full bundle at `gyango_pad_model_source/gemma-4-E2B-it.litertlm`
- Run a PAD build; Gradle task `splitPadBaseModelChunks` writes
  `gyango_pack_base_llm_{0..4}/src/main/assets/models/pad_chunk_{0..4}.bin`
- At runtime the app fetches packs `gyango_base_llm_0` … `_4` in order and merges them
  into app storage before LiteRT loads.

Local app asset path (optional for `localDev` only):
- `app/src/main/assets/models/gemma-4-E2B-it.litertlm`

Model details:
- Base model: Gemma 4 E2B instruction-tuned LiteRT model for on-device chat.

Adapter status:
- Adapter work is currently paused; the app ships base model only.

If you are choosing a single "model name" for the base model file, use:
- `gemma-4-E2B-it.litertlm`
