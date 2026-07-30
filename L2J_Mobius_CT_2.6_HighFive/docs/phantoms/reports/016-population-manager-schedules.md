# Goal 016 — PopulationManager и schedules

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

Базовая Goal 016 из commit `92a0040f8eb919154067db6c6297b02c858b1b72`
завершена одним bounded safety completion без suffix-задач. Сохранены atomic
managed shells, target-driven population, shared scheduler hook, schedule/
ACTIVE admission, deterministic retirement/return и real materialization.

Completion закрывает findings независимого review:

- полностью packet-free POPULATION initializer при сохранённом CLIENT delivery;
- schema-v2 immutable creation authority и startup drift gate;
- exact durable projection, strict-subset repair и fail-closed extra rows;
- один explicit character store и read-only fresh verification под exact-object
  autosave suppression;
- explicit scheduler ownership retries;
- bounded pulse без полного `_entries` scan/sort;
- creation-pending target reduction, shutdown publication barrier и population
  snapshot в `PhantomSystem`.

Production defaults остаются `target=0`, `activeTarget=0`. `Player.java`,
schema, другие хроники и background/combat/commerce/progression/topology
semantics не менялись. Goal 017/025 не начаты.

## READ_SET

Использован закрытый read set completion:

1. `PhantomPopulationManager`, `Store`, `State`, `StateCodec`, `Catalog`.
2. `PlayerCreationInitializer`, `InitialShortcutData`,
   `PlayerAutoSaveTaskManager`.
3. `PhantomScheduler`, `PhantomSystem`.
4. Goal 016 population и performance suites.
5. verifier 016, architecture contract и этот report.

Четыре разрешённых exact symbol reads:

1. outbound-session attachment/sink;
2. MacroList, Shortcuts и SkillTreeData exact APIs;
3. Player status/inventory exact methods;
4. test-schema columns для relevant durable projection.

Локальные паттерны: atomic `createWithComponent`, bounded component codec,
shared scheduler statuses, retained materialization lifecycle, existing
CharacterCreate initializer delegation и deterministic test launcher.

## Changed files

Production:

- `InitialShortcutData.java`;
- `PlayerCreationInitializer.java`;
- `PlayerAutoSaveTaskManager.java`;
- `PhantomSystem.java`;
- `phantoms/population/PhantomPopulationManager.java`;
- `PhantomPopulationStore.java`;
- `PhantomPopulationState.java`;
- `PhantomPopulationStateCodec.java`;
- новые `PopulationInitializationContract.java`,
  `PhantomPopulationPersistencePort.java`,
  `PhantomPopulationOwnershipPort.java`.

Tests:

- `PhantomPopulationSuite.java`;
- `PhantomPopulationPerformanceSuite.java`;
- новый `PhantomPopulationTestDoubles.java`.

Process/docs:

- `verify-task-016.ps1`;
- `POPULATION_MANAGER_SCHEDULE_CONTRACT.md`;
- `016-population-manager-schedules.md`;
- status-only `docs/PHANTOM_BOTS_ROADMAP.md`.

Config, catalog, migrations и `build.xml` не изменялись.

## Architecture decisions

- `InitialShortcutData.InitialPlan` — immutable logical authority. CLIENT
  публикует прежние packets; POPULATION использует durable-only registration.
- `PopulationInitializationContract` фиксируется до writer и включает version,
  catalog/timezone/config/initializer, identity-independent creation facts,
  equipment, skills, shortcuts и macros.
- `population.state` schema 2 хранит authority hash. Bounded v1 decode даёт
  typed legacy-authority rejection, а не неявное принятие.
- Projection допускает только pristine, canonical или доказанный strict subset.
  Unknown/excess/conflicting facts не удаляются и дают `INCONSISTENT`.
- `INITIALIZATION_STORED` — durable marker единственного explicit store.
  Verification load не пишет Player state; autosave suppression scoped по
  текущему потоку и exact object ID.
- Manager использует persistence/ownership ports для deterministic real-manager
  tests. Retry heap хранит action, generation, attempt и due time.
- READY/region/creation/retired indexes обслуживаются при transition; pulse
  читает только bounded due/retry work и dirty admission slices.
- Lifecycle claims закрывают DB-commit/in-memory-publication race.

## Creation matrix

| Boundary | Restart outcome |
|---|---|
| shell/account/character intents | continue exact owned identity |
| each item/skill/shortcut/macro writer | add only missing expected facts |
| character store | resume from `INITIALIZATION_STORED`, no second store |
| fresh verification | repeat read-only load, byte-identical DB |
| profile link/READY update | retry optimistic durable transition |
| extra/conflicting row | typed `INCONSISTENT`, no unknown-row mutation |

Dynamic transport evidence: POPULATION outbound count `0`; CLIENT count is
positive and preserves legacy delivery.

## Schedule matrix

| Case | Evidence |
|---|---|
| midnight/DST/clock direction | latest-state deterministic evaluation |
| ACTIVE cap | `min(activeTarget, maxMaterialized)` |
| region distribution | largest remainder + daily deterministic rotation |
| 10 000 dirty READY | bounded admission over multiple pulses |
| 100 000 steady pulses | no DB writes and no full managed scan |

## Retirement matrix

| Stage | Target reduction/return |
|---|---|
| every creation stage | durable retire without finishing extra creation |
| READY | withdraw → unregister → absent → RETIRED |
| RETIRE_REQUESTED restart | resume exact ownership actions |
| RETIRED return | same lowest-ID profile/character before new shell |
| scheduler retry/conflict | backoff or typed `INCONSISTENT` |

## DB, configs and performance

Все DB tests используют только `l2jmobiush5_phantom_test`, seed `16001601`.
Миграций нет. Production `PhantomPopulationTarget` и
`PhantomPopulationActiveTarget` равны `0`.

Real-manager performance evidence:

- 100 000 steady control pulses: DB writes `0`;
- 10 000 dirty READY profiles: admission и daily rotation bounded per pulse;
- 100 000-profile memory smoke: без worker/task/Future на профиль.

## Verification

Focused results до final freeze:

- compile-tests: PASS;
- catalog: PASS 3/3;
- schedule: PASS 3/3;
- creation: PASS 6/6;
- reconciliation: PASS 3/3;
- lifecycle: PASS 3/3;
- server integration: PASS 1/1;
- performance smoke: PASS 3/3.

Terminal gate results после freeze:

- affected suites: skeleton 12/12, headless-player 18/18, production
  materialization 20/20, shutdown handoff 7/7, activity scheduler 20/20,
  decision core 35/35 — PASS;
- verifier 014A: `TASK014A_VERIFIER_OK`;
- verifier 015: `TASK015_VERIFIER_OK`;
- working verifier 016: `TASK016_VERIFIER_OK`, scope 18;
- единственный combat preflight: PASS 20/20;
- единственный final Goal 016 aggregate: PASS 22/22, 1:28;
- первый и единственный full `ant verify`: `BUILD SUCCESSFUL`, 12:21;
- standalone `ant jar`: `BUILD SUCCESSFUL`, 0:16;
- два byte-identical post-commit verifier 016 выполняются после immutable
  report/commit; их hashes и result приводятся в финальном ответе.

## Limits, encoding and usage

- No packets, fake `GameClient`, client packet handler или `OnPlayerCreate` в
  population path.
- Геодата и navigation semantics не входят в completion.
- Mojibake-маркеры в изменённых файлах проверены: 0.
- Escaped Cyrillic в изменённых файлах проверены отдельно: 0.
- Goal usage превысил 400k из-за унаследованного длинного Goal 016 run и
  обязательной real-DB fault-injection матрицы, restart projection диагностики,
  lifecycle publication race и повторных тяжёлых Ant suites после реальных
  source fixes; scope при этом оставался закрытым completion contract.

## Git

Branch: `feature/phantom-world`.

Required parent:
`92a0040f8eb919154067db6c6297b02c858b1b72`.

Commit subject:
`fix(phantoms): complete population safety contracts`.

Commit SHA и push result сообщаются в финальном ответе: report не изменяется
после ordinary commit и не требует amend.

Next step: независимый review Goal 016; Goal 017/025 остаются `NOT_STARTED`.
