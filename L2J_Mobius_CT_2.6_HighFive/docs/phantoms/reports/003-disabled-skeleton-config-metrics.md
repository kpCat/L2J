# Codex report — 003-disabled-skeleton-config-metrics

## Status and baseline

`SUCCESS`

- Branch: `feature/phantom-world`.
- Accepted baseline и parent Task 003:
  `84f29a0002b25d2b1ff1a19fa9c92867479fd6a5`.
- Starting local HEAD и `origin/feature/phantom-world` совпадали с baseline.
- Ожидаемый untracked package:
  `docs/phantoms/tasks/003-disabled-skeleton-config-metrics/**`.
- Независимый pre-existing `docs/agent-tasks/**` сохранён, не менялся и
  исключён из scope/staging.
- ADR 0001 остаётся `Proposed`.

## Task 002A closure

До production-изменений зафиксирован окончательный provenance Task 002A:

```text
Commit: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Parent: 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
Push: successful
Remote ref: exact
Final verifier 1: 52/52
Final verifier 2: 52/52
Outputs identical SHA-256:
3DEBD45D104620BE262FC6AE83A0A9244F80D9D409E9FEA504DF0EA815E0249E
Independent review: ACCEPT
```

Review record сохраняет исходные findings Task 002 и фиксирует:

```text
Original Task 002 implementation: FIX REQUIRED
Task 002A closure: ACCEPT
Combined Task 002 test infrastructure: ACCEPT
Task 003: ALLOWED
Task 004: NOT_STARTED
```

## Changed files

- `build.xml` — skeleton/static targets и cumulative Task 003 `verify`.
- `java/org/l2jmobius/gameserver/GameServer.java` — guarded start после
  `ThreadPool`, до `IdManager`.
- `java/org/l2jmobius/gameserver/Shutdown.java` — guarded stop перед
  `ThreadPool.shutdown()`.
- `java/org/l2jmobius/gameserver/config/ConfigLoader.java` — canonical config
  load в существующем custom block.
- `java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java` —
  immutable fail-closed settings.
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — inert lifecycle
  owner.
- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java` — одна bounded
  queue без consumer.
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java` — шесть fixed
  counters.
- `java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java` —
  optional bounded sampled ring.
- `dist/game/config/Custom/PhantomPlayers.ini` — оба canonical flags `False`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — explicit
  mode `skeleton`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java` — 12
  focused tests.
- `tools/phantoms/verify-task-003.ps1` — deterministic read-only verifier.
- `docs/phantoms/tasks/003-disabled-skeleton-config-metrics/**` — supplied task
  package.
- `docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md` — immutable
  Task 002A provenance и ACCEPT.
- `docs/phantoms/reviews/002-automated-test-infrastructure-review.md` —
  combined ACCEPT и Task 003 gate.
- этот отчёт.

## Config and fail-closed behavior

Canonical path:

`./config/Custom/PhantomPlayers.ini`

Ключи только:

```text
EnablePhantomSystem = False
EnablePhantomDiagnostics = False
```

`PhantomPlayersConfig` — final utility с public path constant, immutable
`Settings` record и volatile current settings, изначально disabled. Production
`load()` вызывается только через существующий `ConfigLoader`; deterministic
`read(Path)` не меняет global state.

Parser принимает только trimmed `true`/`false` без учёта регистра. Missing
file/key, blank и malformed value дают disabled settings. Diagnostics
нормализуются в false, если system disabled. Config не читает environment,
system properties, DB или network и не прерывает обычный startup при
отсутствующем optional file.

## Disabled and enabled skeleton behavior

Disabled configured path:

- `startConfigured()` возвращает false;
- configured instance не создаётся;
- queue и trace не создаются;
- metrics не меняются;
- Phantom section не печатается;
- task/thread/DB/network work отсутствует.

Direct disabled instance нужен только deterministic tests:
`NEW -> DISABLED -> STOPPED`; queue capacity и trace capacity равны нулю,
metrics остаются all-zero, repeated shutdown — no-op.

Enabled skeleton остаётся inert:

- создаётся не более одного configured instance;
- `NEW -> RUNNING -> STOPPED`;
- starts/stops меняются ровно один раз;
- repeated start/stop — no-op;
- `STOPPED` terminal;
- нет profiles, players, NPC, actions, AI, persistence или gameplay effect.

## Lifecycle ordering

Startup:

```text
ConfigLoader
  -> DatabaseFactory
  -> ThreadPool
  -> if Phantom enabled: section + startConfigured
  -> GameTimeTaskManager
  -> IdManager
```

Configured instance публикуется только после successful `start()`. False return
при enabled path и runtime exception прерывают startup; partial configured
reference не остаётся.

Shutdown:

```text
GameTimeTaskManager interrupt
  -> PhantomSystem.shutdownIfStarted
  -> ThreadPool.shutdown
```

Shutdown имеет local try/catch. INFO выводится только если реально остановлен
started instance; disabled path ничего не создаёт и не логирует.

## Queue and scheduled tasks

`PhantomScheduler` содержит одну `ArrayBlockingQueue<Runnable>` capacity `256`.
Consumer/worker отсутствует, `Runnable` никогда не выполняется.

- `offer()` разрешён только в RUNNING;
- capacity + 1 отклоняется;
- accepted/rejected counters точны;
- `stop()` делает queue terminal и очищает её;
- `scheduledTaskCount` всегда `0`;
- production Task 003 не вызывает `offer()`.

Scheduled tasks/futures: `0`.

## Metrics and trace

`PhantomMetrics` использует ровно шесть fixed `AtomicLong`:

- lifecycle starts/stops;
- queue accepted/rejected;
- trace recorded/dropped.

Dynamic map/exporter/timer/publisher отсутствуют. Snapshot immutable.

Trace по умолчанию disabled. Disabled trace не выделяет ring array. При
diagnostics=true используется fixed ring capacity `64`, deterministic
`sampleEvery=16`, short internal event names до 48 символов, overwrite oldest и
точный dropped counter. Snapshot копирует только bounded entries по запросу.
Логи/background work отсутствуют.

## DB and network safety

```text
Production DB connection/read/mutation: false
Test DB schema mutation: false
Phantom network activity: false
Player/GameClient/NPC/World access: false
```

Production package не импортирует DB, network, Player, GameClient, World,
packets, NPC/FakePlayer. Existing DB integration suite во время regression
читала только allowlisted `l2jmobiush5_phantom_test`.

Existing local config и durable schema manifest были present и fresh:

```text
schemaVersion=1
scriptCount=117
statementCount=205
aggregateSha256=A3C9FC62C662DC5E0E690D6E7D6E63B5B0268BAD3019348E75F565DA5C84453A
```

Re-provisioning не выполнялся и admin credentials не использовались.

## Concurrency and memory

- configured lifecycle synchronized и singleton-bounded;
- queue fixed capacity `256`;
- trace ring capacity `64` либо null;
- metrics — шесть fixed counters;
- нет executor, worker, scheduled future или отдельного потока;
- stop очищает queue и configured reference;
- snapshot allocations bounded;
- profile/action keyed collections отсутствуют.

Focused tests сравнивают non-daemon thread IDs до/после disabled lifecycle и
queue lifecycle: новых потоков нет.

## Tests and counts

- `compile-tests`: PASS, production `1900` sources и test `22` sources.
- Unit: `66/66 PASS`.
- Skeleton: `12/12 PASS`.
- Runner negative: expected child exit `1`, wrapper PASS.
- Production DB guard negative: expected exit `2`, sentinel untouched.
- Cross-process provisioning lock: PASS.
- Schema freshness negative: expected exit `2`, sentinel untouched.
- Lifecycle negative: expected exit `2`, marker absent.
- DB integration: `9/9 PASS`.
- Scenario: `1/1 PASS`,
  checksum `A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45`.
- Performance: `1/1 PASS`, `250000` operations, `6 ms`,
  checksum `BC2F4B1A43621F54`.

Intentional nested lifecycle and runner failures в negative controls являются
ожидаемым evidence; их Ant wrappers прошли.

## Ant targets, verify and jar

Добавлены:

```text
phantom-skeleton-test
phantom-static-verify-003
```

Все Java invocations остаются forked. Все Task 002A target names сохранены.
Frozen historical static targets делегируют cumulative Task 003 verifier,
который отдельно доказывает byte-identical сохранность старых verifiers и
safety artifacts.

- Targeted suites: PASS.
- `ant verify`: PASS, `17 s`.
- `ant jar`: PASS, `10 s`.
- Production `GameServer.jar` test entries: `0`.
- Production `LoginServer.jar` test entries: `0`.

## Static verifier

Verifier проверяет base/branch/one-commit shape, exact scope, required files,
Task 004 absence, forbidden dependencies, no worker/task, config contract,
startup/shutdown ordering, bounded queue/metrics/trace, tests/Ant, Task 002A
closure, frozen safety artifacts, credentials/binaries и text encoding.

- Early development run до создания этого отчёта: expected `70/72`, два
  отсутствующих report checks.
- Required pre-commit run: `72/72 PASS`.
- Final run 1 и final run 2 выполняются после ordinary commit на одном
  immutable tree; outputs сравниваются вне repository.

## Scope, commands, deviations and limitations

Scope ограничен exact allowlist Task 003. Это bounded exception к обычному
лимиту 8–10 файлов: один заранее определённый config/lifecycle/test/report
artifact family. Other chronicles, master plan, Agents, ADR, Player,
GameClient, NPC/Fake Players, DB support/schema, old verifiers, migrations,
dependencies и Task 004 не менялись.

Основные выполненные команды:

```text
git rev-parse --show-toplevel
git status --short --branch
git branch --show-current
git rev-parse --abbrev-ref --symbolic-full-name @{upstream}
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 84f29a...
java -version
official external Ant 1.10.15 -version
ant compile-tests
ant test
ant phantom-skeleton-test
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
ant verify
ant jar
powershell ... tools/phantoms/verify-task-003.ps1
git diff --check
```

Git-команды разрешены TASK.md и прямым запросом пользователя для provenance,
scope, commit и push. Amend/rebase/reset/restore/force push не использовались.

Deviations:

- `ant` отсутствовал в PATH, а прежний external каталог Task 002A отсутствовал.
  Official Apache Ant 1.10.15 загружен во временный каталог OS вне repository;
  archive SHA-256:
  `E59BAF898DC5B6D1AA6CD57544715F7E0060B1FDC9E56F24F9898D56612A9E0B`.
- Existing test config/manifest были fresh, поэтому разрешённое только при
  необходимости destructive re-provisioning не выполнялось.

Limitations:

- GameServer/LoginServer вручную не запускались — это запрещено Task 003.
- Enabled mode — только inert skeleton, без gameplay.
- ADR 0001 не менялся и остаётся `Proposed`.

- Mojibake-маркеры в изменённых файлах проверены: `0` совпадений.
- Escaped Cyrillic в изменённых файлах проверены: `0` совпадений.

## Branch, parent and subject

- Branch: `feature/phantom-world`.
- Parent: `84f29a0002b25d2b1ff1a19fa9c92867479fd6a5`.
- Subject: `feat(phantoms): add disabled system skeleton`.

Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.

## Manual gate

`PENDING_INDEPENDENT_REVIEW`

## Task 004

`NOT_STARTED`

## Recommended next step

Провести независимое review Task 003. Task 004 не начинать до принятия этого
manual gate.
