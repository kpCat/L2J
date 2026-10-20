# Подэтап B — умный отдельный Gemma-наставник и управляемые заметки

## Design
Only after **explicit user opt-in** in the current lab session can Gemma help. Default OFF; toggle indicates that *the limited current conversation context* may be sent to **local** LM Studio. Lab packets include a bounded 10-turn/8 KiB editable/safe preview; no archive scans, no complete corpus, no private TELL. On ambiguous conversation, app may propose a mentor clarification under opted-in Auto Mentor; manual «Спросить наставника» available otherwise (also requires explicit confirmation/opt-in). No calls on every ordinary line.

Mentor reply is a strict JSON object with fields such as `worldHint` (`GAME|REAL|MIXED|UNKNOWN`), `needsClarification` (boolean), `question` (0..220 chars), `interpretations` (0..3 concise options), `reason` (<=240), `editorialSuggestion` (0..400). JSON schema `additionalProperties:false`, bounded arrays/strings. After receipt, validate exact keys, types, ranges, UTF-8/control chars; question required if `needsClarification`, forbidden otherwise or explicitly empty; no surprise `code`, `tools`, `function_call`, `refusal`, multi-choice, `finish_reason != stop`, broken JSON. Detect and reject duplicate keys. Return sanitized `BAD_RESPONSE` failure categories without raw model bodies/prompts/tokens.

Lab message roles: `YOU`, `PACK`, `MENTOR`, `EDITOR_NOTE`; **no conversion from MENTOR to PACK**. Mentor can phrase a question like «Ты про рейд-босса в игре или начальника на работе?»; while waiting, one pending clarification record bound to original turn ID + source fingerprint. Subsequent user answer updates ephemeral local `WorldScope` only after explicit confirmation, and can be an ordinary new turn only at user's choice. If source/turn changed, advice becomes `STALE`; user may discard. Model suggestion never triggers creation of new `PATTERN`/`TEMPLATE` automatically.

## Memory
Separate clearly:
- **Short-lived context**: bounded last turns; auto discarded on Clear/Close unless explicit save.
- **Editor insight**: provisional scope note (ambiguous meanings, intent) visible but **not authoritative**, can be edited.
- **Saved scoped lesson**: only explicit «Сохранить замечание» after choosing verified existing `Topic/Act/Band/Register/Gender`, binding source fingerprint and calling existing `EditorialLesson` validation; never cross-contaminate all topics, not a new runtime memory owner. Approval is independent and unchanged.
- **Draft candidate** (optional, scope fitting existing topic/act only): explicit per-item action with clear DRAFT, no review hash/APPROVED, normal dedup and future staging gates. Never infer unknown topic/act by machine guess.

If `SessionState` storage is used for lessons, preserve its version=1 and 500 lesson bound. For longer optional lab history use distinct atomic private workspace storage, version+limit+lock+source fingerprint, no automatic migration or long-term conversation capture. Do not accidentally save raw model history to Git or token to disk.

## LM limits and tests
One POST per user-triggered mentor action or explicitly opted-in ambiguous turn, max 20 per lab session; bounded temperature and exact model ID; no JIT/unload/lifecycle performed by app or Codex. Retain all existing LM semantic duplicate/generation validators untouched where possible. Positive fake HTTP creates MENTOR only and returns no pack reply; negative tests empty/duplicate/extra keys, mismatched turn/baseline, cancellation, timeout, error 4xx/5xx, tool calls, refusal, unsafe text, oversized response. Failed request keeps session/approvals untouched; old advice remains marked stale. Live test optional tiny explicit operator request **only if model already loaded and user consent**; otherwise `BLOCKED_LM`, no automatic loading or repeat after first known failure.
