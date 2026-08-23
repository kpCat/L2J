# Goal 028 Checkpoint 2 — safe operator runtime controls

## Status

- Delivery status: SUCCESS.
- Goal 028 Checkpoint 1: `ACCEPT`.
- Goal 028 Checkpoint 2: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `e2246966499b090c639d15abcd3b2ea4cdadec7c`.
- Branch: `feature/phantom-world`.
- occurred_context_compaction: no.

## Summary

Добавлены только process-local runtime controls `//phantom enable`, `//phantom drain` и `//phantom disable`. Intent хранится статически в JVM как `AUTO`, `ENABLED`, `DRAINED` или `DISABLED`, не записывается в config/DB и после полного JVM restart снова начинается с `AUTO`, то есть следует `PhantomPlayers.ini`.

`drain` и `disable` не создают новый lifecycle engine: оба проходят через один configured-owner helper, который вызывает существующий canonical `PhantomSystem.shutdown()`. `_configuredInstance` очищается только после actual `State.STOPPED`. Failed shutdown сохраняет owner, requested off-intent и desired-vs-actual status; `enable` поверх такого owner возвращает `OWNER_BUSY` и не меняет retained off-intent.

Stuck/slow policy, economic audit, replay, scale/soak, DB и domain lifecycle contracts не затрагивались.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — process-local mode, typed control results, shared configured-start helper, canonical shutdown wrapper, desired-vs-actual status и узкие test helpers.
2. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java` — три команды и bounded typed result/status messages; status/trace сохранены.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomOperatorRuntimeControlsSuite.java` — 6 focused compound CP2 scenarios.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — focused suite route.
5. `build.xml` — один focused CP2 Ant target.
6. `docs/PHANTOM_BOTS_ROADMAP.md` — только status truth: CP1 ACCEPT, CP2 implemented pending review, Goal028 in progress.
7. `docs/phantoms/reports/028-checkpoint-2-operator-runtime-controls.md` — этот отчёт.

`GameServer.java`, `AdminCommands.xml`, admin family/access/XSD, domain services и user task packages не изменялись.

## Operator state and result semantics

| Action/state | Result | Desired mode | Actual ownership |
|---|---|---|---|
| startup в `AUTO`, config enabled | existing `startConfigured()` result | `AUTO` | canonical configured runtime |
| `enable`, config disabled, owner absent | `CONFIG_DISABLED` | `ENABLED` | runtime absent; config guard не обходится |
| `enable`, owner RUNNING | `ALREADY_RUNNING` | `ENABLED` | тот же owner/runtime |
| `enable`, owner FAILED/non-stopped | `OWNER_BUSY` | retained prior mode | owner retained, duplicate запрещён |
| `drain`, successful shutdown | `DRAINED` | `DRAINED` | owner cleared only after STOPPED |
| repeated drained/no owner | `ALREADY_DRAINED` | `DRAINED` | runtime absent |
| `disable`, successful/already stopped | `DISABLED` / `ALREADY_DISABLED` | `DISABLED` | runtime absent |
| drain/disable shutdown failure | `SHUTDOWN_FAILED` | requested off-mode | owner retained, actual state exposed |

`desiredRuntimeEnabled` истинен только при enabled config и mode `AUTO`/`ENABLED`. `OperatorStatus` одновременно показывает configured flag, operator mode, desired running permission, configured-owner presence и actual runtime state.

## Canonical lifecycle proof

- Configured construction sequence `new PhantomSystem(PhantomPlayersConfig.settings(), true)` остаётся ровно в одном `startConfiguredInternal()`.
- Обычный `startConfigured()` и operator `enable` вызывают этот же helper; construction sequence не копировалась.
- `shutdownIfStarted()`, `drain` и `disable` используют один `shutdownConfiguredInstance()`.
- Внутри него выполняется direct `configured.shutdown()`; отдельной quiesce/drain orchestration нет.
- Clear owner расположен после проверки `configured.snapshot().state() == State.STOPPED`.
- Failed/non-stopped owner остаётся в `_configuredInstance`; enable проверяет actual state до config/start helper и возвращает `OWNER_BUSY`.
- `GameServer` сохраняет exact startup call `PhantomSystem.startConfigured()`; startup block не изменён.

## Admin contract

Existing native family остаётся `admin_phantom`. Existing `//phantom status`, `//phantom trace <profileId>` и `//phantom trace clear` не изменены функционально. Добавлены только:

- `//phantom enable`;
- `//phantom drain`;
- `//phantom disable`.

Каждая control-команда возвращает одну bounded строку с `result`, `desiredMode`, `desiredRunning`, `runtimeConfigured` и actual `runtime`. Status дополнен `operatorMode` и `desiredRunning`. `AdminCommands.xml`, access level, confirm contract и XSD не менялись.

## Tests and commands

Baseline checks:

- `git status --short --branch` — branch/upstream и user-owned untracked packages зафиксированы.
- `git rev-parse HEAD` — exact required parent PASS.
- `git branch --show-current` — `feature/phantom-world` PASS.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'` — `origin/feature/phantom-world` PASS.

Development compile:

- первый `compile-tests` — FAIL до test execution: record accessor затенил static helper; исправлено единственной qualification;
- повторный `compile-tests` — PASS, 2204 production + 106 test sources.

Первая final-sequence attempt остановилась в CP2 до CP1 и до `jar`: 2/6 PASS, 4/6 FAIL из-за отсутствующего global `ThreadPool` в focused JVM у synthetic fixture. Production path не падал и jar не вызывался. Fixture переведён на существующий package-private no-schedule `PhantomScheduler` test constructor; domain lifecycle не менялся.

Финальная exact sequence:

`& '.\.phantom-local\apache-ant-1.10.17\bin\ant.bat' phantom-operator-runtime-controls-goal028cp2-test phantom-operator-observability-goal028cp1-test phantom-skeleton-test jar`

- CP2 focused — PASS, 6/6, seed `20260725001`:
  - config guard;
  - idempotent enable/no duplicate;
  - drain success/start gate;
  - disable/repeated disable/explicit enable config guard;
  - failed shutdown owner+intent retention and canonical retry;
  - admin/canonical source contract.
- CP1 focused — PASS, fail-on-error target completed before later skeleton/jar targets; existing suite remains 6 compound scenarios.
- Exact PhantomSystem disabled/lifecycle skeleton — PASS, 14/14, seed `20260725001`; сохранённый report подтверждает `total=14`, `passed=14`, `failed=0`.
- Exact server-shutdown regression target не запускался: focused CP2 deterministic retained-owner scenario полностью закрыл требуемый regression без DB/domain suite.
- `jar` достигнут и выполнен ровно один раз за задачу: первая failed sequence остановилась до jar; final sequence обновила `dist/libs/GameServer.jar` и `dist/libs/LoginServer.jar` в `2026-08-23 11:54:25`.

DB/domain/broad/performance/stress/soak/replay/economic gates не запускались.
## DB, configs, persistence and performance

- Production/test DB не использовались и не изменялись.
- Миграции, таблицы и config keys не добавлялись.
- Operator intent не сериализуется и не переживает JVM restart.
- Persistent profiles/goals/components не меняются controls.
- Performance/scale/soak measurements запрещены scope и не запускались.
- Новых threads, timers, futures, queues, persistence stores или polling нет.

## Static, encoding and scope checks

- `git -c core.whitespace=cr-at-eol diff --check` — PASS.
- Exact changed allowlist — PASS: 7 файлов, только runtime/admin/focused harness/roadmap/report.
- Strict UTF-8 decode changed allowlist — PASS.
- UTF-8 BOM — 0.
- mojibake-маркеры в изменённых файлах проверены — PASS, 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены — PASS, 0 совпадений.
- Temporary `.028cp2.*.tmp` artifacts — 0 после atomic promotion.
- User-owned task packages остаются untracked/read-only и не входят в staging.

## Deviations, limitations and risks

- `apply_patch` не вызывался. Использованы только bounded unique exact-anchor UTF-8-no-BOM temp + atomic replacements; новый suite/report создавались bounded chunks с atomic promotion.
- Два PowerShell edit commands завершились parser error до file access/write; три roadmap attempts завершились на exact-anchor guards до write. Partial target files не создавались.
- Один initial PhantomSystem anchor ожидал CRLF, но файл использовал LF; guard остановил write, повтор использовал фактический anchor.
- Первый compile-only run и первая CP2 attempt выявили только локальные compile/test-fixture defects; обе причины исправлены до final gates.
- Test-only shutdown failure flag default false и доступен только через package-private configured testing helpers. Он не меняет production shutdown path при обычной работе и нужен для deterministic no-DB proof retained-owner semantics.
- Operator intent process-local: внешний process/JVM restart намеренно сбрасывает mode в `AUTO`; это требуемая, а не durable, семантика.

## Git and delivery

Git использовался только для explicit required baseline, bounded diff/scope/whitespace checks, staging, ordinary commit и push. Использованные команды перечислены по назначению: `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'`, bounded `git diff`, `git diff --name-only`, `git diff --check`, затем exact allowlist `git add`, staged diff/scope check, ordinary `git commit` и `git push`.

Commit subject: `feat(phantoms): add operator runtime controls`.

Commit SHA: this atomic report-bearing commit; exact SHA фиксируется в финальном сообщении после commit.

Push result: фиксируется в финальном сообщении после push.

No amend/rebase/reset/squash/merge/force-push.

## Next step

Independent review Goal 028 Checkpoint 2. Goal 028 остаётся `IN_PROGRESS`; stuck/slow policy, economic audit, replay и scale/soak остаются будущими checkpoint scope.
