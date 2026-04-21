# GyanGo

**Gyan on the go** — GyanGo is an offline-first, on-device AI tutor designed for kids and curious learners.  
It focuses on safe, age-aware explanations, voice-first interaction, and personalized learning support without requiring an internet connection for core usage.

## What This Project Is About

GyanGo aims to put a private, child-friendly learning assistant on every supported device:

- Works on-device for low-latency, private interactions
- Adapts explanation style to the learner's profile
- Keeps responses kid-safe with guardrails
- Encourages curiosity without academic pressure

## Current Features

- **Offline-first inference** with on-device model runtime
- **Kids-safe mode** enabled by default with request/response guardrails
- **Age-aware responses** using optional birth profile details
- **Learner personalization**
  - Learning profile (Explorer / Thinker / Builder)
  - Subject comfort bands
  - Optional low-pressure check-in flow
- **Voice and text chat** with language-aware response handling
- **Subject-focused tiles** (Curiosity, Math, Science, Coding, etc.) with icons
- **Hint reveal UX** (`How did I think?`) to show reasoning only when needed
- **Feedback flow** via prefilled email compose action
- **PAD-style model preparation flow** for setup/download UX

## Product Principles

- **Safety first for children**
- **Curiosity over exam pressure**
- **Simple explanations first, then guided follow-up**
- **Privacy by design** (on-device as default)

## High-Level Roadmap (Pipeline)

### 1) Specialized Trained Adapters

- Add domain adapters/fine-tuned heads for specific age bands and subjects
- Route by subject + learner profile + safety policy
- Improve answer quality while keeping base model lightweight

### 2) Knowledge Expansion Beyond School Core

- Extend structured knowledge support into:
  - **Engineering foundations**
  - **Medical and life sciences foundations**
- Keep domain expansion child-safe and level-aware

### 3) Lighter Models With Better Knowledge Utility

- Build/ship lighter model variants with better prompt grounding and retrieval quality
- Improve response quality-per-parameter rather than only scaling model size
- Optimize for lower memory devices and faster first-token latency

### 4) Guardrails and Trust Enhancements

- Expand policy coverage for subtle unsafe phrasing
- Add stronger parent-facing controls and safety lock options
- Improve moderation transparency (why an answer was redirected/blocked)

### 5) Personalization Improvements

- Better ongoing skill inference from interaction patterns
- Adaptive follow-up strategies by learner profile and topic confidence
- Optional parent summary view (local-first)

### 6) UX and Accessibility

- Refine microcopy for younger readers
- Improve hinting/scaffolding behavior in difficult topics
- Continue voice quality and multilingual improvements

## Future Ideas Under Consideration

- Hybrid local retrieval packs for stronger factual consistency
- Topic-specific mini curricula and exploration paths
- Better lightweight multilingual adapters
- Safer and clearer “explain simpler / challenge me” controls

## Status

This project is under active development with a strong focus on:

1. safe kids-first behavior,
2. practical offline usability,
3. robust personalization without pressure labels.

## License and third-party notices

- **Application source:** proprietary — see [`LICENSE.txt`](LICENSE.txt) (Gyango; product name **GyanGo**).
- **Open-source & SDK attribution:** see [`legal/THIRD_PARTY_OPEN_SOURCE.md`](legal/THIRD_PARTY_OPEN_SOURCE.md) for direct dependencies, Gemma / LiteRT-LM notes, ML Kit terms pointers, and maintenance guidance.
- **Privacy policy:** see [`legal/PRIVACY_POLICY.md`](legal/PRIVACY_POLICY.md) and web-ready [`legal/privacy-policy.html`](legal/privacy-policy.html) for Play Console publishing.
- **Short index:** [`NOTICE`](NOTICE) at the repo root.
- **Shipped in the APK:** `app/src/main/assets/licenses/` contains `Apache-2.0.txt` and a copy of the third-party document for offline reference (`README.txt` in that folder explains the bundle).

When you upgrade Gradle dependencies or change model sources, update the third-party document and its asset copy together.
