# PSS-008 Implementation Plan — 2026-10-09

> Execution: superpowers:executing-plans, main coding agent, focused TDD and independent read-only review. Task design already supplied and execution without repeated agreement explicitly required.

**Goal:** read-only quality map → exact isolated v3 proposal → native content proof and optional offline handoff, never installation.
**Architecture:** existing PackSnapshot/CandidateReview/StageBatchSelection and WorkspaceStore/PathSafety; separate v3 owners without modifying custom staging. Standard Designer forms, worker code in main .cs.
**Stack:** .NET 10, C# 14, WinForms, existing Microsoft.Data.Sqlite 10.0.12; no new packages/projects.
**Spec:** docs/tasks/PSS-008/DESIGN.md and GOAL.md, A_QUALITY/B_V3_PROPOSAL/C_JAVA_RELEASE, SOURCE_CONTRACT, SECURITY_GIT, TEST_CASES, ACCEPTANCE, HANDOFF.

Read-first: AGENTS.md, README_RU.md, docs/DESIGN_RU.md, SOURCE_AUDIT_RU.md, BASELINE_VERIFICATION, PSS007 final/UI/review, PSS003/PSS006 final, all 12 task files; Models, PackReader, CandidateValidator, TextRules, StageBatchSelection, IsolatedPackStager, WorkspaceStore, PathSafety, SemanticDuplicateScout, DialogueLab/Store, CorpusStore, MainForm, StageSelectionForm, DialogueLabForm, console runner/fixtures/STA/verifiers, PSS003 Java operator/probe, projects and props. Ancestor AGENTS/code-map/pattern files not found. Local analogs: custom physical copy + atomic receipt, exact manual selection form, cancellable DialogueLab worker with view-version guard.

Initial HEAD = required base 0344c9c0671d53a7bb757db7a92b1dfd03246e9c. origin fetch/push exact https://github.com/kpCat/PhantomSemanticStudio.git. Initial ls-remote sandbox network denied; approved read-only retry confirmed same main SHA. Initial dirt only 12 incoming PSS008 instruction files. Manifest 11 listed hashes/bytes verified; PACKAGE_MANIFEST SHA256 7c80e3c90aefac50879c8f74e9108ca3f7844a6bc14c9e136023a0ffc86f3550. Preserve bytes; no task ZIP exists in visible inventory.

## Global constraints

- Entire L2J_Mobius read-only; no git/native tools/temp in source. Work in existing Studio checkout, preserve master, non-force HEAD:refs/heads/main only.
- No review/source/release ZIP, raw corpus/PII/model response/proposed XML in Git. All operator stages/scratch private ignored artifacts/workspace.
- C# stage status STAGED_V3_UNVALIDATED / Java NOT_RUN. Proof is independent, immutable and exact-stage-bound. Java content acceptance never runtime or installation permission.
- 1–20 exact CURRENT APPROVED, allPeers validation, exact declared category pair, existing v3 topic/act provenance; two default-NO consents; third release consent.
- KEY ^[a-z][a-z0-9_.-]{0,63}$, 64 segments, 1MiB/file, 32MiB including manifest, 8192/32768/2048/1024 counts, indexed pattern256 and NONE template4096 per act/band/register/mature.
- PATTERN normalized UTF16<=160; TEMPLATE UTF8<=240, Gender ANY, no placeholders, memory, mature/profanity or gameplay claims; editorial truth remains human attestation.
- Constructor only InitializeComponent, explicit Designer layout/events, resx and project nesting. GUI no shell/native Java.
- Physical UI/DPI100/150/VS and live LM must retain honest separate statuses.

Bounded exception >10 paths: task explicitly requests three connected independently reviewable checkpoints. Final production/test/script/docs owners (21): PackCoverageAnalyzer.cs; IsolatedV3ProposalStager.cs; V3ProposalContract.cs; V3ReleaseHandoff.cs; Core.csproj operator asset copy; PackQualityForm trio; V3ProposalForm trio; MainForm.cs/Designer.cs; WinForms.csproj nesting; Pss008.cs/Controls.cs; Program.cs; Test-PSS008-V3-Java.ps1; java/Pss008V3CatalogProbe.java; Verify-PSS008.ps1; README_RU.md. Task files and safe evidence/reports separately inventoried. Custom staging/operator and historical artifacts frozen.

## A — analyzer and UI

- [x] Add 3 focused console tests + executable empty scaffold; run --pss-008-a, expect assertion failures, save RED.
- [x] Implement Analyze(PackSnapshot,CancellationToken): immutable counts/capacities, pattern topic/act and template act/band/register/source rows, clean buckets, missing response coverage, exact duplicates and deterministic lexical sample <=256/2048 comparisons, explicit caveats. No I/O or session/corpus dependency.
- [x] Implement PackQualityForm SetPack + async Analyze/filter/sort/details/cancel with version guards; MainForm entry on existing library tab. Real STA missing-form RED then GREEN.
- [x] Run focused synthetic 5k/20k timing/cancel/stability and actual source aggregate scan with stamps, no raw corpus.
- [x] Read-only checkpoint review; record A evidence.

## B — v3 physical proposal

- [x] Add focused synthetic paired manifest tests/scaffold; run --pss-008-b executable RED.
- [x] V3ProposalContract verifies actual stamped manifest and target XML schema, exposes immutable V3SegmentPair and source/copy/hash helpers. Preserve roots/comments/attributes and XML semantics, no duplicate parser for quality analysis.
- [x] IsolatedV3ProposalStager.Create(store,pack,allPeers,ids,pair,selectionConsent,editorialConsent,token): all preflight before output, exact bytes copy, append-only target(s), preservation/deltas, source and current approval checks, own .partial cleanup and atomic rename.
- [x] V3ProposalForm reuses StageSelectionForm, explicit unselected pair, full text/paths confirmation and editorial check, independent custom UI entry. No model calls.
- [x] Focused negatives: scope/schema/DTD, current approval, enums/text/UTF/caps/ID/duplicates, drift/cancel/reparse, no finished output or source/session mutation. Keep original custom tests passing.
- [x] Read-only checkpoint review; record B evidence.

## C — oracle and offline handoff

- [x] Add release negatives and positive synthetic proof fixture/scaffold; run --pss-008-c RED (synthetic proof acceptance only contract, never native evidence).
- [x] Separate operator exact finished v3 path guard; strict receipt/delta/approval-hash validation and source/stage stamps. Audited PSS003 physical copy and pinned six reachable Ant shapes/classpaths, max4000/256MiB/free512MiB. Pin all output/temp under oracle; verify bridge bytes before/after, no arbitrary source execution.
- [x] Native baseline+stage loadV3(true), counts and selected ID/text-hash/act/topic understand/select, stable combinedHash, unmatched and wrong selector cases, duplicate and schema native negatives. New Pss008 bridge, never relabel PSS003.
- [x] Immutable java-validation.json bound to receipt hash + all stamps + bridge/input hashes + native exits/log hashes. Fail closed if not exact.
- [x] V3ReleaseHandoff.Prepare with current exact proof/source/delta and third human consent, .partial rename; proposed1–2 XML, backup original bytes, SHA-manifest and non-executable Russian checklist. UI manual proof-folder choice; no shell, install destination or apply API.
- [x] Run actual source synthetic selected candidates in own ignored workspace and Java operator if safe dependencies available. Missing/unsafe contract = BLOCKED_JAVA, preserve A/B.

## Review focus and closure

- Source or approval/peer drift during copy: reject atomically, existing finished stages untouched.
- Untrusted receipt/proof/directory contents: exact finished path, bounded JSON, no extra stage files/target delta; proof stale/mutated/missing rejects before handoff.
- Manifest compatible-looking pairs with different root/category/symbols: require exact current pair and same semantic topic/act attestation.
- Revoked UI consent/selection/category or cancel during worker: cancel/version invalidate, no stale repaint/output.
- Ant top-level tasks/property environment/bridge drift: audit before invocation; working dirs and all writes constrained to physical Studio scratch.

- [x] One independent fresh whole-change review; reproduce important findings RED→GREEN and document rulings.
- [x] Final Release Build-Verify; --pss-008-a/b/c and STA controls; affected legacy controls; static Designer/UTF8/mojibake and escaped Cyrillic separately, task hashes, exact source equality, exact allowlist/privacy guards.
- [x] reports/PSS-008-final.md, UI, review and owned-files, safe stdout and SHA summaries; updated README. No physical/manual gate passed automatically.
- [x] Remote required base rechecked; exact publication inventory prepared. Ordinary commit, non-force main push and local/remote/public SHA comparison are reported after commit in the final message; this pre-commit document does not predict its own commit SHA. STOP after PSS008.

## Checkpoint evidence / closure ledger

A completed: focused4/0, original scaffold RED0/3, review empty-scope RED3/1, actual read-only65 source scan, separate Designer controls. B completed: focused8/0, real append whitespace RED4/1, deterministic final peer-cancel RED7/1, fixture65 mixed/single-kind preservation, mid-copy source/approval/cancel, junction/sentinel, actual modal selection/consent revoke2/0 STA. C completed locally: native Ant/compile/probe0, duplicate/schema3, real selected2; metadata forgery RED → signed proof + consistent forged public hashes/old MAC GREEN; genuine offline synthetic handoff proposed2/backup2/manifest/checklist. No source mutation or installation.

Independent whole-change review read five Important fixes; open Critical/Important0. A/B preflight and checkpoint findings are recorded in PSS-008-review.md. Core csproj exception delivers fixed operator assets for hash trust, no package/project/dependency change. Per-stage random HMAC key outside oracle/export is a bounded fake-proof risk fix, not OS-owner isolation. Audited build and catalog SHA fail closed on drift.

Final Release122/0, zero warnings/errors; focused A4/B8/C3, STA0044/0053/0062/0077/0082, all0 FAIL. Static/forms/UTF8/privacy/inventory/source gates independently run. Initial guard fixture hit expected repository disjoint guard because enclosing Studio .git was interpreted as synthetic source boundary; test-only .git marker (no initialized repository or Git command) fixes fixture boundary. Initial redirected verifier stdout locked its own inventory file; rerun captures outside owned inventory then copies closed stdout. These harness failures are not product GREEN and are not hidden as native proof.

Physical UI100/150/VS NOT_TESTED, live LM BLOCKED_LM (loaded list[]/POST0). Final manual acceptance remains required; no gate self-approval. Publication exact allowlist76, ordinary commit/non-force main push and remote/public SHA equality are final authorized steps; no following Goal/Slice.

Final publication orchestration correction: first ordinary commit36824f5 was made despite whitespace-gate failure in two new diagnostic logs. No push before repair. Original raw stdout retained privately; trailing spaces/blank EOF normalized only in those2 authored reports. A second ordinary commit records corrected evidence. No history rewrite; cumulative final index/base76 is checked before push. Production/Release/native gates unchanged.

## Historical preflight ledger

Preflight shared interfaces: A consumes PackSnapshot only; B consumes its original stamps/approvals and emits immutable receipt; C consumes receipt and independently revalidates entire stage against current source. C never changes B receipt. Source contract read: actual normalizer codepoint iteration; pattern index exact normalized phrase or first/last literal word for value placeholders; template bucket includes mature boolean. Separate contract reviewer dispatched read-only.
Unverified before edits: new native Java, all new UI controls, physical UI/DPI/VS and live LM. No inherited PASS.

A Core complete: --pss-008-a RED0/3 exit1 → GREEN3/0 exit0, 25000 entries 95ms/2012 lexical comparisons. A STA missing-form RED0/1 → GREEN1/0. Initial Build-Verify sandbox failed NuGet lock ACL; approved ordinary retry passed107/0, zero warnings/errors. No lock deletion. Independent contract review found no drift in six Ant task shapes; current build.xml SHA048a16cd53f694d85803b16af631e14fb20c0c3b49f400a749a328d572c63114, Catalog SHA b7876a52a487bc1e1bc469c9c71b47de805b31993e589b4be7ca55182c640970. Local manifest52 paths/26 pairs,130topics/139acts, SHA185cffd604a842be2ff119e9a25174d042b655ef96809af28e801d2646a8de37.
Ruling: native template selector enumeration uses global32768 bound, not historical4096 attempts — real selector unions exceed4096, deterministic sorted-ID/floorMod. B provenance is exact stamped XML rather than effective entry SourceFile, because custom can mask it. Ant whole audited build SHA additionally pinned to prevent top-level property drift. No source writes.
