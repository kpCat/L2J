# PSS-006 — acceptance matrix

## Required local C# gates

- Verified required `main` base commit and own repo root; 88 prior tests remain PASS. Final .NET Release build exit 0, 0 errors, 0 compiler warnings. Exact new tests and exits recorded.
- UI freshness refresh selection runs off UI thread; first/cancel/second selection results and destroyed-form events never write stale UI; SourceDrift and edit show STALE, no false CURRENT, approval/session untouched. Distinguish static/control tests from physical UI evidence.
- Strict local LM error class tells operator what failed **without raw response, prompt, token or text**; malformed response/refusal/finish/tool/mismatched refs still fail closed; no auto retry/model switching/JIT load/unload via tool.
- Import synthetic multi-channel ZIP with original grammar, malformed entries and UTF8; timestamps are unzoned and provenance stays local. `TELL`/`FRIENDTELL` never persist in corpus DB/index, including hidden row columns. No raw nicks in public logs.
- Import large synthetic 100k+ lines with bounded memory, cancellation, deterministic counts/hash/dedup, pagination 100–200 and filter search without binding the whole corpus; screen remains responsive. If 1m-line benchmark possible, record facts not fabricated numbers.
- Untrusted ZIP traversal/symlink/encryption/bomb/oversize and malformed Unicode fail closed before final index appears; old corpus remains available; clean only own partial. Separate per-entry streaming budget from central-directory declared sizes.
- Corpus files are created ONLY in Studio private workspace, not in source repo paths or L2J; Git inventory has zero .db, .sqlite, .log, chat.zip, snippets/nicknames, binary exports.
- New WinForms Form opens in VS Designer if available, constructors only InitializeComponent; six MainForm tabs unchanged; `Verify-Designer` plus added static guards. DPI 100/150 and end-to-end physical interaction must be NOT_TESTED if unavailable.
- No extraction/modification of L2J. If actual HighFive is read, 65/65 source file stamps unchanged before/after. No Java gate new run required; previous PSS-003 historical Java PASS does not imply this task Java PASS.

## Real corpus gate

Optional: when user has the provided `chat.zip` on their Windows filesystem, operator chooses it through UI and records **only** date/channel/language/counts, errors, SHA of raw input and strict no-privacy-exposure. If not present or operator did not choose, `ACTUAL_CHAT_IMPORT_NOT_RUN` is acceptable with working synthetic importer. Never copy private original into task/reports/Git or send to model automatically.

## Status matrix

- `CORE/UI/ZIP/DB` independent statuses; `LIVE_LM` and `DPI/DESIGNER` independent.
- `GREEN_IMPLEMENTATION` only if all local executable gates incl. ZIP privacy passed; `BLOCKED` with concrete reason for anything incomplete.
- `HANDOFF_REVIEW_PENDING` until independent GitHub audit. No claim of fully ready Semantic Pack/server XML, semantic correctness, privacy-perfect anonymity or pack conversations. Corpus content and selected excerpts remain `SOURCE_MATERIAL_ONLY`.
