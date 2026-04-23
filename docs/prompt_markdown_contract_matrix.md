# Prompt And Markdown Contract Matrix

This document is the canonical contract between:
- prompt generation (`PromptBuilder`, `TopicPromptFormats`),
- output parsing (`GyangoOutputEnvelope`, `AssistantParsingPipeline`),
- rendering (`AssistantTextPolisher`, `MarkwonAssistantMarkdown`).

## Global Contract

- Assistant reply shape:
  1) lesson prose in markdown,
  2) mandatory tail lines:
     - `STATE >> Goal:[TOPIC]; Next_Level:[N]`
     - `SPARKS >> q1||q2`
     - `SAVE >> Topic:TOPIC | Memory:[summary] | Next:[prerequisite]`
     - `CURIOSITY >> LANGUAGE: [hook] | M: [memory-echo] | U: [user-echo]`
- Tail schema marker: `TAIL_SCHEMA: v1`
- Delimiter safety:
  - `SPARKS` uses `||` between exactly 2 chips.
  - `SAVE` fields must not include unescaped pipe characters (`|`).
  - `SAVE` must stay single-line.
- Stream-safe behavior:
  - While stream is incomplete, UI shows only lesson prose.
  - Tail and prompt-scaffold echoes are hidden from visible lesson text.
- Parse statuses:
  - `valid`: envelope complete and topic contract satisfied.
  - `partial`: envelope incomplete or recoverable mismatch.
  - `invalid`: envelope structurally broken or topic mismatch.

## Topic Matrix

### GENERAL
- Activation: `DISCOVERY_GENERALIST_MODE`
- Domain: `GENERAL_KNOWLEDGE_AND_INTERDISCIPLINARY_EXPLORATION`
- Lesson markdown expectations:
  - concise prose first,
  - optional thematic breaks (`***`) on dedicated lines.
- Topic validation:
  - no mandatory code/math fences.

### CURIOSITY
- Activation: `DISCOVERY_GENERALIST_MODE`
- Domain: `GENERAL_KNOWLEDGE_AND_INTERDISCIPLINARY_EXPLORATION`
- Lesson markdown expectations:
  - concise answer plus one curiosity hook.
- Topic validation:
  - no mandatory code/math fences.

### MATH
- Activation: `MATHEMATICAL_REASONING_ENGINE`
- Domain: `ALGEBRAIC_LOGIC_AND_SYMBOLIC_PRECISION`
- Lesson markdown expectations:
  - formulas in `$...$` or `$$...$$`,
  - multi-step solutions in one fenced `text` block.
- Topic validation:
  - if multi-step language is present, fenced `text` block should be present.

### SCIENCE
- Activation: `SCIENTIFIC_EMPIRICISM_MODE`
- Domain: `SCIENTIFIC_METHOD_AND_FIRST_PRINCIPLES`
- Lesson markdown expectations:
  - clear first-principles prose,
  - variables/formulas may use `$...$`.
- Topic validation:
  - no hard code-fence requirement.

### PHYSICS
- Activation: `PHYSICS_REASONING_MODE`
- Domain: `MOTION_FORCES_ENERGY_AND_FIELDS`
- Lesson markdown expectations:
  - equations in `$...$` / `$$...$$`,
  - units and variable names explicit.
- Topic validation:
  - equation-friendly markdown allowed and preserved.

### CHEMISTRY
- Activation: `CHEMISTRY_REASONING_MODE`
- Domain: `ATOMS_BONDS_REACTIONS_AND_STOICHIOMETRY`
- Lesson markdown expectations:
  - chemical formulas and variables in `$...$`,
  - stepwise balancing/reaction logic in fenced `text` block when multi-step.
- Topic validation:
  - chemistry symbols and formulas must survive sanitation.

### BIOLOGY
- Activation: `BIOLOGY_REASONING_MODE`
- Domain: `LIFE_SYSTEMS_CELLS_GENETICS_AND_ECOLOGY`
- Lesson markdown expectations:
  - explanatory prose with clear terminology.
- Topic validation:
  - no hard code-fence requirement.

### CODING
- Activation: `ALGORITHMIC_COMPUTATION_ENGINE`
- Domain: `SYNTACTIC_LOGIC_AND_COMPUTATIONAL_THINKING`
- Lesson markdown expectations:
  - multi-line code only in fenced blocks with language tags,
  - short snippets may use inline code.
- Topic validation:
  - at least one fenced code block for multi-line code answers.

### WRITING
- Activation: `LINGUISTIC_COMPOSITION_MODE`
- Domain: `CREATIVE_NARRATIVE_AND_GRAMMATICAL_FLOW`
- Lesson markdown expectations:
  - readable prose,
  - optional `***` section transitions.
- Topic validation:
  - no hard code/math constraints.

### EXAM_PREP
- Activation: `EXAM_STRATEGY_MODE`
- Domain: `TEST_STRATEGY_RETRIEVAL_AND_STRUCTURED_PRACTICE`
- Lesson markdown expectations:
  - structure:
    - `Plan`
    - `Answer`
    - `Check`
  - each section short and test-focused.
- Topic validation:
  - must include all section headers (`Plan`, `Answer`, `Check`) in markdown form.

## Renderer Alignment Rules

- Math normalization:
  - single-dollar inline math (`$x+1$`) is normalized for JLatexMath when expression appears mathematical.
  - currency-like spans (`$5`) must remain untouched.
- Code preservation:
  - markdown transformations do not modify content inside fenced code blocks.
- Decoration stripping:
  - only prompt scaffold echoes are removed,
  - legitimate blockquotes or headings from the lesson are preserved.

## Regression Test Obligations

- One prompt contract test per `SubjectMode`.
- One envelope parse test per `SubjectMode` with noisy variants.
- Markdown regressions:
  - simple inline math (`$x+1$`),
  - fenced code streaming behavior,
  - JSON at line start in coding contexts,
  - writing separators (`***`) and preserved blockquote semantics.
