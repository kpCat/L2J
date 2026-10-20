# PSS-007 — архитектура, точные границы доверия

## Existing source map (read-first)
- `src/PhantomSemanticStudio.Core/PackReader.cs`, `PackPreview.cs`, `Models.cs`, `WorkspaceStore.cs`, `TextRules.cs`, `CandidateValidator.cs` — pack-inspector, source stamps, session/approval; сохранять их контракт.
- `src/PhantomSemanticStudio.Core/LmStudioClient.cs` — loopback HTTP, strict envelope, JSON Schema, timeout/cancel/no tools/no auto retry, categorized failure; **переиспользовать**, не дублировать небезопасный HTTP-клиент.
- `src/PhantomSemanticStudio.Core/{ChatCorpusImporter,CorpusStore,CorpusQueries,CorpusLanguageTriage}.cs` — приватный streaming ZIP/SQLite, public only, immutable `Original`; не допускать raw в prompts без выбора.
- `src/PhantomSemanticStudio.WinForms/MainForm.cs + .Designer.cs/.resx`, `ChatCorpusForm.*`, `StageSelectionForm.*` — stable Designer. Existing dialogue tab `Preview_Click`, `Teach_Click`, `ApplyLesson_Click` не ломать; лаборатория — отдельная форма.
- `src/PhantomSemanticStudio.Core/{IsolatedPackStager,ReviewExporter,PathSafety}.cs` и scripts/Test-PSS003-Java.ps1 — **FROZEN**; read first, не менять без воспроизводимого багфикса и отдельного targeted теста.

## Suggested coherent new units (names can be adjusted with documented rationale)
1. `src/PhantomSemanticStudio.Core/DialogueLab.cs`: pure deterministic `DialogueLabSession`, typed `DialogueTurn`/`DialogueTrace`/`WorldScope`, turns bounded, per-session `PackPreview`; no networking/IO, no production `Player` emulation.
2. `src/PhantomSemanticStudio.Core/DialogueMentor.cs` or `LmStudioClient.Dialogue.cs`: typed `MentorAdvice` and strict JSON parsing; reuse existing SendAsync/CompletionText by refactoring `LmStudioClient` to partial if practical; **do not weaken generation/semantic-review contracts**.
3. `src/PhantomSemanticStudio.Core/DialogueLabStore.cs` (if needed): *explicitly requested* local persistence, atomic and bounded in `workspace/labs/<id>`, no unbounded session.json migration; default memory volatile.
4. `src/PhantomSemanticStudio.WinForms/DialogueLabForm.cs`, `.Designer.cs`, `.resx`: standard WinForms controls, constructor **only `InitializeComponent();`**, dependencies supplied after construction (SetWorkspace/SetPack/SetModel etc), no live/IO inside Designer/ctor.
5. `ChatCorpusForm.cs/.Designer.cs`: new explicit «Выбранные фрагменты → лаборатория» action; returns detached immutable **sanitized** public excerpts with `corpus ID`/`row ID`/`hash`/`source scope`. No raw source nickname/entry name. Existing import/filter/paging/cancel preserved.
6. Existing `MainForm` adds one button/open action to the current dialogue tab; do not generate extra tabs unnecessarily. No dynamic BuildUi.
7. Focused test files `tests/PhantomSemanticStudio.Tests/Pss007.cs`, optionally separate `Pss007Controls.cs`, with runner switches `--pss-007` / `--pss-007-controls`; no new test project/dependencies.

## Trust boundary / provenance
`Humanized Pack (read-only) -> PackPreview` produces only `PACK_CATALOG_APPROXIMATE` or `NO_PACK_MATCH`; every produced reply references source IDs; never present as full game runtime. `Gemma` can only emit `MENTOR_ADVISORY` and proposed questions/transliteration notes, **never PACK**. `Corpus` emits only `SOURCE_MATERIAL_ONLY`, best-effort scrub; user must explicitly select -> preview -> allow optional limited LM use. User can save an editorial lesson or draft deliberately, with normal source fingerprint and manual approval intact; no direct updates to v1/v2/v3/custom/semantic functional pack.

## Context vs game truth
Scope `AUTO`, `GAME`, `REAL`, `MIXED` applies to **editor interpretation of conversational turn**, not an imaginary Java filter. AUTO may choose `UNKNOWN` rather than guess. "Босс" can be raid or начальник; in history explicitly about Antaras, GAME candidate is stronger; in history about работу, REAL stronger. If insufficient information, ask one clarifying question with mode-specific explanation; do not invent dialogue act/rule. Human answer may update lab context but may **not** silently replace user text before applying `PackPreview` or change approval. AI-suggested memories must be marked provisional until user explicitly saves exact text/scope.

## Boundaries: resource, model and UI
- Per turn user text <= 1024 chars (or stricter existing Java-bound of 256 for match; report too-long instead of truncating); session <=200 turns, transcript shown bounded, history sent to model <=10 turns / <=8 KiB. No raw full imported corpus in request or logs.
- Model: loopback-only exact model, strict JSON, max response 1 MiB, bounded 1 POST per clarification/manual invocation, `max 20 calls per session`, no tools, no redirects/proxy, no automatic retry/load/unload. If opt-in off, 0 calls even if model running.
- Corpus: selected snippets max20, model translation batches <=3 chosen, no full-corpus translation/transliteration. Trace/private notes stay local; do not publish conversations in Git reports.
- UI: async/cancellation/versioned result rendering. One pending dialog clarification, reject stale reply on source/context/message changes; keyboard Save/Discard/Cancel on edits and close, no invisible auto commit.
- Live generation remains BLOCKED_LM unless actually measured; no fake model answers or silent fallback to plain-text/unrestricted JSON.

## Semantic Pack meaning
`PackPreview` approximate is not full functional-first, social relationships, real identity, memory, gender, exact Java selection. The dialog lab is **source-authentic in texts**, not Java-behavior-parity. An exact native Java oracle/truth comparison is a PSS-008 or independent optional read-only evaluation step; do not mislabel C# responses as native Java.
