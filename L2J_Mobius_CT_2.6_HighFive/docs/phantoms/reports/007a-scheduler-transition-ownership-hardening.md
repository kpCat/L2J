# Goal 007A — scheduler transition ownership hardening

## Status

```text
Status: SUCCESS
Baseline: 9958edd9e133557f4966eed0a4124e68326401b3
Parent baseline: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Branch: feature/phantom-world
Subject: fix(phantoms): harden scheduler transition ownership
Goal 007: ACCEPT after Goal 007A
Goal 007A: ACCEPT
Goal 008: ALLOWED
Goal 009: NOT_STARTED
```

Production, focused regressions, cumulative `ant verify`, финальный `ant jar`
и два byte-identical verifier run завершены. Ordinary commit
`357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018` принят независимым review Goal 007A.

## Summary

Закрыты только пять findings независимого review Goal 007:

- slot больше не удаляется во время `processing` или lifecycle
  `boundaryInFlight`;
- retained failure планируется раньше requested/effective equality и не
  исчезает после signal change, withdrawal или TTL expiry;
- успешный `retryCleanup` оставляет truthful non-materialized state, а
  `ACTIVE`/`NEARBY_PERCEPTIBLE` требуют отдельный fresh materialize;
- production adapter классифицирует retained по фактическому service ownership;
- `STOPPING` не начинает новый boundary/work, `finishStop` отказывается
  очищать in-flight pulse, а `PhantomSystem` сохраняет configured instance до
  успешного finish.

Goal 006 lifecycle, config/schema, Player/World/network и Goals 008/009 не
изменялись.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java`.

Tests/build:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java`;
- `tools/phantoms/verify-task-007a.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md`;
- пакет `docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/**`;
- `docs/phantoms/reports/007-shared-activity-scheduler.md`;
- этот отчёт;
- `docs/phantoms/reviews/007-shared-activity-scheduler-review.md`.

Bounded exception по числу файлов задан TASK 007A: scheduler ownership,
focused race/integration suites, cumulative verifier и обязательные
review/report/progress artifacts образуют одну artifact family.

## Architecture decisions

### Slot ownership

`Slot` хранит только bounded state: `processing`, `boundaryInFlight` и
`boundaryGeneration`. Lifecycle boundary получает ownership под `_monitor`, но
сам port вызывается без global lock. `removeSlotLocked` и terminal unregister
проверяют оба in-flight marker. In-flight unregister очищает signal values,
запрашивает `SLEEPING` и coalesce-ит одну следующую возможность.

### Retained precedence и cleanup truth

Retained branch расположен раньше equality/grace/ordinary transition logic.
Только explicit `retryTransition` запускает cleanup. После успешного cleanup
requested non-materialized state публикуется напрямую; requested
`ACTIVE`/`NEARBY_PERCEPTIBLE` сначала получает effective `SLEEPING`, затем
отдельную ready/due opportunity для fresh materialize.

### Actual service ownership

Узкий lifecycle port дополнен `hasLifecycleOwnership(profileId)`.
Production implementation использует только `PhantomMaterializationService.find`
и не раскрывает `Player`, internal `Entry` или mutable map. Любой specific
materialization result при сохранённой service entry становится
`RETAINED_FAILURE`; cleanup success допустим только после исчезновения entry.

### Stop quiescence

Один scheduler-wide `pulseInFlight` запрещает overlapping pulse. `beginStop`
закрывает admission и отменяет единственный recurring future. Уже начатый
boundary/work может завершиться и reconciled; новый вызов после STOPPING не
начинается. `finishStop` не ждёт под monitor и возвращает `false` при любом
in-flight marker. `PhantomSystem.shutdown()` проверяет этот результат и
сохраняет `FAILED` configured instance для следующего explicit shutdown.

## DB, migrations и config

```text
Production DB: не использовалась
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Config: unchanged
Goal 006 lifecycle core: unchanged
```

## Tests and commands

Seed: `20260725001`. Apache Ant 1.10.15 запускался подтверждённым локальным
launcher, поскольку `ant` отсутствовал в `PATH`.

| Gate | Result |
|---|---:|
| `ant compile-tests` | PASS; 1924 production / 32 test sources |
| Scheduler suite | PASS `17/17 ×3` |
| Scale smoke | PASS `2/2`, не менее двух обязательных прогонов |
| Stable scale summaries | byte-identical |
| Stable scale SHA-256 | `67B7FC26B98141661890DFAAE5F307B86BB5C768EA82A2DF6A8D1F1556F7EE30` |
| Production materialization | PASS `20/20 ×3` |
| Shutdown handoff | PASS `5/5 ×3` |
| Headless Player | PASS `18/18` |
| Profile persistence | PASS `18/18` |
| DB integration | PASS `9/9` |
| Harness unit | PASS `66/66` |
| Skeleton | PASS `12/12` |
| Headless performance | PASS `2/2` |
| Production materialization performance | PASS `2/2` |
| Harness performance | PASS `1/1` |
| Scenario smoke | PASS `1/1` |
| `ant verify` | PASS; `1 min 15 s` |
| `ant jar` | PASS; 1924 production sources; `12 s` |
| Production `GameServer.jar` test entries | `0` |
| Goal 007A verifier | PASS `63/63 ×2`, byte-identical |
| Verifier output SHA-256 | `D0F1BBD00C96AE180BA7D96A9B808F20C18467A2F996183CBBD9E559702C78A1` |

Один ранний shutdown-suite run завершился `4/5`: новый test signal использовал
TTL `10000` при локальной maximum TTL `1000`. Production-код не менялся;
fixture исправлена на допустимое значение, после чего получены три отдельные
успешные серии `5/5`.

`ant test` дополнительно вывел ожидаемый внутренний lifecycle negative-control
summary `0/2` и завершился успешно после harness `66/66`; проверки не
отключались.

Основные выполненные команды:

```text
<local-ant-1.10.15>\bin\ant.bat compile-tests
<local-ant-1.10.15>\bin\ant.bat phantom-activity-scheduler-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-activity-scheduler-performance-smoke ×2+
<local-ant-1.10.15>\bin\ant.bat phantom-production-materialization-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-server-shutdown-handoff-test ×3 successful
<local-ant-1.10.15>\bin\ant.bat phantom-headless-player-test
<local-ant-1.10.15>\bin\ant.bat phantom-profile-persistence-test
<local-ant-1.10.15>\bin\ant.bat phantom-db-test
<local-ant-1.10.15>\bin\ant.bat test
<local-ant-1.10.15>\bin\ant.bat phantom-skeleton-test
<local-ant-1.10.15>\bin\ant.bat phantom-headless-player-performance-smoke
<local-ant-1.10.15>\bin\ant.bat phantom-production-materialization-performance-smoke
<local-ant-1.10.15>\bin\ant.bat phantom-performance-smoke
<local-ant-1.10.15>\bin\ant.bat phantom-scenario-test
<local-ant-1.10.15>\bin\ant.bat verify
<local-ant-1.10.15>\bin\ant.bat jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007a.ps1 ×2
git diff --check
```

Первый вызов `ant compile-tests` без абсолютного launcher не стартовал, потому
что Ant отсутствовал в `PATH`; сборка не начиналась. После обнаружения
подтверждённого Task 001 launcher все учитываемые Ant-команды завершились
успешно.

## Performance measurements

Scale smoke сохранил Goal 007 bounds: `10000` dormant profiles имеют
`ready=0`, `due=0`; WARM cohort обрабатывается budget `128` за `79` pulses,
`maximumReady=10000`, `maximumDue=10000`, CRITICAL WARM due =
`24000000 ns`. В slot не появились `Future`, `Thread`, `Executor` или `Player`.

## Deviations, limitations и risks

- Исторический `tools/phantoms/verify-task-007.ps1` сохранён неизменным; его
  frozen Goal 007 allowlist заменён в cumulative Ant chain новым verifier 007A.
- Полный Goal 007 verifier hash с prefix `AA5E4956` и suffix `E05690` в
  retained artifacts не найден; в Goal 007 report сохранён только честный
  abbreviated handoff.
- Новый тестовый overload `PhantomSystem.configureForTesting(service,
  scheduler)` package-private и не участвует в configured production start.
- Независимое review Goal 007A завершено с verdict `ACCEPT`; revert не требуется.
- Bounded follow-up перенесён в Goal 008: cleanup retry обязан использовать
  current requested state, вычисленный после внешнего cleanup call.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 23 text files, 0 matches;
- escaped Cyrillic в изменённых файлах проверены: 23 text files, 0 matches.

## Git

Git использован по прямому требованию TASK 007A для baseline/scope audit,
exact diff verification, одного ordinary commit и push.

```text
Expected commit parent: 9958edd9e133557f4966eed0a4124e68326401b3
Commit SHA: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Push result: подтверждён exact remote ref
```

## Next step

Goal 008 разрешена. Goal 009 остаётся `NOT_STARTED`.

Result:
`ACTIVITY_SCHEDULER_HARDENED_ACCEPTED`.
