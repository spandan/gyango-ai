# Third-party software and data notices

**Product:** GyanGo (application `ai.gyango`)  
**Repository:** gyango-ai  
**Last reviewed:** 2026-04-19  

This document lists **direct** open-source and SDK dependencies declared in Gradle, their **intended** SPDX or vendor license class, and links for **authoritative** terms. **Transitive** libraries ( pulled in by AndroidX, Google Play libraries, ML Kit, etc.) are mostly **Apache License 2.0** or similar permissive licenses from Google LLC, JetBrains, and the Android Open Source Project; see each artifact’s POM on Maven Central or Google’s Maven repository for the exact text bundled with that release.

> **Not legal advice.** Maintain this file when you upgrade dependencies or add modules. Regenerate a full tree with:  
> `./gradlew :app:dependencies --configuration padReleaseRuntimeClasspath`  
> (or `localDevReleaseRuntimeClasspath` for the other product flavor.)

---

## 1. Proprietary application code

| Component | License |
|-----------|---------|
| Gyango / GyanGo application source (excluding third-party blocks below) | **Proprietary** — see root `LICENSE.txt` |

---

## 2. On-device language model weights (Gemma / LiteRT-LM package)

| Component | Version / source | License | Notes |
|-----------|-------------------|---------|--------|
| Gemma 4 E2B IT (LiteRT-LM packaged weights) | As described in `LICENSE.txt` (Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`) | **Apache-2.0** | Full text shipped in-app at `assets/licenses/Apache-2.0.txt`. Upstream: Google DeepMind Gemma terms and HF model card. |

---

## 3. Google AI Edge — LiteRT-LM Android runtime

| Maven coordinates | Version (Gradle) | License (summary) |
|-------------------|------------------|-------------------|
| `com.google.ai.edge.litertlm:litertlm-android` | `0.10.0` | **Apache-2.0** (Google AI Edge; confirm in the AAR / Maven POM for your exact revision) |

**References:** [Google AI Edge](https://ai.google.dev/edge) · [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 4. Google ML Kit

| Maven coordinates | Version (Gradle) | License / terms |
|-------------------|------------------|-----------------|
| `com.google.mlkit:text-recognition` | `16.0.1` | **Google ML Kit / Play services–style terms** — not solely Apache-2.0; see Google’s ML Kit terms and release notes for the SDK and any **on-device model** blobs shipped with this dependency. |

**References:** [ML Kit terms](https://developers.google.com/ml-kit/terms) · [ML Kit documentation](https://developers.google.com/ml-kit)

---

## 5. Google Play — Play Asset Delivery

| Maven coordinates | Version (Gradle) | License (summary) |
|-------------------|------------------|-------------------|
| `com.google.android.play:asset-delivery-ktx` | `2.2.2` | **Apache-2.0** (typical for Play Core–family artifacts; verify in POM) |

**References:** [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery) · Maven artifact page for the version you ship.

---

## 6. Material Components for Android

| Maven coordinates | Version (Gradle) | License (summary) |
|-------------------|------------------|-------------------|
| `com.google.android.material:material` | `1.13.0` | **Apache-2.0** |

**Reference:** [Material Android repo](https://github.com/material-components/material-components-android)

---

## 7. JetBrains Kotlin and kotlinx libraries

| Maven coordinates | Version (Gradle) | License (summary) |
|-------------------|------------------|-------------------|
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.3.0` (Kotlin plugin) | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.8.1` / resolved `1.9.x` transitively | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.8.1` | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | `1.8.1` | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.6.3` | **Apache-2.0** |

**Reference:** [Kotlin GitHub](https://github.com/JetBrains/kotlin) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

---

## 8. AndroidX / Jetpack (Compose, Lifecycle, Navigation, Activity, DataStore, etc.)

| BOM / libraries (representative) | Version (Gradle) | License (summary) |
|----------------------------------|------------------|-------------------|
| `androidx.compose:compose-bom` | `2024.04.01` (resolves Compose `1.6.6`) | **Apache-2.0** |
| `androidx.activity:activity-compose` | `1.9.0` | **Apache-2.0** |
| `androidx.compose.ui:ui` | via BOM | **Apache-2.0** |
| `androidx.compose.material3:material3` | via BOM | **Apache-2.0** |
| `androidx.compose.material:material-icons-extended` | via BOM | **Apache-2.0** |
| `androidx.compose.ui:ui-tooling-preview` | via BOM | **Apache-2.0** |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.7.0` | **Apache-2.0** |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.7.0` | **Apache-2.0** |
| `androidx.navigation:navigation-compose` | `2.7.7` | **Apache-2.0** |
| `androidx.datastore:datastore-preferences` | `1.0.0` | **Apache-2.0** |

**Reference:** [Android Open Source Project — Apache 2.0](https://source.android.com/docs/setup/about/licenses)

---

## 9. PDFBox (Android port)

| Maven coordinates | Version (Gradle) | License (summary) |
|-------------------|------------------|-------------------|
| `com.tom-roush:pdfbox-android` | `2.0.27.0` | **Apache-2.0** (port of Apache PDFBox; see project `LICENSE` in the artifact source repository) |

**Reference:** [pdfbox-android](https://github.com/TomRoush/pdfbox-android)

---

## 10. Other notable transitive components (non-exhaustive)

Your dependency graph also pulls libraries such as **Guava** `listenablefuture`, **AndroidX Core**, **Emoji2**, **SavedState**, **Startup**, **ProfileInstaller**, **Annotations**, etc. These are overwhelmingly **Apache-2.0** or **Android Software Development Kit License**–style where Google distributes them. For a **shipping compliance** audit, export the full resolved graph for the exact variant you release and archive POMs / `NOTICE` files from each AAR as required.

---

## 11. Trademarks

- **Gemma** is a trademark of Google LLC.  
- **Android**, **Google Play**, **ML Kit**, and related marks are trademarks of Google LLC.  
- Use marks only in nominative/fair ways; do not imply endorsement.

---

## 12. Bundled license text in the APK

| Path in app assets | Purpose |
|---------------------|---------|
| `assets/licenses/Apache-2.0.txt` | Canonical Apache License, Version 2.0 text (model + many OSS components). |
| `assets/licenses/README.txt` | Index of shipped legal files. |
| `assets/licenses/THIRD_PARTY_OPEN_SOURCE.md` | Copy of this document for offline in-app display (sync with `legal/` when you edit). |

---

## 13. Maintenance checklist

- [ ] After any dependency version bump: re-run Gradle `dependencies` for **release** classpaths you ship.  
- [ ] If ML Kit or Play Services ships new on-device models: re-check Google’s terms for those blobs.  
- [ ] If you change the HF model revision: update `LICENSE.txt` + this file + checksum / manifest records.  
- [ ] Keep `assets/licenses/THIRD_PARTY_OPEN_SOURCE.md` in sync with `legal/THIRD_PARTY_OPEN_SOURCE.md`.
