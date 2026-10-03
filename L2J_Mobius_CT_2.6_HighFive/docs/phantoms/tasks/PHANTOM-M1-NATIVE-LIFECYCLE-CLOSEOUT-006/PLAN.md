# Native lifecycle closeout — implementation plan

> For agentic workers: use superpowers:executing-plans (or existing supported executor),
> systematic-debugging and test-driven-development. These are internal workstreams,
> not a demand for separate Codex chats or user approvals between every test.

**Goal:** исправить единый класс native lifecycle/session corruption и ложной M1 приёмки.
**Architecture:** existing native pools + exact ownership; b4f persistence preserved.
**Tech stack:** Java25, Ant, MariaDB guarded TEST, existing PowerShell Pilot.
**Spec:** TASK.md и DESIGN.md. Baseline: 461a4abe.

## Global constraints

Только High Five. PLAY SELECT/read-only, current JVM untouched. No deploy/synthetic/arm.
Чужой diff сохранить. No global verify storm; focused+affected once, controlled RED loops.
No architecture replacement, new combat engine, reward/reset bypass, population reduction.

## Review focus

Late managed tick after detach; P1 failure after P2 publication; partial AutoUse pair;
finally masks Error/primary; delayed native reward after immutable store; failed actor
excluded from cohort. Каждый пункт имеет тест в SCENARIOS, не только static assertions.

## Workstream 0 — read-first / evidence boundary

Files: previous local reports; SOURCE_MAP entries; AGENTS; local git/staged status.
- [ ] Зафиксировать baseline/local SHA, clean-JAR claim provenance и current JVM identity.
- [ ] Прочитать локальный missing SUMMARY и сравнить published8files vs reported11files.
- [ ] Сохранить sanitised READ_FIRST.md с evidence gaps, без секретов/arm tokens.
- [ ] Сопоставить реальные test selectors C/D по локальному отчёту; составить COMMANDS.txt.
- [ ] Никаких PLAY action commands; old exceptions искать только existing logs/read snapshots.

## Workstream 1 — RED matrix и native pool ownership

Modify: Player.java; AutoPlayTaskManager.java; AutoUseTaskManager.java;
PhantomVisibleAutoPlay.java. Tests: PhantomBackgroundSuite.java и при необходимости
новый `test/java/org/l2jmobius/tests/phantoms/PhantomM1NativeLifecycleSuite.java` с launcher.
- [ ] P01–P09 воспроизводят current defects на actual managers; зафиксировать correct RED.
- [ ] Не считать model/proofs или вызов собственного helper тестом production run-loop.
- [ ] Sticky managed flag и fail-closed before stock branch.
- [ ] Conditional expected stops, synchronized registration mutation, lock-order proof.
- [ ] Session owns policy; exact health both pools; stale publication/rollback guarded.
- [ ] GREEN P01–P09; stock REAL/offline/nonphantom synthetic positive controls.

## Workstream 2 — first-exception lifecycle / forensic evidence

Modify: PhantomMaterializedPlayer, MaterializationService, Player store/resume,
PhantomSystem; tests ProductionMaterializationSuite/BackgroundSuite/LocalPlayPilotSuite.
New optional bounded value class: `phantoms/player/PhantomCleanupIncident.java`.
- [ ] E01–E06 RED before fix; simultaneous primary and finally errors in actual boundary.
- [ ] Preserve primary/suppressed in all listed finally/abort paths.
- [ ] first/latest detached facts, limited archive, cause/suppressed/truncation/log-once.
- [ ] Census incident link within existing payload budget; offline parse tests updated.
- [ ] GREEN; cleanup identity ownership remains fail closed and idempotent.

## Workstream 3 — terminal travel resolution

Modify: PhantomVisibleFarmTravel, PhantomHistoricalBackgroundService (если требуется),
PhantomSystem callback. Tests actual native executor and existing travel suites.
- [ ] T01–T05 RED for missing terminal reason and same-revision reset-loop.
- [ ] Typed outcome explicit at every terminal producer, no silent default/whitelist.
- [ ] Existing route failure replan and transient cooldown bounded; no permanent geo edits.
- [ ] Verify actual production callback and goal revision continuation to native AutoPlay.
- [ ] GREEN, plus normal gatekeeper/walk/same-player continuity regression.

## Workstream 4 — native quiescence / persistence

Modify ONLY after correct RED: existing native teardown seam in PhantomMaterializedPlayer,
owned boundary diagnostics in PhantomBackgroundService; Player-specific minimal seam.
- [ ] Q01–Q05 run via native attack/cast/reward/cleanup composition, not manual EXP only.
- [ ] If current path already safe, record PASS_WITHOUT_PATCH; no speculative rewrite.
- [ ] If failing, establish exact writer/callback ordering and minimal native fix before
  immutable PREPARE; preserve previously earned reward and existing retained behavior.
- [ ] owned C/D/restart/abort/idempotence + mismatch negative controls GREEN.
- [ ] If this needs a broad new native concurrency architecture: BLOCKED with exact
  reproducer and remaining design decision, not an unsafe fallback or hidden runtime fix.

## Workstream 5 — acceptance tied to useful progress

Modify: PhantomSystem census; relevant LocalPlay passive observation/action snapshots;
Run-M1RuntimeHandoff.ps1; Test-M1ObserverOffline.ps1; LocalPlayPilotSuite.
New optional pure helper `localplay/LocalPlayM1NativeEvidence.java`.
- [ ] A01–A08 RED on current grading, preserve exact 4-profile denominator.
- [ ] Passive exact native damage + EXP/SP/kill-reward evidence, bounded lifecycle identity.
- [ ] Separate selected proof; typed cohort health, no failed-profile exclusion.
- [ ] End-to-end native sensor TEST and offline negative controls GREEN.

## Workstream 6 — integration / publication

- [ ] Выполнить VERIFY.md focused+affected in one planned Ant invocation; сохранить logs.
- [ ] Review all changed production code, lock order, ownership, first exception, false PASS.
- [ ] Запустить negative controls, затем исправления review и только затронутые regressions.
- [ ] Exact-path commit/push; verify remote tree includes RESULT/HANDOFF and source.
- [ ] Clean build из committed production SHA в отдельном build output; record SHA256 JAR.
- [ ] Если RESULT обновлён отдельным docs-only commit, явно различать codeSHA/reportSHA;
  deployment всегда exact SHA с доказанным совпадением sources и JAR, не «примерно HEAD».
- [ ] STOP: READY_FOR_CONTROLLED_DEPLOY; M1_OPEN, либо BLOCKED. Next chat/runtime только
  после отдельной проверки; не спрашивать «разрешите #5» вместо выполнения этого пакета.
