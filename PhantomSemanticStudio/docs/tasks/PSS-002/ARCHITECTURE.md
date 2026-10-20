# PSS-002 — архитектурная граница изменений

## Подтверждённые текущие владельцы

- `StudioSettings`: endpoint `http://127.0.0.1:1234/v1`, model id `gemma-4-26b-a4b-it-ultra-uncensored-heretic`, temperature/maxTokens/timeout/source path; token intentionally absent from saved config.
- `LmStudioClient.ListModelsAsync`: GET models; `GenerateAsync`: JSON Schema chat/completions; `ParseDrafts` требует только `{items:[{kind,text,reason}]}`; response<=1 MiB; без redirects/proxy, tools/retries.
- `GenerationRequest`: `Topic,Act,Band,Register,Gender,Instruction,Words,Count`; currently **no kind selection**.
- `MainForm`: 6 designed tabs; event methods `CheckLm_Click`, `Generate_Click`, `Teach_Click`, `ApplyLesson_Click`, `CandidateSelection_Changed`; stage only DRAFT.
- `CandidateValidator` vs `CandidateReview`: errors vs warnings, source fingerprint/review hash; candidate kind may be PATTERN/TEMPLATE, no runtime gender filtering.
- `ReviewExporter`: `DO_NOT_INSTALL.txt`, JSON approved candidates, source-baseline and review-summary, **NO XML**.

## Desired flow

1. Operator explicitly checks local endpoint / model ID; UI renders typed diagnostic (`SERVER_OFFLINE`, `AUTH`, `MISMATCH`, `BAD_RESPONSE`, `TIMEOUT`, `CHECKED_MODEL_LIST`, etc.). This is advisory, not an implicit auto-load or remote call. Debug logs contain categories, not secrets.
2. Operator selects existing topic+act, relation, register, gender, count and kind (`TEMPLATE`, `PATTERN`, `MIXED`), enters human task/words.
3. Core independently validates preconditions and serialized JSON request. For single-kind modes, schema restricts `kind` and local validator double-checks every item. Never ask Gemma to produce code, XML, IDs, executable instructions or gameplay effects.
4. Only after a complete, valid LM response, core returns typed `DraftItem` collection. UI constructs staged Candidates for **one atomic workspace save**. On error no session change.
5. Operator sees warnings, edits text, reviews, may approve for JSON-only review; no direct server mutation.

## Safety subtleties

- `GET /v1/models` when JIT enabled may include downloaded but unloaded models. 'ID найден' ≠ successful inference.
- Model choice **is literal**. Don't silently substitute `Gemma` for an arbitrary loaded model, don't auto-retry without schema on a 400 error.
- `HttpClient` stays loopback-only; no redirects/proxy, TLS downgrade or SSRF through URLs; no filesystem access for model, tool-calls rejected.
- Legacy `GenerationRequest` and `EditorialLesson.ToRequest(...)` must still work. Missing kind defaults to `MIXED` in core compatibility, while UI defaults to `TEMPLATE`; explain semantic difference in report.
- HTML/XML/Java/source code from model is untrusted and never executed or written to L2J.
- For `Gender != ANY`, REVIEW_ONLY remains because Java TemplateBucket doesn't filter gender; don't invent a safe runtime workaround.
- Structured output doesn't prove good grammar, factual correctness, appropriate profanity or semantic uniqueness. Never remove the `SEMANTIC_NOT_CHECKED` warning.

## Future boundary (NOT PSS-002)

An isolated Java oracle and compiler can later validate generated XML **only in a separate staged copy**, with actual loader and content gate. Until proven, no server-ready XML and no 'safe apply' buttons. Don't take this task in PSS-002 just because PSS-001 handoff suggested it; focus on the currently blocked local generation/UI evidence first.
