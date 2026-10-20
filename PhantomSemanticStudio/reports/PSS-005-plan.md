# PSS-005 Implementation Plan

> Execution: superpowers:executing-plans, inline основной Codex. RED/GREEN по superpowers:test-driven-development. Пользовательский пакет требует автономного исполнения и один финальный commit; повторное согласование и промежуточные commits не выполняются.

**Goal:** два явных этапа advisory поиска повторов с bounded evidence и проверкой актуальности.
**Architecture:** pure offline scout → existing loopback LmStudioClient → existing atomic WorkspaceStore. WinForms показывает локальные references и модельное мнение; approval/staging не зависят от него.
**Tech Stack:** .NET 10, C# 14, WinForms, System.Text.Json, HttpClient; без NuGet.
**Spec:** docs/tasks/PSS-005/GOAL.md, ARCHITECTURE.md, IMPLEMENTATION.md, TEST_CASES.md, ACCEPTANCE.md, SAFETY_AND_GIT.md.

## Read pass / base

HEAD, local origin/main и фактический remote main = `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`; own root; branch master; fetch/push origin kpCat/PhantomSemanticStudio. Initial tree clean; затем пользователь добавил ровно 10 task files. Они включаются неизменёнными. Ancestor AGENTS.md, README.md, code-map/pattern files не найдены; README_RU.md прочитан.

Прочитаны root AGENTS, README_RU, DESIGN_RU, SOURCE_AUDIT_RU, BASELINE_VERIFICATION, PSS-003/PSS-004 final и PSS-004 UI, весь пакет PSS-005, Core Models/TextRules/CandidateValidator/PackReader/WorkspaceStore/LmStudioClient; MainForm editing/RunAsync/staging owners, MainForm Designer, Program/Pss004 test runner, Build-Verify/Verify-Designer, projects/props, .gitignore.

Локальные аналоги: TextRules.Normalize/Similarity; LmStudioClient.SendAsync, ExactFields/UniqueFields и generation envelope; SaveCandidateVersion/WorkspaceStore atomic session; StageSelectionForm Designer и existing STA control tests (read-only). CandidateReview.Fingerprint не изменяется.

## Scope / constraints

Bounded exception из IMPLEMENTATION: 12 source/test/script/docs paths плюс package и reports. Разрешены: новый Core SemanticDuplicateScout.cs; Models.cs; LmStudioClient.cs; WorkspaceStore.cs; CandidateValidator.cs (только Edit reset); MainForm.cs; MainForm.Designer.cs; tests/Program.cs; новый tests/Pss005.cs; README_RU.md; новый scripts/Verify-PSS005.ps1; этот plan и PSS-005 reports/logs/inventory.

Неприкосновенны: TextRules/PackReader/PathSafety, Stager/Exporter/StageBatchSelection/StageSelectionForm, MainForm.resx, Java scripts, projects/solution/props, historical reports. L2J READ_ONLY. No review ZIP, raw prompts/responses/workspace/stage in Git, retries, tools, auto approval/rejection/repair, background scans, new dependencies. Max 12 references / 240 chars reason / 256 chars model ID / 1 MiB HTTP response. Session version 1.

## Tasks / checks

- [ ] 1. Offline: Search(snapshot, candidate, peers, maxMatches=12, token), SemanticShortlist/Neighbor DTO. Focused tests: kind/cross-act exact/ID namespace/provenance/counts; stable ranking/order and 5k/20k one-candidate corpus; synonym counterexample always COVERAGE_LIMITED, cancellation. RED API scaffold → GREEN.
- [ ] 2. HTTP: ReviewSemanticAsync(settings, key, candidate, shortlist, token), strict ParseSemanticVerdicts; shared envelope extraction without weakening drafts. Tests: exact one POST/schema/model/temperature, valid advisory only; output/HTTP failures/no retries/sanitization/cancel/timeout. RED fail-closed scaffold → GREEN.
- [ ] 3. Persistence: optional Candidate.SemanticReview; bounded DTO validation via SaveSession/LoadSession; IsEvidenceCurrent and WithCurrentEvidence pure recheck. Tests: legacy approvals unchanged, round-trip; malformed bounds preserve bytes; candidate/source stamp/peer/reference/race drift; Edit reset. RED → GREEN. Hashes include source stamps and referenced contents in addition to import fingerprint.
- [ ] 4. UI: static buttons below existing validation panel, retain panel/editor height and six tabs. ResolvePendingEdit first; Task.Run single selected scout; explicit one model call; PackReader.Load before save plus pure freshness recheck. Preserve local preview on LM failure. Show current advisory or STALE on selection; unsaved edits visibly invalidate display. Static wiring RED → GREEN; existing STA control pattern for available layout/flow checks, separate from interactive/DPI.
- [ ] 5. Final: targeted PSS-005, Release Build-Verify, static Designer, separate mojibake/escaped Cyrillic scans, exact protected-path/package guard. Conditional one live POST only if local server already runs; unload if invoked. UI/VS/DPI statuses honest. Fresh-context read-only review per executing-plans. Final reports and exact inventory; exact scoped commit, non-force push HEAD:refs/heads/main, remote SHA and public verification; STOP.

## Review Focus

1. Source changes while HTTP runs: reread and reject before atomic save; test changed physical fixture.
2. Same ID source/peer and reordered input: namespace + canonical hashes; test both.
3. Legacy approval hash: evidence excluded; test current approval after attaching result.
4. A valid model answer with forged reference/hash: exact parser and final recomputation; test mismatch and unchanged saved bytes.
5. Unsaved editor/review-note and visible report: existing Save/Discard/Cancel before actions, STALE label on edits; static/control checks, interactive limitations explicit.

## Ledger

Pre-flight: task interfaces and constraints consistent. Ruling: existing branch master/root reused, no worktree/branch mutation — explicit required own root and one commit to origin/main. Cost if wrong: no isolation beyond exact allowlist; preserve user dirt.
Task 1: complete — scout RED 0/3 → GREEN 3/0.
Task 2: complete — HTTP RED 3/4 → GREEN 7/0.
Task 3: complete — evidence RED 8/3 → GREEN 11/0; fixture no-op mutation corrected (attempt 10/1 retained).
Task 4: complete — static Designer and actual controls RED → GREEN 3/0; hidden TabPage anchor baseline fixed, candidate tab Size only.
Final review: 0 Critical, 2 Important fixed RED→GREEN (UTF-8 selection catch; unconditional freshness override). Deferred Minor: synchronous saved-report refresh reads pack and searches twice; can pause UI/Cancel.
Task 5: local checks complete — final Release 88/0, 0 warnings/errors, targeted 11/0, controls 3/0. Actual source 65/65 SHA/bytes equal, 5310 patterns/20963 templates. One live POST BAD_RESPONSE, 119 seconds, no evidence/retry; model unloaded and loaded-list empty. UI/DPI/VS NOT_TESTED; Java NOT_RUN. Text/scope guards and publication closing sequence captured in final report/verification; final SHA proof belongs to postcommit handoff.
Rulings for reviewer-declined checks: actual verification supplied by parent; live/UI/DPI/VS statuses separate; Java/runtime/install outside scope and frozen; final docs checked against logs; commit/remote outcome only after commands. Full rationale/costs in PSS-005-review.md. Historical Java PASS not inherited. PSS-006 not started.
