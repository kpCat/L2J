# Goal 028 Checkpoint 5 — deterministic selected-decision diagnostic replay

## Status

- Delivery status: `SUCCESS`.
- Goal 028 Checkpoint 1: `ACCEPT`.
- Goal 028 Checkpoint 2: `ACCEPT`.
- Goal 028 Checkpoint 3: `ACCEPT`.
- Goal 028 Checkpoint 4: `ACCEPT`.
- Goal 028 Checkpoint 5: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS_PENDING_CP5_INDEPENDENT_REVIEW`.
- Required parent: exact `c0c92fb33692cb7d078fc1a0fd22e897ea85e0dc`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Реализован только deterministic replay замороженных selected-decision diagnostic observations. Это не gameplay/world/packet replay: replay проверяет порядок retained frames, structural unchanged age, shared CP3 health, bounded top-8 candidate evidence и canonical digest, не выполняя Decision planner/selector/handlers или доменные действия.

Operator flow:

1. `//phantom trace <profileId>`;
2. `//phantom replay capture`;
3. опционально `//phantom drain` или `//phantom disable`;
4. `//phantom replay run`;
5. `//phantom replay clear`.

Frozen bundle хранится в одном process-static synchronized slot вне configured `PhantomSystem`, поэтому успешный drain/disable с `_configuredInstance=null` его не удаляет. JVM restart очищает slot естественно; DB/config/files не используются.

## Changed files

1. `build.xml` — один focused CP5 target с fixed seed `28002805`.
2. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java` — exact replay capture/run/clear и bounded summary без frame dump.
3. `docs/PHANTOM_BOTS_ROADMAP.md` — только exact Goal028 truth: CP1–CP4 ACCEPT, CP5 pending independent review, overall pending CP5 review.
4. `java/org/l2jmobius/gameserver/phantoms/PhantomDecisionHealthModel.java` — smallest pure shared CP3 fingerprint/health model.
5. `java/org/l2jmobius/gameserver/phantoms/PhantomDecisionReplay.java` — immutable schema-v1 validation, structural replay, candidate tri-state и canonical digest.
6. `java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java` — parallel bounded retained-frame metadata и immutable capture.
7. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — один process-level bundle slot и typed operator facades.
8. `test/java/org/l2jmobius/gameserver/phantoms/PhantomDeterministicDecisionReplayGoal028Checkpoint5Suite.java` — 7 focused deterministic scenarios.
9. `test/java/org/l2jmobius/tests/phantoms/PhantomSelectedSlowStuckDiagnosticsSuite.java` — CP3 static regression переведён на обязательный shared model.
10. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — один CP5 mode.
11. `docs/phantoms/reports/028-checkpoint-5-deterministic-selected-decision-replay.md` — этот отчёт.

User-owned untracked task packages оставлены read-only и не входят в commit.

## Bundle bounds and lifecycle

- ровно один frozen bundle process-wide;
- ровно один positive profile ID;
- `1..64` frames, oldest-to-newest;
- максимум 8 candidate explanations на frame;
- immutable `List.copyOf` bundle/frame evidence;
- live trace остаётся one-profile ring capacity 64 с accepted history API;
- observation logical nanos, unchanged age nanos и health хранятся параллельно только retained slots под existing trace monitor;
- switch/clear очищает live history и metadata; same-profile reselect не сбрасывает baseline;
- первый retained frame capture нормализуется в relative time `0`, но сохраняет nonzero pre-eviction unchanged age;
- successful capture атомарно replaces slot; failed capture не меняет prior bundle; explicit clear удаляет;
- start/enable/drain/disable не очищают frozen slot; JVM restart очищает static state естественно.
## Shared health replay

`PhantomDecisionHealthModel` является единственным source of truth для live trace и replay:

- fingerprint включает structural reason fields: goal id/revision/status, runtime state, selected candidate/score, plan/step/attempt, last result, reason key, bounded candidates;
- fingerprint исключает `decisionSequence`, `activityState` и `goalType`;
- unchanged fingerprint добавляет logical delta saturating; changed fingerprint сбрасывает expected age в `0`;
- first retained frame использует captured nonzero age как initial evidence;
- thresholds сохранены exact: `SLOW=5000ms`, `STUCK=30000ms`;
- `WAITING_RETRY` всегда `WAITING` независимо от age;
- explicit-reload persistence states дают immediate `ATTENTION`;
- no-goal/terminal/detached operator view остаётся `IDLE`;
- replay сравнивает captured age и health, останавливается на первом несовпадении с bounded reason.

Goal028A prefilter остаётся до observer monitor/`RuntimeSnapshot` build; CP1/CP3 final gates подтвердили контракт.

## Candidate tri-state

Перед проверкой winner replay требует canonical explanation order `score DESC, candidateKey ASC` и unique visible keys.

- visible selected `ELIGIBLE`, exact score, no visible outranker — `VERIFIED`;
- visible selected contradiction или visible eligible outranker — `MISMATCH`, replay `FAIL`;
- selected отсутствует в top-8 — `UNVERIFIABLE`, не mismatch;
- selected null при visible eligible — `MISMATCH`, replay `FAIL`;
- selected null без visible eligible — `UNVERIFIABLE`;
- noncanonical explanation order — first-frame mismatch/fail.

`PhantomUtilitySelector.select()` не вызывается: исходные consideration inputs отсутствуют.

## Canonical digest

SHA-256 вычисляется из explicit fixed-order binary encoding через `DataOutputStream`:

- schema/profile/thresholds/frame count;
- каждый frame: relative time, captured age, captured health;
- все `DecisionView` и candidate fields в фиксированном порядке;
- nullable values имеют presence flag;
- strings имеют signed int byte length и UTF-8 bytes;
- enums кодируются explicit `name()` strings;
- result — ровно 64 lowercase hex chars.

Java serialization, `hashCode`, `toString`, JSON и unordered map formatting не используются. Два replay одного frozen bundle дают equal result и digest независимо от current clock/runtime; изменение одного encoded structural field меняет digest.

## Capture → drain → run proof

Focused process scenario выполнил:

- diagnostics-enabled running configured fixture;
- selected trace + retained history;
- `operatorReplayCapture()` => `CAPTURED`;
- `operatorDrain()` => `DRAINED`, `_configuredInstance == null`;
- `operatorReplayRun()` => `REPLAY_PASS` с теми же profile/frame count/digest;
- `operatorReplayClear()` после drain => `CLEARED`;
- следующий run => `NO_CAPTURE`.

Отдельный scenario подтвердил: failed no-selection capture сохраняет A byte-for-byte/digest-equivalent; успешный B заменяет A; clear удаляет B.

## No-action proof

Replay production path не вызывает и не содержит execution seams для:

- planner/plan/handler execute;
- `PhantomUtilitySelector.select()`;
- reload/replan/setGoal/clearGoal;
- operator enable/drain/disable;
- economy mutation;
- navigation/combat/chat/domain actions;
- DB/repository;
- threads/timers/scheduled work/polling/global scan.

Pure replay имеет только bundle argument и локальные bounded collections. Process facade читает/заменяет один frozen slot. Admin renderer выводит максимум три summary строки и не обходит frames/history.
## Commands and test results

Baseline:

- `git status --short --branch` — branch/upstream и user-owned untracked packages зафиксированы;
- `git rev-parse HEAD` — exact required parent PASS;
- `git rev-parse --abbrev-ref HEAD` — `feature/phantom-world` PASS;
- upstream — `origin/feature/phantom-world` PASS.

Development checks без `jar`:

- два `compile-tests` — PASS;
- первый CP5 attempt остановился на compile-tests: пять test-fixture call sites передали `DecisionView` вместо existing `RuntimeSnapshot`; production compile прошёл, test execution не начался;
- fixture исправлен только через existing local `runtime(view)` helper;
- повторный CP5 — PASS, 7/7.

Final exact ordered Ant invocation:

`phantom-deterministic-replay-goal028cp5-test phantom-operator-observability-goal028cp1-test phantom-operator-runtime-controls-goal028cp2-test phantom-selected-slow-stuck-goal028cp3-test phantom-economic-audit-goal028cp4-test phantom-decision-core-test jar`

Results:

1. CP5 replay — PASS, 7/7, seed `28002805`.
2. CP1 observability — PASS, 6/6, seed `20260725001`.
3. CP2 runtime controls — PASS, 6/6, seed `20260725001`.
4. CP3 slow/stuck — PASS, 6/6, seed `20260725001`.
5. CP4 economic audit — PASS, 6/6, seed `20260725001`.
6. Exact DecisionEngine core — PASS, 35/35, seed `20260725001`.
7. Ровно один final `jar` после final source changes — PASS; LoginServer/GameServer/DatabaseInstaller jars built, server jars copied в `dist/libs`.
8. Total final Ant time: 1 minute 49 seconds.

DB/domain/performance/stress/soak/gameplay replay gates не запускались согласно TASK.

## DB, config, persistence and performance

Production/test DB не открывались и не изменялись. Новые schema/migration/config/file persistence artifacts отсутствуют. Frozen bundle process-local и bounded. Performance/stress/soak запрещены scope и не запускались; structural bounds доказаны focused suite.

## Static, encoding and scope

- `apply_patch` не вызывался;
- правки выполнены small unique exact-anchor UTF-8-no-BOM temp + atomic moves;
- `git -c core.whitespace=cr-at-eol diff --check` — PASS;
- exact changed allowlist и no-other-chronicle scope — PASS;
- generated jars не входят в Git diff;
- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены;
- UTF-8 BOM в changed files — отсутствует;
- temporary `*.028cp5.tmp` artifacts — отсутствуют;
- user task packages read-only и вне staging.

## Deviations, limitations and risks

- Первый focused CP5 compile выявил только test-fixture type mismatch; production source не был оставлен некомпилируемым, fixture исправлен до final gates.
- Replay подтверждает достаточность только retained diagnostic evidence. `UNVERIFIABLE` явно не является доказательством правильного winner вне top-8.
- Replay не воспроизводит world state, RNG, packets, inventory или gameplay effects.
- Independent review CP5 обязателен; Goal028 намеренно не помечен `ACCEPT`.

## Git and delivery

Git-команды разрешены TASK для exact parent/branch/upstream, bounded diff/scope, ordinary atomic commit и push. Использованы только baseline status/rev-parse, bounded diff/name/status/diff-check, exact allowlist add, staged verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force-push не используются.

Commit subject: `feat(phantoms): add deterministic decision replay`.

Commit SHA и push result фиксируются в финальном сообщении после ordinary commit/push: self-referential SHA невозможно записать внутрь того же atomic report-bearing commit.

## Next step

Independent review Goal 028 Checkpoint 5. До acceptance CP5 остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, а Goal028 — `IN_PROGRESS_PENDING_CP5_INDEPENDENT_REVIEW`; Goal028 `ACCEPT` не выставляется.