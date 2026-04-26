# Prompt And Markdown Contract Matrix

This document is the canonical contract between:
- prompt generation (`PromptBuilder`, packaged prompt assets `features/chatbot/src/main/assets/prompts/gemma4_e2b_<topic>.txt`),
- output parsing (`GyangoOutputEnvelope`, `AssistantParsingPipeline`),
- rendering (`AssistantTextPolisher`, `MarkwonAssistantMarkdown`).

Prompt source-of-truth is file-based per model+version+topic. `PromptBuilder` selects templates by
`prompts/<version>/<model_family>_<topic>.txt` and only performs placeholder rendering.
Current runtime policy is version-first with fallback: active version (`v2`) -> `v1` -> legacy
`prompts/<model_family>_<topic>.txt` -> in-code fallback constants.

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

## Exam Prep Updates (Current)

- Prompt contract now uses supportive coaching language with `### Feedback` and `### Next Question`.
- Exam turn input now includes prior question replay for grading:
  - `M: {{MEMORY}}`
  - `Q: {{EXAM_PRIOR_QUESTION}}`
  - `U: {{USER_TEXT}}`
- Exam metadata tail now uses:
  - `CONTEXT: [Question: <num>] [Level: <current_diff>] [Status: <Correct/Incorrect/Complete>]`
- Parsing updates:
  - `Level` supports decimal values (e.g. `2.5`) for +0.5 progression.
  - Question progression is coerced to monotonic increment if the model repeats/decrements.
- Display polish updates:
  - strips leaked inline `[Question: k]` tags from user-visible markdown,
  - removes extra blank lines directly after `###` headings (feedback/question spacing cleanup).
