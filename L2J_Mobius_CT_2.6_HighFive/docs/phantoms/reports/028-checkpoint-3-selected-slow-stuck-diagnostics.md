# Goal 028 Checkpoint 3 — selected Phantom slow/stuck diagnostics

## Status

- Delivery status: SUCCESS.
- Goal 028 Checkpoint 1: `ACCEPT`.
- Goal 028A: `ACCEPT`.
- Goal 028 Checkpoint 2: `ACCEPT`.
- Goal 028 Checkpoint 3: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `91c2d13b35738dc28bf481ddf34a568fb6eb02a5`.
- Branch: `feature/phantom-world`.
- occurred_context_compaction: no.

## Summary

Реализована только read-only диагностика health выбранного Phantom: `IDLE`, `HEALTHY`, `WAITING`, `SLOW`, `STUCK`, `ATTENTION`. Модель использует monotonic logical time, structural progress fingerprint и один существующий selected trace ring capacity 64.

Automatic remediation, economic audit, replay, scale/soak, DB/domain actions, новые threads/timers/polling и diagnostic persistence не добавлялись. CP2 controls сохранены без изменения семантики.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java` — существующий abstract two-argument `DecisionObserver.onDecision` сохранён; добавлен default richer callback; observer получает `workItem.logicalNowNanos()` после cheap `interested(profileId)` prefilter и RuntimeSnapshot build только для выбранного profile.
2. `java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java` — deterministic health model, injectable thresholds/`LongSupplier`, structural fingerprint, age, attachment truth; one-profile ring 64 сохранён.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — existing snapshot проверяет live attachment выбранного profile и не показывает detached selection как live STUCK.
4. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java` — existing `status`/`trace` compact output дополнен `attached`, `health`, `ageMs`, `slowMs`, `stuckMs` и goal revision.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomSelectedSlowStuckDiagnosticsSuite.java` — 6 focused deterministic no-sleep scenarios.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — CP3 focused route.
7. `build.xml` — один CP3 focused Ant target.
8. `docs/PHANTOM_BOTS_ROADMAP.md` — только Goal028 status truth: CP2 ACCEPT, CP3 pending independent review, overall IN_PROGRESS.
9. `docs/phantoms/reports/028-checkpoint-3-selected-slow-stuck-diagnostics.md` — этот отчёт.

User-owned task packages read-only и не входят в changed/staged allowlist.

## Health and progress fingerprint semantics

| Condition | Health |
|---|---|
| selection отсутствует, current отсутствует, profile detached, goal отсутствует или terminal | `IDLE` |
| active structural state моложе slow threshold | `HEALTHY` |
| runtime `WAITING_RETRY` при любом age | `WAITING` |
| active structural state неизменен не менее slow и менее stuck threshold | `SLOW` |
| active structural state неизменен не менее stuck threshold | `STUCK` |
| `PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD` или `PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD` | immediate `ATTENTION` |

Fingerprint состоит только из bounded structural reason-view fields: goal id/revision/status, runtime state, selected candidate/score, plan id, step, attempt, last result, reason key и максимум 8 candidate explanations. `decisionSequence`, activity state и goal type в fingerprint не входят. Поэтому одинаковая структура при растущем `decisionSequence` стареет до `SLOW` и `STUCK`.

Selection switch и clear очищают history/counters и baseline. Повторный select того же profile не сбрасывает age при неизменной структуре. Любое изменение fingerprint сбрасывает baseline. Detached profile возвращает `attached=false`, `health=IDLE`, `ageMs=0`.

## Threshold and time proof

- Production defaults: `slow=5000ms`, `stuck=30000ms`; constructor validates `0 < slow < stuck`.
- Test seam injects both thresholds and `LongSupplier`; no test sleeps.
- DecisionEngine forwards canonical `PhantomActivityWorkItem.logicalNowNanos()` through the richer callback.
- Trace clamps observed/read time monotonically; age is measured from the last structural fingerprint change.
- Deterministic boundary proof: unchanged structure is `HEALTHY` at 4999ms, `SLOW` at 5000ms and `STUCK` at 30000ms while `decisionSequence` rises from 1 to 4.
- Structural step/revision changes reset age to 0.
- `WAITING_RETRY` remains `WAITING` at 120000ms; persistence reload-required states are `ATTENTION` immediately.

## Cheap prefilter and bounds proof

`notifyObserver` retains the Goal028A ordering:

1. observer null check;
2. `_observer.interested(workItem.profileId())`;
3. only for interested profile: engine monitor, slot lookup and `snapshotLocked(slot)` allocation;
4. callback outside engine monitor.

Production `interested(profileId)` remains a primitive selected-id check. The richer callback did not move or weaken the prefilter. Selected trace still stores exactly one selected profile, fixed ring capacity 64 and at most 8 existing candidate explanations. No unbounded profile scan or collection was added.

## No-remediation proof

Diagnostics production source contains no calls to reload, replan, setGoal, clearGoal, operator enable/drain/disable or domain actions. Static scan for those calls and for `new Thread`, `Timer`, `Scheduled`, `poll(` and `Thread.sleep` returned no matches.

There are no new threads, timers, workers, futures, polling loops, persistent stores, DB access, config keys or automatic actions. Health evaluation only reads selected trace state, monotonic time and current attachment truth.

## Tests and commands

Baseline:

- `git status --short --branch` — branch/upstream and user-owned untracked task packages recorded.
- `git rev-parse HEAD` — exact required parent PASS: `91c2d13b35738dc28bf481ddf34a568fb6eb02a5`.
- `git branch --show-current` — `feature/phantom-world` PASS.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world` PASS.

Development focused run after initial implementation:

- `phantom-selected-slow-stuck-goal028cp3-test` — PASS, 6/6, seed `20260725001`; no `jar` target.

Final exact Ant sequence:

`phantom-selected-slow-stuck-goal028cp3-test phantom-operator-observability-goal028cp1-test phantom-operator-runtime-controls-goal028cp2-test phantom-decision-core-test jar`

- CP3 focused — PASS, 6/6, seed `20260725001`:
  - default thresholds and source-compatible observer;
  - rising decision sequence ages to slow/stuck;
  - structural progress resets baseline;
  - waiting suppression and immediate persistence attention;
  - idle/switch/clear/detached behavior;
  - bounds/admin/no-remediation source contracts.
- CP1 focused — PASS, 6/6.
- CP2 focused — PASS, 6/6.
- Exact DecisionEngine core — PASS, 35/35.
- Final `jar` target invoked exactly once in the task — PASS; LoginServer.jar, GameServer.jar and DatabaseInstaller.jar built, working LoginServer.jar/GameServer.jar copied to `dist/libs`.
- Total final Ant time: 1 minute 2 seconds.

No DB/domain/broad/performance/stress/soak/economic/replay gates were run.

## Static, encoding and scope checks

- `git -c core.whitespace=cr-at-eol diff --check` — PASS.
- Strict UTF-8 decode implementation/test/roadmap allowlist — PASS, 8/8 before report; report checked after creation.
- UTF-8 BOM — 0.
- mojibake-маркеры в изменённых файлах проверены — PASS, 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены — PASS, 0 совпадений.
- Temporary `*.028cp3.tmp` artifacts — 0 after report promotion.
- Exact final allowlist — 9 files including report.

## DB, configs, performance and limitations

- Production/test DB were not used or changed.
- No migrations, tables, config keys or persistence were added.
- Thresholds are code defaults with injectable test seam and are observational only.
- Performance/scale/soak measurements were explicitly out of scope and not run.
- Health advances only when an operator reads status/trace or a selected-profile decision pulse is observed; there is intentionally no background timer or polling.
- CP3 remains pending independent review; Goal028 remains in progress because economic audit and replay are outside this checkpoint.

## Deviations

- `apply_patch` was not invoked. All edits used bounded unique exact-anchor UTF-8-no-BOM temporary files and atomic promotion.
- Two early selected-trace edit attempts stopped before target write: one PowerShell here-string parser error and one accidental collision with the built-in `R` alias; a subsequent exact-anchor guard detected LF line endings. Changes were then applied with the actual newline contract.
- Three combined encoding-check attempts ended in PowerShell parser errors before checks ran because literal mojibake/escaped-XML patterns interacted with command parsing. The checks were rerun separately with code-point regexes and completed successfully.
- No production compile/test failure occurred. One focused development CP3 run preceded the exact final gate sequence and did not invoke `jar`.

## Git and delivery

Git commands were used only because TASK explicitly requires exact parent/branch/upstream, bounded diff/scope verification, ordinary atomic commit and push. Commands used: baseline `git status`, `git rev-parse`, `git branch`; bounded `git diff`, `git diff --name-only`, `git diff --check`; exact allowlist `git add`; staged status/diff verification; ordinary `git commit`; `git push`.

Commit subject: `feat(phantoms): add slow stuck diagnostics`.

Commit SHA: recorded in final delivery because the report-bearing atomic commit cannot contain its own SHA.

Push result: recorded in final delivery after ordinary push.

No amend/rebase/reset/squash/merge/force-push.

## Next step

Independent review Goal 028 Checkpoint 3. Checkpoint 3 remains `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal028 remains `IN_PROGRESS`.
