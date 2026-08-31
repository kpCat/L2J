# Goal 030 CP2 — natural clan chat and release completion

Verdict: **BLOCKED — implementation delivered, runtime verification unavailable**.
Date: 2026-08-31. Branch: `feature/phantom-world`.
Required parent: `0e6ef8c00cdec8755c4c93991dd7d2aed87a04da` (verified local HEAD before changes).
Required commit subject: `fix(phantoms): complete clan chat and alpha release gate`.

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

## Gate ledger and blocker

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

Unfinished: provide JDK 25 executable path, run natural gate, then existing runtime-sync gate (with its one allowed correction/rerun), then CP2 run1; only after PASS run the requested release regressions, final aggregate and exactly one jar. Promote docs/matrix only on observed green evidence. Compilation and behavioral correctness remain unverified until these gates run. No further product slice was started.

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
