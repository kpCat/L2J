# Goal 006A — materialization boundary hardening

## Статус

```text
Status: PRODUCTION_MATERIALIZATION_LIFECYCLE_HARDENED_PENDING_INDEPENDENT_REVIEW
Baseline: ff0b33abad0affc4fe64b4324aee67f256dc96fa
Parent baseline: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Branch: feature/phantom-world
Goal 006 review: FIX_REQUIRED
Goal 006A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 007: NOT_STARTED / BLOCKED
```

Закрыты только четыре finding независимого review Goal 006. Архитектурное
направление Goal 006 сохранено; schema, config, retained identity recovery truth
table, network/GameServer/Shutdown и будущий Goal 007 не менялись.

## Прочитанный контекст и переиспользованный паттерн

До изменений прочитаны обязательные AGENTS/master-plan/workflow/task документы,
пакеты, отчёты и reviews Task 004–Goal 006, lifecycle contract, `World`,
`PlayerAutoSaveTaskManager`, canonical materialization actor/service/System,
production suites, verifier 006 и штатный `ThreadPool`.

Переиспользованы:

- exact conditional ownership и освобождение только после terminal `STORED`;
- обе штатные World maps без изменения `World`;
- narrow read-only autosave inspection;
- существующий shared `ThreadPool`;
- один service-level attempt вместо per-profile workers;
- локальный deterministic `ObjectIdResidue` fixture.

Bounded exception по числу файлов задан самим TASK: production, build/test,
verifier, provenance и progress-документация образуют один обязательный
Goal 006A closure artifact set.

## Реализация

### World и autosave identity boundary

`PhantomMaterializedPlayer` теперь:

1. до PHANTOM claim и сразу после claim требует пустые `World.getPlayer`,
   `World.findObject` и autosave object ID;
2. после `Player.load` требует exact object ID, пустые обе World maps, наличие
   exact Player в autosave и отсутствие другого autosave Player с тем же ID;
3. непосредственно перед `spawnMe` повторно проверяет обе World maps;
4. после spawn требует, чтобы обе World maps указывали на exact loaded Player.

`PlayerAutoSaveTaskManager.containsOtherObjectId(int, Player)` — узкий
read-only query. Различимые результаты:

```text
WORLD_PLAYER_IDENTITY_BUSY
WORLD_OBJECT_IDENTITY_BUSY
AUTOSAVE_IDENTITY_BUSY
WORLD_REGISTRATION_MISMATCH
```

Foreign World residue не удаляется cleanup чужого Player. Actor, service maps,
permit и PHANTOM identity остаются retained до удаления residue и успешного
explicit `retryCleanup`.

### Action admission и STOPPING

`tryAcquireAction(profileId)` выполняет проверку `RUNNING`, поиск entry и
bounded actor admission внутри одного `_stateMonitor`. DB, Player и World work
под monitor не выполняются. Action либо принят раньше `STOPPING` и drained,
либо после `STOPPING` отклонён.

### Wall-clock-bounded shutdown

`shutdown` создаёт или переиспользует один tracked service-level `DrainAttempt`.
Его единственный command отправляется в существующий `ThreadPool`; новых
executor/raw thread и per-profile future нет. Caller ждёт latch только остаток
своего wall-clock budget.

Timeout возвращает `FAILED` с exact retained profile IDs и не отменяет
`storeMe`/`deleteMe`, не освобождает map, permit или identity. Второй ранний
caller переиспользует attempt. Command делает не более двух ordered pass.
Успешное late completion переводит service в `STOPPED`; после завершившейся
ошибки разрешён новый explicit retry attempt. Ownership освобождается только
после terminal `STORED`.

## Provenance Goal 005

Полный SHA-256 найден в локальном сохранённом final handoff Goal 005. Два run
`69/69` имели byte-identical output:

```text
483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97
```

SHA-256
`39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9`
относится только к двум verifier run `66/66` Task 004B. Ошибочная provenance
исправлена в отчётах/reviews Goal 005 и Goal 006 без придумывания значения.

## Изменённые файлы

Production:

- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`;
- `java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java`.

Build и tests:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java`;
- `tools/phantoms/verify-task-006a.ps1`.

Документация:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md`;
- task package `docs/phantoms/tasks/006a-materialization-boundary-hardening/**`;
- отчёты Goal 005, Goal 006 и Goal 006A;
- reviews Goal 005 и Goal 006.

`PhantomSystem`, performance suite и launcher оставлены без изменений: их
существующие contracts и regressions достаточны.

## DB, migrations и config

- Production DB `l2jmobiush5`: не использовалась.
- Все DB suites используют только `l2jmobiush5_phantom_test`.
- Schema/migrations: без изменений.
- Config: без изменений.
- Retained identity recovery truth table: без изменений.

## Tests и команды

Production matrix расширена с `16/16` до `19/19`:

- World Player/object/autosave collisions и post-load deterministic insertion;
- 1000 последовательных отказов action после наблюдаемого `STOPPING`;
- blocked `BEFORE_STORE_OPERATION`, caller timeout, single attempt, retained
  ownership и late completion.

Pre-commit выполнены:

| Команда / gate | Результат |
|---|---|
| `ant compile` | PASS, 1913 production sources |
| `ant compile-tests` | PASS, 29 test sources |
| production materialization run 1 | PASS, `19/19` |
| production materialization run 2 | PASS, `19/19` |
| production materialization run 3 | PASS, `19/19` |
| production materialization performance | PASS, `2/2` |
| headless player | PASS, `18/18` |
| headless player performance | PASS, `2/2` в cumulative verify |
| profile persistence | PASS, `18/18`, final owned-row residue `0` |
| DB integration | PASS, `9/9` |
| ordinary harness | PASS, `66/66` |
| skeleton | PASS, `12/12` |
| scenario/performance smoke | PASS, `1/1` и `1/1` |
| expected negative controls | PASS |
| `ant verify` | PASS |
| `verify-task-006a.ps1` | PASS, `81/81` |

Historical `verify-task-006.ps1` запущен отдельно и ожидаемо завершился
`68/72`: его frozen allowlist запрещает обязательные для Goal 006A autosave
manager/task/report/verifier artifacts, запрещает разрешённый service-level
`ScheduledFuture` и требует старый status отчёта Goal 006. Старый verifier не
изменялся; cumulative `ant verify` использует verifier 006A и проходит.

Финальные `ant jar`, post-commit `verify`/`jar`, два verifier run и byte
comparison фиксируются в external final handoff после их фактического выполнения.

## Performance

Три независимых focused run:

```text
run 1 blocked/second: 151197400 / 151726800 ns
run 2 blocked/second: 150956500 / 150330000 ns
run 3 blocked/second: 150770600 / 150700700 ns
wall-clock gate: < 1000000000 ns
```

Caller timeout ограничен wall-clock budget; canonical store/delete продолжает
выполнение на tracked shared-pool command и не force-cancelled.

Cumulative performance run:

```text
1 production cycle: 37021900 ns
10 production cycles: 118466300 ns
average for 10 cycles: 11846630 ns
```

## Deviations, limitations и risks

- Historical `verify-task-006.ps1` оставлен неизменным. Его frozen Goal 006
  allowlist не может принимать обязательное изменение autosave manager и
  task package 006A; cumulative build target направлен на verifier 006A.
- Goal 006A не принимает собственный manual gate. Требуется независимое review.
- Goal 007 не начат.
- Реальная geodata и navigation не относятся к этой задаче и не проверялись.

## Git и следующий шаг

```text
Branch: feature/phantom-world
Expected subject: fix(phantoms): harden materialization boundaries
Expected parent: ff0b33abad0affc4fe64b4324aee67f256dc96fa
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
Next step: independent review Goal 006A
```

Result:
`PRODUCTION_MATERIALIZATION_LIFECYCLE_HARDENED_PENDING_INDEPENDENT_REVIEW`.

## Immutable independent-review handoff

```text
Commit: c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f
Parent: ff0b33abad0affc4fe64b4324aee67f256dc96fa
Push/remote: origin/feature/phantom-world exact
Production tests: 19/19 ×3
Blocked caller measurements: 150.33–151.73 ms
Final verifier: 81/81 ×2
Verifier SHA-256: 8F459EEEB37EBF368DC6FB7E1826CDAA38B3249A469FB906D1F29220D77174C8
Independent local-hardening review: ACCEPT
Server integration review: FIX_REQUIRED
Goal 006B: REQUIRED
Goal 007: BLOCKED
```
