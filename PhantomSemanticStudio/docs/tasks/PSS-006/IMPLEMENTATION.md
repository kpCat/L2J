# PSS-006 — implementation steps (TDD, small checked phases)

## Checkpoint A — fix responsiveness without stale bypass

1. Read `MainForm.ShowCandidate` and `SemanticDuplicateScout` code and create reproducible focused test: after selecting stored-evidence candidate, UI thread must remain responsive while source/similarity recheck runs in background; show deterministic 2 or more fast selection changes; old job must never repaint new candidate.
2. Confirm RED. Add an in-flight cancellation/version mechanism to MainForm (not Designer), `Task.Run` only for read-only source/scout work, no `Control` access off UI thread. IsEvidenceCurrent currently calls Search again: avoid unnecessary duplicate expensive work, but **do not** weaken stale equality or duplicate evidence freshness rules. Add at least one cancel/close/error test.
3. Confirm GREEN and existing PSS-005 stale approval/evidence tests. Do not claim fixed real-world freeze without actual responsive UI evidence; static/STA gate does not equal human click.
4. Examine LM error handling with fake HTTP; add diagnostic-only safe error *category* for specific failure stage. Add one live microtest only if explicitly available locally. BAD_RESPONSE cannot be auto-fallback to arbitrary prose; no raw response retention or debug dump.

## Checkpoint B — separate large-scale corpus store

1. Choose corpus store architecture with explicit index/memory budget and migration isolation. Test read-only ZipArchive parser against synthetic channel/date/encoding and private data (RED). Build minimal `ChatCorpusImporter` + `CorpusStore` + language triage + tests (GREEN). Metadata counts and byte hashes per source entry only.
2. Test zip bombs, duplicates, private channels, malformed zip, huge line, encoding errors, and cancellation/timeouts. No side-effect files outside corpus scratch. `ZipArchiveEntry.Open()` must be bounded by streaming byte budget, not `ReadToEnd` for 67 MB.
3. Test imported corpus read-back after new process, query pagination, deterministic filters, no unbounded RAM, failed transaction leaves existing version. Negative case DB/index corruption handled fail closed. Parameterized DB queries and not fake sanitized placeholders.
4. Benchmark synthetic large corpus (>=100k lines, scale toward 1m without uncontrolled time/memory); record wall time/peak private bytes/read throughput if measurable. Functional acceptance more important than raw throughput; UI must retain cancellation and progress. Do not run heavy stress more than required.

## Checkpoint C — independent Designer form and meaningful workflow

1. Create standard `ChatCorpusForm.cs/.Designer.cs/.resx` (plus correct csproj nesting), with explicit static controls for Open ZIP, Import, Cancel, search/filters/stats, selected excerpt preview, locally persisted corpus listing. Integrate one static MainForm button. Both constructors only `InitializeComponent()`; `scripts/Verify-Designer.ps1` should continue to pass with six original tabs.
2. UI displays only paged rows. Controlled selection of 1–20 **public** items for inspection; no auto candidate creation/approve/LM call. Filter changes preserve only explicit selections where data provenance remains current; select from another corpus cannot silently mix. Cancel/close/failed import preserves last committed corpus.
3. Actual STA WinForms public control route validates layout/min size, import progress, cancellation, 100/150 DPI if physically available; if interactive tools absent keep NOT_TESTED and provide exact manual steps. No runtime UI factories inside Designer.
4. Final `Build-Verify`, focused tests, Git exact staging. If LM is blocked, it is a nonfatal independent gate for offline corpus features; if importer or privacy gate fails, implementation BLOCKED/FAILED and never claim all-acceptance GREEN.

## Source owner/file budget

Favor small focused classes: `src/PhantomSemanticStudio.Core/{ChatCorpusImporter,CorpusStore,CorpusLanguageTriage,CorpusQueries}.cs`, `src/PhantomSemanticStudio.WinForms/{ChatCorpusForm,ChatCorpusForm.Designer}.cs/.resx`, narrowly `MainForm.cs/.Designer.cs`, project files if dependency, `tests/Pss006.cs` and runner route, targeted `scripts/Verify-PSS006.ps1`, `README_RU.md`, reports. Bounded exception to prior 8-10 file norm allowed for this task; disclose exact changes in plan. **Do not** change Java, all PSS-003 staging gates or workspace session JSON contract except a focused and proven bug. No runtime Java subprocess within GUI.

## Internal review and commit

Each checkpoint has its own RED/GREEN evidence. When checkpoint A passes, record local clean bounded commit if desired; checkpoint B after safe ingestion/index; checkpoint C after UI and aggregate. At end verify all last changes pass (including code review), then push to `origin/main`. Large task may be BLOCKED midway, but each independently working checkpoint must be described and committed with precise status. New dialogue always for PSS-007.
