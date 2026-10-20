# PSS-007 — targeted RED/GREEN and regression matrix

## A Pack-only (no LM)
A1 existing imported synthetic `PATTERN` + `TEMPLATE` -> matched IDs and returned text byte-for-byte from pack only, source hash recorded; no hallucinated filler.
A2 unknown phrase -> `NO_PACK_MATCH`, `PACK` bubble says no reply instead of invented text, 0 network calls, no candidate/session XML mutations.
A3 functional/support/identity/recall target -> explicit unsupported/no Java parity; no pretend action/memory.
A4 AUTO/GAME/REAL/MIXED: two context histories disambiguate `босс`; ambiguous UNKNOWN never becomes authoritative game act; forced editor context doesn't mutate Java catalog.
A5 200-turn bound, 256 normalized codepoint matching limit, cancellation, new import/source drift, cleared history, repetitive answers, multi-turn trace and input state.
A6 actual WinForms STA public controls for three turns + empty/default Mentor OFF, form Save/Cancel, source status; static Designer open/update safe by code inspection; physical UI if accessible.

## B Mentor and memory
B1 opt-in OFF -> 0 requests, even after ambiguous input. ON allows exactly one bounded request after explicit trigger, not model-generated PACK text.
B2 valid `MENTOR_ADVISORY` schema one short question and interpretations; source + turn hash binding; pending clarification one at time.
B3 malformed/duplicate/extra keys/refusal/tool_calls/length/timeout/HTTP 4xx/5xx/cancel/unauthorized -> fail-closed 0 new evidence, no body/token leak, no auto retry.
B4 stale pending advice if turn/source/mode changes; user confirmation updates only lab world context and editor insight, no silent input rewrite.
B5 explicit scoped lesson save, source fingerprint and existing act/topic gate; DRAFT optional, NEVER APPROVED; old `SessionState Version=1` opens unchanged; default session ephemeral.

## C Corpus bridge
C1 existing PSS-006 corpus UI selection (20/21, hidden filter marks, switch corpus) preserved; detached preview hashes and no private channels, no origin/file/speaker leak.
C2 no automatic corpus→LM data; explicit separate consent for 1–3 sanitized public excerpts; fake transport request exact data only.
C3 Spanish/English/translit ambiguity; `privet`, `pvp`, `kak dela`, words `boss` GAME/REAL, non-English Latin; do not mass backtransliterate; propose/edit only.
C4 strict translated suggestion IDs/ref hashes, failure leaves unchanged original corpus/SQLite DB SHA and approval/session/staged XML/source stamps.
C5 cancellation/slow SQLite/large excerpt, memory/cpu bounded; no query/UI freeze; CorpusStore reconnect/query existing PSS-006 tests unchanged.

## Cross-cutting
- Before: baseline `98 PASS / 0 FAIL` according to PSS-006, re-run real Build-Verify after changes and assert at least baseline 98 preserved.
- Real RED→GREEN for A/B/C new targeted tests; named tests and exact exit codes in reports, not invented PASS. Negative-controls assertions must fail if protection removed.
- Use synthetics for all tracked evidence: no real chat text/nick/message/filename, no private DB, no prompt/token/log in Git/Code review. Exact allowed change inventory and Git scope guard.
- Strict UTF-8 and two separate mojibake / escaped Cyrillic checks, exclude only literal marker table in the verifier itself. No broad encoding rewrite.
- Static Designer for new form `.Designer.cs`/`.resx`/project nesting + MainForm, no loops/if/IO in InitializeComponent and only InitializeComponent in form constructors. Real UI/DPI100/150/VS Designer roundtrip separately PASS/NOT_TESTED/BLOCKED.
- Live LM/status and Java/status explicitly independent; no Java/server/install scope in PSS-007. A live tiny test only when operator opted-in and model already loaded, otherwise BLOCKED_LM/NOT_RUN.
- Final `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build-Verify.ps1` exit 0 only on real run. Additional focused STA runner `dotnet exec --runtimeconfig ... PhantomSemanticStudio.Tests.dll --pss-007-controls` or equivalent.
