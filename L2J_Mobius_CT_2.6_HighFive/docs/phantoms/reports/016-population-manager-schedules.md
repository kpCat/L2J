# Goal 016 — PopulationManager и schedules

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

## Summary

Реализована одна цельная capability Goal 016 без suffix-задач:

- target-driven `PhantomPopulationManager` с production defaults `target=0` и
  `activeTarget=0`;
- атомарный managed shell (`phantom_profiles` + `population.state`) и
  restart-safe saga канонического level-1 персонажа;
- общий transport-neutral initializer, используемый `CharacterCreate` и
  population creation;
- строгий data-driven High Five catalog классов, имён, регионов и расписаний;
- один optional control hook существующего shared scheduler вне его monitor;
- SLEEPING/WARM/BACKGROUND/ACTIVE, ACTIVE admission и пропорциональные region
  quotas;
- детерминированные retirement/return, bounded startup paging, backpressure,
  lifecycle и production composition.

Схема БД, `Player.java`, другие хроники и gameplay semantics Goal 015/017/025
не менялись. Population creation не создаёт `GameClient`, не вызывает packet
handler и не отправляет client-origin `OnPlayerCreate`.

## READ_SET

Обязательный bounded read-first pass:

1. `docs/phantoms/tasks/016-population-manager-schedules/TASK.md`,
   `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`.
2. Goal 016 в `docs/PHANTOM_BOTS_ROADMAP.md` и
   `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`.
3. `PhantomSystem`: construction/start/shutdown/composition.
4. `PhantomPlayersConfig`, `PhantomPlayers.ini`.
5. `PhantomScheduler`: register/signal/pulse/unregister/stop.
6. `PhantomProfileRepository`: profile/component SQL и transaction wrapper.
7. `PhantomGoalStateStore`, `PhantomGoal`, candidate/handler contracts.
8. `PhantomMaterializationService` и Goal 015 absent-state lifecycle.
9. `CharacterCreate`: validation, creation и initialization.
10. `Player.create/createDb/load/restore` — только целевые диапазоны.
11. `InitialEquipmentData`, `InitialShortcutData`, `SkillTreeData` APIs.
12. Test-DB schema/metadata для accounts, characters, items, skills, shortcuts.

Восемь дополнительных exact reads:

1. `PhantomMaterializedPlayer.java` — штатная headless materialization identity.
2. `PlayerAutoSaveTaskManager.java` — доказательство отсутствия autosave при
   creation.
3. `LoginController.java` — disabled access-level семантика reserved account.
4. `MapRegionData.java` — authoritative home-region ID.
5. `PhantomHeadlessPlayerTestEnvironment.java` — real loader/test DB harness.
6. `PhantomGoalStateCodec.java` — локальный bounded component codec pattern.
7. `Shortcuts.java#registerShortcut` — durable shortcut writer.
8. `Creature.java` status setters — диагностика pristine zero-HP restore.

`World.java` ownership и границы координат проверены вместе с обязательным
Player/World creation-owner чтением. Другие хроники не читались.

Локальные аналоги: `PhantomProfileRepository.write`, Goal 015 component/store
codec, `PhantomScheduler` signal ownership, `PhantomMaterializationService`,
ordinary `CharacterCreate`, существующие Goal test suites и static verifiers.

## Architecture decisions

- `population.state` schema version 1 хранит только bounded identity и
  reconciliation facts, payload ограничен 4096 байт.
- Account имеет имя `p` + base36 profile ID (не более 14 ASCII), access level
  `-1`; ownership подтверждается случайным Base64URL token до insert.
- Creation location разрешается один раз и долговечно хранится в saga, потому
  что `PlayerTemplate.getCreationPoint()` выбирает случайную точку.
- Pristine `Player.load` закономерно видит нулевой HP как dead; общий initializer
  восстанавливает creation state, затем повторно нормализует vitals после
  equipment.
- Startup читает managed profiles страницами не более 256 и fail-closed при
  превышении `MaxScheduledPhantomProfiles`.
- Scheduler вызывает единственный `PhantomSchedulerControlPort.onPulse()`
  вне `_monitor`; PopulationManager не создаёт worker/task/Future.
- ACTIVE overflow деградирует в WARM; largest-remainder quotas считаются по
  текущей READY-популяции, daily rotation стабилен.
- Retired profiles возвращаются по наименьшему ID раньше создания shell;
  retirement выбирает наибольшие ID и никогда не удаляет character/account.

## Creation matrix

| Boundary | Durable fact | Restart action | Anti-dup proof |
|---|---|---|---|
| SHELL | profile + component atomically | restore bootstrap | one profile |
| ACCOUNT_INTENT | token/account intent | insert-or-verify owner | one account |
| ACCOUNT_VERIFIED | owned disabled account | continue character intent | no guessing |
| CHARACTER_INTENT | exact account/name/class | find zero/one, then create | one character |
| CHARACTER_CREATED | expected object ID | initialize only while unlinked | bounded resume |
| INITIALIZATION_INTENT | exact init contract | fresh-load verify | stable item/skill/shortcut sets |
| LINKED/READY | optimistic profile link/hash | remove bootstrap, schedule | same identity |

Selected real fixtures: два разных канонических starting classes из
`PlayerClass.level()==0`, seed `16001601`, test DB
`l2jmobiush5_phantom_test`; создаются реальные level-1 offline characters.

## Schedule matrix

| Case | Expected |
|---|---|
| Midnight wrap | current local interval selected across day boundary |
| DST gap | first valid instant, no fabricated missed sequence |
| DST overlap | monotonic latest sequence, no replay |
| Forward jump | apply latest state only |
| Backward jump | older boundary cannot replay |
| ACTIVE overflow | deterministic WARM degradation |
| Region quotas | proportional largest remainder + stable daily rotation |
| Restart | recompute current state/next boundary without tick writes |

## Retirement matrix

| Transition | Required ownership |
|---|---|
| READY → RETIRE_REQUESTED | persist before withdrawal |
| withdraw schedule | only source `population.schedule` |
| unregister | shared scheduler exact slot |
| wait | actor absent and slot absent |
| RETIRE_REQUESTED → RETIRED | durable terminal state |
| RETIRED → READY | same profile/character before any new shell |
| restart at request | resume unregister/wait/terminal sequence |

## Configs and data

- `PhantomPopulationTarget = 0`.
- `PhantomPopulationActiveTarget = 0`.
- `PhantomPopulationCreationInFlight = 2`.
- `PhantomPopulationBoundariesPerPulse = 64`.
- `PhantomPopulationTimeZone = UTC`.
- Catalog: `dist/game/data/phantoms/population/high-five-population-v1.xml`.

Invalid enabled config fails closed; legacy configs without Goal 016 keys keep
zero targets.

## Tests and commands

Выполненные focused/implementation прогоны до финального gate:

- initial `compile`: FAILED (3 compile errors), затем исправлен source;
- `compile`: PASS;
- `compile-tests`: PASS, включая повтор после restart/backpressure additions;
- catalog, schedule, performance: PASS;
- creation: сначала выявлены nullable profile link, pristine HP и randomized
  location defects; после source fixes PASS 2/2;
- reconciliation: PASS 1/1;
- lifecycle: сначала исправлено ошибочное test assertion, затем PASS 2/2;
- server integration: сначала fixed-clock fixture выбрал inactive schedule;
  после test-clock fix PASS 1/1;
- повтор creation/reconciliation/lifecycle/server-integration после усиления
  restart/backpressure: PASS; финальный integration report 1/1,
  `elapsedNanos=5310044400`.

Вызов `ant ...` без абсолютного пути не являлся тестовым прогоном: shell не
наследовал Ant PATH. Все учитываемые команды используют
`C:\Users\endim\AppData\Local\CodexTools\apache-ant-1.10.17\bin\ant.bat`.

Финальные pre-commit gates:

- семь focused modes: catalog 3/3, schedule 3/3, creation 3/3,
  reconciliation 1/1, lifecycle 2/2, server integration 1/1,
  performance 3/3; `BUILD SUCCESSFUL`, 2:30;
- affected suites: skeleton 12/12, activity scheduler 20/20, shutdown handoff
  7/7, production materialization 20/20, decision core 35/35;
  `BUILD SUCCESSFUL`, 1:33;
- corrected verifier 015: `TASK015_VERIFIER_OK`;
- pre-commit verifier 016: `TASK016_VERIFIER_OK`, working-tree scope 30;
- единственный final `phantom-population-test`: все 16 cases PASS,
  `BUILD SUCCESSFUL`, 1:27;
- первый full `ant verify`: FAILED на unrelated historical
  `combat-server-integration.02`, 19/20, после всех предыдущих green suites;
- разрешённый один exact retry `phantom-combat-server-integration-test`:
  PASS 20/20 с тем же seed `20260725001`;
- diff-аудит нашёл и исправил реальный initializer parity defect: восстановлен
  прежний `PacketLogger.warning` для неуспешного starting-item insert;
  targeted lifecycle 2/2 и creation 3/3 PASS;
- разрешённый второй и последний full `ant verify`: все runtime suites, включая
  Goal 016 16/16, PASS; остановился только на deterministic historical verifier
  014A, ожидавшем superseded pre-acceptance status Goal 015;
- roadmap status-only closure сохранил явно помеченный historical marker;
  exact verifier 014A, verifier 015 и verifier 016 после closure: PASS;
- final catalog audit заменил unordered `Map.copyOf` на immutable
  `LinkedHashMap`; targeted catalog/schedule/creation — по 3/3 PASS;
  третий full verify не запускался согласно запрету задачи;
- финальный standalone `ant jar`: `BUILD SUCCESSFUL`, 0:15.

Два byte-identical post-commit verifier 016 выполняются после создания commit.

## Performance and lifecycle evidence

Performance suite: 100 000 control pulses за 16 917 700 ns, 100 000 schedule
evaluations за 418 905 500 ns, 10 000 admission rebalances за 143 222 000 ns,
synthetic 10 000 managed profiles за 7 426 000 ns; DB writes = 0.

## Deviations, limitations and risks

- mojibake-маркеры в изменённых файлах проверены: 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: 0 совпадений.
- `InitialShortcutData` является существующим ordinary initializer API;
  population/helper не содержат прямых packet, `GameClient` или serverpacket
  вызовов, а headless session не имеет network I/O.
- Геодата отсутствует; creation position использует текущую GeoEngine height
  policy, полноценная навигация этой целью не заявляется.
- Goal 016 не принят самостоятельно: требуется независимый review.

## Git

Branch: `feature/phantom-world`.

Required parent: `a546dae868d93d54ec4bc6e1836080b90f810167`.

Commit: этот ordinary Goal 016 commit с subject
`feat(phantoms): add population manager and schedules`; фактический SHA
фиксируется Git после создания и приводится в финальном сообщении.

Push: выполняется после двух byte-identical post-commit verifier runs в
`origin/feature/phantom-world`; фактический результат приводится в финальном
сообщении.
