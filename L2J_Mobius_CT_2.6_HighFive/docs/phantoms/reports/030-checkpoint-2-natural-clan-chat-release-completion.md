# Goal 030 CP2 — natural clan chat and release completion

Verdict: **BLOCKED — natural gate green; runtime-sync gate exhausted its permitted rerun without green**.
Continuation date: 2026-09-01. Branch: `feature/phantom-world`.
Continuation required parent: `8e826173b97d06530eec5e8f9910020ed5cf99d5` (verified local and remote HEAD before gates).
Continuation required commit subject: `fix(phantoms): complete alpha release verification`.

## Delivered block

- PhantomGoal has optional bounded UTF-8 payloadText, inner schema v2, source-compatible old constructor, payload-preserving withStatus and Conversation withRevision.
- Null is allowed; non-null must be nonblank, single-line, well-formed Unicode, <=105 code points and <=420 UTF-8 bytes. No normalization or truncation.
- Codec reads canonical v1 with null payload and canonical v2 with exact payload; writes v2, appending after reasonKey. Schema-aware canonical byte validation and trailing-byte rejection remain.
- Outer goal.runtime component schema remains 1; 4096-byte cap and DB schema are unchanged. No SQL or migration.
- clan.chat reads payloadText, falling back to key-compatible legacy acquisitionMethod, counts Unicode code points and delegates the existing idempotent postClanChat/backend seam.
- PhantomDecisionKey and PhantomDomainRef are untouched. Spaces/Cyrillic are not admitted as acquisitionMethod.
- Terminal Conversation action and exact Goal revision are persisted before runtime synchronization. BUSY/UNAVAILABLE retain that same durable entry and retry through existing delayed scheduling. FAILED becomes UNCERTAIN. No worker/timer or Decision hot-path polling.
- resolveMissingSubmittedInvitation, rejectSubmittedGoal and expireOwnedGoal retain the exact post-mutation revision until runtime acknowledgment; accept/observed terminal revisions also use the shared barrier. QUERY is prioritized ahead of waiting terminal Goal sync.

## Evidence and verification truth

No production/runtime PASS is claimed for this delivery.

Natural fixture text (90 code points; deliberately exceeds the old key cap):
`Собираемся у склада, через пять минут идём на рейд. Проверьте припасы и ждите приглашения!`

Added focused target `phantom-natural-clan-chat-goal030cp2-test`, seed 30003025:
1. Independent canonical v1 fixture; exact Russian v2 round-trip; status copy; malformed UTF-8/noncanonical/trailing-byte rejection.
2. 105 supplementary code points / 420 bytes accepted; 106 code points, blank/multiline and unpaired surrogates rejected; acquisitionMethod remains key-only.
3. Natural Russian, legacy v1 key text and supplementary payload dispatch once each through the existing fake backend; invalid direct text cannot dispatch.

Extended existing `phantom-conversation-decision-runtime-sync-goal030cp2-test`, seed 30003024:
- Existing corrected synthetic-clock cases retained.
- All three terminal routes: BUSY -> UNAVAILABLE, unchanged durable row versions, restart -> exact terminal sync -> compaction, no duplicate Party/Goal/outbound mutation.
- FAILED -> durable UNCERTAIN for all three routes; newer QUERY proceeds during blocked terminal sync.
- Existing guarded DB fixture reopens legacy v1 and v2 profile components, checks outer schema/cap, exercises payload-preserving withRevision via supersession. No provisioning added.

These are implemented test assertions, not observed runtime results.

## Historical environment-blocked gate ledger

- Default shell / apply_patch failed before access: sandbox CryptUnprotectData 2148073483. Escalated bounded shell operations worked. apply_patch attempts: 1, applied: 0; bounded incremental UTF-8 edits used instead.
- Bare ant resolution attempt: unavailable in PATH, no compiler/test started. The shell wrapper returned 0 despite command resolution failure; this is NOT a green gate.
- Module-local `.phantom-local/apache-ant-1.10.17/bin/ant.bat` invoked for the natural target, with explicit module-local build directory: exit 9009, java.exe unavailable. Compiler runs: 0; natural suite runtime runs: 0.
- JAVA_HOME was unset in process/user/machine environments; java/javac were absent from process PATH. No JDK executable/archive found within module. Requested the installed JDK 25 path; did not search outside the permitted module.
- Runtime-sync suite runs: 0. CP2 runtime runs: **0** (seed 30003002 not run).
- Actually observed CP2 E2E evidence: **none**. Population/materialization, autonomous decision, native WHISPER, real Party invitation/accept, durable runtime visibility, ITEM57 response, leave/social evidence, rematerialization and clean shutdown are not claimed.
- PASS-only 027A/CP1/conversation checkpoint2/Party/materialization/final CP2 aggregate: NOT RUN.
- Standalone ant jar: **0**, NOT RUN; plain ant verify: **0**. No forbidden historical/full aggregates, stress, soak, geodata or provisioning ran.
- Build XML parsed successfully. Bounded diff --check and delivery scope: PASS; strict UTF-8: PASS (10 files).

Exact attempted Ant command (module working directory):
`.phantom-local/apache-ant-1.10.17/bin/ant.bat -Dbuild=C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/build-goal030cp2 phantom-natural-clan-chat-goal030cp2-test`

Local log: `.phantom-local/logs/goal030cp2-completion/natural-run1.log` (not committed).

## Release matrix and unfinished work

No release-matrix delta: 11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030.
CP2 remains BLOCKED / IN_PROGRESS; Goal030 overall remains IN_PROGRESS. Goal027 ACCEPT is not reopened.
Activity-materialization remains PENDING_GOAL030:CP2; restart-failure-recovery and rollback-release-control remain pending CP3.

Unfinished: runtime-sync post-run2 fixture correction is compile-green but cannot receive a third runtime run under the task budget. CP2 remains forbidden until runtime-sync is green. Promote docs/matrix only on observed green evidence; no further product slice was started.

## Scope and process

Exact delivery allowlist (all paths relative to HighFive):
- build.xml
- java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoal.java
- java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateCodec.java
- java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java
- java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationGoalRuntimePort.java
- java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java
- test/java/org/l2jmobius/tests/phantoms/PhantomClanGoal027Checkpoint1Suite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
- docs/phantoms/reports/030-checkpoint-2-natural-clan-chat-release-completion.md

Read-first used current task/review/test matrix, named Goal/chat/runtime files, exact build/release rows and current CP2 report. No local AGENTS.md/root README or relevant current code-map/pattern file found. Historical code-map was not read. Bounded read-set extensions: local workflow/efficiency contract, ExecutionModel terminal semantics, existing test launcher/fixtures and assertion/context helper APIs. No other chronicles read or changed.

Initial read batch output was partially truncated; subsequent reads used exact symbols/ranges. One narrow goal-copy search confirmed withRevision. LF-sensitive replacement omissions were detected with targeted diff and corrected before delivery. No false compile result is reported.

Writer boundary: existing Conversation store atomic mutation, optimistic row versions, canonical Decision find/reload, existing single pulse ownership and delayed scheduling; existing synchronized Clan service and backend receipt path. Runtime synchronization exceptions fail closed to UNCERTAIN; no new writer/cache/lock added.

User's pre-existing CP1-suite change and untracked task packages were not edited/staged. Git inspection/ordinary exact-path staging, commit and push are explicitly authorized by this task. No amend/rebase/reset/force-push.

Telemetry at implementation review: goal API 150082 tokens / 1065 seconds (not final total); standalone compile 0, runtime suites 0, jar 0, verifier target runs 0.
`occurred_context_compaction: no`

Commit SHA, local HEAD, remote HEAD and push result are recorded in the final response; this report is part of that commit.

- mojibake-маркеры в изменённых файлах проверены: PASS, 10 файлов (после исправления quoting диагностической команды).
- escaped Cyrillic в изменённых файлах проверены: PASS, 10 файлов.

## 2026-09-01 verification continuation

Environment sanity:
- `java --version`: PASS, Temurin OpenJDK 25.0.4.1 LTS.
- `javac --version`: PASS, 25.0.4.1.
- `ant -version`: PASS, Apache Ant 1.10.17.
- Existing isolated DB/config/manifest used; provisioning was not run; production DB was not used.

Ordered gates:
1. `phantom-natural-clan-chat-goal030cp2-test`, seed `30003025`: **PASS 3/3**, runtime run 1. Observed legacy-v1/v2 codec, exact bounded Unicode and natural/legacy no-spam cases.
2. `phantom-conversation-decision-runtime-sync-goal030cp2-test`, seed `30003024`:
   - run 1: **FAIL 2/6**. Existing exact sync and QUERY cases passed; four new task-owned fixtures exposed cross-profile recovery counting, premature due-pulse expectations and missing required Party invite slots.
   - one task-owned fixture isolation/pulse correction applied; no production file changed.
   - run 2 (the only permitted rerun): **FAIL 3/6**. Existing cases 01-03 all passed, including BUSY retry/no duplicate Goal and QUERY priority. Terminal/payload fixture cases 04-06 remained red.
   - post-run2 source-backed fixture completion added required TARGET_PLAYER slots and allowed FAILED terminal retry to reach pulse +10. A third runtime run was intentionally not performed.
3. Post-run2 `compile-tests`: **PASS**, 2219 production + 123 test sources, two unrelated removal warnings.
4. `phantom-cross-domain-autonomous-alpha-goal030cp2-test`: **NOT RUN**, runtime runs `0`; prerequisite runtime-sync was not green.
5. PASS-only natural rerun, Goal027A consent chat, CP1 baseline, Conversation CP2, Party integration, production materialization and final CP2 aggregate: **NOT RUN**.
6. Standalone `ant jar`: **NOT RUN**, count `0`.

Actually observed CP2 E2E evidence: **none**. Population/materialization, autonomous Decision, native WHISPER, real Party invite/accept, durable runtime visibility, ITEM57 response, leave/social evidence, rematerialization and clean shutdown are not claimed.

Release matrix delta: **none**. Goal027 remains ACCEPT. Goal030 CP2 remains BLOCKED / IN_PROGRESS; activity-materialization remains PENDING_GOAL030:CP2; Goal030 overall remains IN_PROGRESS.

Continuation changed only the runtime-sync focused fixture suite and this report. Natural production implementation was not rewritten. Runtime logs are under `.phantom-local/logs/goal030cp2-verification/` and are not committed.

`occurred_context_compaction: no`
Final continuation static checks: git diff --check PASS; exact two-file task scope confirmed; strict UTF-8 PASS; mojibake markers PASS; escaped Cyrillic PASS.

## Explicitly authorized additional runtime-sync run

Required parent/current remote before run: `8c632197e050e81516a0e567962118b596286144`.

Exactly one additional `phantom-conversation-decision-runtime-sync-goal030cp2-test` run was executed with seed `30003024`, using the already committed compile-green post-run2 fixture correction.

Result: **FAIL 4/6**, `BUILD FAILED`.
- PASS: 01 external Goal mutation -> exact Decision runtime sync.
- PASS: 02 BUSY reload retry without duplicate Goal.
- PASS: 03 newer QUERY not starved by submitted Goal.
- FAIL: 04 terminal BUSY/UNAVAILABLE/restart — `Terminal route did not abandon its owned goal. Expected <ABANDONED> but was <ACTIVE>.`
- FAIL: 05 terminal FAILED -> UNCERTAIN — `java.util.NoSuchElementException: No value present` while reading the expected terminal receipt.
- PASS: 06 legacy/v2 payload profile reopen and withRevision preservation.

Observed state changed from the preceding run only by case 06 becoming green; cases 04 and 05 remained reproducibly red. No subsequent runtime-sync run, fixture correction or production change was made, as required.

CP2 runtime run count remains `0`; CP2 E2E evidence remains none. PASS-only final gates and aggregate were not run. Jar count remains `0`. Release matrix remains unchanged: Goal027 ACCEPT; Goal030 CP2 BLOCKED / IN_PROGRESS; activity-materialization PENDING_GOAL030:CP2; Goal030 overall IN_PROGRESS.

Authoritative log: `.phantom-local/logs/goal030cp2-verification/04-runtime-sync-authorized-additional.log` (local, not committed).

Current unfinished blocker for independent review: explain why terminal route fixture retains ACTIVE owned Goal in case 04 and why case 05 has no terminal receipt. The task forbids masking these failures with another correction/run.

`occurred_context_compaction: no`