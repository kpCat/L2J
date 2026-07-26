# Goal 006B — server shutdown handoff

## Статус

```text
Status: SERVER_SHUTDOWN_HANDOFF_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline: c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
Parent baseline: ff0b33abad0affc4fe64b4324aee67f256dc96fa
Branch: feature/phantom-world
Goal 006A local hardening: ACCEPT
Goal 006 overall: FIX_REQUIRED pending 006B
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 007: NOT_STARTED / BLOCKED
```

Закрыт только реальный GameServer shutdown handoff. Schema, config, identity
recovery, `Player`, `Disconnection`, `World`, production profile model и Goal 007
не менялись.

## Read-first и локальные паттерны

До изменений прочитаны обязательные AGENTS/master-plan/roadmap/workflow/task
документы, полные пакеты и отчёты Goal 006/006A, lifecycle contract, реальный
`Shutdown`, configured `PhantomSystem`, materialization service/core/identity
registry, `Disconnection`, релевантные `Player`/`World`/offline seams, build,
launcher, production suites и verifier 006A.

Переиспользованы:

- существующий tracked `DrainAttempt` и shared `ThreadPool`;
- exact conditional `_activeByCharacter` ownership;
- `Player.hasHeadlessOutboundSession()`;
- process-local `OwnerKind.PHANTOM`;
- configured instance retention до terminal `STOPPED`;
- существующий isolated test DB environment и blocked-store failure injection.

Новый executor, raw thread, per-profile future или второй cleanup lifecycle не
добавлены.

## Реализация

### Первый Phantom shutdown

В `Shutdown.startShutdownActions()` первый
`PhantomSystem.shutdownIfStarted()` теперь вызывается до
`disconnectAllCharacters()`, пока shared ThreadPool и DatabaseFactory полностью
доступны.

Terminal stop получает aggregate success log. Incomplete attempt сохраняет
configured instance и получает aggregate warning с system/service state и
retained count.

### Strict managed-player classifier

`PhantomSystem.isMaterializationManaged(Player)` возвращает `true` только для
одновременного сочетания:

```text
non-null Player
headless outbound session
identity owner PHANTOM
configured PhantomSystem
exact character object ID в active service map
```

`PhantomMaterializationService.ownsCharacterObjectId(int)` — read-only
`containsKey` exact map query. Он не раскрывает Entry/Player/IDs collection, не
делает DB/World access и не запускает work.

Generic loop сохранил исходный `Disconnection.of(player).storeAndDeleteWith(...)`
для ordinary, detached/offline real и unowned headless Players. Только доказанно
managed actor получает `continue`. Прямой service cleanup внутри loop
отсутствует.

### Второй bounded shutdown перед ThreadPool

Непосредственно перед `ThreadPool.shutdown()` выполняется второй условный
`PhantomSystem.shutdownIfStarted()`. Accepted service contract наблюдает late
completion, переиспользует in-flight drain или запускает один explicit retry
после завершившейся ошибки.

Если второй attempt остаётся incomplete:

- configured instance не очищается;
- map, permit, PHANTOM identity и actor остаются retained;
- generic `Disconnection` не вызывается для managed actor;
- перед остановкой shared ThreadPool пишется aggregate `SEVERE`;
- legacy success wording удалено.

Server-level source содержит ровно две shutdown opportunity. Force-delete,
direct `characters.online=0` и unbounded loop отсутствуют.

### Bounded diagnostics

`ConfiguredShutdownSnapshot` содержит только configured flag, system state,
service state и retained entry count. Service предоставляет отдельный bounded
`ShutdownSnapshot`; materialization/profile/character IDs и DB access
отсутствуют.

Для isolated configured-system tests добавлен package-private seam, который
принимает только уже запущенный production service и не меняет
`startConfigured()`.

## Изменённые файлы

Production:

- `java/org/l2jmobius/gameserver/Shutdown.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`.

Build/tests:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java`;
- `tools/phantoms/verify-task-006b.ps1`.

Документация:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md`;
- `docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md`;
- пакет `docs/phantoms/tasks/006b-server-shutdown-handoff/**`;
- `docs/phantoms/reports/006a-materialization-boundary-hardening.md`;
- этот отчёт;
- `docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md`.

Bounded exception по числу файлов задан TASK 006B: production handoff,
focused suite/build/verifier и обязательные review/contract/report artifacts
образуют одну shutdown artifact family.

## Tests

Seed: `20260725001`. Test DB:
`l2jmobiush5_phantom_test`.

Focused shutdown-handoff suite:

| Run | Result | Cases | Ant time |
|---|---:|---:|---:|
| 1 | PASS | 4/4 | 22 s |
| 2 | PASS | 4/4 | 21 s |
| 3 | PASS | 4/4 | 21 s |

Production materialization:

| Run | Result | Cases | Ant time |
|---|---:|---:|---:|
| 1 | PASS | 19/19 | 22 s |
| 2 | PASS | 19/19 | 22 s |
| 3 | PASS | 19/19 | 22 s |

Targeted regressions:

| Command | Result |
|---|---:|
| `ant compile` | PASS; 1913 production sources |
| `ant compile-tests` | PASS; 30 test sources |
| `ant phantom-headless-player-test` | PASS; 18/18 |
| `ant phantom-profile-persistence-test` | PASS; 18/18 |
| `ant phantom-db-test` | PASS; 9/9 |
| `ant test` | PASS; 66/66 |
| `ant phantom-skeleton-test` | PASS; 12/12 |
| `ant phantom-production-materialization-performance-smoke` | PASS; 2/2 |
| cumulative headless performance | PASS; 2/2 |
| cumulative scenario/performance smoke | PASS; 1/1 и 1/1 |
| cumulative expected negative controls | PASS |
| pre-commit `ant verify` | PASS; 1 min 14 s |
| pre-commit `ant jar` | PASS; 1913 production sources; 11 s |
| pre-commit `verify-task-006b.ps1` | PASS; 71/71 |
| production `GameServer.jar` | PASS; test entries 0 |

Focused suite доказывает classifier matrix, реальный source order, maximum two
server opportunities, in-flight reuse без duplicate cleanup, generic skip,
persistent failure retention и explicit safe teardown. Полный GameServer и
`System.exit` не запускаются.

Предварительный baseline `ant verify` до изменений выполнил runtime suites, но
ожидаемо завершился на historical verifier 006A `80/81`: новый untracked пакет
006B отсутствовал в frozen Goal 006A allowlist. Старый verifier не менялся.

Post-commit `verify`/`jar`, два byte-identical verifier run, commit SHA, push и
remote ref фиксируются в external final handoff после commit этого отчёта.

## DB, config и performance

```text
Production DB: no access
l2jmobiush5: no read, no write, no mutation
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Config: unchanged
Identity recovery truth table: unchanged
```

First blocked configured shutdown: `151593000 ns` (`151.593 ms`), ниже
one-second gate. Hot-path periodic logging и per-actor logs не добавлены.

## Проверки изменённых файлов

- mojibake-маркеры в изменённых файлах проверены: 21 text file, 0 matches;
- escaped Cyrillic в изменённых файлах проверены: 21 text file, 0 matches.

## Git

Git использован по прямому требованию TASK 006B для baseline/scope audit,
ordinary commit и push. Выполненные read-only команды и финальные
commit/push-команды перечисляются в external final handoff.

```text
Expected subject: fix(phantoms): coordinate server shutdown handoff
Expected parent: c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

## Ограничения и следующий gate

Задача не гарантирует завершение навсегда заблокированной canonical DB
операции. Гарантия: отсутствие concurrent generic/service cleanup, две bounded
возможности при живом ThreadPool, fail-closed retained ownership и явный
terminal diagnostic.

Goal 006B не принимает собственный manual gate. Следующий шаг — независимое
review Goal 006B. Goal 007 остаётся `NOT_STARTED / BLOCKED`.

Result:
`SERVER_SHUTDOWN_HANDOFF_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
